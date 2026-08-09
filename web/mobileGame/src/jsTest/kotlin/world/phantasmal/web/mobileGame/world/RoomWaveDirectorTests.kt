package world.phantasmal.web.mobileGame.world

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The encounter data these run against is a miniature of the real thing: two rooms 320 apart on
 * the same grid Forest 1 uses, one of them a two-wave chain that opens a door at the end.
 */
class RoomWaveDirectorTests {
    private class TestEnemy : SpawnedEnemy {
        var dead = false
        override fun isDead() = dead
    }

    private val sections = listOf(
        SpawnSection(id = 1, x = .0, y = .0, z = .0),
        SpawnSection(id = 2, x = 320.0, y = .0, z = .0),
    )

    private fun enemy(section: Int, wave: Int, x: Double = .0) =
        SpawnEnemy("Booma", section, wave, x, .0, .0, .0)

    private fun layout(
        enemies: List<SpawnEnemy>,
        events: List<SpawnEvent>,
    ) = SpawnLayout("test", solo = true, enemies = enemies, events = events)

    /** Wave 1 of room 1 chains into wave 2, which then opens door 7. */
    private fun chainLayout() = layout(
        enemies = listOf(enemy(1, 1), enemy(1, 1, x = 10.0), enemy(1, 2)),
        events = listOf(
            SpawnEvent(id = 11, section = 1, wave = 1, delay = 30, triggers = listOf(12), doors = emptyList()),
            SpawnEvent(id = 12, section = 1, wave = 2, delay = 30, triggers = emptyList(), doors = listOf(7)),
        ),
    )

    private fun directorFor(layout: SpawnLayout, spawned: MutableList<TestEnemy>) =
        RoomWaveDirector(AreaSpawnTable(sections, listOf(layout)), layout) {
            TestEnemy().also { spawned.add(it) }
        }

    @Test
    fun spawnsNothingUntilThePlayerEntersTheRoom() {
        val spawned = mutableListOf<TestEnemy>()
        val director = directorFor(chainLayout(), spawned)

        // Standing in room 2, which this layout has no encounter for.
        director.update(0.1, playerX = 320.0, playerZ = .0)

        assertEquals(0, spawned.size)
    }

    @Test
    fun enteringARoomSpawnsItsFirstWaveOnlyOnce() {
        val spawned = mutableListOf<TestEnemy>()
        val director = directorFor(chainLayout(), spawned)

        director.update(0.1, playerX = .0, playerZ = .0)
        assertEquals(2, spawned.size)

        // Staying put must not keep re-spawning the wave.
        repeat(5) { director.update(0.1, playerX = .0, playerZ = .0) }
        assertEquals(2, spawned.size)
    }

    @Test
    fun theNextWaveWaitsForTheDelayAfterTheLastOneIsWipedOut() {
        val spawned = mutableListOf<TestEnemy>()
        val director = directorFor(chainLayout(), spawned)

        director.update(0.1, playerX = .0, playerZ = .0)
        spawned.forEach { it.dead = true }

        // Registers the wave as cleared, which starts the 30-frame (1 second) countdown.
        director.update(0.1, playerX = .0, playerZ = .0)
        assertEquals(2, spawned.size)

        director.update(0.5, playerX = .0, playerZ = .0)
        assertEquals(2, spawned.size, "wave 2 arrived before its delay had run")

        director.update(0.6, playerX = .0, playerZ = .0)
        assertEquals(3, spawned.size, "wave 2 never arrived")
    }

    @Test
    fun clearingTheLastWaveUnlocksTheRoomsDoors() {
        val spawned = mutableListOf<TestEnemy>()
        val director = directorFor(chainLayout(), spawned)

        director.update(0.1, playerX = .0, playerZ = .0)

        repeat(3) {
            spawned.forEach { e -> e.dead = true }
            director.update(0.1, playerX = .0, playerZ = .0)
            director.update(1.1, playerX = .0, playerZ = .0)
        }

        assertEquals(setOf(7), director.unlockedDoors)
    }

    /**
     * A wave whose placements were all dropped (a species with no roster entry) still has to hand
     * off to whatever it triggers, or the room dead-ends with its door shut and no way to open it.
     */
    @Test
    fun aWaveThatSpawnsNothingStillAdvancesTheChain() {
        val layout = layout(
            enemies = listOf(enemy(1, 2)),
            events = listOf(
                SpawnEvent(11, section = 1, wave = 1, delay = 0, triggers = listOf(12), doors = emptyList()),
                SpawnEvent(12, section = 1, wave = 2, delay = 0, triggers = emptyList(), doors = listOf(3)),
            ),
        )
        val spawned = mutableListOf<TestEnemy>()
        val director = directorFor(layout, spawned)

        director.update(0.1, playerX = .0, playerZ = .0)
        director.update(0.1, playerX = .0, playerZ = .0)

        assertEquals(1, spawned.size, "the empty wave 1 didn't hand off to wave 2")
    }

    @Test
    fun roomsAreIndependentOfEachOther() {
        val layout = layout(
            enemies = listOf(enemy(1, 1), enemy(2, 1, x = 320.0)),
            events = listOf(
                SpawnEvent(11, section = 1, wave = 1, delay = 30, triggers = emptyList(), doors = emptyList()),
                SpawnEvent(21, section = 2, wave = 1, delay = 30, triggers = emptyList(), doors = emptyList()),
            ),
        )
        val spawned = mutableListOf<TestEnemy>()
        val director = directorFor(layout, spawned)

        director.update(0.1, playerX = .0, playerZ = .0)
        assertEquals(1, spawned.size)

        // Walking into the second room starts that room, and only that room.
        director.update(0.1, playerX = 320.0, playerZ = .0)
        assertEquals(2, spawned.size)

        // Walking back into the first doesn't restart it.
        director.update(0.1, playerX = .0, playerZ = .0)
        assertEquals(2, spawned.size)
    }

    /** Two chains starting in the same room, as a couple of Forest 2's rooms really do. */
    @Test
    fun aRoomCanRunSeveralChainsAtOnce() {
        val layout = layout(
            enemies = listOf(enemy(1, 1), enemy(1, 2)),
            events = listOf(
                SpawnEvent(11, section = 1, wave = 1, delay = 30, triggers = emptyList(), doors = emptyList()),
                SpawnEvent(12, section = 1, wave = 2, delay = 30, triggers = emptyList(), doors = emptyList()),
            ),
        )
        val spawned = mutableListOf<TestEnemy>()
        val director = directorFor(layout, spawned)

        director.update(0.1, playerX = .0, playerZ = .0)

        assertEquals(2, spawned.size)
    }

    @Test
    fun aTriggerNamingAnUnknownEventEndsTheChainQuietly() {
        val layout = layout(
            enemies = listOf(enemy(1, 1)),
            events = listOf(
                SpawnEvent(11, section = 1, wave = 1, delay = 0, triggers = listOf(999), doors = listOf(4)),
            ),
        )
        val spawned = mutableListOf<TestEnemy>()
        val director = directorFor(layout, spawned)

        director.update(0.1, playerX = .0, playerZ = .0)
        spawned.forEach { it.dead = true }
        director.update(0.1, playerX = .0, playerZ = .0)
        director.update(0.1, playerX = .0, playerZ = .0)

        assertTrue(4 in director.unlockedDoors, "the door didn't open despite the wave being cleared")
    }
}

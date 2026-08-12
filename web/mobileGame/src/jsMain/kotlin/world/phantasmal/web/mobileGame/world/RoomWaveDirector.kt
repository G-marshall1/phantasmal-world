package world.phantasmal.web.mobileGame.world

/**
 * A live enemy the director is waiting on. The director only ever needs to know whether it's
 * still up, so the host can back this with whatever its own enemy representation is.
 */
fun interface SpawnedEnemy {
    fun isDead(): Boolean
}

/**
 * Runs an area's encounter the way PSO does: nothing is on the map until you walk into a room,
 * and each room then feeds you its waves one at a time, the next only starting once you've
 * cleared the last.
 *
 * All of the content comes from [AreaSpawnTable] -- which room holds what, in what order, after
 * how long a pause, and which doors it opens when you're done. This class is only the clock and
 * the bookkeeping; it invents no encounters of its own.
 *
 * The player is considered to be in whichever room's origin is nearest horizontally. The Forest's
 * rooms are an even grid of terrain tiles, so nearest-origin is exactly "which tile am I standing
 * on" -- no trigger radius to tune, and never two rooms active at once from one position.
 */
/**
 * One authored wave trigger: crossing its circle fires its event. These are the map's own
 * Event Collision objects -- the real game starts waves when you walk THROUGH places, not
 * when you're standing in a room's section, and the difference matters: chains that gate
 * energy barriers are triggered from the approach path, sometimes rooms away.
 */
class TriggerVolume(val x: Double, val z: Double, val radius: Double, val eventId: Int)

class RoomWaveDirector(
    table: AreaSpawnTable,
    private val layout: SpawnLayout,
    private val volumes: List<TriggerVolume> = emptyList(),
    private val spawn: (SpawnEnemy) -> SpawnedEnemy?,
) {
    // The caves' layout variants each carry their own terrain, and so their own section table.
    private val sections = layout.sections.ifEmpty { table.sections }
    private val eventsById = layout.events.associateBy { it.id }

    /** Every wave's enemies, keyed the way events name them. */
    private val enemiesByWave: Map<Pair<Int, Int>, List<SpawnEnemy>> =
        layout.enemies.groupBy { it.section to it.wave }

    /**
     * Each room's opening waves: the events no other event triggers. Rooms usually have exactly
     * one, but a few of Forest 2's run two chains side by side.
     */
    private val openingEvents: Map<Int, List<SpawnEvent>> =
        layout.events
            .filter { event -> layout.events.none { event.id in it.triggers } }
            .groupBy { it.section }

    /**
     * Every root event with where its own first wave would stand, for the proximity backstop:
     * a player standing among a wave's authored placements is *in that room*, whatever the
     * nearest-origin section test says. The section origins are room centres, and the Forest's
     * connector tiles put their origin closer to some rooms' far corners than the room's own --
     * standing at the sealed barrier of the third softlock resolves to the connector's section,
     * which runs no events, so a section-entry backstop alone still dead-ends there.
     */
    private val rootWavePlacements: List<Pair<SpawnEvent, List<SpawnEnemy>>> =
        layout.events
            .filter { event -> layout.events.none { event.id in it.triggers } }
            .map { event -> event to (enemiesByWave[event.section to event.wave] ?: emptyList()) }

    private val enteredSections = mutableSetOf<Int>()

    /**
     * Every event that has ever been started, by any mechanism. An event runs once, period:
     * without this, a trigger circle crossed *after* its chain had already run (or a room
     * re-entered) would spawn the same wave again.
     */
    private val startedEvents = mutableSetOf<Int>()

    /** Rooms the player has set foot in, for the area map to draw as explored. */
    val visitedSections: Set<Int> get() = enteredSections

    /** The room the player is standing in right now, or null before the first update. */
    var currentSectionId: Int? = null
        private set

    /** Every room this area has, whether or not it holds an encounter. */
    val allSections: List<SpawnSection> = sections

    /** Waves that are on the map right now, with the enemies each is waiting to have killed. */
    private val activeWaves = mutableMapOf<Int, MutableList<SpawnedEnemy>>()

    /** Cleared waves counting down their [SpawnEvent.delay] before whatever they trigger. */
    private val pending = mutableListOf<PendingEvent>()

    /** Doors this layout has opened so far, for the host to act on once field doors exist. */
    val unlockedDoors = mutableSetOf<Int>()

    /**
     * Rooms whose wave chain has run to its end -- the last event fired and triggered nothing
     * further. Energy barriers authored with no door ID (-1) drop on this, per the client's
     * own behaviour.
     */
    val completedSections = mutableSetOf<Int>()

    private class PendingEvent(val event: SpawnEvent, var remaining: Double)

    fun update(deltaTime: Double, playerX: Double, playerZ: Double) {
        currentSection(playerX, playerZ)?.let { section ->
            currentSectionId = section
            enteredSections.add(section)

            // Standing in a room backstops its own chains. The authored trigger circles are
            // the authority on when a chain starts -- they fire from the approach paths, often
            // rooms early -- but they are one-shot circles 35-70 units wide in rooms four times
            // that, and this recreation's collision lets a player walk lines the real game's
            // corridors never allow. A player who slipped past every circle on the way in used
            // to stand in a sealed room with no enemies and no way forward (the third Forest
            // softlock, and then a fourth); now the room itself starts what the approach
            // missed. [startWave]'s once-ever guard keeps the two mechanisms from ever
            // double-running a chain.
            openingEvents[section]?.forEach(::startWave)
        }

        for (volume in volumes) {
            if (volume.eventId in startedEvents) continue
            val dx = playerX - volume.x
            val dz = playerZ - volume.z
            if (dx * dx + dz * dz <= volume.radius * volume.radius) {
                eventsById[volume.eventId]?.let(::startWave)
            }
        }

        // The proximity backstop -- see [rootWavePlacements]. 220 sits above the farthest
        // placement-to-barrier distance in the sealed rooms (198, Forest 1's first solo
        // layout) and below the ~500-unit spacing between room origins, so it can't reach a
        // room the player isn't standing in.
        for ((event, placements) in rootWavePlacements) {
            if (event.id in startedEvents) continue
            val inRoom = placements.any { placement ->
                val dx = playerX - placement.x
                val dz = playerZ - placement.z
                dx * dx + dz * dz <= BACKSTOP_RADIUS * BACKSTOP_RADIUS
            }
            if (inRoom) startWave(event)
        }

        val cleared = activeWaves.entries.filter { (_, enemies) -> enemies.all { it.isDead() } }

        for ((eventId, _) in cleared) {
            activeWaves.remove(eventId)
            val event = eventsById[eventId] ?: continue
            pending.add(PendingEvent(event, event.delay / FRAMES_PER_SECOND))
        }

        val fired = mutableListOf<PendingEvent>()

        for (entry in pending) {
            entry.remaining -= deltaTime
            if (entry.remaining <= 0.0) fired.add(entry)
        }

        for (entry in fired) {
            pending.remove(entry)
            unlockedDoors.addAll(entry.event.doors)
            if (entry.event.triggers.isEmpty()) completedSections.add(entry.event.section)

            for (triggeredId in entry.event.triggers) {
                eventsById[triggeredId]?.let(::startWave)
                // A trigger naming an event this layout doesn't define is left alone: two of
                // Forest 2's tables really do have one, and it just means that chain ends there.
            }
        }
    }

    private fun startWave(event: SpawnEvent) {
        // Every path in (trigger circle, section entry, chain trigger) funnels through this
        // guard: an event that has ever started -- running OR already cleared -- never starts
        // again.
        if (!startedEvents.add(event.id)) return

        val spawned = enemiesByWave[event.section to event.wave].orEmpty().mapNotNull(spawn)

        // A wave that put nothing on the map still has to hand off, or the room stalls with its
        // door shut. Registering it empty makes the very next update see it as cleared, which
        // runs its actions through the normal path rather than a second special-cased one.
        activeWaves[event.id] = spawned.toMutableList()
    }

    private fun currentSection(x: Double, z: Double): Int? =
        sections.minByOrNull { section ->
            val dx = x - section.x
            val dz = z - section.z
            dx * dx + dz * dz
        }?.id

    private companion object {
        /** PSO's own tick rate, which is the unit event delays are counted in. */
        const val FRAMES_PER_SECOND = 30.0

        /** How close the player must stand to a root wave's placements to backstop-start it. */
        const val BACKSTOP_RADIUS = 220.0
    }
}

/**
 * Picks the layout to play. Only the offline tables are eligible: they're the single-player
 * densities, roughly half the enemies per wave of the online ones this also ships.
 *
 * [override] names one by [SpawnLayout.name] instead, for testing a particular room's waves
 * repeatedly rather than getting a different area every load.
 */
/**
 * The terrain variants this area has a solo encounter table for, as geometry slugs. Empty for
 * the forests, whose tables are untagged because one terrain serves them all.
 *
 * The free-roam geometry roll is limited to these. Several areas ship more terrain variants
 * than tables -- Ruins 1 has five sets of rooms but only three tables -- and rolling one of the
 * extras produced an area with no encounter *and* no Player Set to stand on, which dropped the
 * player through the floor on arrival. Those variants aren't dead: quests designate them
 * directly, which is what they exist for.
 */
fun AreaSpawnTable.soloGeometrySlugs(): List<String> =
    layouts.filter { it.solo }.mapNotNull { it.geometry }.distinct()

fun AreaSpawnTable.pickSoloLayout(override: String? = null, geometry: String? = null): SpawnLayout? =
    if (override != null) layouts.find { it.name == override }
    // A layout tagged with a geometry slug only fits that terrain (the caves); untagged tables
    // (the forests) fit whatever loaded.
    else layouts.filter { it.solo && (it.geometry == null || it.geometry == geometry) }.randomOrNull()

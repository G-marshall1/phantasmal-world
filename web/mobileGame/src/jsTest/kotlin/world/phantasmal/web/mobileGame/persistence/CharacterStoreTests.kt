package world.phantasmal.web.mobileGame.persistence

import kotlinx.browser.localStorage
import world.phantasmal.web.shared.dto.SectionId
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CharacterStoreTests {
    @AfterTest
    fun cleanUp() {
        localStorage.clear()
    }

    @Test
    fun saveAndLoadRoundTrips() {
        val store = CharacterStore()
        val save = CharacterSave(
            id = "1",
            name = "Testchar",
            characterClassSlug = "HUmar",
            sectionId = SectionId.Viridia,
            headIndex = 0,
            hairIndex = 3,
            accessoryEquipped = false,
            createdAtEpochMs = 0.0,
        )

        store.save(save)

        assertEquals(listOf(save), store.loadAll())
    }

    @Test
    fun saveUpsertsById() {
        val store = CharacterStore()
        val original = CharacterSave("1", "A", "HUmar", SectionId.Viridia, 0, 0, false, 0.0)
        val updated = original.copy(name = "B", hairIndex = 5)

        store.save(original)
        store.save(updated)

        assertEquals(listOf(updated), store.loadAll())
    }

    @Test
    fun deleteRemovesById() {
        val store = CharacterStore()
        val save = CharacterSave("1", "A", "HUmar", SectionId.Viridia, 0, 0, false, 0.0)

        store.save(save)
        store.delete("1")

        assertTrue(store.loadAll().isEmpty())
    }

    @Test
    fun nicknamePersists() {
        val store = CharacterStore()

        assertNull(store.loadLastNickname())

        store.saveLastNickname("Roger")

        assertEquals("Roger", store.loadLastNickname())
    }
}

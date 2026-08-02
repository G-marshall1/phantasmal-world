package world.phantasmal.web.mobileGame.persistence

import kotlinx.browser.localStorage
import kotlinx.serialization.json.Json

/**
 * Local-only character persistence (no backend exists). A single-purpose wrapper around
 * `localStorage`, not a generic KeyValueStore/Persister copied from :web's `core/persistence/` --
 * mobileGame only ever persists this one thing, so that extra abstraction layer isn't earning its
 * keep here, and mobileGame shouldn't depend on the separate :web app module anyway.
 */
class CharacterStore {
    private val format = Json { ignoreUnknownKeys = true }

    fun loadAll(): List<CharacterSave> =
        localStorage.getItem(CHARACTERS_KEY)?.let {
            try {
                format.decodeFromString<List<CharacterSave>>(it)
            } catch (e: Throwable) {
                emptyList()
            }
        } ?: emptyList()

    fun save(save: CharacterSave) {
        val updated = loadAll().filterNot { it.id == save.id } + save
        localStorage.setItem(CHARACTERS_KEY, format.encodeToString(updated))
    }

    fun delete(id: String) {
        localStorage.setItem(CHARACTERS_KEY, format.encodeToString(loadAll().filterNot { it.id == id }))
    }

    fun loadLastNickname(): String? = localStorage.getItem(NICKNAME_KEY)

    fun saveLastNickname(nickname: String) {
        localStorage.setItem(NICKNAME_KEY, nickname)
    }

    companion object {
        private const val CHARACTERS_KEY = "phantasmal.mobileGame.characters"
        private const val NICKNAME_KEY = "phantasmal.mobileGame.lastNickname"
    }
}

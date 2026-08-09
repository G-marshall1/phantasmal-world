package world.phantasmal.web.mobileGame.loading

import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Uint8Array
import world.phantasmal.web.core.loading.AssetLoader

/**
 * An [AssetLoader] that prefers the git-ignored personal-asset overlay for the per-class player
 * texture archives: the bundled copyright-free psov2 set genuinely contains one body texture per
 * class (verified by hashing -- its variant slots are padding duplicates), so the real archives
 * with every body variant live in `assets/skin/player/` on personal builds only and are used
 * when present.
 *
 * The override is validated by magic bytes rather than HTTP status: a missing file inside the
 * packaged app returns an error page whose bytes would otherwise be handed to the AFS parser.
 * Anything that isn't a real AFS falls back to the bundled asset.
 */
class SkinAssetLoader : AssetLoader() {
    override suspend fun loadArrayBuffer(path: String): ArrayBuffer {
        if (path.startsWith("/player/") && path.endsWith("Tex.afs")) {
            try {
                val buffer = super.loadArrayBuffer("/skin$path")
                if (isAfs(buffer)) return buffer
            } catch (e: Throwable) {
                // Fall through to the bundled archive.
            }
        }

        return super.loadArrayBuffer(path)
    }

    private fun isAfs(buffer: ArrayBuffer): Boolean {
        if (buffer.byteLength < 4) return false
        val bytes = Uint8Array(buffer, 0, 4)
        return bytes.asDynamic()[0] == 'A'.code && bytes.asDynamic()[1] == 'F'.code &&
            bytes.asDynamic()[2] == 'S'.code
    }
}

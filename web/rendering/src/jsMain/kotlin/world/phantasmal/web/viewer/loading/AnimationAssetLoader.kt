package world.phantasmal.web.viewer.loading

import world.phantasmal.psolib.Endianness
import world.phantasmal.psolib.cursor.cursor
import world.phantasmal.psolib.fileFormats.ninja.NjMotion
import world.phantasmal.psolib.fileFormats.ninja.parseNjm
import world.phantasmal.web.core.loading.AssetLoader
import world.phantasmal.web.core.loading.LoadingCache
import world.phantasmal.webui.DisposableContainer

class AnimationAssetLoader(private val assetLoader: AssetLoader) : DisposableContainer() {
    private val ninjaMotionCache: LoadingCache<String, NjMotion> =
        addDisposable(LoadingCache(::loadNinjaMotion) { /* Nothing to dispose. */ })

    /**
     * [boneCount] is the animated skeleton's real bone count. The v2 NJM format doesn't record
     * it, and without it parseNjm has to guess where the per-bone offset table ends -- a guess
     * that can stop short and freeze every bone past it (arms that never rise, props that never
     * reach their full height). Pass it whenever the model is known; it's part of the cache key
     * so the same file parsed for different skeletons can't collide.
     */
    suspend fun loadAnimation(filePath: String, boneCount: Int? = null): NjMotion =
        ninjaMotionCache.get("$filePath#${boneCount ?: 0}")

    private suspend fun loadNinjaMotion(key: String): NjMotion {
        val separator = key.lastIndexOf('#')
        val filePath = key.substring(0, separator)
        val boneCount = key.substring(separator + 1).toInt().takeIf { it > 0 }
        return parseNjm(
            assetLoader.loadArrayBuffer(filePath).cursor(Endianness.Little),
            boneCount,
        )
    }
}

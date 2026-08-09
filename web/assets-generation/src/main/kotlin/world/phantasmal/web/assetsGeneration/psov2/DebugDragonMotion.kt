package world.phantasmal.web.assetsGeneration.psov2

import java.io.File
import kotlin.math.abs
import world.phantasmal.psolib.Endianness
import world.phantasmal.psolib.buffer.Buffer
import world.phantasmal.psolib.cursor.cursor
import world.phantasmal.psolib.fileFormats.ninja.NjKeyframeTrack
import world.phantasmal.psolib.fileFormats.ninja.NjObject
import world.phantasmal.psolib.fileFormats.ninja.parseNj
import world.phantasmal.psolib.fileFormats.ninja.parseNjm

/**
 * Scratch diagnostic: parses the Dragon's model and a few of its clips exactly the way the
 * mobile game's EnemyAssetLoader does, and prints per-bone track statistics -- looking for the
 * point where the data stops making sense (bones with no tracks, or keyframe values far outside
 * plausible ranges).
 */
fun main(args: Array<String>) {
    val assets = File(args[0])

    fun loadModel(name: String): NjObject {
        val objects = parseNj(Buffer.fromByteArray(File(assets, "npcs/$name.nj").readBytes(), Endianness.Little).cursor())
            .unwrap()
        println("$name: parseNj returned ${objects.size} root object(s)")
        return objects.first()
    }

    val dragon = loadModel("Dragon")
    var boneCount = 0
    while (dragon.getBone(boneCount) != null) boneCount++
    println("Dragon boneCount (getBone walk) = $boneCount")

    val booma = loadModel("Booma")
    var boomaBones = 0
    while (booma.getBone(boomaBones) != null) boomaBones++
    println("Booma boneCount = $boomaBones")

    for (clip in listOf("walk", "stand", "fire")) {
        val file = File(assets, "npcs/Dragon/${clip}_boss1_s_nb_dragon.njm")
        val motion = parseNjm(Buffer.fromByteArray(file.readBytes(), Endianness.Little).cursor(), boneCount = boneCount)
        println("\n=== Dragon $clip: frames=${motion.frameCount} motionData entries=${motion.motionData.size}")

        var silent = 0
        var suspicious = 0
        for ((i, md) in motion.motionData.withIndex()) {
            if (md.tracks.isEmpty() || md.tracks.all { it.keyframes.isEmpty() }) {
                silent++
                continue
            }
            for (track in md.tracks) {
                when (track) {
                    is NjKeyframeTrack.Position -> {
                        val maxAbs = track.keyframes.maxOfOrNull {
                            maxOf(abs(it.value.x), abs(it.value.y), abs(it.value.z))
                        } ?: 0f
                        if (maxAbs > 1_000f) {
                            suspicious++
                            println("  bone $i POS max |v| = $maxAbs over ${track.keyframes.size} keys")
                        }
                    }
                    is NjKeyframeTrack.EulerAngles -> {
                        val maxAbs = track.keyframes.maxOfOrNull {
                            maxOf(abs(it.value.x), abs(it.value.y), abs(it.value.z))
                        } ?: 0f
                        if (maxAbs > 7f) {
                            suspicious++
                            println("  bone $i ANG max |rad| = $maxAbs over ${track.keyframes.size} keys")
                        }
                    }
                    is NjKeyframeTrack.Scale -> {}
                    is NjKeyframeTrack.Quaternion -> println("  bone $i has QUATERNION track (${track.keyframes.size} keys)")
                }
            }
        }
        println("  bones with no animation: $silent / ${motion.motionData.size}; suspicious tracks: $suspicious")

        // Frame coverage: the first and last keyframe of the first few animated bones.
        motion.motionData.take(4).forEachIndexed { i, md ->
            md.tracks.forEach { t ->
                if (t.keyframes.isNotEmpty()) {
                    println("  bone $i ${t::class.simpleName}: keys=${t.keyframes.size} frames ${t.keyframes.first().frame}..${t.keyframes.last().frame}")
                }
            }
        }
    }

    // The same walk parse but with the OLD heuristic (no bone count), to see what it guessed.
    val walkNoHint = parseNjm(
        Buffer.fromByteArray(
            File(assets, "npcs/Dragon/walk_boss1_s_nb_dragon.njm").readBytes(),
            Endianness.Little,
        ).cursor(),
    )
    println("\nDragon walk WITHOUT boneCount hint: motionData entries=${walkNoHint.motionData.size}")
}

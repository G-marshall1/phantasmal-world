package world.phantasmal.web.mobileGame.input

import kotlin.math.PI
import kotlinx.browser.document
import org.w3c.dom.CanvasRenderingContext2D
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.HTMLElement
import world.phantasmal.core.disposable.TrackedDisposable

/**
 * Top-right radar: the actual map, the enemies on it, and the player. The area's walkable
 * collision triangles are rendered once into an offscreen bitmap at load ([setMapGeometry]);
 * every frame [update] crops a player-centred window out of it, then stamps living enemies as
 * red dots and the player as an arrow that turns with their facing -- the original game's radar,
 * without its rotation (north stays up).
 */
class MiniMap(container: HTMLElement) : TrackedDisposable() {
    private val root = (document.createElement("div") as HTMLElement).also { el ->
        el.className = "pw-hud-minimap"
        container.appendChild(el)
    }

    private val canvas = (document.createElement("canvas") as HTMLCanvasElement).also { el ->
        el.width = CANVAS_SIZE
        el.height = CANVAS_SIZE
        el.style.width = "100%"
        el.style.height = "100%"
        root.appendChild(el)
    }

    private val ctx = canvas.getContext("2d") as CanvasRenderingContext2D

    private val styleTag = (document.createElement("style") as HTMLElement).also { el ->
        el.textContent = STYLESHEET
        document.head!!.appendChild(el)
    }

    /** The whole area's floor, drawn once. Null until the map's geometry arrives. */
    private var mapBitmap: HTMLCanvasElement? = null
    private var worldMinX = 0.0
    private var worldMinZ = 0.0
    private var worldScale = 1.0

    /**
     * Renders the area's walkable triangles (flat [x1,z1,x2,z2,x3,z3] world coordinates) into
     * the offscreen bitmap the per-frame crop reads from.
     */
    fun setMapGeometry(triangles: List<DoubleArray>) {
        if (triangles.isEmpty()) {
            mapBitmap = null
            return
        }

        var minX = Double.MAX_VALUE
        var maxX = -Double.MAX_VALUE
        var minZ = Double.MAX_VALUE
        var maxZ = -Double.MAX_VALUE
        for (t in triangles) {
            for (i in 0 until 6 step 2) {
                if (t[i] < minX) minX = t[i]
                if (t[i] > maxX) maxX = t[i]
                if (t[i + 1] < minZ) minZ = t[i + 1]
                if (t[i + 1] > maxZ) maxZ = t[i + 1]
            }
        }
        // A margin so the crop window can slide past the map's edge without sampling outside
        // the bitmap.
        val margin = WINDOW_WORLD_UNITS
        minX -= margin; maxX += margin; minZ -= margin; maxZ += margin

        val span = maxOf(maxX - minX, maxZ - minZ)
        val bitmap = document.createElement("canvas") as HTMLCanvasElement
        bitmap.width = OFFSCREEN_SIZE
        bitmap.height = OFFSCREEN_SIZE
        val bctx = bitmap.getContext("2d") as CanvasRenderingContext2D

        worldMinX = minX
        worldMinZ = minZ
        worldScale = OFFSCREEN_SIZE / span

        bctx.fillStyle = "rgba(4, 18, 26, 1)"
        bctx.fillRect(0.0, 0.0, OFFSCREEN_SIZE.toDouble(), OFFSCREEN_SIZE.toDouble())

        bctx.fillStyle = "rgba(96, 160, 176, 0.85)"
        bctx.beginPath()
        for (t in triangles) {
            bctx.moveTo((t[0] - minX) * worldScale, (t[1] - minZ) * worldScale)
            bctx.lineTo((t[2] - minX) * worldScale, (t[3] - minZ) * worldScale)
            bctx.lineTo((t[4] - minX) * worldScale, (t[5] - minZ) * worldScale)
            bctx.closePath()
        }
        bctx.fill()

        mapBitmap = bitmap
    }

    /** Redraws the radar around the player. [enemies] are the living enemies' world (x, z). */
    fun update(
        playerX: Double,
        playerZ: Double,
        playerYaw: Double,
        enemies: List<Pair<Double, Double>>,
    ) {
        val bitmap = mapBitmap ?: return
        val size = CANVAS_SIZE.toDouble()

        // The crop: a WINDOW_WORLD_UNITS-wide square centred on the player.
        val cropSize = WINDOW_WORLD_UNITS * worldScale
        val cropX = (playerX - worldMinX) * worldScale - cropSize / 2
        val cropZ = (playerZ - worldMinZ) * worldScale - cropSize / 2
        ctx.clearRect(0.0, 0.0, size, size)
        ctx.drawImage(bitmap, cropX, cropZ, cropSize, cropSize, 0.0, 0.0, size, size)

        val viewScale = size / WINDOW_WORLD_UNITS

        // Enemies: red dots, only inside the window.
        ctx.fillStyle = "#ff4438"
        for ((ex, ez) in enemies) {
            val sx = (ex - playerX) * viewScale + size / 2
            val sz = (ez - playerZ) * viewScale + size / 2
            if (sx < 0 || sx > size || sz < 0 || sz > size) continue
            ctx.beginPath()
            ctx.arc(sx, sz, DOT_RADIUS, 0.0, 2 * PI)
            ctx.fill()
        }

        // The player: an arrow at centre, turning with their facing. Facing +Z (yaw 0) points
        // down the canvas, since +Z is drawn downward.
        ctx.save()
        ctx.translate(size / 2, size / 2)
        ctx.rotate(PI - playerYaw)
        ctx.fillStyle = "#ffd24d"
        ctx.beginPath()
        ctx.moveTo(0.0, -ARROW_SIZE)
        ctx.lineTo(ARROW_SIZE * 0.7, ARROW_SIZE)
        ctx.lineTo(0.0, ARROW_SIZE * 0.45)
        ctx.lineTo(-ARROW_SIZE * 0.7, ARROW_SIZE)
        ctx.closePath()
        ctx.fill()
        ctx.restore()
    }

    override fun dispose() {
        root.remove()
        styleTag.remove()
        super.dispose()
    }

    companion object {
        /** On-screen backing resolution; the CSS box is half this for a crisp retina draw. */
        private const val CANVAS_SIZE = 240
        private const val OFFSCREEN_SIZE = 1024

        /** How much world the radar shows edge to edge, in world units. */
        private const val WINDOW_WORLD_UNITS = 340.0

        private const val DOT_RADIUS = 7.0
        private const val ARROW_SIZE = 13.0

        private const val STYLESHEET = """
            .pw-hud-minimap {
              position: fixed;
              top: calc(12px + var(--pw-safe-top));
              right: calc(12px + var(--pw-safe-right));
              width: 120px;
              height: 120px;
              z-index: 15;
              border: 2px solid rgba(140,220,220,.6);
              border-radius: 6px;
              overflow: hidden;
              background: rgba(4,18,26,.9);
              box-shadow: 0 0 6px rgba(0,0,0,.6);
              pointer-events: none;
              user-select: none;
            }
        """
    }
}

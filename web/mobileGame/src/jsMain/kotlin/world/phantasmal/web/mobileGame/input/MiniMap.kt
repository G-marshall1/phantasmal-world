package world.phantasmal.web.mobileGame.input

import kotlin.math.PI
import kotlinx.browser.document
import org.w3c.dom.CanvasRenderingContext2D
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.HTMLElement
import org.w3c.dom.pointerevents.PointerEvent
import world.phantasmal.core.disposable.Disposable
import world.phantasmal.core.disposable.TrackedDisposable
import world.phantasmal.webui.dom.disposableListener

/**
 * One door as the map draws it: the two ends of the doorway it blocks (the gate's own blocking
 * segment, so the drawn bar spans exactly the gap the door fills), and whether it's still
 * locked.
 */
class MapDoor(
    val ax: Double,
    val az: Double,
    val bx: Double,
    val bz: Double,
    val open: Boolean,
) {
    val x: Double get() = (ax + bx) / 2
    val z: Double get() = (az + bz) / 2
}

/** One of the map's rooms, for the fog of war: its id and where its floor sits. */
class MapRoom(val id: Int, val x: Double, val z: Double)

/**
 * The radar, and the full map it opens into.
 *
 * The area's walkable collision is rendered once into an offscreen bitmap, but only the rooms
 * the player has actually walked into: every triangle is assigned to the nearest room origin --
 * the same nearest-origin rule the wave director uses to decide which room the player is
 * standing in -- and unvisited rooms simply aren't drawn. The bitmap is rebuilt whenever that
 * visited set grows, so the map fills in as the area is explored.
 *
 * Tapping the radar opens a large, borderless, semi-transparent version over the middle of the
 * screen; tapping again (or tapping the big map) closes it.
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

    /** The expanded map: centre screen, borderless, see-through, hidden until asked for. */
    private val fullRoot = (document.createElement("div") as HTMLElement).also { el ->
        el.className = "pw-hud-fullmap"
        container.appendChild(el)
    }

    private val fullCanvas = (document.createElement("canvas") as HTMLCanvasElement).also { el ->
        el.width = FULL_CANVAS_SIZE
        el.height = FULL_CANVAS_SIZE
        el.style.width = "100%"
        el.style.height = "100%"
        fullRoot.appendChild(el)
    }

    private val fullCtx = fullCanvas.getContext("2d") as CanvasRenderingContext2D

    private val styleTag = (document.createElement("style") as HTMLElement).also { el ->
        el.textContent = STYLESHEET
        document.head!!.appendChild(el)
    }

    private val listeners = mutableListOf<Disposable>()

    var isFullMapOpen = false
        private set

    /** The full map's own view: a pan offset in world units and a zoom about its centre. */
    private var panX = 0.0
    private var panZ = 0.0
    private var zoom = 1.0

    /** Pointer bookkeeping for dragging and pinching the full map. */
    private val mapPointers = LinkedHashMap<Int, Pair<Int, Int>>()
    private var mapDragMoved = false
    private var lastPinchSpan: Double? = null
    /** World units per screen pixel at the current view -- set each draw, used by the drag. */
    private var fullMapWorldPerPixel = 1.0

    init {
        // The radar is pointer-transparent for the game's sake, so its own tap target is a
        // sibling button sitting exactly over it.
        val hit = (document.createElement("div") as HTMLElement).also { el ->
            el.className = "pw-hud-minimap-hit"
            container.appendChild(el)
        }
        listeners.add(hit.disposableListener<PointerEvent>("pointerdown", { toggleFullMap() }))

        // The full map is draggable: a tap closes it, a drag moves the view, two fingers zoom.
        listeners.add(
            fullRoot.disposableListener<PointerEvent>("pointerdown", { e: PointerEvent ->
                if (mapPointers.size < 2) {
                    mapPointers[e.pointerId] = e.clientX to e.clientY
                    if (mapPointers.size == 1) mapDragMoved = false
                    if (mapPointers.size == 2) lastPinchSpan = pinchSpan()
                }
            })
        )
        listeners.add(
            fullRoot.disposableListener<PointerEvent>("pointermove", { e: PointerEvent ->
                val last = mapPointers[e.pointerId] ?: return@disposableListener

                if (mapPointers.size == 2) {
                    mapPointers[e.pointerId] = e.clientX to e.clientY
                    val span = pinchSpan()
                    lastPinchSpan?.let { previous ->
                        if (previous > 1.0 && span > 1.0) {
                            zoom = (zoom * span / previous).coerceIn(ZOOM_MIN, ZOOM_MAX)
                        }
                    }
                    lastPinchSpan = span
                    mapDragMoved = true
                    return@disposableListener
                }

                val dx = e.clientX - last.first
                val dz = e.clientY - last.second
                mapPointers[e.pointerId] = e.clientX to e.clientY
                if (dx != 0 || dz != 0) mapDragMoved = true
                // Drag the map with the finger: the view slides opposite the pointer.
                panX -= dx * fullMapWorldPerPixel
                panZ -= dz * fullMapWorldPerPixel
            })
        )
        listeners.add(
            fullRoot.disposableListener<PointerEvent>("pointerup", { e: PointerEvent ->
                mapPointers.remove(e.pointerId)
                lastPinchSpan = null
                if (mapPointers.isEmpty() && !mapDragMoved) toggleFullMap()
            })
        )
        listeners.add(
            fullRoot.disposableListener<PointerEvent>("pointercancel", { e: PointerEvent ->
                mapPointers.remove(e.pointerId)
                lastPinchSpan = null
            })
        )
    }

    private fun pinchSpan(): Double {
        val points = mapPointers.values.toList()
        if (points.size < 2) return 0.0
        val dx = (points[0].first - points[1].first).toDouble()
        val dy = (points[0].second - points[1].second).toDouble()
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }

    fun toggleFullMap() {
        isFullMapOpen = !isFullMapOpen
        fullRoot.style.display = if (isFullMapOpen) "block" else "none"
        // Every opening starts framed on the explored area again, however it was left.
        if (isFullMapOpen) {
            panX = 0.0
            panZ = 0.0
            zoom = 1.0
        }
    }

    fun closeFullMap() {
        if (!isFullMapOpen) return
        isFullMapOpen = false
        fullRoot.style.display = "none"
    }

    // --- The area's geometry, kept so the bitmap can be rebuilt as rooms are discovered ---

    private var triangles: List<DoubleArray> = emptyList()
    private var rooms: List<MapRoom> = emptyList()
    /** Room id per triangle, resolved once both the geometry and the room list have arrived. */
    private var triangleRoom: IntArray = IntArray(0)

    private var mapBitmap: HTMLCanvasElement? = null
    private var renderedRooms: Set<Int> = emptySet()

    /** The explored area's own world bounds -- what the full map frames. */
    private var seenMinX = 0.0
    private var seenMaxX = 0.0
    private var seenMinZ = 0.0
    private var seenMaxZ = 0.0
    private var hasSeenBounds = false
    private var worldMinX = 0.0
    private var worldMinZ = 0.0
    private var worldScale = 1.0

    /** The area's walkable triangles, flat [x1,z1,x2,z2,x3,z3] in world space. */
    fun setMapGeometry(triangles: List<DoubleArray>) {
        this.triangles = triangles
        triangleRoom = IntArray(0)
        doorSideRooms.clear()
        mapBitmap = null
        renderedRooms = emptySet()

        if (triangles.isEmpty()) return

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
        // A margin all round so the radar's crop window can slide past the map's edge without
        // sampling outside the bitmap.
        val margin = WINDOW_WORLD_UNITS
        worldMinX = minX - margin
        worldMinZ = minZ - margin
        val span = maxOf(maxX - minX, maxZ - minZ, 1.0) + margin * 2
        worldScale = OFFSCREEN_SIZE / span
    }

    /** The area's rooms. Assigning triangles to them is what makes the fog of war possible. */
    fun setRooms(rooms: List<MapRoom>) {
        this.rooms = rooms
        triangleRoom = IntArray(0)
        doorSideRooms.clear()
        mapBitmap = null
        renderedRooms = emptySet()
    }

    /** The doorway lines the flood fill below must not cross, each [ax, az, bx, bz]. */
    private var doorBarriers: List<DoubleArray> = emptyList()

    /**
     * Records the map's doorways as hard boundaries for room attribution, invalidating the
     * current assignment if they weren't known yet. Doors exist by the time the first frame
     * draws, so in practice this runs once per map.
     */
    private fun rememberDoorBarriers(doors: List<MapDoor>) {
        if (doorBarriers.isNotEmpty() || doors.isEmpty()) return
        doorSideRooms.clear()
        doorBarriers = doors.map { door ->
            // Extended past each end so links squeezing around a doorpost still count as
            // crossing -- the seam between wall and door isn't pixel-tight in the data.
            val ex = (door.bx - door.ax) * DOOR_BARRIER_EXTENSION
            val ez = (door.bz - door.az) * DOOR_BARRIER_EXTENSION
            doubleArrayOf(door.ax - ex, door.az - ez, door.bx + ex, door.bz + ez)
        }
        triangleRoom = IntArray(0)
        mapBitmap = null
    }

    /**
     * Which room each triangle belongs to. Grown by flood fill from each room's origin, never
     * expanding across a doorway: a room the player hasn't entered stays dark right up to its
     * gate, instead of its near half leaking into the corridor's fog simply for being closer
     * to the corridor's origin -- which is exactly what let the old nearest-origin rule show
     * half a locked room through its door. Isolated leftovers fall back to nearest-origin.
     */
    private fun assignTriangles() {
        if (triangles.isEmpty() || rooms.isEmpty()) return
        val n = triangles.size

        val centroidX = DoubleArray(n)
        val centroidZ = DoubleArray(n)
        for (i in 0 until n) {
            val t = triangles[i]
            centroidX[i] = (t[0] + t[2] + t[4]) / 3
            centroidZ[i] = (t[1] + t[3] + t[5]) / 3
        }

        // Adjacency through shared edges, keyed on rounded endpoints so mesh seams that agree
        // to within half a unit still connect.
        fun vertexKey(x: Double, z: Double) = "${(x * 2).toInt()},${(z * 2).toInt()}"
        val edgeToTriangles = HashMap<String, MutableList<Int>>()
        for (i in 0 until n) {
            val t = triangles[i]
            for (e in 0 until 3) {
                val ax = t[e * 2]; val az = t[e * 2 + 1]
                val bx = t[(e * 2 + 2) % 6]; val bz = t[(e * 2 + 3) % 6]
                val ka = vertexKey(ax, az); val kb = vertexKey(bx, bz)
                val key = if (ka < kb) "$ka|$kb" else "$kb|$ka"
                edgeToTriangles.getOrPut(key) { mutableListOf() }.add(i)
            }
        }

        fun crossesDoor(x1: Double, z1: Double, x2: Double, z2: Double): Boolean {
            for (seg in doorBarriers) {
                val o1 = (seg[0] - x1) * (z2 - z1) - (seg[1] - z1) * (x2 - x1)
                val o2 = (seg[2] - x1) * (z2 - z1) - (seg[3] - z1) * (x2 - x1)
                if (o1 * o2 >= 0) continue
                val o3 = (x1 - seg[0]) * (seg[3] - seg[1]) - (z1 - seg[1]) * (seg[2] - seg[0])
                val o4 = (x2 - seg[0]) * (seg[3] - seg[1]) - (z2 - seg[1]) * (seg[2] - seg[0])
                if (o3 * o4 < 0) return true
            }
            return false
        }

        // Every room claims its nearest triangle as a seed, then the claims grow outward
        // together, halting at doorways.
        val owner = IntArray(n) { -1 }
        val queue = ArrayDeque<Int>()
        for (room in rooms) {
            var seed = -1
            var seedD = Double.MAX_VALUE
            for (i in 0 until n) {
                val dx = centroidX[i] - room.x
                val dz = centroidZ[i] - room.z
                val d = dx * dx + dz * dz
                if (d < seedD) { seedD = d; seed = i }
            }
            if (seed >= 0 && owner[seed] == -1) {
                owner[seed] = room.id
                queue.add(seed)
            }
        }

        while (queue.isNotEmpty()) {
            val i = queue.removeFirst()
            val t = triangles[i]
            for (e in 0 until 3) {
                val ax = t[e * 2]; val az = t[e * 2 + 1]
                val bx = t[(e * 2 + 2) % 6]; val bz = t[(e * 2 + 3) % 6]
                val ka = vertexKey(ax, az); val kb = vertexKey(bx, bz)
                val key = if (ka < kb) "$ka|$kb" else "$kb|$ka"
                for (j in edgeToTriangles[key] ?: continue) {
                    if (owner[j] != -1) continue
                    if (crossesDoor(centroidX[i], centroidZ[i], centroidX[j], centroidZ[j])) continue
                    owner[j] = owner[i]
                    queue.add(j)
                }
            }
        }

        // Anything the fill couldn't reach keeps the old nearest-origin rule.
        triangleRoom = IntArray(n) { i ->
            if (owner[i] != -1) owner[i]
            else {
                var best = -1
                var bestD = Double.MAX_VALUE
                for (room in rooms) {
                    val dx = centroidX[i] - room.x
                    val dz = centroidZ[i] - room.z
                    val d = dx * dx + dz * dz
                    if (d < bestD) { bestD = d; best = room.id }
                }
                best
            }
        }
    }

    /** Redraws the offscreen bitmap with only the rooms the player has seen. */
    private fun renderBitmap(visited: Set<Int>) {
        if (triangles.isEmpty()) return
        if (triangleRoom.isEmpty()) assignTriangles()

        val bitmap = mapBitmap ?: (document.createElement("canvas") as HTMLCanvasElement).also {
            it.width = OFFSCREEN_SIZE
            it.height = OFFSCREEN_SIZE
            mapBitmap = it
        }
        val bctx = bitmap.getContext("2d") as CanvasRenderingContext2D
        bctx.clearRect(0.0, 0.0, OFFSCREEN_SIZE.toDouble(), OFFSCREEN_SIZE.toDouble())

        bctx.fillStyle = FLOOR_COLOR
        bctx.beginPath()
        hasSeenBounds = false
        seenMinX = Double.MAX_VALUE; seenMaxX = -Double.MAX_VALUE
        seenMinZ = Double.MAX_VALUE; seenMaxZ = -Double.MAX_VALUE

        for ((index, t) in triangles.withIndex()) {
            // No room list (or no assignment): draw everything, as the map did before.
            if (triangleRoom.isNotEmpty() && triangleRoom[index] !in visited) continue
            bctx.moveTo((t[0] - worldMinX) * worldScale, (t[1] - worldMinZ) * worldScale)
            bctx.lineTo((t[2] - worldMinX) * worldScale, (t[3] - worldMinZ) * worldScale)
            bctx.lineTo((t[4] - worldMinX) * worldScale, (t[5] - worldMinZ) * worldScale)
            bctx.closePath()

            hasSeenBounds = true
            for (i in 0 until 6 step 2) {
                if (t[i] < seenMinX) seenMinX = t[i]
                if (t[i] > seenMaxX) seenMaxX = t[i]
                if (t[i + 1] < seenMinZ) seenMinZ = t[i + 1]
                if (t[i + 1] > seenMaxZ) seenMaxZ = t[i + 1]
            }
        }
        bctx.fill()

        renderedRooms = visited.toSet()
    }

    /**
     * Redraws both maps. [visitedRooms] is what the player has explored -- rooms outside it stay
     * dark -- and [doors] are drawn red while locked, green once open.
     */
    fun update(
        playerX: Double,
        playerZ: Double,
        playerYaw: Double,
        enemies: List<Pair<Double, Double>>,
        doors: List<MapDoor> = emptyList(),
        visitedRooms: Set<Int> = emptySet(),
    ) {
        if (triangles.isEmpty()) return
        rememberDoorBarriers(doors)
        if (mapBitmap == null || renderedRooms != visitedRooms) renderBitmap(visitedRooms)
        val bitmap = mapBitmap ?: return

        drawRadar(bitmap, playerX, playerZ, playerYaw, enemies, doors)
        if (isFullMapOpen) drawFullMap(bitmap, playerX, playerZ, playerYaw, enemies, doors)
    }

    /** The corner radar: a window on the map, centred on the player. */
    private fun drawRadar(
        bitmap: HTMLCanvasElement,
        playerX: Double,
        playerZ: Double,
        playerYaw: Double,
        enemies: List<Pair<Double, Double>>,
        doors: List<MapDoor>,
    ) {
        val size = CANVAS_SIZE.toDouble()
        val cropSize = WINDOW_WORLD_UNITS * worldScale
        val cropX = (playerX - worldMinX) * worldScale - cropSize / 2
        val cropZ = (playerZ - worldMinZ) * worldScale - cropSize / 2

        ctx.clearRect(0.0, 0.0, size, size)
        ctx.fillStyle = BACKDROP_COLOR
        ctx.fillRect(0.0, 0.0, size, size)
        ctx.drawImage(bitmap, cropX, cropZ, cropSize, cropSize, 0.0, 0.0, size, size)

        val viewScale = size / WINDOW_WORLD_UNITS
        drawMarkers(
            ctx, size, playerX, playerZ, playerYaw, enemies,
            doors.filter { isDoorVisible(it) },
            toScreenX = { x -> (x - playerX) * viewScale + size / 2 },
            toScreenZ = { z -> (z - playerZ) * viewScale + size / 2 },
        )
    }

    /** The full map: the whole explored area, fitted to the overlay. */
    private fun drawFullMap(
        bitmap: HTMLCanvasElement,
        playerX: Double,
        playerZ: Double,
        playerYaw: Double,
        enemies: List<Pair<Double, Double>>,
        doors: List<MapDoor>,
    ) {
        val size = FULL_CANVAS_SIZE.toDouble()
        fullCtx.clearRect(0.0, 0.0, size, size)

        // Frame what has actually been explored rather than the whole area: one room out of a
        // cave's full extent is a speck otherwise.
        val pad = FULL_MAP_PADDING_UNITS
        val boundsMinX = if (hasSeenBounds) seenMinX - pad else worldMinX
        val boundsMaxX = if (hasSeenBounds) seenMaxX + pad else worldMinX + OFFSCREEN_SIZE / worldScale
        val boundsMinZ = if (hasSeenBounds) seenMinZ - pad else worldMinZ
        val boundsMaxZ = if (hasSeenBounds) seenMaxZ + pad else worldMinZ + OFFSCREEN_SIZE / worldScale
        val fitSpan = maxOf(boundsMaxX - boundsMinX, boundsMaxZ - boundsMinZ, 1.0)
        // Square window centred on the explored box, so the aspect stays honest -- then the
        // player's own zoom and drag on top.
        val span = fitSpan / zoom
        val centreX = (boundsMinX + boundsMaxX) / 2 + panX
        val centreZ = (boundsMinZ + boundsMaxZ) / 2 + panZ
        val viewMinX = centreX - span / 2
        val viewMinZ = centreZ - span / 2
        fullMapWorldPerPixel = span / size

        val srcX = (viewMinX - worldMinX) * worldScale
        val srcZ = (viewMinZ - worldMinZ) * worldScale
        val srcSize = span * worldScale
        fullCtx.drawImage(bitmap, srcX, srcZ, srcSize, srcSize, 0.0, 0.0, size, size)

        val fit = size / srcSize * worldScale
        drawMarkers(
            fullCtx, size, playerX, playerZ, playerYaw, enemies,
            doors.filter { isDoorVisible(it) },
            toScreenX = { x -> (x - viewMinX) * fit },
            toScreenZ = { z -> (z - viewMinZ) * fit },
            fullMap = true,
        )
    }

    /** Which rooms a door joins, cached per door -- found by sampling either side of it. */
    private val doorSideRooms = HashMap<String, Pair<Int, Int>>()

    /**
     * A door shows once *either* of its rooms has been walked -- standing before a locked room
     * you see the gate and its colour, and nothing past it. Hiding doors whose nearest room
     * was unvisited kept exactly the wrong ones dark.
     */
    private fun isDoorVisible(door: MapDoor): Boolean {
        if (rooms.isEmpty()) return true
        if (renderedRooms.isEmpty()) return false
        val key = "${(door.x * 10).toInt()},${(door.z * 10).toInt()}"
        val sides = doorSideRooms.getOrPut(key) {
            // Perpendicular to the doorway, a short step out on each side.
            val dx = door.bx - door.ax
            val dz = door.bz - door.az
            val length = kotlin.math.sqrt(dx * dx + dz * dz)
            if (length < 1e-6) return@getOrPut roomAt(door.x, door.z).let { it to it }
            val nx = -dz / length * DOOR_SIDE_SAMPLE
            val nz = dx / length * DOOR_SIDE_SAMPLE
            roomAt(door.x + nx, door.z + nz) to roomAt(door.x - nx, door.z - nz)
        }
        return sides.first in renderedRooms || sides.second in renderedRooms
    }

    /**
     * The room owning the floor at this point -- the flood fill's own attribution. Containment
     * first: floor triangles vary wildly in size, so the nearest *centroid* is routinely a
     * small triangle in the wrong room while the point stands inside a huge one. Falls back to
     * nearest centroid only off the floor entirely.
     */
    private fun roomAt(x: Double, z: Double): Int {
        if (triangleRoom.isEmpty() || triangles.isEmpty()) return -1
        var nearest = -1
        var nearestD = Double.MAX_VALUE
        for ((index, t) in triangles.withIndex()) {
            val o1 = (t[2] - t[0]) * (z - t[1]) - (t[3] - t[1]) * (x - t[0])
            val o2 = (t[4] - t[2]) * (z - t[3]) - (t[5] - t[3]) * (x - t[2])
            val o3 = (t[0] - t[4]) * (z - t[5]) - (t[1] - t[5]) * (x - t[4])
            if ((o1 >= 0 && o2 >= 0 && o3 >= 0) || (o1 <= 0 && o2 <= 0 && o3 <= 0)) {
                return triangleRoom[index]
            }
            val cx = (t[0] + t[2] + t[4]) / 3
            val cz = (t[1] + t[3] + t[5]) / 3
            val dx = x - cx
            val dz = z - cz
            val d = dx * dx + dz * dz
            if (d < nearestD) {
                nearestD = d
                nearest = triangleRoom[index]
            }
        }
        return nearest
    }

    /** The moving parts both maps share: doors, enemies, and the player's arrow. */
    private fun drawMarkers(
        target: CanvasRenderingContext2D,
        size: Double,
        playerX: Double,
        playerZ: Double,
        playerYaw: Double,
        enemies: List<Pair<Double, Double>>,
        doors: List<MapDoor>,
        toScreenX: (Double) -> Double,
        toScreenZ: (Double) -> Double,
        fullMap: Boolean = false,
    ) {
        fun screenX(x: Double) = toScreenX(x)
        fun screenZ(z: Double) = toScreenZ(z)

        // Doors: a bar drawn right across the gap the door fills, red while locked and green
        // once open. Using the gate's own blocking span means the bar is the doorway's real
        // width and lies along the wall, rather than floating as a marker beside it.
        for (door in doors) {
            val ax = screenX(door.ax)
            val az = screenZ(door.az)
            val bx = screenX(door.bx)
            val bz = screenZ(door.bz)
            if (maxOf(ax, bx) < 0 || minOf(ax, bx) > size) continue
            if (maxOf(az, bz) < 0 || minOf(az, bz) > size) continue

            target.strokeStyle = if (door.open) DOOR_OPEN_COLOR else DOOR_LOCKED_COLOR
            target.lineWidth = if (fullMap) FULL_DOOR_THICKNESS else DOOR_THICKNESS
            target.beginPath()
            target.moveTo(ax, az)
            target.lineTo(bx, bz)
            target.stroke()
        }

        target.fillStyle = ENEMY_COLOR
        val dotRadius = if (fullMap) FULL_DOT_RADIUS else DOT_RADIUS
        for ((ex, ez) in enemies) {
            val sx = screenX(ex)
            val sz = screenZ(ez)
            if (sx < 0 || sx > size || sz < 0 || sz > size) continue
            target.beginPath()
            target.arc(sx, sz, dotRadius, 0.0, 2 * PI)
            target.fill()
        }

        target.save()
        target.translate(screenX(playerX), screenZ(playerZ))
        target.rotate(PI - playerYaw)
        target.fillStyle = PLAYER_COLOR
        val arrow = if (fullMap) FULL_ARROW_SIZE else ARROW_SIZE
        target.beginPath()
        target.moveTo(0.0, -arrow)
        target.lineTo(arrow * 0.7, arrow)
        target.lineTo(0.0, arrow * 0.45)
        target.lineTo(-arrow * 0.7, arrow)
        target.closePath()
        target.fill()
        target.restore()
    }

    override fun dispose() {
        for (listener in listeners) listener.dispose()
        root.remove()
        fullRoot.remove()
        styleTag.remove()
        super.dispose()
    }

    companion object {
        private const val CANVAS_SIZE = 240
        private const val FULL_CANVAS_SIZE = 900
        private const val OFFSCREEN_SIZE = 1024

        /** How much world the radar shows edge to edge, in world units. */
        private const val WINDOW_WORLD_UNITS = 340.0

        private const val DOT_RADIUS = 7.0
        private const val ARROW_SIZE = 13.0
        /** How thick a door bar is drawn, in canvas pixels -- a wall, not a dot. */
        private const val DOOR_THICKNESS = 7.0
        private const val FULL_DOOR_THICKNESS = 10.0
        private const val FULL_DOT_RADIUS = 9.0
        private const val FULL_ARROW_SIZE = 18.0
        /** Breathing room around the explored area when the full map frames it, in world units. */
        private const val FULL_MAP_PADDING_UNITS = 60.0

        /** How far past each end a doorway extends as a flood-fill barrier. */
        private const val DOOR_BARRIER_EXTENSION = 0.35

        /** How far to each side of a doorway to sample for the rooms it joins, world units. */
        private const val DOOR_SIDE_SAMPLE = 8.0

        /** How far the full map's pinch zoom ranges. */
        private const val ZOOM_MIN = 0.6
        private const val ZOOM_MAX = 6.0

        private const val FLOOR_COLOR = "rgba(96, 160, 176, 0.85)"
        private const val BACKDROP_COLOR = "rgba(4, 18, 26, 0.9)"
        private const val ENEMY_COLOR = "#ff4438"
        private const val PLAYER_COLOR = "#ffd24d"
        private const val DOOR_LOCKED_COLOR = "#ff3b30"
        private const val DOOR_OPEN_COLOR = "#37e06a"

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
            .pw-hud-minimap-hit {
              position: fixed;
              top: calc(12px + var(--pw-safe-top));
              right: calc(12px + var(--pw-safe-right));
              width: 120px;
              height: 120px;
              z-index: 16;
              background: transparent;
              -webkit-tap-highlight-color: transparent;
            }
            .pw-hud-fullmap {
              position: fixed;
              top: 50%;
              left: 50%;
              transform: translate(-50%, -50%);
              width: min(78vw, 78vh);
              height: min(78vw, 78vh);
              z-index: 40;
              display: none;
              background: rgba(4, 18, 26, 0.55);
              user-select: none;
            }
        """
    }
}

package world.phantasmal.web.mobileGame.rendering

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random
import kotlinx.browser.window
import org.khronos.webgl.Float32Array
import org.khronos.webgl.set
import world.phantasmal.webui.obj
import world.phantasmal.web.externals.three.AdditiveBlending
import world.phantasmal.web.externals.three.BoxGeometry
import world.phantasmal.web.externals.three.Float32BufferAttribute
import world.phantasmal.web.externals.three.BufferGeometry
import world.phantasmal.web.externals.three.Color
import world.phantasmal.web.externals.three.CylinderGeometry
import world.phantasmal.web.externals.three.IcosahedronGeometry
import world.phantasmal.web.externals.three.Mesh
import world.phantasmal.web.externals.three.MeshBasicMaterial
import world.phantasmal.web.externals.three.Object3D
import world.phantasmal.web.externals.three.OctahedronGeometry
import world.phantasmal.web.externals.three.Points
import world.phantasmal.web.externals.three.PointsMaterial
import world.phantasmal.web.externals.three.RingGeometry
import world.phantasmal.web.externals.three.SphereGeometry
import world.phantasmal.web.externals.three.Texture

/**
 * The technique effect engine: GPU particle clouds (one [Points] draw call per cloud, however
 * many particles ride in it), procedural bolt meshes, and the handful of animated solids the
 * spell blueprint calls for. GameRenderer keeps the combat; this owns the light show.
 *
 * All sizes derive from [unit] (world units per PSO unit) times [mobileScale] -- the blueprint's
 * 50% boost on phone-sized screens, judged by the window's short edge so a landscape phone
 * still counts as a phone.
 */
class TechniqueFx(
    private val scene: Object3D,
    private val unit: Double,
    private val texture: (String) -> Texture,
) {
    val mobileScale: Double =
        if (min(window.innerWidth, window.innerHeight) < 768) 1.5 else 1.0

    /**
     * One blueprint "unit" in world space. First pass used 2.2 and every spell came out about
     * twice the player's size; halved on device feedback.
     */
    private val bu = 1.1 * unit * mobileScale

    // ---------------------------------------------------------------- particle clouds

    private class Cloud(
        val points: Points,
        val positions: Float32Array,
        val velocities: FloatArray,
        val count: Int,
        var life: Double,
        val maxLife: Double,
        val drag: Double,
        val gravity: Double,
        /** Sine-wobble amplitude on x per particle (the support glitter's sway). */
        val wobble: Double = 0.0,
        var age: Double = 0.0,
    )

    private val clouds = mutableListOf<Cloud>()

    private fun cloud(
        count: Int,
        colorHex: Int,
        size: Double,
        textureName: String,
        life: Double,
        drag: Double = 0.0,
        gravity: Double = 0.0,
        wobble: Double = 0.0,
        opacity: Double = 0.95,
        place: (Int) -> DoubleArray,
    ): Cloud {
        val positions = Float32Array(count * 3)
        val velocities = FloatArray(count * 3)
        for (i in 0 until count) {
            val p = place(i)
            positions[i * 3] = p[0].toFloat()
            positions[i * 3 + 1] = p[1].toFloat()
            positions[i * 3 + 2] = p[2].toFloat()
            velocities[i * 3] = p[3].toFloat()
            velocities[i * 3 + 1] = p[4].toFloat()
            velocities[i * 3 + 2] = p[5].toFloat()
        }
        val geometry = BufferGeometry()
        geometry.setAttribute("position", Float32BufferAttribute(positions, 3))
        val material = PointsMaterial(obj {
            this.size = size
            this.sizeAttenuation = true
            this.transparent = true
            this.opacity = opacity
            this.depthWrite = false
            this.blending = AdditiveBlending
            this.color = Color(colorHex)
            this.map = texture(textureName)
        })
        val points = Points(geometry, material)
        scene.add(points)
        val c = Cloud(points, positions, velocities, count, life, life, drag, gravity, wobble)
        clouds.add(c)
        return c
    }

    // ---------------------------------------------------------------- animated solids

    private class Solid(
        val mesh: Mesh,
        var life: Double,
        val maxLife: Double,
        /** Scale animated from [scaleFrom] to [scaleTo] over the life. */
        val scaleFrom: Double = 1.0,
        val scaleTo: Double = 1.0,
        val riseSpeed: Double = 0.0,
        val spin: Double = 0.0,
        val fade: Boolean = true,
    )

    private val solids = mutableListOf<Solid>()

    private fun solid(
        mesh: Mesh,
        life: Double,
        scaleFrom: Double = 1.0,
        scaleTo: Double = 1.0,
        riseSpeed: Double = 0.0,
        spin: Double = 0.0,
        fade: Boolean = true,
    ) {
        scene.add(mesh)
        mesh.scale.set(scaleFrom, scaleFrom, scaleFrom)
        solids.add(Solid(mesh, life, life, scaleFrom, scaleTo, riseSpeed, spin, fade))
    }

    private fun basic(colorHex: Int, additive: Boolean = true, opacity: Double = 1.0): MeshBasicMaterial =
        MeshBasicMaterial(obj {
            this.color = Color(colorHex)
            this.transparent = true
            this.opacity = opacity
            if (additive) this.blending = AdditiveBlending
        }).also { it.depthWrite = false }

    // ---------------------------------------------------------------- bolts

    private class Bolt(
        val segments: List<Mesh>,
        val fromX: Double, val fromY: Double, val fromZ: Double,
        val toX: Double, val toY: Double, val toZ: Double,
        var life: Double,
        val maxLife: Double,
        /** Re-jitter the zig-zag every frame -- Gizonde's violent shimmer. */
        val shimmer: Boolean,
        val jitter: Double,
    )

    private val bolts = mutableListOf<Bolt>()

    /** A jagged lightning bolt built from stretched boxes -- thick on any screen. */
    private fun bolt(
        fromX: Double, fromY: Double, fromZ: Double,
        toX: Double, toY: Double, toZ: Double,
        colorHex: Int,
        thickness: Double,
        life: Double,
        shimmer: Boolean = false,
    ) {
        val segmentCount = 7
        val segments = (0 until segmentCount).map {
            Mesh(BoxGeometry(thickness, thickness, 1.0), basic(colorHex)).also { scene.add(it) }
        }
        val b = Bolt(segments, fromX, fromY, fromZ, toX, toY, toZ, life, life, shimmer, thickness * 4)
        layoutBolt(b)
        bolts.add(b)
    }

    private fun layoutBolt(b: Bolt) {
        val n = b.segments.size
        var previousX = b.fromX
        var previousY = b.fromY
        var previousZ = b.fromZ
        for ((index, segment) in b.segments.withIndex()) {
            val t = (index + 1).toDouble() / n
            val endJitter = if (index == n - 1) 0.0 else 1.0
            val x = b.fromX + (b.toX - b.fromX) * t + (Random.nextDouble() - 0.5) * b.jitter * endJitter
            val y = b.fromY + (b.toY - b.fromY) * t + (Random.nextDouble() - 0.5) * b.jitter * endJitter
            val z = b.fromZ + (b.toZ - b.fromZ) * t + (Random.nextDouble() - 0.5) * b.jitter * endJitter
            val dx = x - previousX
            val dy = y - previousY
            val dz = z - previousZ
            val length = kotlin.math.sqrt(dx * dx + dy * dy + dz * dz).coerceAtLeast(1e-4)
            segment.position.set((x + previousX) / 2, (y + previousY) / 2, (z + previousZ) / 2)
            segment.scale.set(1.0, 1.0, length)
            segment.lookAt(x, y, z)
            previousX = x; previousY = y; previousZ = z
        }
    }

    // ---------------------------------------------------------------- the spells

    /** Foie's flying body: a white-hot core with an ember glow shell. */
    fun foieCore(): Object3D {
        val core = Mesh(SphereGeometry(0.35 * bu, 12, 10), basic(0xffffaa))
        val shell = Mesh(SphereGeometry(0.55 * bu, 12, 10), basic(0xff6622, opacity = 0.5))
        core.add(shell)
        return core
    }

    /** A puff of embers left along Foie's flight -- call per frame at the orb's position. */
    fun foieTrail(x: Double, y: Double, z: Double) {
        cloud(
            count = 3, colorHex = 0xff4400, size = 0.9 * bu, textureName = "burst_orange",
            life = 0.45, drag = 2.0,
        ) {
            doubleArrayOf(
                x + (Random.nextDouble() - 0.5) * 0.3 * bu,
                y + (Random.nextDouble() - 0.5) * 0.3 * bu,
                z + (Random.nextDouble() - 0.5) * 0.3 * bu,
                (Random.nextDouble() - 0.5) * 2.0 * bu,
                (Random.nextDouble() - 0.2) * 1.5 * bu,
                (Random.nextDouble() - 0.5) * 2.0 * bu,
            )
        }
    }

    /** Gifoie: flame wheels riding a logarithmic spiral outward and upward. */
    fun gifoie(x: Double, y: Double, z: Double) {
        // Two clouds give the size/colour gradient a single material can't: small hot
        // yellow near the centre, large crimson at the rim.
        for ((count, colorHex, size, speedScale) in listOf(
            Quad(60, 0xffaa00, 0.6 * bu, 0.8),
            Quad(60, 0xdc143c, 1.4 * bu, 1.25),
        )) {
            cloud(
                count = count, colorHex = colorHex, size = size, textureName = "burst_orange",
                life = 2.2, drag = 0.25, opacity = 0.85,
            ) { i ->
                val theta = i * 0.45
                val r = 0.3 * bu * exp(0.16 * theta)
                val px = x + cos(theta) * r
                val pz = z + sin(theta) * r
                // Velocity carries the spiral outward as it rises.
                doubleArrayOf(
                    px, y + 0.5 * bu + i * 0.008 * bu, pz,
                    cos(theta) * 2.6 * bu * speedScale,
                    (0.9 + Random.nextDouble() * 0.5) * bu,
                    sin(theta) * 2.6 * bu * speedScale,
                )
            }
        }
    }

    /** Rafoie: the violent inflating explosion sphere with a white heart. */
    fun rafoie(x: Double, y: Double, z: Double) {
        solid(
            Mesh(IcosahedronGeometry(3.5 * bu, 1), basic(0xff5500, opacity = 0.85)),
            life = 0.3, scaleFrom = 0.1, scaleTo = 1.0,
        ).also { solids.last().mesh.position.set(x, y, z) }
        solid(
            Mesh(SphereGeometry(1.2 * bu, 10, 8), basic(0xffffff)),
            life = 0.2, scaleFrom = 0.6, scaleTo = 1.4,
        ).also { solids.last().mesh.position.set(x, y, z) }
        cloud(
            count = 80, colorHex = 0xff7733, size = 1.0 * bu, textureName = "burst_orange",
            life = 0.7, drag = 1.4,
        ) {
            val theta = Random.nextDouble() * 2 * PI
            val phi = Random.nextDouble() * PI
            val speed = (4.0 + Random.nextDouble() * 6.0) * bu
            doubleArrayOf(
                x, y, z,
                sin(phi) * cos(theta) * speed, cos(phi) * speed, sin(phi) * sin(theta) * speed,
            )
        }
    }

    /** One of Barta's crystal spikes surging along the ground. */
    fun bartaSpike(x: Double, y: Double, z: Double) {
        // Normal blending, not additive: ice must stay solid against bright terrain.
        val spike = Mesh(
            OctahedronGeometry(0.6 * bu, 0),
            MeshBasicMaterial(obj {
                this.color = Color(0x00d2ff)
                this.transparent = true
                this.opacity = 0.85
            }),
        )
        spike.scale.y = 3.0
        spike.position.set(x, y + 0.9 * bu, z)
        spike.rotation.y = Random.nextDouble() * PI
        solid(spike, life = 0.8, scaleFrom = 1.0, scaleTo = 1.0)
        cloud(
            count = 6, colorHex = 0x66eaff, size = 0.5 * bu, textureName = "burst_bright",
            life = 0.5, gravity = -3.0,
        ) {
            doubleArrayOf(
                x + (Random.nextDouble() - 0.5) * bu, y + 0.3 * bu,
                z + (Random.nextDouble() - 0.5) * bu,
                (Random.nextDouble() - 0.5) * 2 * bu, Random.nextDouble() * 2.5 * bu,
                (Random.nextDouble() - 0.5) * 2 * bu,
            )
        }
    }

    /** Gibarta: the freezing cone -- big diamond glints inside a fog of frost. */
    fun gibarta(x: Double, y: Double, z: Double, dirX: Double, dirZ: Double) {
        fun coneVelocity(speed: Double): DoubleArray {
            // Within 45 degrees of dead ahead.
            val spread = (Random.nextDouble() - 0.5) * PI / 2
            val cosS = cos(spread); val sinS = sin(spread)
            val vx = dirX * cosS - dirZ * sinS
            val vz = dirX * sinS + dirZ * cosS
            return doubleArrayOf(vx * speed, (Random.nextDouble() - 0.3) * 1.2 * bu, vz * speed)
        }
        cloud(
            count = 50, colorHex = 0xffffff, size = 1.2 * bu, textureName = "nt_shard",
            life = 0.9, drag = 0.8, opacity = 0.9,
        ) {
            val v = coneVelocity((6.0 + Random.nextDouble() * 5.0) * bu)
            doubleArrayOf(x, y + 1.0 * bu, z, v[0], v[1], v[2])
        }
        cloud(
            count = 150, colorHex = 0x1e90ff, size = 0.4 * bu, textureName = "burst_bright",
            life = 1.1, drag = 0.6, opacity = 0.7,
        ) {
            val v = coneVelocity((4.0 + Random.nextDouble() * 6.0) * bu)
            doubleArrayOf(x, y + 1.0 * bu, z, v[0], v[1], v[2])
        }
    }

    /** Rabarta: the full-circle blizzard of needles and frost. */
    fun rabarta(x: Double, y: Double, z: Double) {
        cloud(
            count = 110, colorHex = 0xafeeee, size = 1.5 * bu, textureName = "nt_shard",
            life = 1.0, drag = 1.0, opacity = 0.9,
        ) {
            val theta = Random.nextDouble() * 2 * PI
            val speed = (5.0 + Random.nextDouble() * 5.0) * bu
            doubleArrayOf(
                x, y + 0.8 * bu, z,
                cos(theta) * speed, (Random.nextDouble() - 0.35) * 1.6 * bu, sin(theta) * speed,
            )
        }
        cloud(
            count = 70, colorHex = 0xffffff, size = 0.7 * bu, textureName = "burst_bright",
            life = 1.2, drag = 0.8, opacity = 0.75,
        ) {
            val theta = Random.nextDouble() * 2 * PI
            val speed = (3.0 + Random.nextDouble() * 4.0) * bu
            doubleArrayOf(
                x, y + 0.6 * bu, z,
                cos(theta) * speed, Random.nextDouble() * 1.2 * bu, sin(theta) * speed,
            )
        }
    }

    /** Zonde: the blinding sky bolt, its impact sparks, and the two-frame flash. */
    fun zonde(x: Double, y: Double, z: Double) {
        bolt(
            x + (Random.nextDouble() - 0.5) * 2 * bu, y + 16.0 * bu, z,
            x, y, z,
            colorHex = 0xffff33, thickness = 0.09 * bu, life = 0.28,
        )
        // The dimmer parallel strand that gives the bolt body.
        bolt(
            x + (Random.nextDouble() - 0.5) * 2 * bu, y + 16.0 * bu, z,
            x, y, z,
            colorHex = 0xffffff, thickness = 0.045 * bu, life = 0.22,
        )
        cloud(
            count = 20, colorHex = 0xffffaa, size = 0.6 * bu, textureName = "burst_bright",
            life = 0.5, gravity = -8.0,
        ) {
            val theta = Random.nextDouble() * 2 * PI
            val speed = (4.0 + Random.nextDouble() * 4.0) * bu
            doubleArrayOf(
                x, y + 0.2 * bu, z,
                cos(theta) * speed, speed * 0.9, sin(theta) * speed,
            )
        }
        // The flash: a huge additive glow for a few hundredths of a second.
        val flash = Mesh(SphereGeometry(2.2 * bu, 8, 6), basic(0xffffaa, opacity = 0.9))
        flash.position.set(x, y + 1.0 * bu, z)
        solid(flash, life = 0.07, scaleFrom = 1.0, scaleTo = 1.6)
    }

    /** Gizonde: the chain -- arcs that snap between bodies and vibrate while they live. */
    fun gizonde(points: List<DoubleArray>) {
        for (i in 0 until points.size - 1) {
            val a = points[i]; val b = points[i + 1]
            bolt(
                a[0], a[1], a[2], b[0], b[1], b[2],
                colorHex = 0xffd700, thickness = 0.06 * bu, life = 0.45, shimmer = true,
            )
        }
    }

    /** Razonde: the crackling dome inflating out of the air. */
    fun razonde(x: Double, y: Double, z: Double) {
        val dome = Mesh(
            SphereGeometry(4.0 * bu, 16, 12),
            MeshBasicMaterial(obj {
                this.color = Color(0xfffa00)
                this.wireframe = true
                this.transparent = true
                this.opacity = 0.8
                this.blending = AdditiveBlending
            }).also { it.depthWrite = false },
        )
        dome.position.set(x, y + 1.0 * bu, z)
        solid(dome, life = 0.7, scaleFrom = 0.15, scaleTo = 1.0)
        cloud(
            count = 80, colorHex = 0xffff66, size = 0.8 * bu, textureName = "burst_bright",
            life = 0.8, drag = 0.3,
        ) {
            val theta = Random.nextDouble() * 2 * PI
            val phi = Random.nextDouble() * PI * 0.6
            val speed = 5.2 * bu
            doubleArrayOf(
                x, y + 1.0 * bu, z,
                sin(phi) * cos(theta) * speed, cos(phi) * speed, sin(phi) * sin(theta) * speed,
            )
        }
    }

    /** Grants: the divine pillar -- white heart, gold shell, glitter spiralling up. */
    fun grants(x: Double, y: Double, z: Double) {
        val outer = Mesh(
            CylinderGeometry(1.2 * bu, 1.2 * bu, 12.0 * bu, 16),
            basic(0xffd700, opacity = 0.45),
        )
        outer.position.set(x, y + 6.0 * bu, z)
        solid(outer, life = 1.1, scaleFrom = 1.0, scaleTo = 1.0, spin = 0.8)
        val inner = Mesh(
            CylinderGeometry(0.5 * bu, 0.5 * bu, 12.0 * bu, 12),
            basic(0xffffff, opacity = 0.75),
        )
        inner.position.set(x, y + 6.0 * bu, z)
        solid(inner, life = 0.9, scaleFrom = 1.0, scaleTo = 1.0)
        cloud(
            count = 100, colorHex = 0xfff5c8, size = 0.5 * bu, textureName = "burst_bright",
            life = 1.4, wobble = 0.8 * bu,
        ) { i ->
            val theta = i * 0.35
            val r = (0.6 + Random.nextDouble() * 0.8) * bu
            doubleArrayOf(
                x + cos(theta) * r, y + Random.nextDouble() * 2.0 * bu, z + sin(theta) * r,
                0.0, (3.0 + Random.nextDouble() * 2.5) * bu, 0.0,
            )
        }
    }

    /** Megid's flying body: a genuinely dark void heart inside a violet aura. */
    fun megidCore(): Object3D {
        // Normal blending and near-black: the centre must swallow light, not add it.
        val core = Mesh(
            SphereGeometry(0.6 * bu, 14, 12),
            MeshBasicMaterial(obj { this.color = Color(0x050010) }),
        )
        val rim = Mesh(SphereGeometry(0.78 * bu, 14, 12), basic(0x4b0082, opacity = 0.55))
        core.add(rim)
        return core
    }

    /** Megid's crackling dark trail. */
    fun megidTrail(x: Double, y: Double, z: Double) {
        cloud(
            count = 3, colorHex = 0x4b0082, size = 1.2 * bu, textureName = "burst_bright",
            life = 0.5, drag = 1.5,
        ) {
            doubleArrayOf(
                x + (Random.nextDouble() - 0.5) * 0.5 * bu,
                y + (Random.nextDouble() - 0.5) * 0.5 * bu,
                z + (Random.nextDouble() - 0.5) * 0.5 * bu,
                (Random.nextDouble() - 0.5) * 1.5 * bu,
                (Random.nextDouble() - 0.5) * 1.5 * bu,
                (Random.nextDouble() - 0.5) * 1.5 * bu,
            )
        }
    }

    /** The support pulse: an expanding floor ring plus glitter rising in a gentle sway. */
    fun supportPulse(x: Double, y: Double, z: Double, colorHex: Int) {
        val ring = Mesh(RingGeometry(0.8 * bu, 1.1 * bu, 40), basic(colorHex, opacity = 0.85))
        ring.rotation.x = -PI / 2
        ring.position.set(x, y + 0.25, z)
        solid(ring, life = 0.9, scaleFrom = 0.4, scaleTo = 3.2)
        cloud(
            count = 40, colorHex = colorHex, size = 0.8 * bu, textureName = "burst_bright",
            life = 1.3, wobble = 0.5 * bu,
        ) {
            val theta = Random.nextDouble() * 2 * PI
            val r = Random.nextDouble() * 1.4 * bu
            doubleArrayOf(
                x + cos(theta) * r, y + Random.nextDouble() * 0.5 * bu, z + sin(theta) * r,
                0.0, (2.2 + Random.nextDouble() * 1.8) * bu, 0.0,
            )
        }
    }

    // ---------------------------------------------------------------- per-frame drive

    fun update(deltaTime: Double) {
        val cloudIterator = clouds.iterator()
        while (cloudIterator.hasNext()) {
            val c = cloudIterator.next()
            c.life -= deltaTime
            c.age += deltaTime
            if (c.life <= 0) {
                c.points.parent?.remove(c.points)
                cloudIterator.remove()
                continue
            }
            val dragFactor = 1.0 - (c.drag * deltaTime).coerceAtMost(0.9)
            for (i in 0 until c.count) {
                var vx = c.velocities[i * 3]
                var vy = c.velocities[i * 3 + 1]
                var vz = c.velocities[i * 3 + 2]
                vy += (c.gravity * deltaTime).toFloat()
                vx = (vx * dragFactor).toFloat()
                vy = (vy * dragFactor).toFloat()
                vz = (vz * dragFactor).toFloat()
                c.velocities[i * 3] = vx
                c.velocities[i * 3 + 1] = vy
                c.velocities[i * 3 + 2] = vz
                val wobbleX =
                    if (c.wobble > 0) (sin(c.age * 5.0 + i) * c.wobble * deltaTime).toFloat()
                    else 0f
                val arr = c.positions.asDynamic()
                arr[i * 3] = (arr[i * 3] as Float) + (vx * deltaTime).toFloat() + wobbleX
                arr[i * 3 + 1] = (arr[i * 3 + 1] as Float) + (vy * deltaTime).toFloat()
                arr[i * 3 + 2] = (arr[i * 3 + 2] as Float) + (vz * deltaTime).toFloat()
            }
            (c.points.geometry.asDynamic().attributes.position).needsUpdate = true
            c.points.material.opacity = (c.life / c.maxLife) * 0.95
        }

        val solidIterator = solids.iterator()
        while (solidIterator.hasNext()) {
            val s = solidIterator.next()
            s.life -= deltaTime
            if (s.life <= 0) {
                s.mesh.parent?.remove(s.mesh)
                solidIterator.remove()
                continue
            }
            val progress = 1.0 - s.life / s.maxLife
            val scale = s.scaleFrom + (s.scaleTo - s.scaleFrom) * progress
            s.mesh.scale.set(scale, scale, scale)
            if (s.riseSpeed != 0.0) s.mesh.position.y += s.riseSpeed * deltaTime
            if (s.spin != 0.0) s.mesh.rotation.y += s.spin * deltaTime
            if (s.fade) {
                val material: dynamic = s.mesh.material
                if (material.pwBaseOpacity == undefined) material.pwBaseOpacity = material.opacity
                material.opacity = (s.life / s.maxLife) * (material.pwBaseOpacity as Double)
            }
        }

        val boltIterator = bolts.iterator()
        while (boltIterator.hasNext()) {
            val b = boltIterator.next()
            b.life -= deltaTime
            if (b.life <= 0) {
                for (segment in b.segments) segment.parent?.remove(segment)
                boltIterator.remove()
                continue
            }
            if (b.shimmer) layoutBolt(b)
            val opacity = b.life / b.maxLife
            for (segment in b.segments) {
                val material: dynamic = segment.material
                material.opacity = opacity
            }
        }
    }

    private data class Quad(val a: Int, val b: Int, val c: Double, val d: Double)
}

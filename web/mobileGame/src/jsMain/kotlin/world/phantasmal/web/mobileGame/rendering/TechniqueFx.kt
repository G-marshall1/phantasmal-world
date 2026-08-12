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
import world.phantasmal.web.externals.three.CatmullRomCurve3
import world.phantasmal.web.externals.three.DoubleSide
import world.phantasmal.web.externals.three.Group
import world.phantasmal.web.externals.three.IcosahedronGeometry
import world.phantasmal.web.externals.three.ShaderMaterial
import world.phantasmal.web.externals.three.TubeGeometry
import world.phantasmal.web.externals.three.Vector3
import world.phantasmal.web.externals.three.Mesh
import world.phantasmal.web.externals.three.MeshBasicMaterial
import world.phantasmal.web.externals.three.Object3D
import world.phantasmal.web.externals.three.OctahedronGeometry
import world.phantasmal.web.externals.three.Points
import world.phantasmal.web.externals.three.PointsMaterial
import world.phantasmal.web.externals.three.RingGeometry
import world.phantasmal.web.externals.three.SphereGeometry
import world.phantasmal.web.externals.three.Sprite
import world.phantasmal.web.externals.three.SpriteMaterial
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
        /** Per-particle birth times: a particle holds at its spawn point until its moment. */
        val delays: FloatArray? = null,
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
        delays: FloatArray? = null,
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
        val c = Cloud(points, positions, velocities, count, life, life, drag, gravity, wobble, delays = delays)
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

    // ---------------------------------------------------------------- orbiters

    /** One flame circling the caster: Gifoie's wheel is made of these. */
    private class Orbiter(
        val sprite: Sprite,
        val centerX: Double,
        val centerY: Double,
        val centerZ: Double,
        val angle0: Double,
        val spin: Double,
        val radiusFrom: Double,
        val radiusTo: Double,
        var life: Double,
        val maxLife: Double,
    )

    private val orbiters = mutableListOf<Orbiter>()

    /**
     * Gifoie as the name promises: solid flames that visibly circle the caster, winding
     * outward turn by turn until the ring dissipates at its rim.
     */
    fun gifoie(x: Double, y: Double, z: Double) {
        val arms = 3
        val flamesPerArm = 5
        for (arm in 0 until arms) {
            for (i in 0 until flamesPerArm) {
                val material = SpriteMaterial(obj {
                    this.map = texture("foie_flame_0")
                    this.color = Color(if (i < 2) 0xffcc44 else 0xff5522)
                    this.transparent = true
                    this.opacity = 1.0
                    this.blending = AdditiveBlending
                    this.depthWrite = false
                })
                val sprite = Sprite(material)
                val size = (1.3 + i * 0.15) * bu
                sprite.scale.set(size, size, 1.0)
                scene.add(sprite)
                orbiters.add(
                    Orbiter(
                        sprite,
                        x, y + 1.2 * bu, z,
                        angle0 = arm * 2 * PI / arms - i * 0.35,
                        spin = 4.2,
                        radiusFrom = 0.8 * bu,
                        radiusTo = 6.5 * bu,
                        life = 2.2, maxLife = 2.2,
                    )
                )
            }
        }
    }

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
    /**
     * Foie's head: a procedural flame sphere rather than two tinted balls.
     *
     * The shell is a shader that walks fbm noise over the surface, so the fire crawls and
     * licks instead of sitting still, shading from ember through gold to a white-hot core and
     * carrying a fresnel rim that brightens where the sphere turns away. Inside it sit a soft
     * hot core and two counter-rotating helices -- one red, one orange, each in its own glow
     * tube -- which is what gives the orb a living centre when it flies past you.
     *
     * Trimmed for a phone against the desktop sketch this came from: subdivision 3 rather than
     * 64 (which would have been tens of thousands of triangles for something the size of a
     * fist), four noise octaves rather than five, and short tubes with few radial segments.
     * Every one of these is drawn per fireball in flight, so the budget is small on purpose.
     */
    fun foieCore(): Object3D {
        val root = Group()

        val flame = Mesh(
            IcosahedronGeometry(FOIE_FLAME_RADIUS * bu, FOIE_FLAME_DETAIL),
            ShaderMaterial(
                obj {
                    uniforms = obj { uTime = obj { value = 0.0 } }
                    vertexShader = FOIE_VERTEX_SHADER
                    fragmentShader = FOIE_FRAGMENT_SHADER
                    transparent = true
                    depthWrite = false
                    blending = AdditiveBlending
                    side = DoubleSide
                }
            ),
        )
        root.add(flame)
        shaderClocks.add(flame)

        // The soft heart inside the flame.
        root.add(Mesh(SphereGeometry(0.34 * bu, 16, 12), basic(0xff7a12, opacity = 0.28)))

        // The double helix: two filaments sharing an axis half a turn apart, so they wind
        // around one another rather than sitting side by side.
        val red = coreSpiral(0xff1708, 0.0)
        val orange = coreSpiral(0xff8a08, PI)
        root.add(red)
        root.add(orange)
        spirals.add(SpiralPair(red, orange))

        return root
    }

    /** One filament of the core's helix, wrapped in a fatter, fainter copy of itself. */
    private fun coreSpiral(colorHex: Int, phase: Double): Object3D {
        val points = Array(FOIE_SPIRAL_SEGMENTS + 1) { i ->
            val u = i.toDouble() / FOIE_SPIRAL_SEGMENTS
            val angle = phase + u * 2.0 * PI * FOIE_SPIRAL_TURNS
            // Fattest in the middle, pinched at both ends, so it reads as a spindle.
            val r = FOIE_SPIRAL_RADIUS * bu * (0.30 + 0.70 * sin(PI * u))
            Vector3(cos(angle) * r, sin(angle) * r, (u - 0.5) * FOIE_SPIRAL_LENGTH * bu)
        }
        val curve = CatmullRomCurve3(points)
        val group = Group()
        group.add(
            Mesh(
                TubeGeometry(curve, FOIE_SPIRAL_SEGMENTS, FOIE_SPIRAL_TUBE * bu * 2.7, 5, false),
                basic(colorHex, opacity = 0.16),
            )
        )
        group.add(
            Mesh(
                TubeGeometry(curve, FOIE_SPIRAL_SEGMENTS, FOIE_SPIRAL_TUBE * bu, 5, false),
                basic(colorHex, opacity = 0.95),
            )
        )
        return group
    }

    /**
     * Foie's tail: a fat ember streak shed every frame, drifting back against the flight so
     * the orb visibly drags fire behind it.
     */
    fun foieTrail(x: Double, y: Double, z: Double, dirX: Double, dirZ: Double) {
        cloud(
            count = 5, colorHex = 0xff5511, size = 1.4 * bu, textureName = "burst_orange",
            life = 0.6, drag = 1.2, opacity = 1.0,
        ) {
            doubleArrayOf(
                x + (Random.nextDouble() - 0.5) * 0.25 * bu,
                y + (Random.nextDouble() - 0.5) * 0.25 * bu,
                z + (Random.nextDouble() - 0.5) * 0.25 * bu,
                -dirX * (2.0 + Random.nextDouble() * 2.0) * bu + (Random.nextDouble() - 0.5) * bu,
                (Random.nextDouble() - 0.3) * 1.2 * bu,
                -dirZ * (2.0 + Random.nextDouble() * 2.0) * bu + (Random.nextDouble() - 0.5) * bu,
            )
        }
    }

    /** Rafoie: the violent inflating explosion sphere with a white heart. */
    fun rafoie(x: Double, y: Double, z: Double, radiusWorld: Double = 3.5 * bu) {
        // The molten sphere IS the blast zone: the caller passes the real damage radius so
        // whatever the dome visibly engulfs is genuinely inside the hit.
        val spread = radiusWorld / (3.5 * bu)
        solid(
            Mesh(IcosahedronGeometry(radiusWorld, 1), basic(0xff5500, opacity = 0.85)),
            life = 0.3, scaleFrom = 0.1, scaleTo = 1.0,
        ).also { solids.last().mesh.position.set(x, y, z) }
        solid(
            Mesh(SphereGeometry(radiusWorld * 0.34, 10, 8), basic(0xffffff)),
            life = 0.2, scaleFrom = 0.6, scaleTo = 1.4,
        ).also { solids.last().mesh.position.set(x, y, z) }
        cloud(
            count = 80, colorHex = 0xff7733, size = 1.0 * bu, textureName = "burst_orange",
            life = 0.7, drag = 1.4,
        ) {
            val theta = Random.nextDouble() * 2 * PI
            val phi = Random.nextDouble() * PI
            val speed = (4.0 + Random.nextDouble() * 6.0) * bu * spread
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
            OctahedronGeometry(1.5 * bu, 0),
            MeshBasicMaterial(obj {
                this.color = Color(0x00d2ff)
                this.transparent = true
                this.opacity = 0.9
            }),
        )
        spike.scale.y = 2.2
        // A white heart inside the shard, so the ice reads dense instead of glassy-thin.
        val heart = Mesh(
            OctahedronGeometry(0.8 * bu, 0),
            MeshBasicMaterial(obj {
                this.color = Color(0xdff6ff)
                this.transparent = true
                this.opacity = 0.95
            }),
        )
        heart.scale.y = 1.8
        spike.add(heart)
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

    /**
     * Gibarta as a cast, not a pop: ice shards stream OUT OF THE HANDS over half a second --
     * each particle holds at the palm until its birth moment, then flies its cone line, so the
     * eye reads a continuous breath of ice rather than an instant scatter.
     */
    fun gibarta(x: Double, y: Double, z: Double, dirX: Double, dirZ: Double) {
        val handX = x + dirX * 0.8 * bu
        val handY = y + 1.6 * bu
        val handZ = z + dirZ * 0.8 * bu
        fun coneVelocity(speed: Double): DoubleArray {
            val spread = (Random.nextDouble() - 0.5) * PI / 2.4
            val cosS = cos(spread); val sinS = sin(spread)
            val vx = dirX * cosS - dirZ * sinS
            val vz = dirX * sinS + dirZ * cosS
            return doubleArrayOf(vx * speed, (Random.nextDouble() - 0.35) * 1.0 * bu, vz * speed)
        }
        cloud(
            count = 60, colorHex = 0xffffff, size = 1.6 * bu, textureName = "nt_shard",
            life = 1.2, drag = 0.4, opacity = 1.0,
            delays = FloatArray(60) { (it * 0.009f) },
        ) {
            val v = coneVelocity((8.0 + Random.nextDouble() * 5.0) * bu)
            doubleArrayOf(handX, handY, handZ, v[0], v[1], v[2])
        }
        cloud(
            count = 120, colorHex = 0x66c8ff, size = 0.7 * bu, textureName = "burst_bright",
            life = 1.2, drag = 0.4, opacity = 0.85,
            delays = FloatArray(120) { (it * 0.0045f) },
        ) {
            val v = coneVelocity((6.0 + Random.nextDouble() * 6.0) * bu)
            doubleArrayOf(handX, handY, handZ, v[0], v[1], v[2])
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
            SphereGeometry(0.3 * bu, 14, 12),
            MeshBasicMaterial(obj { this.color = Color(0x050010) }),
        )
        val rim = Mesh(SphereGeometry(0.42 * bu, 14, 12), basic(0x4b0082, opacity = 0.55))
        core.add(rim)
        return core
    }

    /** Megid's tail: a violet wake dragged behind the void core. */
    fun megidTrail(x: Double, y: Double, z: Double, dirX: Double, dirZ: Double) {
        cloud(
            count = 5, colorHex = 0x7722cc, size = 1.5 * bu, textureName = "burst_bright",
            life = 0.65, drag = 1.0, opacity = 1.0,
        ) {
            doubleArrayOf(
                x + (Random.nextDouble() - 0.5) * 0.3 * bu,
                y + (Random.nextDouble() - 0.5) * 0.3 * bu,
                z + (Random.nextDouble() - 0.5) * 0.3 * bu,
                -dirX * (1.8 + Random.nextDouble() * 1.8) * bu + (Random.nextDouble() - 0.5) * bu,
                (Random.nextDouble() - 0.5) * 0.8 * bu,
                -dirZ * (1.8 + Random.nextDouble() * 1.8) * bu + (Random.nextDouble() - 0.5) * bu,
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

    /** Fireball shells whose shader clock has to be wound on each frame. */
    private val shaderClocks = mutableListOf<Mesh>()

    /** A fireball's two filaments, so they can be turned against each other. */
    private class SpiralPair(val red: Object3D, val orange: Object3D)

    private val spirals = mutableListOf<SpiralPair>()

    private var fxClock = 0.0

    fun update(deltaTime: Double) {
        // The fireballs' own life: the flame shader's clock, and the two filaments winding
        // against one another inside each core.
        fxClock += deltaTime
        shaderClocks.retainAll { it.parent != null }
        for (shell in shaderClocks) {
            shell.asDynamic().material.uniforms.uTime.value = fxClock
        }
        spirals.retainAll { it.red.parent != null }
        for (pair in spirals) {
            pair.red.rotation.z += FOIE_SPIRAL_SPIN * deltaTime
            pair.orange.rotation.z -= FOIE_SPIRAL_SPIN * 0.79 * deltaTime
        }

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
                if (c.delays != null && c.age < c.delays[i]) continue
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

        val orbiterIterator = orbiters.iterator()
        while (orbiterIterator.hasNext()) {
            val o = orbiterIterator.next()
            o.life -= deltaTime
            if (o.life <= 0) {
                o.sprite.parent?.remove(o.sprite)
                orbiterIterator.remove()
                continue
            }
            val progress = 1.0 - o.life / o.maxLife
            val radius = o.radiusFrom + (o.radiusTo - o.radiusFrom) * progress
            val angle = o.angle0 + o.spin * progress * o.maxLife
            o.sprite.position.set(
                o.centerX + cos(angle) * radius,
                o.centerY + sin(progress * PI * 3) * 0.3 * bu,
                o.centerZ + sin(angle) * radius,
            )
            // Full flame until the last quarter, then it burns out at the rim.
            o.sprite.material.opacity = if (progress < 0.75) 1.0 else (1.0 - progress) * 4.0
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


    private companion object {
        const val FOIE_FLAME_RADIUS = 0.30
        const val FOIE_FLAME_DETAIL = 3
        const val FOIE_SPIRAL_SEGMENTS = 48
        const val FOIE_SPIRAL_TURNS = 2.25
        const val FOIE_SPIRAL_RADIUS = 0.16
        const val FOIE_SPIRAL_LENGTH = 0.32
        const val FOIE_SPIRAL_TUBE = 0.012
        const val FOIE_SPIRAL_SPIN = 1.45

        /** Passes the surface position through; the flame is entirely the fragment's doing. */
        const val FOIE_VERTEX_SHADER = """
            varying vec3 vNormal;
            varying vec3 vPos;
            void main() {
                vNormal = normalize(normalMatrix * normal);
                vPos = position;
                gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0);
            }
        """

        /**
         * Value-noise fbm crawling over the sphere, biased upward so the tongues lick the way
         * fire does, shaded ember -> gold -> white-hot and rimmed with a fresnel edge.
         */
        const val FOIE_FRAGMENT_SHADER = """
            precision mediump float;
            varying vec3 vNormal;
            varying vec3 vPos;
            uniform float uTime;

            float hash(vec3 p) {
                p = fract(p * 0.3183099 + vec3(0.1, 0.2, 0.3));
                p *= 17.0;
                return fract(p.x * p.y * p.z * (p.x + p.y + p.z));
            }

            float noise(vec3 p) {
                vec3 i = floor(p), f = fract(p);
                f = f * f * (3.0 - 2.0 * f);
                return mix(mix(mix(hash(i), hash(i + vec3(1,0,0)), f.x),
                               mix(hash(i + vec3(0,1,0)), hash(i + vec3(1,1,0)), f.x), f.y),
                           mix(mix(hash(i + vec3(0,0,1)), hash(i + vec3(1,0,1)), f.x),
                               mix(hash(i + vec3(0,1,1)), hash(i + vec3(1,1,1)), f.x), f.y), f.z);
            }

            float fbm(vec3 p) {
                float v = 0.0, a = 0.5;
                for (int i = 0; i < 4; i++) { v += a * noise(p); p *= 2.03; a *= 0.5; }
                return v;
            }

            void main() {
                vec3 p = normalize(vPos);
                float n = fbm(p * 3.0 + vec3(0.0, uTime * 0.55, -uTime * 0.35));
                float n2 = fbm(p * 7.0 - vec3(uTime * 0.35, 0.0, uTime * 0.6));

                float tongue = smoothstep(0.05, 0.95, n * 0.75 + n2 * 0.45);
                float directional = smoothstep(-0.15, 0.9, p.y + 0.35 * n);
                float alpha = clamp(0.16 + tongue * 0.95 + directional * 0.35, 0.0, 1.0);

                vec3 hot = vec3(1.0, 0.95, 0.52);
                vec3 gold = vec3(1.0, 0.38, 0.025);
                vec3 ember = vec3(0.42, 0.025, 0.005);

                float heat = smoothstep(0.12, 0.72, tongue + directional * 0.25);
                vec3 color = mix(ember, gold, heat);
                color = mix(color, hot, smoothstep(0.55, 1.0, n2 + directional * 0.3));

                float fresnel = pow(1.0 - max(dot(normalize(vNormal), vec3(0.0, 0.0, 1.0)), 0.0), 2.4);
                color += vec3(1.0, 0.25, 0.02) * fresnel * 1.5;

                gl_FragColor = vec4(color, alpha);
            }
        """
    }
}

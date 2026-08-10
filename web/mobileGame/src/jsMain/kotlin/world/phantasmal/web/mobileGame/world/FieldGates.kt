package world.phantasmal.web.mobileGame.world

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import world.phantasmal.web.externals.three.AnimationAction
import world.phantasmal.web.externals.three.AnimationMixer
import world.phantasmal.web.externals.three.LoopOnce
import world.phantasmal.web.externals.three.Object3D
import world.phantasmal.web.externals.three.Vector3

/**
 * A field area's interactive gates: the room-exit doors the wave script opens, the laser fences,
 * and the floor switches that drop them. Placements come straight out of the layout's own object
 * records (see SpawnObject); this class is the runtime state -- which gates are still shut, the
 * open animations, and the wall-segment push that makes a shut gate actually block the player.
 *
 * Gate collision is a single oriented segment per gate rather than mesh geometry: a gate is a
 * flat barrier across a path, and pushing the player out of a thickened line captures exactly
 * that (the authored map collision has no triangles for them -- in the real game they're dynamic
 * objects, which is the whole reason they can open).
 */
class FieldGates(
    /** The player's cylinder radius in world units, captured once at build time. */
    private val playerRadius: Double,
) {
    class Gate(
        val meshes: List<Object3D>,
        private val mixers: List<AnimationMixer>,
        private val openActions: List<AnimationAction>,
        val doorId: Int,
        val x: Double,
        val y: Double,
        val z: Double,
        halfWidth: Double,
        yaw: Double,
        /** Fences vanish when dropped; doors stay and play their opening clip instead. */
        private val hideOnOpen: Boolean,
        /**
         * The rising bridge's inversion: hidden until opened, *appearing* on open rather than
         * vanishing, and never blocking -- it's floor, not wall.
         */
        private val revealOnOpen: Boolean = false,
        /** False for reveal-style gates whose closed state shouldn't push the player. */
        val blocking: Boolean = true,
        /**
         * The Caves' pattern: unlocking doesn't remove the door, it *unlocks* it. The status
         * light goes red to green ([lockedMeshes] swap out for [meshes]) and the door then
         * slides open and shut as anyone approaches, blocking only while it's shut. Forest
         * gates leave this empty and keep their open-once behaviour.
         */
        private val lockedMeshes: List<Object3D> = emptyList(),
        /** How far the door slides up when opening, in world units. Measured from the model. */
        private val slideHeight: Double = 0.0,
    ) {
        var isOpen = false
            private set

        /** True for a Caves-style door: unlocked, but still a door that opens and shuts. */
        private val isSlidingDoor = lockedMeshes.isNotEmpty() && slideHeight > 0.0

        /** 0 shut, 1 fully open -- the slide's progress while [isOpen] and someone is near. */
        private var slide = 0.0

        /** A sliding door only blocks while it's still down. */
        val blocksNow: Boolean get() = blocking && (!isOpen || (isSlidingDoor && slide < 0.5))

        init {
            if (revealOnOpen) for (mesh in meshes) mesh.visible = false
            // Locked: the red twin is what's on screen until the mechanism releases.
            if (lockedMeshes.isNotEmpty()) for (mesh in meshes) mesh.visible = false
        }

        /**
         * Drives a Caves door's slide: unlocked doors part when someone comes near and close
         * again behind them. [near] is whether the player is within opening distance.
         */
        fun updateSlide(deltaTime: Double, near: Boolean) {
            if (!isSlidingDoor || !isOpen) return

            val target = if (near) 1.0 else 0.0
            val step = deltaTime / SLIDE_SECONDS
            slide = when {
                slide < target -> minOf(target, slide + step)
                slide > target -> maxOf(target, slide - step)
                else -> slide
            }
            for (mesh in meshes) mesh.position.y = y + slide * slideHeight
        }

        // The blocking segment's endpoints: the gate's local X axis rotated into world space
        // (same convention as Object3D.rotation.y), half the gate's width out to each side.
        val ax = x + cos(yaw) * halfWidth
        val az = z - sin(yaw) * halfWidth
        val bx = x - cos(yaw) * halfWidth
        val bz = z + sin(yaw) * halfWidth

        fun open() {
            if (isOpen) return
            isOpen = true

            if (revealOnOpen) {
                for (mesh in meshes) mesh.visible = true
                return
            }

            // A Caves door: the red twin goes away and the green one takes its place. The
            // door itself stays -- it's unlocked, not gone.
            if (lockedMeshes.isNotEmpty()) {
                for (mesh in lockedMeshes) mesh.visible = false
                for (mesh in meshes) mesh.visible = true
                return
            }

            if (hideOnOpen) {
                for (mesh in meshes) mesh.visible = false
            } else {
                for (action in openActions) {
                    action.setLoop(LoopOnce, 1)
                    action.clampWhenFinished = true
                    action.reset()
                    action.play()
                }
            }
        }

        fun update(deltaTime: Double) {
            for (mixer in mixers) mixer.update(deltaTime)
        }
    }

    class FloorSwitch(
        val meshes: List<Object3D>,
        private val mixers: List<AnimationMixer>,
        private val pressActions: List<AnimationAction>,
        /** The gate this switch drops -- linked at build time, see GameRenderer. */
        private val linked: Gate?,
        val x: Double,
        val y: Double,
        val z: Double,
        /**
         * Fires instead of [linked] when set: the Caves' four-button doors need every panel
         * in their set pressed, which one switch alone can't decide.
         */
        private val onPressed: (() -> Unit)? = null,
    ) {
        var pressed = false
            private set

        fun press() {
            if (pressed) return
            pressed = true

            for (action in pressActions) {
                action.setLoop(LoopOnce, 1)
                action.clampWhenFinished = true
                action.reset()
                action.play()
            }

            linked?.open()
            onPressed?.invoke()
        }

        fun update(deltaTime: Double) {
            for (mixer in mixers) mixer.update(deltaTime)
        }
    }

    val doors = mutableListOf<Gate>()
    val fences = mutableListOf<Gate>()

    /** Door-driven reveals: the rising bridges. Open = rise into view; never blocking. */
    val bridges = mutableListOf<Gate>()

    val switches = mutableListOf<FloorSwitch>()

    /**
     * Advances animations, opens any door the wave script has unlocked since last frame, presses
     * any switch the player is standing on, and pushes [playerPosition] out of every gate still
     * shut. Call after the character controller has moved the player.
     */
    fun update(deltaTime: Double, playerPosition: Vector3, unlockedDoors: Set<Int>) {
        for (door in doors) {
            if (!door.isOpen && door.doorId in unlockedDoors) door.open()
            door.update(deltaTime)
            // An unlocked Caves door parts for whoever walks up to it.
            val dx = playerPosition.x - door.x
            val dz = playerPosition.z - door.z
            door.updateSlide(deltaTime, dx * dx + dz * dz <= OPEN_RADIUS * OPEN_RADIUS)
        }

        for (fence in fences) fence.update(deltaTime)

        for (bridge in bridges) {
            if (!bridge.isOpen && bridge.doorId in unlockedDoors) bridge.open()
            bridge.update(deltaTime)
        }

        for (switch in switches) {
            if (!switch.pressed) {
                val dx = playerPosition.x - switch.x
                val dz = playerPosition.z - switch.z

                if (dx * dx + dz * dz <= PRESS_RADIUS * PRESS_RADIUS &&
                    abs(playerPosition.y - switch.y) <= HEIGHT_TOLERANCE
                ) {
                    switch.press()
                }
            }

            switch.update(deltaTime)
        }

        resolve(playerPosition)
    }

    /** Pushes [position] out of every shut gate's segment, in the XZ plane. */
    private fun resolve(position: Vector3) {
        for (gate in doors) resolveGate(gate, position)
        for (gate in fences) resolveGate(gate, position)
    }

    private fun resolveGate(gate: Gate, position: Vector3) {
        // blocksNow, not isOpen: an unlocked Caves door still blocks while it's shut.
        if (!gate.blocksNow) return
        if (abs(position.y - gate.y) > HEIGHT_TOLERANCE) return

        // Closest point on the gate's segment to the player, in XZ.
        val abx = gate.bx - gate.ax
        val abz = gate.bz - gate.az
        val lengthSq = abx * abx + abz * abz
        if (lengthSq <= 0.0) return

        val t = (((position.x - gate.ax) * abx + (position.z - gate.az) * abz) / lengthSq)
            .coerceIn(0.0, 1.0)
        val cx = gate.ax + abx * t
        val cz = gate.az + abz * t

        val dx = position.x - cx
        val dz = position.z - cz
        val distSq = dx * dx + dz * dz
        val minDist = playerRadius + GATE_THICKNESS

        if (distSq < minDist * minDist && distSq > 1e-9) {
            val dist = sqrt(distSq)
            val push = minDist - dist
            position.x += dx / dist * push
            position.z += dz / dist * push
        }
    }

    companion object {
        /** How close the player's feet have to be to a floor switch to press it. */
        private const val PRESS_RADIUS = 6.0

        /** Gates on another floor don't block or press -- same idea as WallCollider's tolerance. */
        private const val HEIGHT_TOLERANCE = 30.0

        /** Half the physical thickness of a gate's barrier plane. */
        private const val GATE_THICKNESS = 2.0

        /** Half-widths of the blocking segment, by gate kind. */
        const val DOOR_HALF_WIDTH = 15.0

        /** How close the player must come before an unlocked Caves door slides open. */
        private const val OPEN_RADIUS = 34.0

        /** How long a Caves door takes to slide fully open or shut. */
        private const val SLIDE_SECONDS = 0.45
        const val FENCE_HALF_WIDTH = 20.0
    }
}

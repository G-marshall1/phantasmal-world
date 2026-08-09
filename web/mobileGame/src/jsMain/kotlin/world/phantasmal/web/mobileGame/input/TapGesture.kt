package world.phantasmal.web.mobileGame.input

import kotlin.js.Date
import kotlin.math.abs
import org.w3c.dom.HTMLElement
import org.w3c.dom.pointerevents.PointerEvent
import world.phantasmal.core.disposable.Disposable
import world.phantasmal.core.disposable.Disposer
import world.phantasmal.webui.dom.disposableListener

/**
 * Fires [onTap] only for a genuine tap: press and release in roughly the same spot, quickly.
 *
 * Menu rows used to act on `pointerdown`, which is the moment a finger *lands* -- so dragging a
 * long item list up or down chose whatever row the scroll happened to start on. That made every
 * shop, bank and pack list a hazard to scroll. Waiting for the release and rejecting anything
 * that moved is what separates "I was scrolling" from "I meant this one".
 *
 * The movement allowance is in CSS pixels and deliberately small, but not zero: fingers always
 * slide a little, and demanding perfect stillness would make the lists feel unresponsive.
 */
fun HTMLElement.disposableTap(onTap: () -> Unit): Disposable {
    var startX = 0.0
    var startY = 0.0
    var startedAt = 0.0
    var tracking = false

    val down = disposableListener<PointerEvent>("pointerdown", { e ->
        e.stopPropagation()
        startX = e.clientX.toDouble()
        startY = e.clientY.toDouble()
        startedAt = Date.now()
        tracking = true
    })

    val move = disposableListener<PointerEvent>("pointermove", { e ->
        if (!tracking) return@disposableListener
        if (abs(e.clientX.toDouble() - startX) > TAP_SLOP_PX ||
            abs(e.clientY.toDouble() - startY) > TAP_SLOP_PX
        ) {
            // Became a scroll; this gesture is no longer a tap.
            tracking = false
        }
    })

    val up = disposableListener<PointerEvent>("pointerup", { e ->
        if (!tracking) return@disposableListener
        tracking = false

        val moved = abs(e.clientX.toDouble() - startX) > TAP_SLOP_PX ||
            abs(e.clientY.toDouble() - startY) > TAP_SLOP_PX
        if (!moved && Date.now() - startedAt <= TAP_MAX_MS) {
            e.stopPropagation()
            onTap()
        }
    })

    val cancel = disposableListener<PointerEvent>("pointercancel", { tracking = false })
    val leave = disposableListener<PointerEvent>("pointerleave", { tracking = false })

    return Disposer(down, move, up, cancel, leave)
}

/** How far a finger may slide and still count as a tap rather than a scroll. */
private const val TAP_SLOP_PX = 10.0

/** How long a press may last and still count as a tap. */
private const val TAP_MAX_MS = 700.0

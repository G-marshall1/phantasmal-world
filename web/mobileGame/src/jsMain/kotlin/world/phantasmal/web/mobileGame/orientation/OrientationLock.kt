package world.phantasmal.web.mobileGame.orientation

import kotlinx.browser.window
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * Locks the screen to landscape orientation. The Screen Orientation lock API only takes effect
 * once installed as a standalone app (e.g. via Capacitor) and is rejected by regular mobile
 * browser tabs (e.g. plain mobile Safari) -- that rejection is expected there, not a bug.
 */
fun lockLandscapeOrientation() {
    val orientation = window.asDynamic().screen?.orientation

    if (orientation == null || orientation.lock == null) {
        logger.warn { "Screen Orientation API not available, can't lock to landscape." }
        return
    }

    val lockResult: dynamic = orientation.lock("landscape")

    lockResult?.then({ _: dynamic -> }, { error: dynamic ->
        logger.warn { "Could not lock screen orientation to landscape: $error" }
    })
}

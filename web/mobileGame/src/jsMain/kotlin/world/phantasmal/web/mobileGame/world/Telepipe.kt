package world.phantasmal.web.mobileGame.world

/**
 * The one live Telepipe, shared across map rebuilds. Using a Telepipe in the field records the
 * spot here and raises a pipe on it; Pioneer 2 raises the matching return pipe while this is
 * set. The pair stays up -- reusable in both directions, like the real item -- until a new pipe
 * replaces it. Session-scoped: pipes don't survive closing the game, which is also true of the
 * original's.
 */
object ActiveTelepipe {
    /** Field map the pipe stands in, or null when no pipe is up. */
    var fieldMap: String? = null
    var x = 0.0
    var z = 0.0

    /**
     * Set when the player leaves Pioneer 2 THROUGH the return pipe, so the field map being
     * rebuilt knows to arrive beside the pipe instead of at the area's entrance.
     */
    var returnRequested = false

    fun open(map: String, atX: Double, atZ: Double) {
        fieldMap = map
        x = atX
        z = atZ
        returnRequested = false
    }
}

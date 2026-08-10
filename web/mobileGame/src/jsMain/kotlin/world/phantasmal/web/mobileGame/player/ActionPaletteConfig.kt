package world.phantasmal.web.mobileGame.player

import kotlinx.browser.localStorage
import org.w3c.dom.get
import org.w3c.dom.set
import world.phantasmal.web.mobileGame.input.ActionHex
import world.phantasmal.web.mobileGame.input.ActionIcon
import world.phantasmal.web.mobileGame.input.ItemIcon

/**
 * Something a palette hex can be set to.
 *
 * This is the list the palette editor offers, and the extension point for everything the game
 * grows later -- techniques, photon arts, items. Adding an entry here makes it assignable
 * everywhere without touching the palette or the editor.
 *
 * An action carries either an [actionIcon] (the attack glyphs) or an [itemIcon] (the smaller
 * category glyphs); both come from the UI sheet, normalised to white so they read on any hex.
 */
enum class GameAction(
    val label: String,
    val description: String,
    val actionIcon: ActionIcon? = null,
    val itemIcon: ItemIcon? = null,
    /** Set on the castable actions -- see Techniques.kt. */
    val technique: Technique? = null,
    /** Set on the tool-use actions: tapping the hex drinks/uses one from the pack. */
    val tool: ToolType? = null,
) {
    NORMAL_ATTACK(
        "Normal Attack",
        "A standard swing. Always connects.",
        actionIcon = ActionIcon.NORMAL,
    ),
    HEAVY_ATTACK(
        "Heavy Attack",
        "Slower and often misses, but hits far harder.",
        actionIcon = ActionIcon.HEAVY,
    ),
    SPECIAL_ATTACK(
        "Special Attack",
        "The equipped weapon's own special. Needs a weapon that has one.",
        actionIcon = ActionIcon.SPECIAL,
    ),
    CHAT(
        "Chat & Emotes",
        "Open the chat window and perform emotes.",
        itemIcon = ItemIcon.CHAT,
    ),
    EMOTE(
        "Emotes",
        "Jump straight to the emote list.",
        itemIcon = ItemIcon.EMOTE,
    ),
    FOIE(
        "Foie",
        "Hurls a fireball at whatever stands ahead.",
        technique = Technique.FOIE,
    ),
    BARTA(
        "Barta",
        "A freezing wave along the ground ahead, striking everything in its path.",
        technique = Technique.BARTA,
    ),
    ZONDE(
        "Zonde",
        "Lightning strikes the nearest enemy instantly.",
        technique = Technique.ZONDE,
    ),
    RESTA(
        "Resta",
        "Restores your own HP.",
        technique = Technique.RESTA,
    ),
    GIFOIE("Gifoie", "A ring of fire circles you, burning everything it touches.", technique = Technique.GIFOIE),
    RAFOIE("Rafoie", "An explosion erupts on the target, catching everything near it.", technique = Technique.RAFOIE),
    GIBARTA("Gibarta", "A freezing breath, wide and short -- it can freeze solid.", technique = Technique.GIBARTA),
    RABARTA("Rabarta", "Ice bursts in a circle around you, freezing what it catches.", technique = Technique.RABARTA),
    GIZONDE("Gizonde", "Lightning that leaps from enemy to enemy.", technique = Technique.GIZONDE),
    RAZONDE("Razonde", "Lightning storms around you, striking everything nearby.", technique = Technique.RAZONDE),
    GRANTS("Grants", "Countless arrows of light on one enemy. The heaviest technique there is.", technique = Technique.GRANTS),
    MEGID("Megid", "A curse of death. Sometimes it simply takes them.", technique = Technique.MEGID),
    SHIFTA("Shifta", "Raises your attack power for a time.", technique = Technique.SHIFTA),
    DEBAND("Deband", "Raises your defense for a time.", technique = Technique.DEBAND),
    JELLEN("Jellen", "Saps the attack of enemies around you.", technique = Technique.JELLEN),
    ZALURE("Zalure", "Saps the defense of enemies around you.", technique = Technique.ZALURE),
    ANTI("Anti", "Cures poison.", technique = Technique.ANTI),
    REVERSER("Reverser", "Revives a fallen ally.", technique = Technique.REVERSER),
    RYUKER("Ryuker", "Opens a telepipe back to Pioneer 2.", technique = Technique.RYUKER),
    USE_MONOMATE("Monomate", "Drink a Monomate: restores 80 HP.", tool = ToolType.MONOMATE),
    USE_DIMATE("Dimate", "Drink a Dimate: restores 200 HP.", tool = ToolType.DIMATE),
    USE_TRIMATE("Trimate", "Drink a Trimate: full HP.", tool = ToolType.TRIMATE),
    USE_MONOFLUID("Monofluid", "Drink a Monofluid: restores 70 TP.", tool = ToolType.MONOFLUID),
    USE_DIFLUID("Difluid", "Drink a Difluid: restores 200 TP.", tool = ToolType.DIFLUID),
    USE_TRIFLUID("Trifluid", "Drink a Trifluid: full TP.", tool = ToolType.TRIFLUID),
    USE_ANTIDOTE("Antidote", "Cures poison.", tool = ToolType.ANTIDOTE),
    USE_ANTIPARALYSIS("Antiparalysis", "Cures paralysis.", tool = ToolType.ANTIPARALYSIS),
    ;
}

/**
 * Which action sits in which hex.
 *
 * Editable rather than fixed because the palette is only four slots and the game will have far
 * more than four things worth putting in them -- the player decides what a given fight needs. The
 * defaults reproduce PSO's own arrangement: normal attack under the thumb, heavy to its right,
 * the weapon special to the left, chat on top.
 *
 * Stored per device rather than per character: it's a control-layout preference, not part of who
 * the character is.
 */
class ActionPaletteConfig(private val assignments: MutableMap<ActionHex, GameAction>) {
    operator fun get(hex: ActionHex): GameAction = assignments.getValue(hex)

    operator fun set(hex: ActionHex, action: GameAction) {
        assignments[hex] = action
        save()
    }

    private fun save() {
        localStorage[STORAGE_KEY] =
            ActionHex.entries.joinToString(",") { assignments.getValue(it).name }
    }

    companion object {
        private const val STORAGE_KEY = "pw.actionPalette"

        val DEFAULTS: Map<ActionHex, GameAction> = mapOf(
            ActionHex.BOTTOM to GameAction.NORMAL_ATTACK,
            ActionHex.RIGHT to GameAction.HEAVY_ATTACK,
            ActionHex.LEFT to GameAction.SPECIAL_ATTACK,
            ActionHex.TOP to GameAction.CHAT,
        )

        /**
         * Restores the saved layout, falling back to [DEFAULTS] for anything missing or no longer
         * recognised -- an action removed in a later version would otherwise leave a slot holding
         * a name that can't be resolved.
         */
        fun load(): ActionPaletteConfig {
            val stored = localStorage[STORAGE_KEY]?.split(",").orEmpty()

            val assignments = ActionHex.entries.withIndex().associate { (i, hex) ->
                val saved = stored.getOrNull(i)?.let { name ->
                    GameAction.entries.find { it.name == name }
                }
                hex to (saved ?: DEFAULTS.getValue(hex))
            }

            return ActionPaletteConfig(assignments.toMutableMap())
        }
    }
}

/**
 * Blue Burst's numbered ability bar: nine extra slots along the bottom of the screen, so a
 * weapon special, a heal, techniques and tools can all sit within reach at once. Same
 * per-device persistence rationale as the palette.
 */
class ActionBarConfig(private val slots: MutableList<GameAction>) {
    operator fun get(index: Int): GameAction = slots[index]

    operator fun set(index: Int, action: GameAction) {
        slots[index] = action
        save()
    }

    private fun save() {
        localStorage[STORAGE_KEY] = slots.joinToString(",") { it.name }
    }

    companion object {
        const val SLOT_COUNT = 9

        private const val STORAGE_KEY = "pw.actionBar"

        fun load(defaults: List<GameAction>): ActionBarConfig {
            val stored = localStorage[STORAGE_KEY]?.split(",").orEmpty()
            val slots = (0 until SLOT_COUNT).map { i ->
                stored.getOrNull(i)?.let { name -> GameAction.entries.find { it.name == name } }
                    ?: defaults.getOrElse(i) { GameAction.CHAT }
            }
            return ActionBarConfig(slots.toMutableList())
        }
    }
}

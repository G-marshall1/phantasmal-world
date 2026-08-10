package world.phantasmal.web.mobileGame.menu

import kotlinx.browser.document
import org.w3c.dom.HTMLElement
import org.w3c.dom.pointerevents.PointerEvent
import world.phantasmal.core.disposable.Disposable
import world.phantasmal.core.disposable.TrackedDisposable
import world.phantasmal.web.mobileGame.input.ActionHex
import world.phantasmal.web.mobileGame.input.ItemIcon
import world.phantasmal.web.mobileGame.input.HudSprites
import world.phantasmal.web.mobileGame.input.actionIconStyle
import world.phantasmal.web.mobileGame.input.disposableTap
import world.phantasmal.web.mobileGame.input.hudSpriteStyle
import world.phantasmal.web.mobileGame.input.itemIconStyle
import world.phantasmal.web.mobileGame.input.toolIconStyle
import world.phantasmal.web.mobileGame.player.itemIcon
import world.phantasmal.web.mobileGame.player.iconCell
import world.phantasmal.web.mobileGame.player.TECH_DISK_ICON_CELL
import world.phantasmal.web.mobileGame.player.ActionPaletteConfig
import world.phantasmal.web.mobileGame.player.BaseStats
import world.phantasmal.web.mobileGame.player.Mag
import world.phantasmal.web.mobileGame.player.ToolType
import world.phantasmal.web.mobileGame.player.TreasureType
import world.phantasmal.web.mobileGame.player.WeaponItem
import world.phantasmal.web.mobileGame.player.WeaponTier
import world.phantasmal.web.mobileGame.player.TOOL_SHOP
import world.phantasmal.web.mobileGame.player.isMagFood
import world.phantasmal.web.mobileGame.player.magFoodHint
import world.phantasmal.web.mobileGame.player.shopPrice
import world.phantasmal.web.mobileGame.player.weaponBuyPrice
import world.phantasmal.web.mobileGame.player.weaponSellPrice
import world.phantasmal.web.mobileGame.player.TREASURE_SELL_PRICE
import world.phantasmal.web.mobileGame.player.GameAction
import world.phantasmal.web.mobileGame.player.ActionBarConfig
import world.phantasmal.web.mobileGame.player.WeaponType
import world.phantasmal.web.mobileGame.player.criticalChance
import world.phantasmal.web.shared.dto.SectionId
import world.phantasmal.web.viewer.models.CharacterClass
import world.phantasmal.webui.dom.disposableListener

/** One room on the area map -- see [MenuState.rooms]. */
class MenuRoom(
    val id: Int,
    val x: Double,
    val z: Double,
    val visited: Boolean,
    val current: Boolean,
)

/**
 * Everything the menu draws, gathered when it opens. Passed in rather than read live so the menu
 * has no reference back into the renderer, and so the numbers can't shift underneath a screen the
 * player is reading.
 */
class MenuState(
    val characterName: String,
    val characterClass: CharacterClass,
    val sectionId: SectionId,
    val stats: BaseStats,
    val hp: Int,
    val maxHp: Int,
    val weaponSlug: String?,
    val weaponType: WeaponType?,
    val areaName: String,
    val rooms: List<MenuRoom>,
    /** The action palette's current assignments, so they can be edited from the menu. */
    val paletteConfig: ActionPaletteConfig?,
    /** The numbered ability bar's assignments -- null in town, like the palette. */
    val barConfig: ActionBarConfig? = null,
    /** The equipped Mag and its blast gauge. */
    val mag: Mag,
    val photonBlast: Double,
    val playerX: Double,
    val playerZ: Double,
    val level: Int = 1,
    val totalExp: Int = 0,
    val toNextLevel: Int = 0,
    val meseta: Int = 0,
    /** Tool stacks carried, in [ToolType] order. */
    val tools: List<Pair<ToolType, Int>> = emptyList(),
    /** Banked weapons -- everything picked up and not currently drawn. */
    val weaponsInventory: List<WeaponItem> = emptyList(),
    val treasures: List<TreasureType> = emptyList(),
    /** Unused technique disks, as display labels; the use callback takes the index. */
    val techDisks: List<String> = emptyList(),
    val onUseDisk: (Int) -> Unit = {},
    /** Fired from the Items pane; the renderer applies the effect and reopens with fresh state. */
    val onUseTool: (ToolType) -> Unit = {},
    val onEquipWeapon: (WeaponItem) -> Unit = {},
    /** The worn frame/barrier and the frame's unit slots; null entries read as empty. */
    val frameLabel: String? = null,
    val frameDetail: String? = null,
    val barrierLabel: String? = null,
    val barrierDetail: String? = null,
    val unitLabels: List<Pair<String, String>?> = emptyList(),
    /**
     * What the player owns for each slot, as (name, detail) in the order the equip callbacks
     * index them. A slot opens its own list rather than cycling blindly: with a pack of a
     * dozen frames, tapping through them one at a time to find the right one is a chore.
     */
    val frameChoices: List<Pair<String, String>> = emptyList(),
    val barrierChoices: List<Pair<String, String>> = emptyList(),
    val unitChoices: List<Pair<String, String>> = emptyList(),
    val weaponChoices: List<Pair<String, String>> = emptyList(),
    /** Null unequips the slot. */
    val onChooseFrame: (Int?) -> Unit = {},
    val onChooseBarrier: (Int?) -> Unit = {},
    val onChooseUnit: (Int, Int?) -> Unit = { _, _ -> },
    val onChooseWeapon: (Int) -> Unit = {},
    /** Current technique points; max is stats.tp. */
    val tp: Int = 0,
    /** True on Pioneer 2, where the shops are. */
    val inTown: Boolean = false,
    /** What the palette editor may assign -- techniques only appear for classes that cast. */
    val availableActions: List<GameAction> = GameAction.entries.toList(),
    val magFeedsLeft: Int = 0,
    val magNextWindowSeconds: Int = 0,
    val magFormName: String = "Mag",
    val onFeedMag: (ToolType) -> Unit = {},
    /** The arms shop's stock for this character's level. */
    val shopStock: List<WeaponTier> = emptyList(),
    val onBuyTool: (ToolType, Int) -> Unit = { _, _ -> },
    val onBuyWeapon: (WeaponTier, Int) -> Unit = { _, _ -> },
    val onSellWeapon: (WeaponItem) -> Unit = {},
    val onSellTreasure: (TreasureType) -> Unit = {},
)

/**
 * The in-game menu, laid out like PSO's: a list of sections down the left, a description of the
 * highlighted one beneath it, and the selected section's contents filling the panel on the right.
 *
 * Opened by a button rather than a console button press, since this is a phone -- see
 * [world.phantasmal.web.mobileGame.input.MenuButton]. Tapping a section switches panes; the close
 * button and a tap on the backdrop both dismiss it. The whole thing is a DOM overlay, like the
 * rest of the HUD, so it costs nothing to draw while shut.
 *
 * Several of PSO's sections have no system behind them yet. Those are drawn greyed out and
 * unselectable rather than omitted -- the shape of the real menu is the point, and a section that
 * visibly exists but isn't ready reads very differently from one silently missing. The panes that
 * do open show only real state.
 */
class GameMenu(
    private val container: HTMLElement,
    private val onClose: () -> Unit,
    /** Called after the player reassigns a palette slot, so the HUD can redraw. */
    private val onPaletteChanged: () -> Unit = {},
) : TrackedDisposable() {
    private val listeners = mutableListOf<Disposable>()

    private val styleTag = (document.createElement("style") as HTMLElement).also { el ->
        el.textContent = STYLESHEET
        document.head!!.appendChild(el)
    }

    private val root = (document.createElement("div") as HTMLElement).also { el ->
        el.className = "pw-menu-root"
        el.style.display = "none"
        container.appendChild(el)
    }

    private val navList = (document.createElement("div") as HTMLElement).also { it.className = "pw-menu-nav-list" }
    private val navItems = mutableListOf<HTMLElement>()
    private val description = (document.createElement("div") as HTMLElement).also { it.className = "pw-menu-desc" }
    private val content = (document.createElement("div") as HTMLElement).also { it.className = "pw-menu-content" }

    private var state: MenuState? = null
    private var selected = MenuSection.EQUIP

    /** The "are you sure" box for irreversible taps -- see renderItems. */
    private val confirmPrompt = ConfirmPrompt(container)

    /** The row the player last chose, lit until the pane is rebuilt. */
    private var selectedRow: HTMLElement? = null

    /** Which equip slot has its list open, if any -- "WEAPON", "ARMOR", "SHIELD", "UNIT0".. */
    private var openEquipSlot: String? = null

    var isOpen: Boolean = false
        private set

    init {
        val backdrop = (document.createElement("div") as HTMLElement).also { el ->
            el.className = "pw-menu-backdrop"
            root.appendChild(el)
        }
        listeners.add(backdrop.disposableListener<PointerEvent>("pointerdown", { close() }))

        val panel = (document.createElement("div") as HTMLElement).also { el ->
            el.className = "pw-menu-panel"
            root.appendChild(el)
        }
        // Taps inside the panel mustn't reach the backdrop, or every selection closes the menu.
        listeners.add(panel.disposableListener<PointerEvent>("pointerdown", { it.stopPropagation() }))

        val left = (document.createElement("div") as HTMLElement).also { el ->
            el.className = "pw-menu-left"
            panel.appendChild(el)
        }

        (document.createElement("div") as HTMLElement).also { el ->
            el.className = "pw-menu-title"
            el.textContent = "ITEM PACK"
            left.appendChild(el)
        }

        left.appendChild(navList)
        left.appendChild(description)

        for (section in MenuSection.entries) {
            val item = (document.createElement("div") as HTMLElement).also { el ->
                el.className = if (section.available) "pw-menu-nav" else "pw-menu-nav pw-menu-nav-off"
                el.textContent = section.label
                navList.appendChild(el)
            }
            navItems.add(item)

            if (section.available) {
                listeners.add(
                    item.disposableTap {
                        selected = section
                        render()
                    }
                )
            }
        }

        panel.appendChild(content)

        val close = (document.createElement("div") as HTMLElement).also { el ->
            el.className = "pw-menu-close"
            el.textContent = "CLOSE"
            panel.appendChild(el)
        }
        listeners.add(close.disposableListener<PointerEvent>("pointerdown", {
            it.stopPropagation()
            close()
        }))
    }

    fun open(state: MenuState) {
        this.state = state
        isOpen = true
        root.style.display = "block"
        document.body?.classList?.add("pw-menu-open")
        render()
    }

    fun close() {
        if (!isOpen) return
        isOpen = false
        closeActionPicker()
        confirmPrompt.close()
        root.style.display = "none"
        document.body?.classList?.remove("pw-menu-open")
        onClose()
    }

    private fun render() {
        val state = state ?: return

        navItems.forEachIndexed { i, el ->
            val section = MenuSection.entries[i]
            val classes = buildString {
                append("pw-menu-nav")
                if (!section.available) append(" pw-menu-nav-off")
                if (section == selected) append(" pw-menu-nav-on")
            }
            el.className = classes
        }

        description.textContent = selected.description
        content.innerHTML = ""
        // The pane is rebuilt from scratch, so the old highlighted element is gone with it.
        selectedRow = null

        when (selected) {
            MenuSection.EQUIP -> renderEquip(state)
            MenuSection.ITEMS -> renderItems(state)
            MenuSection.MAG -> renderMag(state)
            MenuSection.SHOP -> renderShop(state)
            MenuSection.ACTIONS -> renderActions(state)
            MenuSection.AREA_MAP -> renderAreaMap(state)
            MenuSection.STATUS -> renderStatus(state)
            else -> Unit
        }
    }

    // --- Panes ---

    private fun renderEquip(state: MenuState) {
        heading("EQUIP")

        tappableSlotRow(
            "WEAPON",
            state.weaponSlug?.let { spaceOutName(it) } ?: "--",
            state.weaponType?.let { "${it.name.lowercase().replace('_', ' ')}  +${it.atp} ATP" }
                ?: "Tap to draw a weapon you own",
            state.weaponType?.itemIcon ?: ItemIcon.MELEE,
        ) { toggleEquipSlot("WEAPON") }

        equipList(
            slotKey = "WEAPON",
            choices = state.weaponChoices,
            icon = ItemIcon.MELEE,
            allowEmpty = false,
        ) { index -> index?.let { state.onChooseWeapon(it) } }

        tappableSlotRow(
            "ARMOR",
            state.frameLabel ?: "--",
            state.frameDetail ?: "Tap to wear a frame you own",
            ItemIcon.ARMOR,
        ) { toggleEquipSlot("ARMOR") }
        equipList("ARMOR", state.frameChoices, ItemIcon.ARMOR) { state.onChooseFrame(it) }

        tappableSlotRow(
            "SHIELD",
            state.barrierLabel ?: "--",
            state.barrierDetail ?: "Tap to raise a barrier you own",
            ItemIcon.SHIELD,
        ) { toggleEquipSlot("SHIELD") }
        equipList("SHIELD", state.barrierChoices, ItemIcon.SHIELD) { state.onChooseBarrier(it) }

        if (state.unitLabels.isEmpty()) {
            if (state.frameLabel != null) {
                note("This frame has no unit slots. Frames roll zero to four when found.")
            }
        } else {
            state.unitLabels.forEachIndexed { i, unit ->
                val key = "UNIT$i"
                tappableSlotRow(
                    "UNIT ${i + 1}",
                    unit?.first ?: "--",
                    unit?.second ?: "Tap to slot a unit you own",
                    ItemIcon.UNIT,
                ) { toggleEquipSlot(key) }
                equipList(key, state.unitChoices, ItemIcon.UNIT) { state.onChooseUnit(i, it) }
            }
        }

        note(
            "Tap a slot to see everything you own that fits it. Frames carry the unit slots; " +
                "the Tekker deals in all of it."
        )
    }

    private fun toggleEquipSlot(key: String) {
        openEquipSlot = if (openEquipSlot == key) null else key
        render()
    }

    /**
     * The open slot's contents: everything owned that fits, plus a way to take the slot empty.
     * Drawn indented under its slot so it reads as belonging to it.
     */
    private fun equipList(
        slotKey: String,
        choices: List<Pair<String, String>>,
        icon: ItemIcon,
        allowEmpty: Boolean = true,
        onChoose: (Int?) -> Unit,
    ) {
        if (openEquipSlot != slotKey) return

        if (choices.isEmpty() && !allowEmpty) {
            note("Nothing else in the pack fits this slot.")
            return
        }

        if (allowEmpty) {
            val row = (document.createElement("div") as HTMLElement).also { el ->
                el.className = "pw-menu-slot pw-menu-action-row pw-menu-equip-choice"
                content.appendChild(el)
            }
            (document.createElement("div") as HTMLElement).also { k ->
                k.className = "pw-menu-slot-k"
                k.textContent = "TAKE OFF"
                row.appendChild(k)
            }
            (document.createElement("div") as HTMLElement).also { v ->
                v.className = "pw-menu-slot-v"
                v.textContent = "(empty)"
                row.appendChild(v)
            }
            listeners.add(row.disposableTap {
                selectRow(row)
                openEquipSlot = null
                onChoose(null)
            })
        }

        choices.forEachIndexed { index, (name, detail) ->
            val row = (document.createElement("div") as HTMLElement).also { el ->
                el.className = "pw-menu-slot pw-menu-action-row pw-menu-equip-choice"
                content.appendChild(el)
            }
            appendIcon(row, icon)
            (document.createElement("div") as HTMLElement).also { v ->
                v.className = "pw-menu-slot-v"
                v.textContent = name
                row.appendChild(v)
            }
            (document.createElement("div") as HTMLElement).also { d ->
                d.className = "pw-menu-slot-d"
                d.textContent = detail
                row.appendChild(d)
            }
            listeners.add(row.disposableTap {
                selectRow(row)
                openEquipSlot = null
                onChoose(index)
            })
        }

        if (choices.isEmpty()) {
            note("Nothing else in the pack fits this slot.")
        }
    }

    private fun tappableSlotRow(
        label: String,
        value: String,
        detail: String,
        icon: ItemIcon? = null,
        onTap: () -> Unit,
    ) {
        val row = (document.createElement("div") as HTMLElement).also { el ->
            el.className =
                if (value == "--") "pw-menu-slot pw-menu-slot-empty pw-menu-action-row"
                else "pw-menu-slot pw-menu-action-row"
            content.appendChild(el)
        }
        (document.createElement("div") as HTMLElement).also { k ->
            k.className = "pw-menu-slot-k"
            k.textContent = label
            row.appendChild(k)
        }
        appendIcon(row, icon)
        (document.createElement("div") as HTMLElement).also { v ->
            v.className = "pw-menu-slot-v"
            v.textContent = value
            row.appendChild(v)
        }
        (document.createElement("div") as HTMLElement).also { d ->
            d.className = "pw-menu-slot-d"
            d.textContent = detail
            row.appendChild(d)
        }
        listeners.add(
row.disposableTap {
            selectRow(row)
            onTap()
        })
    }

    /**
     * The pack: meseta up top, then the tool stacks (tap to use), the carried weapons (tap to
     * equip -- any class; equipping across classes hot-swaps the model and motion set), and the
     * rare trophies.
     */
    private fun renderItems(state: MenuState) {
        heading("ITEMS")

        val header = (document.createElement("div") as HTMLElement).also { el ->
            el.className = "pw-menu-card"
            content.appendChild(el)
        }
        (document.createElement("div") as HTMLElement).also { el ->
            el.className = "pw-menu-row"
            el.innerHTML = """<span class="pw-menu-k"></span><span class="pw-menu-v"></span>"""
            (el.firstChild as HTMLElement).textContent = "MESETA"
            (el.lastChild as HTMLElement).textContent = "${state.meseta}"
            header.appendChild(el)
        }

        if (state.tools.isEmpty() && state.weaponsInventory.isEmpty() && state.treasures.isEmpty() &&
            state.techDisks.isEmpty()
        ) {
            note(
                "The pack is empty. Enemies in the field drop tools, weapons and Meseta -- " +
                    "and the truly rare finds arrive as the red box."
            )
            return
        }

        for ((tool, count) in state.tools) {
            val row = (document.createElement("div") as HTMLElement).also { el ->
                el.className = "pw-menu-slot pw-menu-action-row"
                content.appendChild(el)
            }
            appendToolIcon(row, tool.iconCell)
            (document.createElement("div") as HTMLElement).also { k ->
                k.className = "pw-menu-slot-k"
                k.textContent = tool.uiName
                row.appendChild(k)
            }
            (document.createElement("div") as HTMLElement).also { v ->
                v.className = "pw-menu-slot-v"
                v.textContent = "x$count"
                row.appendChild(v)
            }
            (document.createElement("div") as HTMLElement).also { d ->
                d.className = "pw-menu-slot-d"
                d.textContent = toolHint(tool)
                row.appendChild(d)
            }
            listeners.add(row.disposableTap {
                selectRow(row)
                // Using an item can't be undone and these lists are scrolled constantly, so it
                // asks before spending anything.
                confirmPrompt.open("Use ${tool.uiName}?", confirmText = "USE") {
                    state.onUseTool(tool)
                }
            })
        }

        for ((index, label) in state.techDisks.withIndex()) {
            val row = (document.createElement("div") as HTMLElement).also { el ->
                el.className = "pw-menu-slot pw-menu-action-row"
                content.appendChild(el)
            }
            appendToolIcon(row, TECH_DISK_ICON_CELL)
            (document.createElement("div") as HTMLElement).also { k ->
                k.className = "pw-menu-slot-k"
                k.textContent = "DISK"
                row.appendChild(k)
            }
            (document.createElement("div") as HTMLElement).also { v ->
                v.className = "pw-menu-slot-v"
                v.textContent = label
                row.appendChild(v)
            }
            (document.createElement("div") as HTMLElement).also { d ->
                d.className = "pw-menu-slot-d"
                d.textContent = "Learn the technique at this level"
                row.appendChild(d)
            }
            listeners.add(row.disposableTap {
                selectRow(row)
                confirmPrompt.open("Use $label?", confirmText = "LEARN") {
                    state.onUseDisk(index)
                }
            })
        }

        for (item in state.weaponsInventory) {
            val row = (document.createElement("div") as HTMLElement).also { el ->
                el.className = "pw-menu-slot pw-menu-action-row"
                content.appendChild(el)
            }
            appendIcon(row, item.itemIcon)
            (document.createElement("div") as HTMLElement).also { k ->
                k.className = "pw-menu-slot-k"
                k.textContent = "WEAPON"
                row.appendChild(k)
            }
            (document.createElement("div") as HTMLElement).also { v ->
                v.className = "pw-menu-slot-v"
                v.textContent = item.displayName
                row.appendChild(v)
            }
            (document.createElement("div") as HTMLElement).also { d ->
                d.className = "pw-menu-slot-d"
                d.textContent = "Tap to equip  (${item.atpMin}-${item.atpMin + item.atpSpread} ATP)"
                row.appendChild(d)
            }
            listeners.add(
row.disposableTap {
                selectRow(row)
                state.onEquipWeapon(item)
            })
        }

        for (treasureItem in state.treasures) {
            val row = (document.createElement("div") as HTMLElement).also { el ->
                el.className = "pw-menu-slot"
                content.appendChild(el)
            }
            appendToolIcon(row, treasureItem.iconCell)
            (document.createElement("div") as HTMLElement).also { k ->
                k.className = "pw-menu-slot-k"
                k.textContent = "RARE"
                row.appendChild(k)
            }
            (document.createElement("div") as HTMLElement).also { v ->
                v.className = "pw-menu-slot-v"
                v.style.color = "#ff8d80"
                v.textContent = treasureItem.uiName
                row.appendChild(v)
            }
            (document.createElement("div") as HTMLElement).also { d ->
                d.className = "pw-menu-slot-d"
                d.textContent = "A trophy of the hunt."
                row.appendChild(d)
            }
        }

        note("Tap a tool to use it. Mates restore HP; Materials raise a stat permanently.")
    }

    private fun toolHint(tool: ToolType): String = when (tool) {
        ToolType.MONOMATE -> "Restores 80 HP"
        ToolType.DIMATE -> "Restores 200 HP"
        ToolType.TRIMATE -> "Fully restores HP"
        ToolType.MONOFLUID -> "Restores 70 TP (nothing spends TP yet)"
        ToolType.DIFLUID -> "Restores 200 TP (nothing spends TP yet)"
        ToolType.TRIFLUID -> "Fully restores TP (nothing spends TP yet)"
        ToolType.ANTIDOTE -> "Cures poison"
        ToolType.ANTIPARALYSIS -> "Cures paralysis"
        ToolType.SOL_ATOMIZER -> "Cures all ailments"
        ToolType.MOON_ATOMIZER -> "Revives a fallen ally (needs multiplayer)"
        ToolType.STAR_ATOMIZER -> "Fully restores HP"
        ToolType.SCAPE_DOLL -> "Fires by itself when you fall"
        ToolType.TELEPIPE -> "Opens a pipe to Pioneer 2 and back"
        ToolType.POWER_MATERIAL -> "+2 ATP, permanent"
        ToolType.MIND_MATERIAL -> "+2 MST, permanent"
        ToolType.HP_MATERIAL -> "+2 max HP, permanent"
        ToolType.EVADE_MATERIAL -> "+2 EVP, permanent"
        ToolType.DEF_MATERIAL -> "+2 DFP, permanent"
        ToolType.LUCK_MATERIAL -> "+2 LCK, permanent"
        ToolType.TP_MATERIAL -> "+2 max TP, permanent"
        ToolType.MONOGRINDER -> "Grinds the equipped weapon +1"
        ToolType.DIGRINDER -> "Grinds the equipped weapon +2"
        ToolType.TRIGRINDER -> "Grinds the equipped weapon +3"
    }

    private fun renderMag(state: MenuState) {
        heading("MAG")

        val mag = state.mag

        val card = (document.createElement("div") as HTMLElement).also { el ->
            el.className = "pw-menu-card"
            content.appendChild(el)
        }

        fun row(label: String, value: String, dim: Boolean = false) {
            (document.createElement("div") as HTMLElement).also { el ->
                el.className = if (dim) "pw-menu-row pw-menu-row-dim" else "pw-menu-row"
                el.innerHTML = """<span class="pw-menu-k"></span><span class="pw-menu-v"></span>"""
                (el.firstChild as HTMLElement).textContent = label
                (el.lastChild as HTMLElement).textContent = value
                card.appendChild(el)
            }
        }

        row("FORM", state.magFormName)
        row("LEVEL", "${mag.level}")
        // PSO writes a Mag's stats as DEF/POW/DEX/MIND.
        row("DEF / POW / DEX / MIND", "${mag.def} / ${mag.pow} / ${mag.dex} / ${mag.mind}")
        row("SYNCHRO", "${mag.synchro}%")
        row("IQ", "${mag.iq}")
        row("PHOTON BLAST", "${state.photonBlast.toInt()} / 100")

        val bonus = (document.createElement("div") as HTMLElement).also { el ->
            el.className = "pw-menu-card"
            content.appendChild(el)
        }

        fun bonusRow(label: String, value: String) {
            (document.createElement("div") as HTMLElement).also { el ->
                el.className = "pw-menu-row"
                el.innerHTML = """<span class="pw-menu-k"></span><span class="pw-menu-v"></span>"""
                (el.firstChild as HTMLElement).textContent = label
                (el.lastChild as HTMLElement).textContent = value
                bonus.appendChild(el)
            }
        }

        bonusRow("GRANTS ATP", "+${mag.bonusAtp}")
        bonusRow("GRANTS DFP", "+${mag.bonusDfp}")
        bonusRow("GRANTS ATA", "+${mag.bonusAta}")
        bonusRow("GRANTS MST", "+${mag.bonusMst}")

        val feedable = state.tools.filter { isMagFood(it.first) }

        (document.createElement("div") as HTMLElement).also { el ->
            el.className = "pw-menu-heading"
            el.textContent =
                if (state.magFeedsLeft > 0) "FEED  (${state.magFeedsLeft} left)"
                else "FEED  (full -- next in ${state.magNextWindowSeconds}s)"
            content.appendChild(el)
        }

        if (feedable.isEmpty()) {
            note("Nothing in the pack is Mag food. Mates, fluids, cures and atomizers all feed it.")
        } else {
            for ((tool, count) in feedable) {
                val feedRow = (document.createElement("div") as HTMLElement).also { el ->
                    el.className = "pw-menu-slot pw-menu-action-row"
                    content.appendChild(el)
                }
                (document.createElement("div") as HTMLElement).also { k ->
                    k.className = "pw-menu-slot-k"
                    k.textContent = tool.uiName
                    feedRow.appendChild(k)
                }
                (document.createElement("div") as HTMLElement).also { v ->
                    v.className = "pw-menu-slot-v"
                    v.textContent = "x$count"
                    feedRow.appendChild(v)
                }
                (document.createElement("div") as HTMLElement).also { d ->
                    d.className = "pw-menu-slot-d"
                    d.textContent = magFoodHint(tool)
                    feedRow.appendChild(d)
                }
                listeners.add(
feedRow.disposableTap {
                    selectRow(feedRow)
                    state.onFeedMag(tool)
                })
            }
        }

        note(
            "A Mag's stats become yours: 1 DFP per DEF, 2 ATP per POW, half an ATA per DEX and " +
                "2 MST per MIND -- raising one is worth far more than levelling. Three feeds " +
                "per 3:30, mates feed POW, fluids MIND, cures DEX. At level 10 it evolves."
        )
    }

    /**
     * The Pioneer 2 shops, reachable from the menu while in town (walk-up counters are still to
     * come). Tool prices are the wiki's; the arms shop's stock grows with character level.
     */
    private fun renderShop(state: MenuState) {
        heading("SHOP")

        if (!state.inTown) {
            note("The shops are on Pioneer 2. Take the teleporter home to trade.")
            return
        }

        val header = (document.createElement("div") as HTMLElement).also { el ->
            el.className = "pw-menu-card"
            content.appendChild(el)
        }
        (document.createElement("div") as HTMLElement).also { el ->
            el.className = "pw-menu-row"
            el.innerHTML = """<span class="pw-menu-k"></span><span class="pw-menu-v"></span>"""
            (el.firstChild as HTMLElement).textContent = "MESETA"
            (el.lastChild as HTMLElement).textContent = "${state.meseta}"
            header.appendChild(el)
        }

        fun shopRow(
            label: String,
            value: String,
            detail: String,
            icon: ItemIcon? = null,
            onTap: (() -> Unit)?,
        ) {
            val row = (document.createElement("div") as HTMLElement).also { el ->
                el.className =
                    if (onTap != null) "pw-menu-slot pw-menu-action-row" else "pw-menu-slot"
                content.appendChild(el)
            }
            (document.createElement("div") as HTMLElement).also { k ->
                k.className = "pw-menu-slot-k"
                k.textContent = label
                row.appendChild(k)
            }
            appendIcon(row, icon)
            (document.createElement("div") as HTMLElement).also { v ->
                v.className = "pw-menu-slot-v"
                v.textContent = value
                row.appendChild(v)
            }
            (document.createElement("div") as HTMLElement).also { d ->
                d.className = "pw-menu-slot-d"
                d.textContent = detail
                row.appendChild(d)
            }
            if (onTap != null) {
                listeners.add(
row.disposableTap {
                    selectRow(row)
                    onTap()
                })
            }
        }

        for ((tool, fullPrice) in TOOL_SHOP) {
            val price = shopPrice(fullPrice)
            shopRow("TOOL", tool.uiName, "$price Meseta -- tap to buy", tool.itemIcon) {
                state.onBuyTool(tool, price)
            }
        }

        for (tier in state.shopStock) {
            val price = weaponBuyPrice(tier)
            shopRow("ARMS", tier.name, "${tier.atpMin}-${tier.atpMax} ATP  --  $price Meseta", tier.type.itemIcon) {
                state.onBuyWeapon(tier, price)
            }
        }

        if (state.weaponsInventory.isNotEmpty() || state.treasures.isNotEmpty()) {
            (document.createElement("div") as HTMLElement).also { el ->
                el.className = "pw-menu-heading"
                el.textContent = "SELL"
                content.appendChild(el)
            }
            for (item in state.weaponsInventory) {
                shopRow("SELL", item.displayName, "Sells for ${weaponSellPrice(item)} Meseta", item.itemIcon) {
                    state.onSellWeapon(item)
                }
            }
            for (treasureItem in state.treasures) {
                shopRow("SELL", treasureItem.uiName, "Sells for $TREASURE_SELL_PRICE Meseta", treasureItem.itemIcon) {
                    state.onSellTreasure(treasureItem)
                }
            }
        }

        note("The arms shop's stock grows with your level. Rares sell for a pittance -- keep them.")
    }

    /**
     * The action palette editor. Each hex cycles through the available actions on tap, so the
     * player can put whatever a given fight calls for under their thumb.
     *
     * A list rather than a drag-and-drop grid: four slots and a handful of actions don't need
     * dragging, and tapping is far more reliable on a phone than a drag that has to start on a
     * small target.
     */
    private fun renderActions(state: MenuState) {
        heading("ACTIONS")

        val config = state.paletteConfig

        if (config == null) {
            note("The action palette only exists in areas where you can fight.")
            return
        }

        for (slot in ActionHex.entries) {
            val current = config[slot]

            val row = (document.createElement("div") as HTMLElement).also { el ->
                el.className = "pw-menu-slot pw-menu-action-row"
                content.appendChild(el)
            }

            (document.createElement("div") as HTMLElement).also { swatch ->
                swatch.className = "pw-menu-action-swatch"
                swatch.style.background = slot.ringColor
                row.appendChild(swatch)
            }
            (document.createElement("div") as HTMLElement).also { k ->
                k.className = "pw-menu-slot-k"
                k.textContent = slotName(slot)
                row.appendChild(k)
            }
            val valueEl = (document.createElement("div") as HTMLElement).also { v ->
                v.className = "pw-menu-slot-v"
                v.textContent = current.label
                row.appendChild(v)
            }
            val descEl = (document.createElement("div") as HTMLElement).also { d ->
                d.className = "pw-menu-slot-d"
                d.textContent = current.description
                row.appendChild(d)
            }

            listeners.add(row.disposableTap {
                selectRow(row)
                val actions = state.availableActions.ifEmpty { GameAction.entries.toList() }
                openActionPicker("${slotName(slot)} HEX", actions, config[slot]) { picked ->
                    config[slot] = picked
                    onPaletteChanged()
                    // Updated in place rather than re-rendering the pane: a rebuild resets the
                    // scroll position, which yanked the player back to the top on every change.
                    valueEl.textContent = picked.label
                    descEl.textContent = picked.description
                }
            })
        }

        state.barConfig?.let { bar ->
            (document.createElement("div") as HTMLElement).also { el ->
                el.className = "pw-menu-heading"
                el.textContent = "ABILITY BAR"
                content.appendChild(el)
            }

            for (i in 0 until ActionBarConfig.SLOT_COUNT) {
                val current = bar[i]

                val row = (document.createElement("div") as HTMLElement).also { el ->
                    el.className = "pw-menu-slot pw-menu-action-row"
                    content.appendChild(el)
                }
                (document.createElement("div") as HTMLElement).also { k ->
                    k.className = "pw-menu-slot-k"
                    k.textContent = "SLOT ${i + 1}"
                    row.appendChild(k)
                }
                val valueEl = (document.createElement("div") as HTMLElement).also { v ->
                    v.className = "pw-menu-slot-v"
                    v.textContent = current.label
                    row.appendChild(v)
                }
                val descEl = (document.createElement("div") as HTMLElement).also { d ->
                    d.className = "pw-menu-slot-d"
                    d.textContent = current.description
                    row.appendChild(d)
                }

                listeners.add(row.disposableTap {
                    selectRow(row)
                    val actions = state.availableActions.ifEmpty { GameAction.entries.toList() }
                    openActionPicker("SLOT ${i + 1}", actions, bar[i]) { picked ->
                        bar[i] = picked
                        onPaletteChanged()
                        valueEl.textContent = picked.label
                        descEl.textContent = picked.description
                    }
                })
            }
        }

        note("Tap a slot, then pick its action from the grid. Saved on this device.")
    }

    // --- The action picker ---

    private var pickerEl: HTMLElement? = null

    private fun closeActionPicker() {
        pickerEl?.remove()
        pickerEl = null
    }

    /** The action's glyph as CSS, the same recipe the bar and palette hexes draw with. */
    private fun actionIconCss(action: GameAction): String = when {
        action.tool != null -> toolIconStyle(action.tool.iconCell, PICKER_ITEM_ICON_SCALE)
        action.actionIcon != null -> actionIconStyle(action.actionIcon, PICKER_ACTION_ICON_SCALE)
        action.itemIcon != null -> itemIconStyle(action.itemIcon, PICKER_ITEM_ICON_SCALE)
        action.technique != null -> hudSpriteStyle(
            HudSprites.hexTile(action.technique.icon.iconCol, action.technique.icon.iconRow),
            PICKER_TECH_TILE_SCALE,
        )
        else -> ""
    }

    /**
     * Every assignable action at once, icons and all -- tap one and it's in the slot. Replaces
     * the old cycle-through-them-one-tap-at-a-time flow, which meant dozens of taps (each one
     * also re-rendering the pane and losing the scroll position) to reach the action wanted.
     */
    private fun openActionPicker(
        title: String,
        actions: List<GameAction>,
        current: GameAction,
        onPick: (GameAction) -> Unit,
    ) {
        closeActionPicker()

        val backdrop = (document.createElement("div") as HTMLElement).also { el ->
            el.className = "pw-menu-picker-backdrop"
            root.appendChild(el)
        }
        pickerEl = backdrop
        listeners.add(backdrop.disposableTap { closeActionPicker() })

        val panel = (document.createElement("div") as HTMLElement).also { el ->
            el.className = "pw-menu-picker"
            backdrop.appendChild(el)
        }
        // Swallows taps on the panel chrome so they don't reach the backdrop's close.
        listeners.add(panel.disposableTap {})

        (document.createElement("div") as HTMLElement).also { bar ->
            bar.className = "pw-menu-picker-title"
            panel.appendChild(bar)
            (document.createElement("span") as HTMLElement).also { t ->
                t.textContent = title
                bar.appendChild(t)
            }
            (document.createElement("span") as HTMLElement).also { x ->
                x.className = "pw-menu-picker-close"
                x.textContent = "CLOSE"
                bar.appendChild(x)
                listeners.add(x.disposableTap { closeActionPicker() })
            }
        }

        val grid = (document.createElement("div") as HTMLElement).also { el ->
            el.className = "pw-menu-picker-grid"
            panel.appendChild(el)
        }

        for (action in actions) {
            val cell = (document.createElement("div") as HTMLElement).also { el ->
                el.className =
                    if (action == current) "pw-menu-picker-cell pw-menu-picker-cell-on"
                    else "pw-menu-picker-cell"
                grid.appendChild(el)
            }
            (document.createElement("div") as HTMLElement).also { box ->
                box.className = "pw-menu-picker-icon"
                cell.appendChild(box)
                (document.createElement("div") as HTMLElement).also { glyph ->
                    glyph.style.cssText = actionIconCss(action)
                    box.appendChild(glyph)
                }
            }
            (document.createElement("div") as HTMLElement).also { label ->
                label.className = "pw-menu-picker-label"
                label.textContent = action.label
                cell.appendChild(label)
            }
            listeners.add(cell.disposableTap {
                onPick(action)
                closeActionPicker()
            })
        }
    }

    private fun slotName(slot: ActionHex): String = when (slot) {
        ActionHex.TOP -> "TOP"
        ActionHex.LEFT -> "LEFT"
        ActionHex.RIGHT -> "RIGHT"
        ActionHex.BOTTOM -> "BOTTOM"
    }

    /**
     * The area map. Real: rooms come from the map's own section table, and which ones are drawn as
     * explored is the set the player has actually walked into (see RoomWaveDirector).
     */
    private fun renderAreaMap(state: MenuState) {
        heading(state.areaName.uppercase())

        if (state.rooms.isEmpty()) {
            note("No map for this area -- its room layout hasn't been generated yet.")
            return
        }

        val grid = (document.createElement("div") as HTMLElement).also { el ->
            el.className = "pw-menu-map"
            content.appendChild(el)
        }

        // Rooms sit on a regular grid, so their own world coordinates place them directly.
        val xs = state.rooms.map { it.x }
        val zs = state.rooms.map { it.z }
        val minX = xs.min()
        val minZ = zs.min()
        val spanX = (xs.max() - minX).coerceAtLeast(1.0)
        val spanZ = (zs.max() - minZ).coerceAtLeast(1.0)

        for (room in state.rooms) {
            val cell = (document.createElement("div") as HTMLElement).also { el ->
                el.className = buildString {
                    append("pw-menu-room")
                    if (room.visited) append(" pw-menu-room-seen")
                    if (room.current) append(" pw-menu-room-here")
                }
                el.textContent = room.id.toString()
                grid.appendChild(el)
            }
            cell.style.left = "${(room.x - minX) / spanX * 100}%"
            cell.style.top = "${(room.z - minZ) / spanZ * 100}%"
        }

        note("Rooms you have entered are lit. The room you're in is marked.")
    }

    /** The character card: every one of the seven base attributes, all real. */
    private fun renderStatus(state: MenuState) {
        heading(state.characterName)

        val card = (document.createElement("div") as HTMLElement).also { el ->
            el.className = "pw-menu-card"
            content.appendChild(el)
        }

        fun row(parent: HTMLElement, label: String, value: String, dim: Boolean = false) {
            (document.createElement("div") as HTMLElement).also { el ->
                el.className = if (dim) "pw-menu-row pw-menu-row-dim" else "pw-menu-row"
                el.innerHTML =
                    """<span class="pw-menu-k"></span><span class="pw-menu-v"></span>"""
                (el.firstChild as HTMLElement).textContent = label
                (el.lastChild as HTMLElement).textContent = value
                parent.appendChild(el)
            }
        }

        row(card, "CLASS", state.characterClass.uiName)
        row(card, "SECTION ID", state.sectionId.uiName)
        row(card, "LEVEL", "${state.level}")
        row(card, "TOTAL EXP", "${state.totalExp}")
        row(card, "TO NEXT LV", "${state.toNextLevel}")
        row(card, "MESETA", "${state.meseta}")

        val stats = (document.createElement("div") as HTMLElement).also { el ->
            el.className = "pw-menu-card"
            content.appendChild(el)
        }

        val weaponAtp = state.weaponType?.atp ?: 0
        row(stats, "HP", "${state.hp} / ${state.maxHp}")
        row(stats, "TP", "${state.tp} / ${state.stats.tp}")
        row(
            stats,
            "ATP",
            if (weaponAtp > 0) "${state.stats.atp}  (+$weaponAtp)" else "${state.stats.atp}",
        )
        row(stats, "DFP", "${state.stats.dfp}")
        row(stats, "MST", "${state.stats.mst}")
        row(stats, "ATA", "${state.stats.ata}")
        row(stats, "LCK", "${state.stats.lck}")
        row(stats, "CRITICAL", "${(criticalChance(state.stats.lck) * 100).toInt()}%")

        note(
            if (state.stats.tp == 0) {
                "An android: no TP or MST at all, so techniques are out. Luck gives every character " +
                    "a 1% critical rate per 5 points."
            } else {
                "Luck gives every character a 1% critical rate per 5 points, capped at 20%."
            }
        )
    }

    // --- Small builders ---

    private fun heading(text: String) {
        (document.createElement("div") as HTMLElement).also { el ->
            el.className = "pw-menu-heading"
            el.textContent = text
            content.appendChild(el)
        }
    }

    /** Lights the row the player just chose, in the HUD sheet's own green. */
    private fun selectRow(row: HTMLElement) {
        selectedRow?.classList?.remove("pw-menu-row-on")
        row.classList.add("pw-menu-row-on")
        selectedRow = row
    }

    /** The category glyph beside a name -- see ItemIcons.kt for what marks what. */
    private fun appendIcon(parent: HTMLElement, icon: ItemIcon?) {
        if (icon == null) return
        (document.createElement("div") as HTMLElement).also { el ->
            el.className = "pw-menu-icon"
            el.style.cssText = itemIconStyle(icon, ITEM_ICON_SCALE)
            parent.appendChild(el)
        }
    }

    /** The per-tool glyph, where a row is one specific tool rather than a category. */
    private fun appendToolIcon(parent: HTMLElement, cell: Int) {
        (document.createElement("div") as HTMLElement).also { el ->
            el.className = "pw-menu-icon"
            el.style.cssText = toolIconStyle(cell, ITEM_ICON_SCALE)
            parent.appendChild(el)
        }
    }

    private fun slotRow(label: String, value: String, detail: String?, icon: ItemIcon? = null) {
        (document.createElement("div") as HTMLElement).also { el ->
            el.className = if (value == "--") "pw-menu-slot pw-menu-slot-empty" else "pw-menu-slot"
            content.appendChild(el)

            (document.createElement("div") as HTMLElement).also { k ->
                k.className = "pw-menu-slot-k"
                k.textContent = label
                el.appendChild(k)
            }
            appendIcon(el, icon)
            (document.createElement("div") as HTMLElement).also { v ->
                v.className = "pw-menu-slot-v"
                v.textContent = value
                el.appendChild(v)
            }
            if (detail != null) {
                (document.createElement("div") as HTMLElement).also { d ->
                    d.className = "pw-menu-slot-d"
                    d.textContent = detail
                    el.appendChild(d)
                }
            }
        }
    }

    private fun note(text: String) {
        (document.createElement("div") as HTMLElement).also { el ->
            el.className = "pw-menu-note"
            el.textContent = text
            content.appendChild(el)
        }
    }

    /** "RedSaber" -> "Red Saber", so catalogue slugs read as names. */
    private fun spaceOutName(slug: String): String =
        slug.replace(Regex("(?<=[a-z0-9])(?=[A-Z])"), " ")

    override fun dispose() {
        listeners.forEach { it.dispose() }
        confirmPrompt.dispose()
        document.body?.classList?.remove("pw-menu-open")
        root.remove()
        styleTag.remove()
        super.dispose()
    }

    private companion object {
        /** The 16px glyphs sit comfortably beside the panes' 13px value text at this scale. */
        const val ITEM_ICON_SCALE = 1.0

        /** Picker glyph scales: 16px item art, 48px attack art, 50x43 technique tiles. */
        const val PICKER_ITEM_ICON_SCALE = 2.2
        const val PICKER_ACTION_ICON_SCALE = 0.75
        const val PICKER_TECH_TILE_SCALE = 0.8

        const val STYLESHEET = """
            /*
             * Laid out to match PSO's own menu rather than filling the screen: the nav column
             * runs down the left under the HP/TP panel -- which stays visible, as it does in the
             * real game -- its description sits at the bottom left, and the selected section
             * fills the right. The world stays partly visible behind, same as the original.
             *
             * Absolute positions rather than a grid: the top-left corner is spoken for by the
             * status panel, so the menu has to start below a known offset, which is awkward to
             * express as a grid track.
             *
             * Every edge offset also clears the display cutout (see --pw-safe-* in index.html).
             * In landscape an iPhone's Dynamic Island takes a strip off one side, which was
             * enough to hide this whole nav column.
             */
            .pw-menu-root { position:fixed; inset:0; z-index:60; font-family:'PWTitleFont', sans-serif; }
            .pw-menu-backdrop { position:absolute; inset:0; background:rgba(0,10,20,0.55); }

            .pw-menu-left {
              position:absolute; left:calc(10px + var(--pw-safe-left)); top:calc(104px + var(--pw-safe-top));
              bottom:calc(10px + var(--pw-safe-bottom)); width:31%; max-width:230px;
              display:flex; flex-direction:column; gap:6px; min-height:0;
            }
            .pw-menu-title {
              color:#9fe9ff; font-size:13px; letter-spacing:2px; padding:6px 10px; flex:none;
              border:2px solid rgba(90,210,255,0.55); border-radius:8px;
              background:repeating-linear-gradient(180deg, rgba(90,200,225,.14) 0px, rgba(90,200,225,.14) 1px, rgba(0,0,0,0) 1px, rgba(0,0,0,0) 3px), linear-gradient(180deg, #04141f 0%, #020a12 100%);
            }
            .pw-menu-nav-list {
              border:2px solid rgba(90,210,255,0.55); border-radius:8px; padding:4px;
              background:repeating-linear-gradient(180deg, rgba(90,200,225,.14) 0px, rgba(90,200,225,.14) 1px, rgba(0,0,0,0) 1px, rgba(0,0,0,0) 3px), linear-gradient(180deg, #04141f 0%, #020a12 100%); display:flex; flex-direction:column; gap:1px;
              overflow-y:auto; touch-action:pan-y; flex:1 1 auto; min-height:0;
            }
            .pw-menu-nav {
              color:#cfefff; font-size:13px; padding:6px 10px; border-radius:5px;
              cursor:pointer; user-select:none; touch-action:manipulation; white-space:nowrap;
            }
            .pw-menu-nav-on { color:#ff9a4a; background:rgba(90,210,255,0.18); }
            .pw-menu-nav-off { color:#587a90; }
            .pw-menu-desc {
              border:2px solid rgba(90,210,255,0.55); border-radius:8px; padding:7px 10px;
              background:repeating-linear-gradient(180deg, rgba(90,200,225,.14) 0px, rgba(90,200,225,.14) 1px, rgba(0,0,0,0) 1px, rgba(0,0,0,0) 3px), linear-gradient(180deg, #04141f 0%, #020a12 100%); color:#bfe6ff; font-size:11px; line-height:1.45;
              flex:none;
            }

            .pw-menu-content {
              position:absolute; left:calc(31% + 20px + var(--pw-safe-left)); right:calc(10px + var(--pw-safe-right));
              top:calc(104px + var(--pw-safe-top)); bottom:calc(10px + var(--pw-safe-bottom));
              border:2px solid rgba(90,210,255,0.55); border-radius:8px;
              background:repeating-linear-gradient(180deg, rgba(90,200,225,.14) 0px, rgba(90,200,225,.14) 1px, rgba(0,0,0,0) 1px, rgba(0,0,0,0) 3px), linear-gradient(180deg, #04141f 0%, #020a12 100%); padding:10px 14px; overflow-y:auto;
              touch-action:pan-y;
            }
            .pw-menu-picker-backdrop {
              position:fixed; inset:0; z-index:60;
              background:rgba(0, 6, 12, 0.62);
              display:flex; align-items:center; justify-content:center;
            }
            .pw-menu-picker {
              width:min(92vw, 620px); max-height:min(80vh, 560px);
              display:flex; flex-direction:column; overflow:hidden;
              border:2px solid rgba(90,210,255,0.65); border-radius:10px;
              background:repeating-linear-gradient(180deg, rgba(90,200,225,.12) 0px, rgba(90,200,225,.12) 1px, rgba(0,0,0,0) 1px, rgba(0,0,0,0) 3px), linear-gradient(180deg, #05202e 0%, #02101c 100%);
              box-shadow:0 10px 40px rgba(0,0,0,0.6);
            }
            .pw-menu-picker-title {
              display:flex; justify-content:space-between; align-items:center;
              padding:10px 14px; border-bottom:1px solid rgba(90,210,255,0.35);
              color:#ffd36b; font-size:14px; letter-spacing:2px;
            }
            .pw-menu-picker-close {
              color:#8fd8f0; font-size:12px; letter-spacing:1px; padding:4px 8px;
              border:1px solid rgba(90,210,255,0.45); border-radius:5px; cursor:pointer;
            }
            .pw-menu-picker-grid {
              overflow-y:auto; touch-action:pan-y; padding:12px;
              display:grid; grid-template-columns:repeat(auto-fill, minmax(92px, 1fr)); gap:8px;
            }
            .pw-menu-picker-cell {
              display:flex; flex-direction:column; align-items:center; gap:5px;
              padding:8px 4px 7px; cursor:pointer;
              border:1px solid rgba(90,210,255,0.28); border-radius:7px;
              background:rgba(4, 22, 32, 0.85);
            }
            .pw-menu-picker-cell-on {
              border-color:#ffd36b;
              background:rgba(40, 34, 8, 0.85);
            }
            .pw-menu-picker-icon {
              height:40px; display:flex; align-items:center; justify-content:center;
            }
            .pw-menu-picker-label {
              color:#bfe8f5; font-size:10px; text-align:center; line-height:1.25;
            }
            .pw-menu-heading {
              color:#ffd36b; font-size:15px; letter-spacing:2px; margin-bottom:8px;
              border-bottom:1px solid rgba(90,210,255,0.35); padding-bottom:6px;
            }
            .pw-menu-card { margin-bottom:10px; }
            .pw-menu-row, .pw-menu-slot {
              display:flex; justify-content:space-between; align-items:baseline; gap:12px;
              padding:4px 6px; border-bottom:1px solid rgba(90,210,255,0.14);
            }
            .pw-menu-row-dim { opacity:0.45; }
            .pw-menu-k, .pw-menu-slot-k { color:#8fc9e6; font-size:11px; letter-spacing:1px; }
            .pw-menu-v, .pw-menu-slot-v { color:#ffffff; font-size:13px; }
            .pw-menu-slot { flex-wrap:wrap; }
            .pw-menu-slot-empty .pw-menu-slot-v { color:#4d6f85; }
            .pw-menu-slot-d { flex-basis:100%; color:#7fd6a0; font-size:10px; text-align:right; }
            .pw-menu-action-row { align-items:center; cursor:pointer; }
            .pw-menu-icon { flex: none; opacity: .92; align-self: center; }
            .pw-menu-equip-choice { padding-left: 22px; border-left: 2px solid rgba(90,210,255,.35); }
            .pw-menu-row-on {
              background:
                repeating-linear-gradient(180deg, rgba(120,255,170,.20) 0px, rgba(120,255,170,.20) 1px,
                  rgba(0,0,0,0) 1px, rgba(0,0,0,0) 3px),
                linear-gradient(180deg, rgba(40,150,80,.55) 0%, rgba(20,90,50,.55) 100%);
              box-shadow: inset 0 0 0 1px rgba(140, 255, 190, .55);
            }
            .pw-menu-action-swatch {
              width:10px; height:10px; border-radius:2px; flex:none; margin-right:8px;
            }
            .pw-menu-note { margin-top:10px; color:#7fa8c0; font-size:10px; line-height:1.55; }

            /* Sized off viewport height so the whole grid fits the pane without scrolling, and
             * square in both axes so the rooms keep their real spacing. */
            .pw-menu-map {
              position:relative; width:clamp(110px, 36vh, 190px); height:clamp(110px, 36vh, 190px);
              margin:0 auto 6px; border:1px solid rgba(90,210,255,0.3); border-radius:6px;
              background:rgba(0,0,0,0.4);
            }
            .pw-menu-room {
              position:absolute; width:17%; height:17%; margin:-8.5% 0 0 -8.5%;
              border:1px solid rgba(90,210,255,0.25); border-radius:3px; color:#3c6076;
              font-size:9px; display:flex; align-items:center; justify-content:center;
              box-sizing:border-box;
            }
            .pw-menu-room-seen { border-color:rgba(90,210,255,0.85); background:rgba(40,120,180,0.45); color:#cfefff; }
            .pw-menu-room-here { border-color:#ffd36b; background:rgba(255,180,60,0.5); color:#fff; }

            .pw-menu-close {
              position:absolute; right:calc(14px + var(--pw-safe-right)); top:calc(76px + var(--pw-safe-top));
              padding:5px 14px; border-radius:16px;
              color:#cfefff; font-size:12px; letter-spacing:2px; user-select:none;
              background:rgba(6,26,44,0.95); border:2px solid rgba(90,210,255,0.6);
              touch-action:manipulation; z-index:2;
            }

            /*
             * The rest of the HUD is hidden while the menu is up -- the action palette and radar
             * would otherwise sit on top of it. The HP/TP panel deliberately stays: the real
             * game keeps it visible behind the menu too.
             */
            body.pw-menu-open .pw-hud-action-root,
            body.pw-menu-open .pw-hud-minimap { display:none !important; }

            @media (max-height: 430px) {
              .pw-menu-left, .pw-menu-content { top:92px; }
              .pw-menu-nav { font-size:12px; padding:5px 9px; }
              .pw-menu-desc { font-size:10px; }
            }
        """
    }
}

/**
 * PSO's own menu sections, in its order. The ones with no system behind them are kept and greyed
 * rather than dropped, so the menu reads as the real one with parts still to come.
 */
enum class MenuSection(
    val label: String,
    val description: String,
    val available: Boolean = true,
) {
    EQUIP("Equip", "Equip weapons and armor here."),
    ACTIONS("Actions", "Choose what each hex on the action palette does."),
    ITEMS("Items", "Use and sort the items you are carrying."),
    MAG("MAG", "Feed and check on the Mag you are raising."),
    SHOP("Shop", "Buy and sell at the Pioneer 2 shops."),
    TRADE("Trade Window", "Trade items with another player. Needs multiplayer.", available = false),
    AREA_MAP("Area Map", "See the rooms of this area you have explored."),
    STATUS("Status", "A full breakdown of your character's attributes."),
    QUEST("Quest Board", "Take on quests. No quest system yet.", available = false),
}

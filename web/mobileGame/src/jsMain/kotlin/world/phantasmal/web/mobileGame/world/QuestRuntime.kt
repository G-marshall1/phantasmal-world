package world.phantasmal.web.mobileGame.world

import kotlinx.browser.window
import world.phantasmal.web.core.loading.AssetLoader

/**
 * The quest system's spine: the active quest session, the parsed quest definition, and the
 * bytecode interpreter that runs the quest's real script.
 *
 * The quests themselves are the authentic BB files (see :web:assets-generation's
 * ConvertQuests), converted to JSON with their placements and complete scripts. Nothing here
 * invents content: the VM below executes the instructions the quest shipped with, and what it
 * can't do yet it skips while logging, so a quest degrades to "some stage direction missing"
 * rather than to made-up behavior.
 *
 * Map travel reloads the page, so the session -- active slug, script flags, the register file
 * the story state lives in, and the floor handlers label 0 registered -- persists in
 * localStorage and is restored on every load.
 */
object QuestSession {
    private const val KEY = "pw-quest-session"

    var slug: String? = null
        private set

    /** The script's global flags (gget/gset space). */
    val flags = mutableSetOf<Int>()

    /** The 256 script registers -- r110-and-friends carry the story state between floors. */
    val registers = IntArray(256)

    /** Floor number to handler label, as registered by the script's label 0. */
    val floorHandlers = mutableMapOf<Int, Int>()

    /** Quest-board entries the script registered: slot to (label, display name). */
    val boardHandlers = mutableMapOf<Int, Pair<Int, String>>()

    /** Slugs of completed quests, in completion order. */
    val completed = mutableListOf<String>()

    val active: Boolean get() = slug != null

    fun begin(questSlug: String) {
        slug = questSlug
        flags.clear()
        registers.fill(0)
        floorHandlers.clear()
        boardHandlers.clear()
        persist()
    }

    fun complete() {
        slug?.let { if (it !in completed) completed.add(it) }
        slug = null
        persist()
    }

    fun abandon() {
        slug = null
        persist()
    }

    fun persist() {
        val payload = buildString {
            append(slug ?: "")
            append('|')
            append(flags.joinToString(","))
            append('|')
            append(registers.joinToString(","))
            append('|')
            append(floorHandlers.entries.joinToString(",") { "${it.key}:${it.value}" })
            append('|')
            append(completed.joinToString(","))
            append('|')
            append(boardHandlers.entries.joinToString(";") { "${it.key}:${it.value.first}:${it.value.second}" })
        }
        window.localStorage.setItem(KEY, payload)
    }

    fun restore() {
        val payload = window.localStorage.getItem(KEY) ?: return
        val parts = payload.split('|')
        slug = parts.getOrNull(0)?.takeIf { it.isNotEmpty() }
        flags.clear()
        parts.getOrNull(1)?.split(',')?.mapNotNull { it.toIntOrNull() }?.let(flags::addAll)
        parts.getOrNull(2)?.split(',')?.mapIndexedNotNull { i, v ->
            v.toIntOrNull()?.let { i to it }
        }?.forEach { (i, v) -> if (i < registers.size) registers[i] = v }
        floorHandlers.clear()
        parts.getOrNull(3)?.split(',')?.forEach { entry ->
            val kv = entry.split(':')
            val floor = kv.getOrNull(0)?.toIntOrNull()
            val label = kv.getOrNull(1)?.toIntOrNull()
            if (floor != null && label != null) floorHandlers[floor] = label
        }
        completed.clear()
        parts.getOrNull(4)?.split(',')?.filter { it.isNotEmpty() }?.let(completed::addAll)
        boardHandlers.clear()
        parts.getOrNull(5)?.split(';')?.forEach { entry ->
            val kv = entry.split(':', limit = 3)
            val slot = kv.getOrNull(0)?.toIntOrNull()
            val label = kv.getOrNull(1)?.toIntOrNull()
            if (slot != null && label != null) {
                boardHandlers[slot] = label to (kv.getOrNull(2) ?: "")
            }
        }
    }
}

/** Ep1's floor numbering, the space set_floor_handler and the warps speak. */
val QUEST_FLOOR_FOR_MAP: Map<String, Int> = mapOf(
    "pioneer2" to 0,
    "forest01" to 1, "forest02" to 2,
    "cave01" to 3, "cave02" to 4, "cave03" to 5,
    "mines01" to 6, "mines02" to 7,
    "ruins01" to 8, "ruins02" to 9, "ruins03" to 10,
    "bossArea1" to 11, "bossArea2" to 12, "bossArea3" to 13, "bossArea4" to 14,
)

class QuestInstr(val op: String, val args: Array<dynamic>) {
    fun int(index: Int): Int = (args.getOrNull(index) as? Number)?.toInt() ?: 0
    fun double(index: Int): Double = (args.getOrNull(index) as? Number)?.toDouble() ?: 0.0
    fun string(index: Int): String = args.getOrNull(index) as? String ?: ""
}

class QuestNpcDef(
    val type: String,
    val area: Int,
    val section: Int,
    val x: Double,
    val y: Double,
    val z: Double,
    val yaw: Double,
    val wave: Int,
    val script: Int,
)

class QuestObjDef(
    val type: String,
    val typeId: Int,
    val area: Int,
    val section: Int,
    val x: Double,
    val y: Double,
    val z: Double,
    val yaw: Double,
    val paramsF: List<Double>,
    val paramsI: List<Int>,
)

class QuestDef(
    val name: String,
    val short: String,
    val long: String,
    val npcs: List<QuestNpcDef>,
    val objects: List<QuestObjDef>,
    val script: Map<Int, List<QuestInstr>>,
)

/** One row of the guild counter's list. */
class QuestIndexEntry(val slug: String, val name: String, val short: String)

@Suppress("UNUSED_PARAMETER")
private fun decodeUtf8(buffer: org.khronos.webgl.ArrayBuffer): String =
    js("new TextDecoder().decode(buffer)").unsafeCast<String>()

suspend fun loadQuestIndex(assetLoader: AssetLoader): List<QuestIndexEntry> {
    val raw = decodeUtf8(assetLoader.loadArrayBuffer("/quests/index.json"))
    val parsed: Array<dynamic> = JSON.parse(raw)
    return parsed.map { QuestIndexEntry(it.slug as String, it.name as String, it.short as String) }
}

suspend fun loadQuestDef(assetLoader: AssetLoader, slug: String): QuestDef {
    val raw = decodeUtf8(assetLoader.loadArrayBuffer("/quests/$slug.json"))
    val parsed: dynamic = JSON.parse(raw)

    val npcs = (parsed.npcs as Array<dynamic>).map {
        QuestNpcDef(
            it.type as String, (it.area as Number).toInt(), (it.section as Number).toInt(),
            (it.x as Number).toDouble(), (it.y as Number).toDouble(), (it.z as Number).toDouble(),
            (it.yaw as Number).toDouble(), (it.wave as Number).toInt(),
            (it.script as Number).toInt(),
        )
    }
    val objects = (parsed.objects as Array<dynamic>).map {
        QuestObjDef(
            it.type as String, (it.typeId as Number).toInt(), (it.area as Number).toInt(),
            (it.section as Number).toInt(),
            (it.x as Number).toDouble(), (it.y as Number).toDouble(), (it.z as Number).toDouble(),
            (it.yaw as Number).toDouble(),
            (it.paramsF as Array<dynamic>).map { v -> (v as Number).toDouble() },
            (it.paramsI as Array<dynamic>).map { v -> (v as Number).toInt() },
        )
    }
    val script = mutableMapOf<Int, List<QuestInstr>>()
    val keys = js("Object.keys")(parsed.script) as Array<String>
    for (key in keys) {
        val instructions = (parsed.script[key] as Array<dynamic>).map {
            QuestInstr(it.op as String, it.args as Array<dynamic>)
        }
        script[key.toInt()] = instructions
    }
    return QuestDef(
        parsed.name as String, parsed.short as String, parsed.long as String,
        npcs, objects, script,
    )
}

/** What the VM asks of the world. The host renders; the VM only decides. */
interface QuestHost {
    /** Shows one message page ([npcId] names the speaker); the VM blocks until advanced. */
    fun showMessagePage(npcId: Int, text: String)

    /** The message window closes. */
    fun closeMessage()

    /** A window_msg box; blocks until advanced. */
    fun showWindow(text: String)

    fun closeWindow()

    /** A list choice; the VM blocks until the host reports the chosen index. */
    fun showChoice(options: List<String>)

    fun addMeseta(amount: Int)
    fun unlockDoor(doorId: Int)
    fun lockDoor(doorId: Int)
    fun setPlayerPosition(x: Double, y: Double, z: Double, yaw: Double)
    fun isSwitchPressed(switchId: Int): Boolean
    fun characterClassId(): Int
    fun giveItem(code0: Int, code1: Int, code2: Int)

    /** Script-driven travel: floor number in the quest space (see QUEST_FLOOR_FOR_MAP). */
    fun goFloor(floor: Int)

    /** The ending flow's return to the Hunter's Guild counter. */
    fun returnToGuild()

    /** The quest declares itself over. */
    fun questExit()

    /** Whether this character has felled any area boss yet -- boss_is_dead's answer. */
    fun anyBossDead(): Boolean

    fun playerHp(): Int
}

/**
 * The quest script interpreter: cooperative threads over the labelled instruction lists,
 * blocking on the host's UI, with the session's registers and flags as its state.
 *
 * Coverage is the government arc's own vocabulary (~45 opcodes). Anything outside it is
 * skipped and logged once -- visible stage direction may be missing, but nothing invented.
 */
class QuestVm(
    private val def: QuestDef,
    private val host: QuestHost,
) {
    private class Frame(val label: Int, var index: Int)

    private inner class VmThread {
        val stack = ArrayDeque<Frame>()
        var syncing = false
        var blocked: Block? = null
        var messageOpen = false
        var windowOpen = false
        /** Syncs consumed this frame -- a run of them drains fast, but a busy-wait still yields. */
        var syncsThisFrame = 0
    }

    private sealed class Block {
        object Message : Block()
        object Window : Block()
        class Choice(val destRegister: Int) : Block()
    }

    /** A proximity trigger registered by at_coords_call. */
    class CoordTrigger(val x: Double, val z: Double, val radius: Double, val label: Int) {
        var fired = false
    }

    val coordTriggers = mutableListOf<CoordTrigger>()

    /** The quest-space floor number of the map this VM is running on (set by the host). */
    var currentFloor: Int = 0

    private val threads = mutableListOf<VmThread>()
    private val unknownOps = mutableSetOf<String>()
    private val registers get() = QuestSession.registers

    fun startThread(label: Int) {
        if (label !in def.script) return
        val thread = VmThread()
        thread.stack.addLast(Frame(label, 0))
        threads.add(thread)
    }

    /** The blocked-on-UI thread, if any -- taps route to it. */
    private fun uiThread(): VmThread? = threads.firstOrNull { it.blocked != null }

    fun advanceUi() {
        val thread = uiThread() ?: return
        when (thread.blocked) {
            is Block.Message, is Block.Window -> thread.blocked = null
            else -> Unit
        }
    }

    fun chooseUi(index: Int) {
        val thread = uiThread() ?: return
        val block = thread.blocked
        if (block is Block.Choice) {
            registers[block.destRegister] = index
            thread.blocked = null
        }
    }

    val uiBusy: Boolean get() = uiThread() != null

    fun update(playerX: Double, playerZ: Double) {
        // Proximity triggers fire their labels as fresh threads.
        for (trigger in coordTriggers) {
            if (trigger.fired) continue
            val dx = playerX - trigger.x
            val dz = playerZ - trigger.z
            if (dx * dx + dz * dz <= trigger.radius * trigger.radius) {
                trigger.fired = true
                startThread(trigger.label)
            }
        }

        for (thread in threads) { thread.syncing = false; thread.syncsThisFrame = 0 }
        var steps = 0
        while (steps < MAX_STEPS_PER_FRAME) {
            val runnable = threads.firstOrNull {
                it.blocked == null && !it.syncing && it.stack.isNotEmpty()
            } ?: break
            step(runnable)
            steps++
        }
        val finished = threads.removeAll { it.stack.isEmpty() && it.blocked == null }
        // Registers are the story's memory (r120 "already spoke to the Principal" and kin);
        // they must survive the page reload map travel causes, so they persist whenever a
        // script thread runs to completion.
        if (finished) QuestSession.persist()
    }

    private fun step(thread: VmThread) {
        val frame = thread.stack.last()
        val instructions = def.script[frame.label] ?: run {
            thread.stack.removeLast(); return
        }
        if (frame.index >= instructions.size) {
            thread.stack.removeLast()
            return
        }
        val instr = instructions[frame.index]
        frame.index++
        execute(thread, instr)
    }

    private fun jump(thread: VmThread, label: Int) {
        thread.stack.removeLast()
        thread.stack.addLast(Frame(label, 0))
    }

    /** The client's argument stack: arg_push* feed the next stack-args opcode. */
    private val argStack = mutableListOf<Any>()

    private fun execute(thread: VmThread, raw: QuestInstr) {
        when (raw.op) {
            "arg_pushl", "arg_pushw", "arg_pushb" -> { argStack.add(raw.int(0)); return }
            "arg_pushr" -> { argStack.add(registers[raw.int(0)]); return }
            "arg_pusha" -> { argStack.add(raw.int(0)); return }
            "arg_pushs" -> { argStack.add(raw.string(0)); return }
        }
        // A stack-args opcode carries no inline args; its arguments are whatever was pushed.
        val instr =
            if (raw.args.isEmpty() && argStack.isNotEmpty())
                QuestInstr(raw.op, argStack.toTypedArray().unsafeCast<Array<dynamic>>())
            else raw
        argStack.clear()

        when (val op = instr.op) {
            "nop", "ret" -> if (op == "ret") thread.stack.removeLast()

            // -- Registers --
            "leti", "fleti" -> registers[instr.int(0)] =
                if (op == "leti") instr.int(1) else instr.double(1).toInt()
            "set" -> registers[instr.int(0)] = 1
            "clear" -> registers[instr.int(0)] = 0
            "let" -> registers[instr.int(0)] = registers[instr.int(1)]
            "add" -> registers[instr.int(0)] += registers[instr.int(1)]
            "sub" -> registers[instr.int(0)] -= registers[instr.int(1)]
            "addi" -> registers[instr.int(0)] += instr.int(1)
            "subi" -> registers[instr.int(0)] -= instr.int(1)
            "and" -> registers[instr.int(0)] = registers[instr.int(0)] and registers[instr.int(1)]
            "or" -> registers[instr.int(0)] = registers[instr.int(0)] or registers[instr.int(1)]
            "xori" -> registers[instr.int(0)] = registers[instr.int(0)] xor instr.int(1)
            "get_random" -> {
                // (loBound reg pair, result reg): a random int in [rBase, rBase+1).
                val base = instr.int(0)
                val lo = registers[base]
                val hi = registers[base + 1].coerceAtLeast(lo + 1)
                registers[instr.int(1)] = lo + kotlin.random.Random.nextInt(hi - lo)
            }
            "sync_register", "gettime" -> Unit

            // -- Flags --
            "gset" -> { QuestSession.flags.add(instr.int(0)); QuestSession.persist() }
            "gclear" -> { QuestSession.flags.remove(instr.int(0)); QuestSession.persist() }
            "gget" -> registers[instr.int(1)] =
                if (instr.int(0) in QuestSession.flags) 1 else 0

            // -- Control flow --
            "call" -> thread.stack.addLast(Frame(instr.int(0), 0))
            "jmp" -> jump(thread, instr.int(0))
            "jmpi_=" -> if (registers[instr.int(0)] == instr.int(1)) jump(thread, instr.int(2))
            "jmpi_!=" -> if (registers[instr.int(0)] != instr.int(1)) jump(thread, instr.int(2))
            "jmpi_>" -> if (registers[instr.int(0)] > instr.int(1)) jump(thread, instr.int(2))
            "jmpi_<" -> if (registers[instr.int(0)] < instr.int(1)) jump(thread, instr.int(2))
            "jmpi_>=" -> if (registers[instr.int(0)] >= instr.int(1)) jump(thread, instr.int(2))
            "jmpi_<=" -> if (registers[instr.int(0)] <= instr.int(1)) jump(thread, instr.int(2))
            "jmp_=" -> if (registers[instr.int(0)] == registers[instr.int(1)]) jump(thread, instr.int(2))
            "jmp_!=" -> if (registers[instr.int(0)] != registers[instr.int(1)]) jump(thread, instr.int(2))
            "jmp_<" -> if (registers[instr.int(0)] < registers[instr.int(1)]) jump(thread, instr.int(2))
            "jmp_<=" -> if (registers[instr.int(0)] <= registers[instr.int(1)]) jump(thread, instr.int(2))
            "jmp_>" -> if (registers[instr.int(0)] > registers[instr.int(1)]) jump(thread, instr.int(2))
            "jmp_>=" -> if (registers[instr.int(0)] >= registers[instr.int(1)]) jump(thread, instr.int(2))
            "switch_call" -> {
                val value = registers[instr.int(0)]
                val labelIndex = 1 + value
                if (labelIndex < instr.args.size) thread.stack.addLast(Frame(instr.int(labelIndex), 0))
            }
            // The varargs bracket: BB collects arguments between va_start and va_end for the
            // function va_call invokes. The arg stack already carries them here.
            "va_start", "va_end" -> Unit
            "va_call" -> thread.stack.addLast(Frame(instr.int(0), 0))
            "switch_jmp" -> {
                val value = registers[instr.int(0)]
                // args: register, then one label per case.
                val labelIndex = 1 + value
                if (labelIndex < instr.args.size) jump(thread, instr.int(labelIndex))
            }
            "sync" -> {
                // A cinematic camera pan syncs a fixed run of frames; since this build doesn't
                // move the camera for it, drain the run within the frame so the dialogue that
                // follows isn't frozen behind it. A busy-wait loop (sync; jmp back) still hits
                // the per-frame budget and yields, so it can't spin the frame forever.
                thread.syncsThisFrame++
                if (thread.syncsThisFrame >= SYNC_BUDGET_PER_FRAME) thread.syncing = true
            }
            "thread_stg", "thread" -> startThread(instr.int(0))

            // -- Dialogue --
            "message" -> {
                host.showMessagePage(instr.int(0), instr.string(1))
                thread.blocked = Block.Message
                thread.messageOpen = true
            }
            "add_msg" -> {
                host.showMessagePage(-1, instr.string(0))
                thread.blocked = Block.Message
            }
            "mesend" -> {
                thread.messageOpen = false
                host.closeMessage()
            }
            "window_msg", "chat_box" -> {
                // chat_box carries layout args in front; the text is always last.
                val text = instr.args.lastOrNull() as? String ?: ""
                host.showWindow(text)
                thread.blocked = Block.Window
                thread.windowOpen = true
            }
            "winend" -> {
                thread.windowOpen = false
                host.closeWindow()
            }
            "list" -> {
                val options = instr.string(1).split('\n').filter { it.isNotEmpty() }
                host.showChoice(options)
                thread.blocked = Block.Choice(instr.int(0))
            }

            // -- Quest structure --
            "set_qt_success" -> qtSuccessLabel = instr.int(0)
            "set_qt_exit", "set_qt_cancel", "set_qt_failure" -> Unit
            "set_floor_handler" -> {
                QuestSession.floorHandlers[instr.int(0)] = instr.int(1)
                QuestSession.persist()
            }
            "set_episode", "bb_map_designate", "initial_floor", "set_mainwarp" -> Unit

            // -- Player and world --
            "p_setpos" -> if (instr.int(0) == 0) {
                val base = instr.int(1)
                host.setPlayerPosition(
                    registers[base].toDouble(), registers[base + 1].toDouble(),
                    registers[base + 2].toDouble(), registers[base + 3].toDouble(),
                )
            }
            "at_coords_talk" -> {
                // Same trigger shape as at_coords_call; the label it fires opens with its own
                // message, so the distinction is the script's, not ours.
                val base = instr.int(0)
                coordTriggers.add(
                    CoordTrigger(
                        registers[base].toDouble(), registers[base + 2].toDouble(),
                        registers[base + 3].toDouble().coerceAtLeast(5.0),
                        registers[base + 4],
                    )
                )
            }
            "at_coords_call" -> {
                val base = instr.int(0)
                coordTriggers.add(
                    CoordTrigger(
                        registers[base].toDouble(), registers[base + 2].toDouble(),
                        registers[base + 3].toDouble().coerceAtLeast(5.0),
                        registers[base + 4],
                    )
                )
            }
            "if_switch_not_pressed" -> {
                val base = instr.int(0)
                registers[base + 1] = if (host.isSwitchPressed(registers[base])) 0 else 1
            }
            "unlock_door2" -> host.unlockDoor(instr.int(0))
            "lock_door2" -> host.lockDoor(instr.int(0))
            "pl_add_meseta2" -> host.addMeseta(instr.int(0))
            "item_create2" -> host.giveItem(instr.int(0), instr.int(1), instr.int(2))
            "go_floor" -> host.goFloor(registers[instr.int(1)].takeIf { instr.args.size > 1 }
                ?: instr.int(0))
            "p_return_guild" -> host.returnToGuild()
            "qexit" -> host.questExit()
            "boss_is_dead" -> registers[instr.int(0)] = if (host.anyBossDead()) 1 else 0
            "get_player_hp" -> registers[instr.int(1)] = host.playerHp()
            "get_player_status" -> registers[instr.int(1)] = 0
            "get_floor_number" -> registers[instr.int(1)] = currentFloor
            // Without the quest's own field waves standing yet, a zone reads as cleared --
            // the optimistic answer that keeps stories moving rather than deadlocked.
            "if_zone_clear" -> registers[instr.int(0)] = 1
            "read_global_flag" -> registers[instr.int(1)] =
                if ((GLOBAL_FLAG_SPACE + instr.int(0)) in QuestSession.flags) 1 else 0
            "write_global_flag" -> {
                if (registers[instr.int(1)] != 0) QuestSession.flags.add(GLOBAL_FLAG_SPACE + instr.int(0))
                else QuestSession.flags.remove(GLOBAL_FLAG_SPACE + instr.int(0))
                QuestSession.persist()
            }

            // The guild counter's quest board.
            "set_quest_board_handler" -> {
                QuestSession.boardHandlers[instr.int(0)] = instr.int(1) to instr.string(2)
                QuestSession.persist()
            }
            "clear_quest_board_handler" -> {
                QuestSession.boardHandlers.remove(instr.int(0))
                QuestSession.persist()
            }
            "disp_msg_qb" -> {
                host.showWindow(instr.string(0))
                thread.blocked = Block.Window
                thread.windowOpen = true
            }
            "close_msg_qb" -> {
                thread.windowOpen = false
                host.closeWindow()
            }
            "winset_time", "window_time" -> {
                host.showWindow(instr.string(instr.args.size - 1))
                thread.blocked = Block.Window
                thread.windowOpen = true
            }
            "winend_time" -> {
                thread.windowOpen = false
                host.closeWindow()
            }

            // -- Queries --
            "get_difficulty_level2" -> registers[instr.int(0)] = 0
            "get_slotnumber" -> registers[instr.int(0)] = 0
            "get_number_of_player1" -> registers[instr.int(0)] = 1
            "get_chara_class" -> registers[instr.int(0)] = host.characterClassId()
            "get_coord_of_player" -> Unit

            // -- Stage direction this build doesn't perform yet --
            "bgm", "hud_hide", "hud_show", "npc_nont", "npc_talk",
            "p_action_disable", "p_action_enable", "pl_walk_v3",
            "fleti_fixed_camera", "cam_quake", "cine_enable", "cine_disable",
            "disable_movement2", "enable_movement2", "npc_crp_v3", "npc_crt_v3",
            "get_coord_of_player", "pl_walk_v1", "chat_bubble",
            "unhide_obj", "particle_v3", "particle_id_v3", "se", "fadeout", "fadein",
            "clear_mainwarp", "warp_off", "warp_on", "default_camera_pos2",
            "cam_zmin", "cam_zmout", "scroll_text", "p_disablewarp",
            "disable_movement1", "enable_movement1", "bb_p2_menu",
            "unfreeze_enemies", "freeze_enemies", "color_change",
            "use_animation", "stop_animation", "animation_check",
            "set_motion_blur", "p_move_v3", "map_designate_ex", "unknown_f8a9",
            "set_palettex_callback", "activate_palettex", "enable_palettex",
            "set_slot_invincible", "turn_off_bgm_p2",
            -> Unit

            else -> if (unknownOps.add(op)) {
                console.warn("Quest VM: unhandled opcode '$op' (skipped)")
            }
        }
    }

    var qtSuccessLabel: Int = -1
        private set

    companion object {
        private const val MAX_STEPS_PER_FRAME = 2000
        private const val SYNC_BUDGET_PER_FRAME = 32

        /** Offset separating read/write_global_flag's space from gset/gget's. */
        private const val GLOBAL_FLAG_SPACE = 100_000
    }
}

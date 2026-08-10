package world.phantasmal.web.mobileGame.world

import world.phantasmal.web.mobileGame.player.PlayerAnimations

/**
 * One town NPC's authored placement in Pioneer 2.
 *
 * [x]/[y]/[z] and [rotationYDegrees] are in **world** space, not section-local space. Quest data
 * stores both: a section-local "Position" plus the section's own transform, and the editor shows
 * the resolved "World Position" alongside it. Only the world values are used here, because the
 * game renders the whole stage as one mesh in world space with no section transforms applied.
 */
/** What talking to an NPC opens -- see GameRenderer.openNpcDialog. */
enum class NpcRole {
    CHAT,
    /** Green counter: consumable tools (the green item box's contents). */
    TOOL_SHOP,
    /** Orange counter: weapons (the orange item box's contents). */
    WEAPON_SHOP,
    /** Blue counter: frames, barriers and units (the blue item box's contents). */
    ARMOR_SHOP,
    BANK,
    /** Appraises ???? rares for a fee -- the hooded figure by the shops. */
    TEKKER,
    /** The Hunter's Guild counter: the government jobs board. */
    GUILD,
}

class Pioneer2Npc(
    /** The quest format's NpcType name (see psolib's NpcType.uniqueName), kept for traceability. */
    val name: String,
    /** Slug for [NpcAssetLoader.loadNpc]. */
    val modelSlug: String,
    val x: Double,
    val y: Double,
    val z: Double,
    val rotationYDegrees: Double,
    /**
     * Index into the shared `animation_NNN.njm` set (see PlayerAnimations.Idles). NPCs run off the
     * player's own clips -- psov2 drives them from the same plymotiondata -- so anything in that
     * catalogue works here.
     */
    val idleAnimation: Int,
    /** The name the talk window shows -- the quest type names read like debug output. */
    val displayName: String = "Citizen",
    val role: NpcRole = NpcRole.CHAT,
    /** The NPC's line when spoken to. Original text in the game's register, not ripped script. */
    val dialog: String = "...",
)

/**
 * Pioneer 2's town NPCs, transcribed from quest-editor readouts of each NPC's type, position and
 * rotation. Replaces the earlier placeholder arrangement that simply spread every available NPC
 * model evenly around a circle centred on the player spawn.
 *
 * **World rotation vs section rotation.** Section 30's transform is a 180-degree Y rotation plus a
 * translation -- derived from its own data, where `x_world = -165 - x_local` and
 * `z_world = 202 - z_local` (both axes negated). Sections 10, 20 and 40 are pure translations with
 * no rotation. So every section-30 NPC's authored rotation has 180 degrees added here to express it
 * in world space; the others are unchanged.
 *
 * **Model mapping.** Quest data names NPCs by type ("Male Fat", "Tekker", ...) while psov2's models
 * are named after their source files with terse abbreviations, so the two were matched by
 * inspecting the actual meshes via the `?viewNpc=` debug route rather than by guessing:
 *  - The citizen models follow `bm_n_e{type}{m|w}_i_body`, and measuring each mesh's bounding box
 *    confirms the letter is a build: `f` is the widest (Male 9.9 wide), `m` tall and broad, `o` the
 *    shortest and thinnest, `t` the tallest, `fs` short and stocky (matched to "Dwarf").
 *  - `gun*` ("GuildStaff") are not guild clerks -- both render as armoured figures carrying rifles,
 *    one blue and one red, which is exactly Blue/Red Soldier.
 *  - `kantei*` ("GovStaff") means appraisal, and the meshes are hooded robed figures -- the Tekker.
 *  - `hakase` (professor) is a white lab coat, and `hisyo` (secretary) is the closest thing to a
 *    Guild counter clerk in the converted set.
 *
 * Y is authored as 0 or 2.6, but is treated as a hint only: each NPC is dropped onto the walkable
 * surface at its own (x, z) at spawn time, since Pioneer 2's stage geometry has several stacked
 * walkable levels (see findNearestStableGroundHeight).
 */
val PIONEER2_NPCS: List<Pioneer2Npc> = listOf(
    // -- Section 30 (rotations include the section's 180-degree offset) --
    // The mall's own vendors, at their authored spots, each running the counter of their
    // item-box colour -- blue sells what the blue box holds, green the green, orange the
    // orange -- with the Tekker at the far right of the big room for the ???? mysteries.
    Pioneer2Npc("Male Fat", "CitizenMan3", -240.884, 2.6, 244.693, 120.932,
        PlayerAnimations.Idles.BREATHING,
        displayName = "Armor Shop", role = NpcRole.ARMOR_SHOP,
        dialog = "Frames, barriers, units -- everything the blue box holds. Keep yourself in one piece."),
    Pioneer2Npc("Female Macho", "CitizenWoman5", -208.871, 2.6, 276.803, 147.651,
        PlayerAnimations.Idles.HAND_ON_HIP,
        displayName = "Tool Shop", role = NpcRole.TOOL_SHOP,
        dialog = "Mates, fluids, Telepipes -- everything the green box holds, stocked and priced fair."),
    Pioneer2Npc("Male Macho", "CitizenMan4", -165.34, 2.6, 286.72, 180.0,
        PlayerAnimations.Idles.STANDING,
        displayName = "Weapon Shop", role = NpcRole.WEAPON_SHOP,
        dialog = "Sabers, guns, canes -- anything that comes in an orange box. Browse away."),
    Pioneer2Npc("Tekker", "GovStaff3", -121.29, 2.6, 254.782, 225.0,
        PlayerAnimations.Idles.BREATHING,
        displayName = "Tekker", role = NpcRole.TEKKER,
        dialog = "Weapons, friend. Buying or selling, nobody knows their worth better than I do."),
    Pioneer2Npc("Female Tall", "CitizenWoman7", -181.004, 0.0, 196.004, 112.495,
        PlayerAnimations.Idles.HAND_ON_HIP,
        displayName = "Citizen", role = NpcRole.CHAT,
        dialog = "The view of Ragol from the deck is beautiful. Hard to believe what's waiting below."),

    // -- Section 20 (no section rotation) --
    Pioneer2Npc("Guild Lady", "Hisyo", -262.51, 0.0, -24.54, 112.5,
        PlayerAnimations.Idles.HAND_ON_HIP,
        displayName = "Checkroom Clerk", role = NpcRole.BANK,
        dialog = "Welcome to the checkroom. I'll keep your items and Meseta safe -- no charge for Hunters."),
    Pioneer2Npc("Male Old", "CitizenMan5", -220.086, 0.0, -100.001, 0.0,
        PlayerAnimations.Idles.STANDING,
        displayName = "Citizen", role = NpcRole.CHAT,
        dialog = "I came aboard with the second wave. Never thought I'd grow old in space."),
    Pioneer2Npc("Scientist", "Hakase", -147.0, 0.0, -7.997, 147.656,
        PlayerAnimations.Idles.BREATHING,
        displayName = "Scientist", role = NpcRole.CHAT,
        dialog = "The lab is still analyzing samples from the surface. The readings make no sense at all."),
    Pioneer2Npc("Female Macho", "CitizenWoman5", -2.002, 0.0, 35.004, 180.0,
        PlayerAnimations.Idles.BREATHING,
        displayName = "Citizen", role = NpcRole.CHAT,
        dialog = "My husband went down to the Forest with a Hunter team this morning. Bring him home safe."),
    Pioneer2Npc("Male Dwarf", "CitizenMan2", 156.003, 0.0, -50.0, 315.0,
        PlayerAnimations.Idles.ARM_RAISED,
        displayName = "Citizen", role = NpcRole.CHAT,
        dialog = "Psst. I hear the shops stock better gear once a Hunter's proven themselves."),
    Pioneer2Npc("Red Soldier", "GuildStaff2", 237.999, 0.0, -14.0, 315.0,
        PlayerAnimations.Idles.RIFLE,
        displayName = "Soldier", role = NpcRole.CHAT,
        dialog = "Military's sealed the central dome. No civilians. No exceptions."),
    Pioneer2Npc("Blue Soldier", "GuildStaff1", 238.004, 0.0, 63.004, 225.0,
        PlayerAnimations.Idles.RIFLE_SHOULDERED,
        displayName = "Soldier", role = NpcRole.CHAT,
        dialog = "Stay sharp down there. The wildlife isn't what the survey teams described."),
    Pioneer2Npc("Female Fat", "CitizenWoman4", 167.998, 0.0, 83.997, 225.0,
        PlayerAnimations.Idles.STANDING,
        displayName = "Citizen", role = NpcRole.CHAT,
        dialog = "Everything is so expensive since the accident. Even Monomates!"),

    // -- Section 40 --
    Pioneer2Npc("Nurse", "Nurse", 265.999, 0.0, -213.997, 315.0,
        PlayerAnimations.Idles.BREATHING,
        displayName = "Nurse", role = NpcRole.TOOL_SHOP,
        dialog = "Welcome to the medical supply counter. Mates, fluids, cures -- everything a Hunter needs."),

    // -- Section 10 --
    Pioneer2Npc("Guild Lady", "Hisyo", 178.999, 0.0, 341.996, 135.0,
        PlayerAnimations.Idles.HAND_ON_HIP,
        displayName = "Guild Receptionist", role = NpcRole.GUILD,
        dialog = "The Hunter's Guild takes quests at this counter... once the quest board reopens."),
)

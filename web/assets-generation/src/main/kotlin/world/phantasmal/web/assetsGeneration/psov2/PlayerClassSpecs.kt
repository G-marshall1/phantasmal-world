package world.phantasmal.web.assetsGeneration.psov2

/**
 * One playable class's psov2 source data, needed to reproduce a `{slug}Body.nj` /
 * `{slug}Head{n}.nj` / `{slug}Hair{n}.nj` / `{slug}Accessory{n}.nj` / `{slug}Tex.afs` set matching
 * exactly what CharacterClassAssetLoader (in :web:rendering, shared with the main viewer) expects.
 *
 * [headCount]/[hairCount]/[accessoryHairIndices] were derived by directly parsing each class's
 * `pl{letter}nj.bml` entry table -- these archives ship far more mesh variants (5-10 each) than
 * the mobile game originally converted (index 0 only). [accessoryHairIndices] is the set of hair
 * indices that have a matching accessory mesh available (`pl{letter}cap{n}.nj`) -- for most
 * accessory-bearing classes this is every hair index (accessories are a full parallel set), but
 * for HUmar it's just `{6}`, matching the real game's own CharacterClass.HUmar.hairStylesWithAccessory
 * exactly (psov2 happens to ship exactly the one accessory mesh the real game pairs with hair 6,
 * nothing else -- not a random leftover file).
 *
 * [slotMap] maps a target `{slug}Tex.afs` slot (the hardcoded real-client texture indices from
 * CharacterClassAssetLoader.textureIds()) to the psov2 `pl{letter}tex.afs` slot that fills the
 * same structural role. Derived by reading psov2's own AssetPlayer.js per-class loader functions
 * (bdyTex/hedTex/haiTex/capTex arrays) and matching array lengths/positions 1:1 against
 * CharacterClassAssetLoader's per-class TextureIds -- the ordering (section, then body, then
 * head, then hair, then accessories) matches exactly between the two, since both ultimately
 * encode the same underlying Ninja mesh texture-slot convention. Textures don't vary per mesh
 * variant, so this slotMap is unaffected by the head/hair/accessory counts above.
 *
 * Confidence is high for classes without an accessory mesh (their psov2 arrays line up exactly
 * with the target counts, no guesswork). For the four accessory classes below, psov2's own arrays
 * didn't always cleanly add up (e.g. Fomarl's body array was one short of what the target needs),
 * so those slotMaps involve a documented best-effort call and should be treated as needing visual
 * verification, same as any first-pass port.
 */
class PlayerClassSpec(
    val slug: String,
    val letter: Char,
    val headCount: Int = 1,
    val hairCount: Int = 0,
    val accessoryHairIndices: Set<Int> = emptySet(),
    val slotMap: Map<Int, Int>,
)

/**
 * psov2 doesn't appear to ship data for HUcaseal, RAmarl or FOmar at all -- AssetPlayer.js only
 * defines 9 of the 12 playable classes, and the 5 leftover unused player archive letters (j-n)
 * all contain a body+head pair with no hair file, so at most one of them (if any) could plausibly
 * be HUcaseal; there's no hair-bearing candidate left for RAmarl or FOmar. Left out until/unless
 * that's confirmed otherwise.
 */
val PLAYER_CLASS_SPECS: List<PlayerClassSpec> = listOf(
    PlayerClassSpec(
        slug = "HUmar",
        letter = 'A',
        hairCount = 7,
        accessoryHairIndices = setOf(6),
        slotMap = mapOf(
            126 to 86, // Section id variant (Viridia).
            0 to 0,
            1 to 1,
            2 to 2,
            108 to 77, // Body variant.
            54 to 27,
            55 to 28,
            94 to 67,
            95 to 68,
        ),
    ),
    PlayerClassSpec(
        slug = "HUnewearl",
        letter = 'B',
        hairCount = 10,
        slotMap = mapOf(
            299 to 176,
            13 to 76,
            0 to 77,
            1 to 75,
            2 to 78,
            277 to 163,
            281 to 0,
            235 to 121,
            239 to 125,
            260 to 142,
            259 to 143,
        ),
    ),
    PlayerClassSpec(
        slug = "HUcast",
        letter = 'C',
        headCount = 5,
        slotMap = mapOf(
            275 to 101,
            0 to 0,
            1 to 1,
            2 to 2,
            250 to 90,
            3 to 3,
            4 to 4,
        ),
    ),
    PlayerClassSpec(
        slug = "RAmar",
        letter = 'D',
        hairCount = 7,
        accessoryHairIndices = (0..6).toSet(),
        slotMap = mapOf(
            197 to 123,
            4 to 4,
            5 to 5,
            6 to 6,
            179 to 111,
            126 to 63,
            127 to 64,
            166 to 103,
            167 to 104,
            2 to 105, // Accessory (haiTex's 3rd entry).
        ),
    ),
    PlayerClassSpec(
        slug = "RAcast",
        letter = 'E',
        headCount = 5,
        slotMap = mapOf(
            300 to 113,
            0 to 1,
            1 to 2,
            2 to 3,
            3 to 99,
            275 to 0,
            4 to 4,
        ),
    ),
    PlayerClassSpec(
        slug = "RAcaseal",
        letter = 'F',
        headCount = 5,
        slotMap = mapOf(
            375 to 141,
            350 to 0,
            0 to 1,
            1 to 2,
            2 to 126,
            3 to 3,
        ),
    ),
    // Best-effort: psov2's bdyTex for Fomarl is one entry short of what the target body group
    // needs, so the section slot and body slot 0 are both filled from the same source texture
    // (185) as a guess.
    PlayerClassSpec(
        slug = "FOmarl",
        letter = 'G',
        hairCount = 10,
        accessoryHairIndices = (0..9).toSet(),
        slotMap = mapOf(
            326 to 185,
            0 to 185,
            1 to 2,
            2 to 1,
            322 to 176,
            288 to 144,
            308 to 164,
            3 to 3,
            4 to 4,
        ),
    ),
    // Best-effort: accessory slot 330 doubles as the class's one real hair slot in the target
    // mapping; capTex's 3rd entry is used for it since accessories all come from capTex for the
    // other accessory classes. haiTex's own entry (160) ends up unused.
    PlayerClassSpec(
        slug = "FOnewm",
        letter = 'H',
        hairCount = 7,
        accessoryHairIndices = (0..6).toSet(),
        slotMap = mapOf(
            344 to 178,
            4 to 4,
            340 to 5,
            0 to 166,
            5 to 0,
            306 to 135,
            310 to 139,
            6 to 159,
            16 to 7,
            330 to 6,
        ),
    ),
    PlayerClassSpec(
        slug = "FOnewearl",
        letter = 'I',
        hairCount = 10,
        accessoryHairIndices = (0..9).toSet(),
        slotMap = mapOf(
            505 to 279,
            1 to 1,
            0 to 2,
            2 to 267,
            501 to 0,
            472 to 238,
            468 to 234,
            492 to 160,
            12 to 12,
            13 to 13, // capTex's 3rd entry (its 2nd, index 1, is a genuine null in the source).
        ),
    ),
)

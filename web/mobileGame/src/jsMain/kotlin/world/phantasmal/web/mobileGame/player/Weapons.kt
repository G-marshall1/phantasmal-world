package world.phantasmal.web.mobileGame.player

import world.phantasmal.web.mobileGame.player.PlayerAnimations.WeaponAnimations

/**
 * A weapon class, which is what decides how the character stands, walks and swings -- PSO animates
 * per class, not per item, so all 30-odd sabers share one motion set.
 *
 * [atp] is the attack power the class contributes. Only the Saber's is grounded in supplied data
 * (see [SABER_ATP]); the rest are scaled off it by how PSO ranks the classes -- heavier and
 * slower-swinging means higher, and the three-hit combo lengths in the animation list are the
 * ordering used. They're a first pass, not measured values, and are all in this one table.
 */
enum class WeaponType(
    val animations: WeaponAnimations,
    val atp: Int,
    /**
     * How far forward the strike zone reaches from the wielder's centre, in PSO units. A hit lands
     * when this reaches the *edge* of the target's cylinder, so the distance between centres it
     * can cover is this plus the target's own hitbox radius.
     */
    val reach: Double,
    /**
     * Half-angle of the strike cone to each side of dead-ahead, in degrees -- the wiki's
     * "horizontal angle", which with [reach] is how the real game states every weapon's area:
     * a cone, not a corridor. A sword's 45 sweeps almost everything in front of the wielder;
     * a gun's 10 must be aimed.
     */
    val angleDegrees: Double,
    /** How many enemies one swing can connect with. */
    val maxTargets: Int,
    /**
     * How many times one attack animation lands on each enemy it reaches. The item
     * descriptions are the source: a Dagger is "a short Photon sword. Attacks enemies twice"
     * -- one strike per blade of the pair, each rolling its own accuracy and damage.
     */
    val hitsPerAttack: Int = 1,
) {
    // Reach is the wiki's horizontal distance divided by ten -- the scale check is that a
    // mechgun's published 85 lands exactly on the 8.5 PSO units this table already used.
    SABER(PlayerAnimations.SABER, SABER_ATP, reach = 1.4, angleDegrees = 26.0, maxTargets = 1),
    SWORD(PlayerAnimations.SWORD, 28, reach = 2.5, angleDegrees = 45.0, maxTargets = 10),
    DAGGER(
        PlayerAnimations.DAGGER, 12, reach = 1.4, angleDegrees = 30.0, maxTargets = 1,
        hitsPerAttack = 2,
    ),
    PARTISAN(PlayerAnimations.PARTISAN, 22, reach = 3.0, angleDegrees = 40.0, maxTargets = 10),
    SLICER(PlayerAnimations.SLICER, 18, reach = 9.5, angleDegrees = 26.0, maxTargets = 4),
    DOUBLE_SABER(PlayerAnimations.DOUBLE_SABER, 20, reach = 1.7, angleDegrees = 75.0, maxTargets = 1),
    KATANA(PlayerAnimations.KATANA, 24, reach = 1.4, angleDegrees = 26.0, maxTargets = 1),
    TWIN_SWORD(PlayerAnimations.TWIN_SWORD, 26, reach = 1.8, angleDegrees = 26.0, maxTargets = 1),
    CLAW(PlayerAnimations.CLAW, 14, reach = 1.4, angleDegrees = 26.0, maxTargets = 1),
    HANDGUN(PlayerAnimations.HANDGUN, 10, reach = 17.0, angleDegrees = 10.0, maxTargets = 1),
    // A rifle's defining trait is the sightline: the real game's 400 range, twice a handgun's.
    RIFLE(PlayerAnimations.RIFLE, 20, reach = 40.0, angleDegrees = 10.0, maxTargets = 1),
    MECHGUN(PlayerAnimations.MECHGUN, 8, reach = 8.5, angleDegrees = 10.0, maxTargets = 1),
    SHOT(PlayerAnimations.SHOT, 16, reach = 15.0, angleDegrees = 45.0, maxTargets = 5),
    LAUNCHER(PlayerAnimations.LAUNCHER, 30, reach = 24.0, angleDegrees = 10.0, maxTargets = 4),
    ROD(PlayerAnimations.ROD, 10, reach = 1.7, angleDegrees = 27.0, maxTargets = 1),
    WAND(PlayerAnimations.WAND, 8, reach = 1.3, angleDegrees = 26.0, maxTargets = 1),
    CANE(PlayerAnimations.CANE, 9, reach = 1.4, angleDegrees = 26.0, maxTargets = 1),
    CARD(PlayerAnimations.CARD, 12, reach = 15.0, angleDegrees = 26.0, maxTargets = 1),

    /** Nothing in hand. Not equippable -- what the character falls back to bare-handed. */
    FIST(PlayerAnimations.FIST, 0, reach = 1.4, angleDegrees = 30.0, maxTargets = 1),
}

/**
 * Which class each shipped weapon model belongs to.
 *
 * Three groups have certain classifications, because the name states the class outright: the 14
 * base weapons, the S-Rank set and the Debug set, plus the "Red" variants. The named rares are
 * classified from what they are in PSO; those are the ones worth spot-checking, since a wrong
 * entry shows up as the model held at an odd angle with the wrong swing, not as an error.
 *
 * Mags, shields and units from the same archive are deliberately absent -- they aren't held in the
 * hand and have no motion set. Anything missing falls back through [weaponType].
 */
val WEAPON_TYPES: Map<String, WeaponType> = buildMap {
    fun put(type: WeaponType, vararg slugs: String) = slugs.forEach { put(it, type) }

    put(
        WeaponType.SABER,
        "Saber", "RedSaber", "AncientSaber", "SRankSaber", "DebugSaber",
        "Elysion", "LavisBlade", "Caduceus", "StingTip", "Delsaber",
    )
    put(
        WeaponType.SWORD,
        "Sword", "RedSword", "SRankSword", "SRankBlade", "DebugSword",
        "TsumikiriJSword", "SealedJSword", "ChainSawd", "LavisCannon", "FlameVisit",
        "HarisenBattleFan", "HugeBattleFan", "PlantainFan", "PlantainHugeFan", "PlantainLeaf",
        "SRankHarisen", "EvilCurst",
    )
    put(
        WeaponType.DAGGER,
        "Dagger", "RedDagger", "SRankDagger", "DebugDagger",
        "MaddamPink", "MaddamPurple", "GameMagazine",
    )
    put(
        WeaponType.PARTISAN,
        "Partisan", "RedPartisan", "SRankPartisan", "SRankScythe", "SRankHammer", "DebugPartisan",
        "SoulEater", "VictorAxe", "Berdysh", "ImperialPick", "DemonicFork", "ChameleonScythe",
        "HeartOfPoumn", "MeteorCudgel", "MonkeyKingBar", "ToyHammer", "Broom",
    )
    put(
        WeaponType.SLICER,
        "Slicer", "RedSlicer", "SRankSlicer", "SRankJCutter", "SRankWindmill", "DebugSlicer",
        "FlightCutter", "FlightFan", "TwinChakram", "Windmill", "CrazyTune", "SummitMoon",
        "SRankMoon",
    )
    put(
        WeaponType.DOUBLE_SABER,
        "DoubleSaber", "DebugDoubleSaber", "TwinBlaze", "Orotiagito",
    )
    put(WeaponType.KATANA, "SRankKatana", "Yamigarasu")
    put(WeaponType.TWIN_SWORD, "SRankTwin", "TwinkleStar")
    put(
        WeaponType.CLAW,
        "Claw", "SRankClaw", "SRankPunch", "DebugClaw",
        "SonicKnuckle", "DragonClaw", "PantherClaw", "RocketPunch", "SBeat", "PArm",
    )
    put(
        WeaponType.HANDGUN,
        "Handgun", "RedHandgun", "SRankGun", "DebugHandgun",
        "HandgunGuld", "HandgunMilla", "EggBlaster", "SuppressedGun", "Yasminkov2000h",
    )
    put(
        WeaponType.RIFLE,
        "Rifle", "SRankRifle", "SRankPsychogun", "DebugRifle",
        "BringerRifle", "AntiAndroidRifle", "AnoRifle", "HolyRay", "HeavensPunisher",
        "Yasminkov3000r", "Yasminkov7000v",
    )
    put(
        WeaponType.MECHGUN,
        "Mechgun", "RedMechguns", "SRankMechgun", "DebugMechgun",
        "GuldMilla", "TwinPsychogun", "Yasminkov9000m",
    )
    put(
        WeaponType.SHOT,
        "Shot", "SRankShot", "SRankNeedle", "DebugShot", "SpreadNeedle", "FrozenShooter",
    )
    put(
        WeaponType.LAUNCHER,
        "SRankBazooka", "InfernoBazooka", "DrillLauncher", "BaranzLauncher", "PanzerFaust",
        "BelraCannon", "DoubleCannon", "MaserBeam",
    )
    put(
        WeaponType.ROD,
        "Rod", "SRankRod", "DebugRod",
        "TechnicalCrozier", "MagicalPiece", "ProphetsOfMotav", "TheSighOfAGod",
    )
    put(
        WeaponType.WAND,
        "Wand", "SRankWand", "DebugWand",
        "PsychoWand", "RabbitWand", "StrikerOfChao", "BranchOfPakuPaku", "SambaMaracas",
    )
    put(
        WeaponType.CANE,
        "Cane", "SRankCane", "DebugCane",
        "Sorcerer", "FlowerCane", "Hildebear", "Hildeblue", "Marina", "FlowerBouquet",
        "Akiko", "AkikoWok", "WokOfAkiko",
    )
}

/**
 * The class this weapon is held as. Unclassified models fall back to the saber's motion set, which
 * is the most generic one-handed melee stance and the safest thing to hold an unknown model in.
 */
fun weaponType(slug: String): WeaponType = WEAPON_TYPES[slug] ?: WeaponType.SABER

/** The firearms: their reach is a sightline rather than a swing. */
private val RANGED_TYPES: Set<WeaponType> = setOf(
    WeaponType.HANDGUN, WeaponType.RIFLE, WeaponType.MECHGUN,
    WeaponType.SHOT, WeaponType.LAUNCHER,
)

val WeaponType.isRanged: Boolean get() = this in RANGED_TYPES

/**
 * Calibration for melee reach.
 *
 * The wiki's distances order the weapons correctly against each other -- a partisan out-reaches
 * a saber, a saber a dagger -- but their absolute scale doesn't match these models' proportions.
 * Taken literally, a saber's 1.4 puts the blade's live zone only about a third of a metre past
 * the character's own cylinder, when an arm plus a blade is nearer a full metre: a swing that
 * visibly passed through an enemy landed nothing until the two were almost overlapping, and the
 * enemy's own 1.4 meant it could stand and hit you from a distance you couldn't answer.
 *
 * Scaling every melee reach by the same factor keeps the published ordering intact while making
 * the strike zone match what the animation shows. Firearms are left alone -- at 17 to 21 units
 * their sightlines were never in question.
 */
const val MELEE_REACH_SCALE = 2.5

/** What the hit test should actually use -- see [MELEE_REACH_SCALE]. */
val WeaponType.effectiveReach: Double
    get() = if (isRanged) reach else reach * MELEE_REACH_SCALE

/**
 * The weapon a fresh character draws, by profession -- PSO's own starters: Hunters a Saber,
 * Rangers a Handgun, Forces a Cane. Conveniently these are model slugs AND shop tier names.
 */
fun starterWeaponSlug(characterClass: world.phantasmal.web.viewer.models.CharacterClass): String =
    when (professionOf(characterClass)) {
        Profession.HUNTER -> "Saber"
        Profession.RANGER -> "Handgun"
        Profession.FORCE -> "Cane"
    }

/** Every equippable weapon, in catalogue order -- what a weapon-switching UI would list. */
val EQUIPPABLE_WEAPONS: List<String> = WEAPON_TYPES.keys.sorted()

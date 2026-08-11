package world.phantasmal.web.mobileGame.player

import world.phantasmal.web.viewer.models.CharacterClass

/**
 * Who can use what: the per-class rules for weapons, techniques and traps.
 *
 * Weapons come straight from the BB client's own data: every ItemPMT weapon record carries a
 * usability mask, and a class can equip a weapon when its profession bit AND its race bit AND
 * its sex bit are all set (verified against the wiki's per-item class grids -- a Saber's 0xFF
 * is everyone, a Rifle's 0b11111010 is the four Rangers, Panzer Faust's 0b11010010 lands on
 * exactly RAcast/RAcaseal).
 *
 * Technique ceilings and trap growth are the wiki's own tables (wiki.pioneer2.net/w/Techniques
 * "Technique levels", wiki.pioneer2.net/w/Traps "Trap growth").
 */

private const val USE_HUNTER = 1 shl 0
private const val USE_RANGER = 1 shl 1
private const val USE_FORCE = 1 shl 2
private const val USE_HUMAN = 1 shl 3
private const val USE_ANDROID = 1 shl 4
private const val USE_NEWMAN = 1 shl 5
private const val USE_MALE = 1 shl 6
private const val USE_FEMALE = 1 shl 7

/** The newmans: HUnewearl, FOnewm, FOnewearl. */
fun isNewman(characterClass: CharacterClass): Boolean =
    characterClass.name.contains("new", ignoreCase = true)

/** Whether this weapon's ItemPMT mask lets [characterClass] equip it. */
fun WeaponTier.usableBy(characterClass: CharacterClass): Boolean {
    val profession = when (professionOf(characterClass)) {
        Profession.HUNTER -> USE_HUNTER
        Profession.RANGER -> USE_RANGER
        Profession.FORCE -> USE_FORCE
    }
    val race = when {
        isAndroid(characterClass) -> USE_ANDROID
        isNewman(characterClass) -> USE_NEWMAN
        else -> USE_HUMAN
    }
    val sex = if (isFemaleCharacter(characterClass)) USE_FEMALE else USE_MALE
    return usability and profession != 0 && usability and race != 0 && usability and sex != 0
}

/**
 * The highest level [characterClass] can ever learn [technique] to -- 0 for "never". The
 * wiki's Technique levels table, row by row: attack techniques run 15/20/15/20/30 across
 * HUmar/HUnewearl/RAmar/RAmarl/Forces; Grants, Megid and Reverser are Force-only; HUmar has
 * no Shifta/Deband, RAmar no Jellen/Zalure; Anti caps at 5 or 7; Ryuker is for everyone who
 * casts at all. Androids are 0 across the board.
 */
fun maxTechniqueLevel(technique: Technique, characterClass: CharacterClass): Int {
    if (isAndroid(characterClass)) return 0
    if (professionOf(characterClass) == Profession.FORCE) {
        return if (technique == Technique.ANTI) 7 else 30
    }
    // The four casting non-Forces. "Deep" = the female pair's higher ceilings.
    val deep = characterClass == CharacterClass.HUnewearl || characterClass == CharacterClass.RAmarl
    val hunter = professionOf(characterClass) == Profession.HUNTER
    return when (technique) {
        Technique.FOIE, Technique.BARTA, Technique.ZONDE,
        Technique.GIFOIE, Technique.GIBARTA, Technique.GIZONDE,
        Technique.RAFOIE, Technique.RABARTA, Technique.RAZONDE,
        Technique.RESTA,
        -> if (deep) 20 else 15

        Technique.GRANTS, Technique.MEGID, Technique.REVERSER -> 0

        Technique.ANTI -> if (deep) 7 else 5

        Technique.SHIFTA, Technique.DEBAND ->
            if (hunter) (if (deep) 20 else 0) else (if (deep) 20 else 15)

        Technique.JELLEN, Technique.ZALURE ->
            if (hunter) (if (deep) 20 else 15) else (if (deep) 20 else 0)

        Technique.RYUKER -> 30
    }
}

/**
 * The `Boosts` term of the wiki's technique damage formula: each Force class amplifies its own
 * signature techniques (wiki.pioneer2.net/w/Techniques, "Class-based boosts"). This is what
 * makes the four Forces play differently rather than being one caster with different stats --
 * FOnewearl is the simple-technique specialist, FOnewm the multi-target one, FOmar the Gi/Grants
 * hybrid, FOmarl the Grants and support caster.
 *
 * Only the damage boosts are modelled. The published range boosts (+100% on Resta, Anti, Shifta
 * and Deband) do nothing yet in a game with no allies to reach, and FOnewearl's piercing Megid
 * needs a projectile that survives its first target.
 */
fun techniqueBoost(technique: Technique, characterClass: CharacterClass): Double = when (characterClass) {
    CharacterClass.FOmar -> when (technique) {
        Technique.GIFOIE, Technique.GIBARTA, Technique.GIZONDE, Technique.GRANTS -> 0.30
        else -> 0.0
    }
    CharacterClass.FOmarl -> when (technique) {
        Technique.GRANTS -> 0.50
        else -> 0.0
    }
    CharacterClass.FOnewm -> when (technique) {
        Technique.GIFOIE, Technique.GIBARTA, Technique.GIZONDE,
        Technique.RAFOIE, Technique.RABARTA, Technique.RAZONDE,
        -> 0.30
        else -> 0.0
    }
    CharacterClass.FOnewearl -> when (technique) {
        Technique.FOIE, Technique.BARTA, Technique.ZONDE -> 0.30
        else -> 0.0
    }
    else -> 0.0
}

/** The three traps every android class deploys. */
enum class PlayerTrapKind(val uiName: String) {
    DAMAGE("Damage Trap"),
    FREEZE("Freeze Trap"),
    CONFUSE("Confuse Trap"),
}

/**
 * How many of [kind] an android of [characterClass] can carry at [level]: the wiki's growth
 * formula, count = 2 + level/x capped at 20, where x is the class's own stride for that trap
 * (HUcast grows freeze/confuse fastest but damage slowest; RAcast/RAcaseal mirror each other
 * on freeze vs confuse). Zero for anyone who isn't an android.
 */
fun trapCapacity(characterClass: CharacterClass, kind: PlayerTrapKind, level: Int): Int {
    val stride = when (characterClass) {
        CharacterClass.HUcast -> when (kind) {
            PlayerTrapKind.DAMAGE -> 11
            PlayerTrapKind.FREEZE -> 7
            PlayerTrapKind.CONFUSE -> 7
        }
        CharacterClass.HUcaseal -> when (kind) {
            PlayerTrapKind.DAMAGE -> 9
            PlayerTrapKind.FREEZE -> 10
            PlayerTrapKind.CONFUSE -> 10
        }
        CharacterClass.RAcast -> when (kind) {
            PlayerTrapKind.DAMAGE -> 7
            PlayerTrapKind.FREEZE -> 9
            PlayerTrapKind.CONFUSE -> 11
        }
        CharacterClass.RAcaseal -> when (kind) {
            PlayerTrapKind.DAMAGE -> 7
            PlayerTrapKind.FREEZE -> 11
            PlayerTrapKind.CONFUSE -> 9
        }
        else -> return 0
    }
    return (2 + level / stride).coerceAtMost(20)
}

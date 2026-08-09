package world.phantasmal.web.mobileGame.player

import world.phantasmal.web.core.rendering.conversion.PSO_FRAME_RATE_DOUBLE

/**
 * How long one swing lasts, in frames, at one step of a combo.
 *
 * Every attack has two lengths. [combo] is how long it takes when another attack follows it, and
 * [full] is how long it takes when it's the last of the sequence -- the animation plays out
 * instead of being cut into the next swing. That difference is what makes a combo feel tight
 * rather than a queue of separate attacks, and it's large: a handgun's first shot is 14 frames
 * mid-combo against 27 on its own.
 *
 * [contact] is the frame the blow actually connects -- the frame data's "attack comes out on".
 * It's dramatically earlier than the swing's end, and the pattern is nothing like proportional:
 * a saber's finisher connects on frame 8 of 31, spending three quarters of the clip in
 * follow-through. Approximating it (as this used to, at 80% of the cancel point) put the third
 * hit of every combo almost a second late.
 *
 * The third step always ends the combo, so its [combo] equals its [full].
 */
class AttackFrames(val full: Int, val combo: Int = full, val contact: Int) {
    /** Seconds, at PSO's 30 frames per second. */
    fun seconds(chaining: Boolean): Double =
        (if (chaining) combo else full) / PSO_FRAME_RATE_DOUBLE

    /** Seconds until the blow connects, at PSO's 30 frames per second. */
    fun contactSeconds(): Double = contact / PSO_FRAME_RATE_DOUBLE

    /**
     * How long this attack spends winding up before the swing itself, measured against the
     * normal attack at the same combo step.
     *
     * A heavy attack in PSO isn't a slower swing -- it's the *same* swing with a charge in
     * front of it, which is exactly what the frame data says: a saber's opening attack runs 29
     * frames normally and 37 heavy, and connects on frame 11 against 20. Both differences are
     * the same 8-9 frames of charge, with the stroke itself unchanged. Treating that extra
     * length as a slower animation made heavy attacks look sluggish rather than deliberate.
     */
    fun chargeFramesAgainst(normal: AttackFrames): Int = (full - normal.full).coerceAtLeast(0)
}

/** The three combo steps for one weapon class and attack type. */
class ComboFrames(val step1: AttackFrames, val step2: AttackFrames, val step3: AttackFrames) {
    operator fun get(step: Int): AttackFrames = when (step) {
        0 -> step1
        1 -> step2
        else -> step3
    }
}

/**
 * Male V101 frame data -- the animation set this game uses for every class, so it's the only table
 * needed right now.
 *
 * PSO also ships slower "no V101" animations and per-class variants (female, HUcaseal, RAmarl,
 * FOmar, FOmarl), which differ by a few frames either way. Those matter once female and
 * class-specific animation sets are wired up; until every character animates from the male set,
 * a second table would just be unused.
 *
 * Special attacks aren't in the source table, which lists Normal and Heavy only. They reuse the
 * heavy timings here -- a special is a heavy swing with a different payload -- which is an
 * assumption, not data.
 */
private fun frames(
    normal: List<Int>,
    heavy: List<Int>,
    /**
     * "Attack comes out on" per step, from the frame data's Detailed section (male V101). Null
     * means the wiki publishes no contact frames for this weapon class, and they're estimated
     * from the classes that do have them (see below).
     */
    normalContact: List<Int>? = null,
    heavyContact: List<Int>? = null,
    /**
     * Drives the estimate when contact frames aren't published: a shot leaves a gun barrel far
     * earlier in its animation (handgun: frame 5, 3, 3) than a melee arc reaches its target.
     */
    ranged: Boolean = false,
): Map<AttackType, ComboFrames> {
    // Estimated contact, as fractions of the cancel point (steps 1-2) or the full length
    // (step 3), fitted to the published classes: melee step-3 contact sits at ~26% of the full
    // clip across saber/sword/partisan, ranged at ~16%.
    fun estimate(v: List<Int>): List<Int> =
        if (ranged) {
            listOf(
                (v[1] * 0.4).toInt().coerceAtLeast(1),
                (v[3] * 0.3).toInt().coerceAtLeast(1),
                (v[4] * 0.16).toInt().coerceAtLeast(1),
            )
        } else {
            listOf(
                (v[1] * 0.75).toInt().coerceAtLeast(1),
                (v[3] * 0.8).toInt().coerceAtLeast(1),
                (v[4] * 0.26).toInt().coerceAtLeast(1),
            )
        }

    // Each list is [step1 full, step1 combo, step2 full, step2 combo, step3 full], the exact
    // column order of the source table.
    fun build(v: List<Int>, contact: List<Int>?) = (contact ?: estimate(v)).let { c ->
        ComboFrames(
            AttackFrames(full = v[0], combo = v[1], contact = c[0]),
            AttackFrames(full = v[2], combo = v[3], contact = c[1]),
            AttackFrames(full = v[4], contact = c[2]),
        )
    }

    val heavyFrames = build(heavy, heavyContact)

    return mapOf(
        AttackType.NORMAL to build(normal, normalContact),
        AttackType.HEAVY to heavyFrames,
        AttackType.SPECIAL to heavyFrames,
    )
}

// Contact frames are the Detailed tables' "attack comes out on" (male V101, first hit where an
// attack strikes multiple times); classes the wiki doesn't detail get the estimate in [frames].
private val ATTACK_FRAMES: Map<WeaponType, Map<AttackType, ComboFrames>> = mapOf(
    WeaponType.SABER to frames(
        listOf(29, 13, 24, 10, 31), listOf(37, 21, 29, 15, 34),
        normalContact = listOf(11, 10, 8), heavyContact = listOf(20, 15, 14),
    ),
    WeaponType.SWORD to frames(
        listOf(39, 17, 31, 15, 43), listOf(46, 24, 35, 19, 43),
        normalContact = listOf(11, 11, 11), heavyContact = listOf(20, 16, 16),
    ),
    WeaponType.DAGGER to frames(listOf(40, 21, 30, 15, 50), listOf(46, 27, 35, 20, 49)),
    WeaponType.PARTISAN to frames(
        listOf(39, 17, 30, 14, 33), listOf(46, 24, 35, 19, 35),
        normalContact = listOf(14, 11, 9), heavyContact = listOf(22, 16, 15),
    ),
    WeaponType.SLICER to frames(listOf(40, 21, 32, 12, 42), listOf(47, 28, 37, 17, 43)),
    WeaponType.DOUBLE_SABER to frames(
        listOf(39, 22, 27, 11, 51), listOf(46, 29, 32, 16, 49),
        normalContact = listOf(16, 8, 9), heavyContact = listOf(23, 14, 14),
    ),
    WeaponType.CLAW to frames(listOf(27, 16, 22, 11, 37), listOf(35, 24, 27, 16, 39)),
    WeaponType.KATANA to frames(listOf(30, 14, 29, 15, 44), listOf(38, 22, 33, 19, 44)),
    WeaponType.TWIN_SWORD to frames(listOf(37, 18, 34, 19, 51), listOf(44, 25, 37, 22, 49)),
    WeaponType.FIST to frames(listOf(26, 19, 26, 19, 35), listOf(33, 26, 29, 22, 36)),
    WeaponType.HANDGUN to frames(
        listOf(27, 14, 25, 11, 19), listOf(34, 22, 30, 16, 25),
        normalContact = listOf(5, 3, 3), heavyContact = listOf(15, 10, 10),
        ranged = true,
    ),
    WeaponType.RIFLE to frames(listOf(29, 15, 25, 12, 20), listOf(37, 23, 30, 17, 26), ranged = true),
    WeaponType.MECHGUN to frames(
        listOf(49, 12, 45, 10, 42), listOf(58, 21, 50, 15, 48),
        normalContact = listOf(5, 3, 3), heavyContact = listOf(15, 10, 10),
        ranged = true,
    ),
    WeaponType.SHOT to frames(listOf(50, 25, 43, 21, 34), listOf(56, 31, 46, 24, 38), ranged = true),
    WeaponType.LAUNCHER to frames(listOf(46, 21, 41, 19, 36), listOf(52, 27, 44, 22, 39), ranged = true),
    WeaponType.CANE to frames(listOf(29, 13, 27, 13, 39), listOf(37, 21, 32, 18, 40)),
    WeaponType.ROD to frames(listOf(29, 14, 27, 14, 40), listOf(37, 22, 32, 19, 41)),
    WeaponType.WAND to frames(listOf(30, 14, 29, 15, 40), listOf(37, 21, 33, 19, 41)),
    WeaponType.CARD to frames(listOf(33, 18, 30, 17, 47), listOf(40, 25, 34, 21, 47), ranged = true),
)

/** This weapon class's timing for one attack type at one combo step. */
fun attackFrames(weapon: WeaponType, type: AttackType, comboStep: Int): AttackFrames =
    ATTACK_FRAMES.getValue(weapon).getValue(type)[comboStep]

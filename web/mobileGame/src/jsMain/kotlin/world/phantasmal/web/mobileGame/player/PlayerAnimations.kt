package world.phantasmal.web.mobileGame.player

/**
 * Indices into the player's 572 `animation_NNN.njm` clips (psov2's `plymotiondata_NNN`),
 * transcribed from the Blue Burst master animation list.
 *
 * **The list is 1-indexed and these files are 0-indexed**, so every index here is the list's
 * number minus one -- its "Male Saber Walk" #105 is `animation_104.njm`. That offset isn't
 * assumed: the list records each clip's frame count, and checking those against the real files
 * matches 205 of 208 at -1 against 38 at +0. The three 4-frame clips in the whole set (the
 * mechgun attacks, #26/#265/#450) land exactly where -1 predicts, and the two indices already
 * confirmed in game -- unarmed walk 200 and run 207 -- are #201 and #208.
 *
 * The numbering runs in per-weapon blocks: PSO gives each weapon class its own walk/idle/attack/
 * flinch/death set rather than sharing one, which is what [WeaponAnimations] groups.
 *
 * Only the male sets are catalogued here. The list continues with female equivalents from #240
 * and class-specific overrides from #445, much of it unlabelled; those are left for when the game
 * actually varies animation by body type, rather than guessed at now.
 */
object PlayerAnimations {
    /**
     * One weapon class's motion set.
     *
     * [walk] and [idle] are the weapon-lowered versions; [aggroWalk] and [aggroIdle] are the
     * weapon-raised ones the real game uses with an enemy nearby. Several classes only ship the
     * aggro variant, in which case both fields hold it.
     *
     * Nullable entries are genuinely absent for that weapon rather than unidentified -- the Claw
     * has no death clip of its own, for instance. Callers fall back to the unarmed set.
     */
    class WeaponAnimations(
        val walk: Int,
        val idle: Int,
        val attacks: List<Int>,
        val run: Int,
        val aggroWalk: Int = walk,
        val aggroIdle: Int = idle,
        val block: Int? = null,
        val cast: Int? = null,
        val hit: Int? = null,
        val knockedDown: Int? = null,
        val getUp: Int? = null,
        val death: Int? = null,
        val switchPress: Int? = null,
        val photonBlast: Int? = null,
    )

    /**
     * Unarmed, list #194-208. The fallback for every weapon whose own block is missing a clip,
     * and what the character uses with nothing in hand.
     */
    val FIST = WeaponAnimations(
        walk = 200, idle = 201, attacks = listOf(195, 196, 197), run = 207,
        aggroWalk = 193, aggroIdle = 198,
        block = 199, cast = 194, hit = 204, knockedDown = 202, getUp = 203, death = 205,
        photonBlast = 206,
    )

    /**
     * Saber, list #98-112. Its run (#112) is noted as also serving rod, wand, cane, slicer, sword
     * and double saber -- those classes ship no run of their own.
     */
    val SABER = WeaponAnimations(
        walk = 104, idle = 102, attacks = listOf(99, 100, 101), run = 111,
        aggroWalk = 97,
        block = 103, cast = 98, hit = 107, knockedDown = 105, getUp = 106, death = 108,
        switchPress = 109, photonBlast = 110,
    )

    /** Sword, list #113-125. */
    val SWORD = WeaponAnimations(
        walk = 119, idle = 117, attacks = listOf(114, 115, 116), run = SABER.run,
        aggroWalk = 112,
        block = 118, cast = 113, hit = 122, knockedDown = 120, getUp = 121, death = 123,
        photonBlast = 124,
    )

    /** Dagger, list #126-138. Shares the unarmed run. */
    val DAGGER = WeaponAnimations(
        walk = 132, idle = 130, attacks = listOf(127, 128, 129), run = FIST.run,
        aggroWalk = 125,
        block = 131, cast = 126, hit = 135, knockedDown = 133, getUp = 134, death = 136,
        photonBlast = 137,
    )

    /** Partisan, list #139-152. */
    val PARTISAN = WeaponAnimations(
        walk = 145, idle = 143, attacks = listOf(140, 141, 142), run = 151,
        aggroWalk = 138,
        block = 144, cast = 139, hit = 148, knockedDown = 146, getUp = 147, death = 149,
        photonBlast = 150,
    )

    /** Slicer, list #153-165. Two flinch clips are listed (#158 and #163); the named one is used. */
    val SLICER = WeaponAnimations(
        walk = 158, idle = 159, attacks = listOf(153, 154, 155), run = SABER.run,
        aggroWalk = 152, aggroIdle = 156,
        hit = 162, knockedDown = 160, getUp = 161, death = 163, photonBlast = 164,
    )

    /**
     * Double saber, list #166-170 -- only five clips, so everything else comes from the saber it
     * shares a run with. Its photon blast is the wand's (#88), per that entry's own note.
     */
    val DOUBLE_SABER = WeaponAnimations(
        walk = 165, idle = 169, attacks = listOf(166, 167, 168), run = SABER.run,
        block = SABER.block, cast = SABER.cast, hit = SABER.hit,
        knockedDown = SABER.knockedDown, getUp = SABER.getUp, death = SABER.death,
        photonBlast = 87,
    )

    /** Claw, list #171-175. Only five clips; shares the unarmed run. */
    val CLAW = WeaponAnimations(
        walk = 170, idle = 174, attacks = listOf(171, 172, 173), run = FIST.run,
        block = FIST.block, cast = FIST.cast, hit = FIST.hit,
        knockedDown = FIST.knockedDown, getUp = FIST.getUp, death = FIST.death,
    )

    /** Twin sword, list #176-189. */
    val TWIN_SWORD = WeaponAnimations(
        walk = 182, idle = 183, attacks = listOf(177, 178, 179), run = 188,
        aggroWalk = 175, aggroIdle = 180,
        block = 181, cast = 176, hit = 186, knockedDown = 184, getUp = 185,
        death = SABER.death, photonBlast = 187,
    )

    /** Katana, list #190-193 -- attacks and a stance only; the rest is the saber's. */
    val KATANA = WeaponAnimations(
        walk = SABER.walk, idle = 192, attacks = listOf(189, 190, 191), run = SABER.run,
        aggroWalk = SABER.aggroWalk,
        block = SABER.block, cast = SABER.cast, hit = SABER.hit,
        knockedDown = SABER.knockedDown, getUp = SABER.getUp, death = SABER.death,
    )

    /**
     * Handgun, list #1-10. Its one attack clip covers all three combo steps, per the list's own
     * note. It ships no death or flinch, and its walk/stand are marked unused in favour of the
     * rifle's -- so those are what it borrows.
     */
    val HANDGUN = WeaponAnimations(
        walk = 15, idle = 16, attacks = listOf(2), run = FIST.run,
        aggroWalk = 0, aggroIdle = 3,
        block = 4, cast = 1, knockedDown = 7, getUp = 8,
        hit = 19, death = 20, photonBlast = 9,
    )

    /** Rifle, list #11-23. */
    val RIFLE = WeaponAnimations(
        walk = 15, idle = 16, attacks = listOf(12), run = 22,
        aggroWalk = 10, aggroIdle = 13,
        block = 14, cast = 11, hit = 19, knockedDown = 17, getUp = 18, death = 20,
        switchPress = 21,
    )

    /** Mechgun, list #24-36. Its knockdown/rise pair is marked unused; the rifle's stands in. */
    val MECHGUN = WeaponAnimations(
        walk = 28, idle = 29, attacks = listOf(25), run = 35,
        aggroWalk = 23, aggroIdle = 26,
        block = 27, cast = 24, hit = 32, knockedDown = RIFLE.knockedDown, getUp = RIFLE.getUp,
        death = 33, photonBlast = 34,
    )

    /** Shot, list #37-49. */
    val SHOT = WeaponAnimations(
        walk = 41, idle = 42, attacks = listOf(38), run = 48,
        aggroWalk = 36, aggroIdle = 39,
        block = 40, cast = 37, hit = 45, knockedDown = 43, getUp = 44, death = 46,
        photonBlast = 47,
    )

    /** Launcher, list #50-62. */
    val LAUNCHER = WeaponAnimations(
        walk = 54, idle = 55, attacks = listOf(51), run = 61,
        aggroWalk = 49, aggroIdle = 52,
        block = 53, cast = 50, hit = 58, knockedDown = 56, getUp = 57,
        death = SHOT.death, switchPress = 59, photonBlast = 60,
    )

    /** Rod, list #63-76. */
    val ROD = WeaponAnimations(
        walk = 69, idle = 70, attacks = listOf(64, 65, 66), run = SABER.run,
        aggroWalk = 62, aggroIdle = 67,
        block = 68, cast = 63, hit = 73, knockedDown = 71, getUp = 72, death = 74,
        photonBlast = 75,
    )

    /** Wand, list #77-88. Ships no plain walk or stand, so the aggro pair does both jobs. */
    val WAND = WeaponAnimations(
        walk = 76, idle = 81, attacks = listOf(78, 79, 80), run = SABER.run,
        block = 82, cast = 77, hit = 85, knockedDown = 83, getUp = 84, death = 86,
        photonBlast = 87,
    )

    /** Cane -- no block of its own; the list notes the saber's cast serves canes. */
    val CANE = WeaponAnimations(
        walk = ROD.walk, idle = ROD.idle, attacks = ROD.attacks, run = SABER.run,
        aggroWalk = ROD.aggroWalk, aggroIdle = ROD.aggroIdle,
        block = ROD.block, cast = SABER.cast, hit = ROD.hit,
        knockedDown = ROD.knockedDown, getUp = ROD.getUp, death = ROD.death,
    )

    /** Card, list #89-97. */
    val CARD = WeaponAnimations(
        walk = 88, idle = 92, attacks = listOf(89, 90, 91), run = 96,
        hit = 95, knockedDown = 93, getUp = 94, death = FIST.death,
    )

    /**
     * Standing poses used to give the town NPCs variety instead of all sharing one idle. All are
     * plain standing clips implying no weapon, except the two rifle ones.
     */
    object Idles {
        /** Plain standing still. */
        const val STANDING = 6

        /** Standing with a visible breathing motion -- reads as more alive than [STANDING]. */
        const val BREATHING = 29

        /** Left hand on hip, right arm by the side -- suits counter staff. */
        const val HAND_ON_HIP = 70

        /** Right arm raised out to the side, bent 90 degrees. */
        const val ARM_RAISED = 55

        /** Holding a rifle at the ready. */
        const val RIFLE = 13

        /** Rifle resting over one shoulder. */
        const val RIFLE_SHOULDERED = 16
    }
}

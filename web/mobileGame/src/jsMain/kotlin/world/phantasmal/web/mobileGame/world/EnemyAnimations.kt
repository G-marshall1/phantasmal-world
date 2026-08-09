package world.phantasmal.web.mobileGame.world

/**
 * The idle/damage/death clips for one enemy family, on top of the walk and attack pair
 * GameRenderer already spawns every enemy with.
 *
 * Clip names are psov2's raw `.njm` entry names, transcribed by watching each one. They're stored
 * per enemy slug rather than per family because every enemy ships its own copy of the set even
 * when several share the same underlying animations -- the Booma clips, for instance, also drive
 * Go/Giga Booma, Evil Shark and Dimenian, but each has them in its own `npcs/{slug}/` directory.
 */
/**
 * The extra clips a hive needs beyond the usual set: it hangs before it deploys, drops and
 * lands when approached, and once beaten down keeps working from the floor with its own
 * idle/release/flinch trio.
 */
class HiveAnimationSet(
    val hang: String,
    val land: String,
    val down: String,
    val downWait: String,
    val downRelease: String,
    val downDamage: String,
)

class EnemyAnimationSet(
    /** Standing/idle clip, used outside aggro range instead of walking on the spot. */
    val wait: String,
    /** Flinch on taking a hit. */
    val damage: String,
    /** Death, played out before the body is removed. */
    val death: String,
    /** Chase clip. Null keeps the walk clip while closing distance. */
    val run: String? = null,
    /**
     * Played once when the player first comes into aggro range -- the enemy noticing you. Null
     * means it goes straight from idling to chasing.
     */
    val wakeUp: String? = null,
    /**
     * Second strike clip, for a species that swings with either side. Null means it only has one.
     */
    val attackAlt: String? = null,
    /** Longer stagger for a heavy hit. Null falls back to [damage]. */
    val stun: String? = null,
    /**
     * One-shot entrance played where the species arrives rather than simply being present -- a
     * Booma bursts out of the ground. Null means it's just standing there when the wave starts.
     */
    val appear: String? = null,
)

/**
 * The Booma family, and the most complete set in the game so far: all twelve of its clips are
 * accounted for, and ten are wired. "leader" (the pack-leader stance) and "deadb" (falling
 * backward) are the two left, both needing systems that don't exist -- pack roles, and knowing
 * which side a killing blow came from.
 */
private val BOOMA = EnemyAnimationSet(
    wait = "mihari_bm1_s_wala_body.njm",
    damage = "damage_bm1_s_wala_body.njm",
    // "dead" falls forward, "deadb" backward -- forward reads better from the usual camera angle.
    death = "dead_bm1_s_wala_body.njm",
    run = "run_bm1_s_wala_body.njm",
    // "wakeup" is the re-notice once you've left and come back.
    wakeUp = "wakeup_bm1_s_wala_body.njm",
    // Left and right claw, alternated per swing.
    attackAlt = "atackr_bm1_s_wala_body.njm",
    stun = "stund_bm1_s_wala_body.njm",
    // Boomas burst out of the ground when their wave triggers.
    appear = "appear_bm1_s_wala_body.njm",
)

private val RAPPY = EnemyAnimationSet(
    wait = "wait_re3_b_base.njm",
    damage = "damage_re3_b_base.njm",
    death = "die_re3_b_base.njm",
    run = "run_re3_b_base.njm",
    wakeUp = "wake_re3_b_base.njm",
    // A Rappy knocked hard goes over rather than merely flinching -- "tumble" is that fall,
    // and it's a longer hold than the flinch, which is exactly what a stagger should be.
    stun = "tumble_re3_b_base.njm",
)

private val WOLF = EnemyAnimationSet(
    wait = "wait_bm5_s_kem_body.njm",
    damage = "dams_bm5_s_kem_body.njm",
    death = "deadr_bm5_s_kem_body.njm",
    run = "run_bm5_s_kem_body.njm",
    // Wolves break cover rather than simply standing up -- "hunt" is that entrance.
    wakeUp = "hunt_bm5_s_kem_body.njm",
    // "okil"/"okir" are the left and right lunges; alternating them keeps a flurry from
    // replaying one bite.
    attackAlt = "okir_bm5_s_kem_body.njm",
)

private val HILDEBEAR = EnemyAnimationSet(
    wait = "stand_bm2f_s_moj_body.njm",
    damage = "damage_bm2f_s_moj_body.njm",
    death = "dead_bm2f_s_moj_body.njm",
    // No run clip in this family; it lumbers on its walk. "jump" is the long-distance arrival.
    wakeUp = "jump_bm2f_s_moj_body.njm",
)

private val MOTHMANT = EnemyAnimationSet(
    // Mothmant never stands -- it hovers, so its flying clip doubles as the idle, and "move" is
    // the flying-while-travelling variant.
    wait = "fly_bm3_fly_body.njm",
    damage = "damage_bm3_fly_body.njm",
    death = "dead_bm3_fly_body.njm",
    run = "move_bm3_fly_body.njm",
)

/**
 * The hive itself. "wait" is its gentle open-mouthed sway; "dam"/"dead" are its flinch and
 * collapse. Its "exit" clip -- the mouth snapping open to release Mothmants -- is wired as its
 * attack clip (see AREA_ENEMIES) and played by the hive-production logic each time it emits one.
 * The "down"/"dwn*" closed-state set and "trance"/"land" are left for when the closed-until-
 * approached behaviour is modelled.
 */
private val MONEST = EnemyAnimationSet(
    wait = "wait_bm3_s_nest.njm",
    damage = "dam_bm3_s_nest.njm",
    death = "dead_bm3_s_nest.njm",
)

/**
 * The Monest's full life: "trance" is the closed husk hanging in the canopy, "land" is it
 * dropping and setting down, and the "dwn" set is everything it does after being knocked over
 * -- it keeps releasing Mothmants from the floor until it's finished off.
 */
val MONEST_HIVE_ANIMATIONS = HiveAnimationSet(
    hang = "trance_bm3_s_nest.njm",
    land = "land_bm3_s_nest.njm",
    down = "down_bm3_s_nest.njm",
    downWait = "dwnwait_bm3_s_nest.njm",
    downRelease = "dwnexit_bm3_s_nest.njm",
    downDamage = "dwndam_bm3_s_nest.njm",
)

/**
 * Covers the Forest roster. Anything absent keeps the previous behaviour exactly -- walk and
 * attack only, vanishing the instant it dies -- so undocumented enemies elsewhere in the game are
 * unaffected until their own clips are identified.
 */
/**
 * The Dragon's grounded moveset: stand, lumber, claw slash and tail swipe, both damage
 * weights, and its burst-in entrance. The flight/fire-breath clips ship too and join when
 * the fight's aerial phases are built.
 */
private val DRAGON = EnemyAnimationSet(
    wait = "stand_boss1_s_nb_dragon.njm",
    damage = "dams_boss1_s_nb_dragon.njm",
    death = "dead_boss1_s_nb_dragon.njm",
    wakeUp = "tobidasi_boss1_s_nb_dragon.njm",
    attackAlt = "tatk_boss1_s_nb_dragon.njm",
    stun = "daml_boss1_s_nb_dragon.njm",
)

val ENEMY_ANIMATIONS: Map<String, EnemyAnimationSet> = mapOf(
    "Booma" to BOOMA,
    "GoBooma" to BOOMA,
    "GigaBooma" to BOOMA,
    "EvilShark" to BOOMA,
    "Dimenian" to BOOMA,
    "Rappy" to RAPPY,
    // The rare variants ship their own copies of their base species' clips.
    "AlRappy" to RAPPY,
    "SavageWolf" to WOLF,
    "BarbarousWolf" to WOLF,
    "Hildebear" to HILDEBEAR,
    "Hildebaby" to HILDEBEAR,
    "Hildeblue" to HILDEBEAR,
    "Mothmant" to MOTHMANT,
    "Monest" to MONEST,
    "Dragon" to DRAGON,
)

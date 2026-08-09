package world.phantasmal.web.mobileGame.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Guards the two ways the weapon catalogue can silently go wrong: an animation index that doesn't
 * exist (the character freezes, no error), and a weapon whose motion set is incomplete (it falls
 * through to a null and takes the wrong clip).
 */
class WeaponsTests {
    /** Clips ship as animation_000..animation_571 -- see generatePlayerAnimations. */
    private val clipRange = 0 until 572

    private val allSets = WeaponType.entries.map { it.name to it.animations }

    @Test
    fun everyAnimationIndexIsAClipThatExists() {
        for ((name, set) in allSets) {
            val indices = buildList {
                add(set.walk); add(set.idle); add(set.run); add(set.aggroWalk); add(set.aggroIdle)
                addAll(set.attacks)
                listOf(
                    set.block, set.cast, set.hit, set.knockedDown,
                    set.getUp, set.death, set.switchPress, set.photonBlast,
                ).forEach { it?.let(::add) }
            }

            for (index in indices) {
                assertTrue(index in clipRange, "$name references clip $index, which doesn't exist")
            }
        }
    }

    @Test
    fun everyWeaponClassHasAtLeastOneAttack() {
        for ((name, set) in allSets) {
            assertTrue(set.attacks.isNotEmpty(), "$name has no attack clip")
        }
    }

    /** The unarmed set is what every other class falls back to, so it can't have holes itself. */
    @Test
    fun theUnarmedSetIsComplete() {
        val fist = PlayerAnimations.FIST
        assertTrue(fist.death != null, "unarmed has no death clip to fall back to")
        assertTrue(fist.hit != null, "unarmed has no flinch clip to fall back to")
        assertTrue(fist.knockedDown != null && fist.getUp != null)
        assertEquals(3, fist.attacks.size, "unarmed should have a full 3-hit combo")
    }

    /** Both confirmed in game by eye before the animation list was cross-checked. */
    @Test
    fun theUnarmedWalkAndRunAreTheConfirmedClips() {
        assertEquals(200, PlayerAnimations.FIST.walk)
        assertEquals(207, PlayerAnimations.FIST.run)
    }

    /** The saber block runs 97..111; its combo and walk were verified in game. */
    @Test
    fun theSaberSetComesFromItsOwnBlock() {
        val saber = PlayerAnimations.SABER
        assertEquals(listOf(99, 100, 101), saber.attacks)
        assertEquals(104, saber.walk)
        assertEquals(111, saber.run, "the saber run is clip 111, not the card run it used to use")
    }

    @Test
    fun everyCataloguedWeaponResolvesToItsOwnType() {
        for ((slug, type) in WEAPON_TYPES) {
            assertEquals(type, weaponType(slug), "$slug resolved to the wrong class")
        }
    }

    @Test
    fun anUnknownWeaponFallsBackRatherThanFailing() {
        assertEquals(WeaponType.SABER, weaponType("NotAWeaponWeShip"))
    }

    /** Fist is the bare-handed fallback, so it must not appear as something to equip. */
    @Test
    fun theCatalogueOnlyListsThingsYouCanHold() {
        assertTrue(WeaponType.FIST !in WEAPON_TYPES.values)
        assertTrue(EQUIPPABLE_WEAPONS.isNotEmpty())
        assertEquals(WEAPON_TYPES.size, EQUIPPABLE_WEAPONS.size)
    }

    /**
     * Fist is bare-handed and Card has an animation block but no model in the shipped archive, so
     * neither is something you can pick up. Everything else must have at least one model, or the
     * class is dead weight nothing can ever reach.
     */
    @Test
    fun everyWeaponClassYouCanHoldHasAtLeastOneModel() {
        val covered = WEAPON_TYPES.values.toSet()

        for (type in WeaponType.entries) {
            if (type == WeaponType.FIST || type == WeaponType.CARD) continue
            assertTrue(type in covered, "no shipped model is classified as $type")
        }
    }
}

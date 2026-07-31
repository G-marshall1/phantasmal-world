package world.phantasmal.web.assetsGeneration.psov2

/**
 * One decorative/interactive map prop from psov2's shared item.bml -- the same archive
 * WEAPON_SPECS' item-box-adjacent entries live in, but referenced by name (bml['x.pvm']/
 * bml['x.nj']) rather than by numeric index the way itemmodel.afs/itemtexture.afs are. psov2's
 * own AssetObjects.js catalogs 116 map objects total (doors, switches, monuments, rocks,
 * machines, etc.), but the overwhelming majority pull from per-map GSL archives with multi-part
 * NJCM merges (several .nj fragments assembled into one object) -- a materially different, much
 * messier sourcing pattern this doesn't attempt yet. This covers just the handful that use the
 * same simple single-archive, single-model pattern weapons do (auto-verified: every one of these
 * loads exactly one archive, no GSL nesting, no multi-part NJCM merge).
 */
class ObjectSpec(
    val slug: String,
    val pvmEntry: String,
    val njEntry: String,
)

val OBJECT_SPECS: List<ObjectSpec> = listOf(
    ObjectSpec("GunBullet", "gun_bullet.pvm", "gun_bullet.nj"),
    ObjectSpec("ItemBox", "ixm_box03.pvm", "ixm_box03.nj"),
    ObjectSpec("Meseta", "ixm_box04.pvm", "ixm_box04.nj"),
    ObjectSpec("ArmorScan", "armor_scan.pvm", "armor_scan.nj"),
    ObjectSpec("Shockwave", "wxmS01_d_w_bullet.pvm", "wxmS01_d_w_bullet.nj"),
    ObjectSpec("WeaponBox", "ixm_box01.pvm", "ixm_box01.nj"),
    ObjectSpec("ArmorBox", "ixm_box02.pvm", "ixm_box02.nj"),
)

plugins {
    id("world.phantasmal.jvm")
}

kotlin {
    sourceSets.configureEach {
        languageSettings.optIn("kotlinx.serialization.ExperimentalSerializationApi")
    }
}

dependencies {
    implementation(project(":psolib"))
    implementation(project(":web:shared"))
    implementation("org.jsoup:jsoup:1.13.1")
}

tasks.register<JavaExec>("generateAssets") {
    val outputFile = layout.buildDirectory.get().asFile.resolve("generatedAssets")
    outputs.dir(outputFile)

    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("world.phantasmal.web.assetsGeneration.MainKt")
    args = listOf(outputFile.absolutePath)
}

// Converts a subset of the psov2 (https://gitlab.com/dashgl/psov2) copyright-free PSO asset
// recreation into the mobile game's asset directory. Pass the psov2 "public/dat" directory with
// -Ppsov2Dir=/path/to/psov2/public/dat.
tasks.register<JavaExec>("generatePsov2MobileAssets") {
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("world.phantasmal.web.assetsGeneration.psov2.MainKt")
    args = listOf(
        requireNotNull(findProperty("psov2Dir")) {
            "Pass -Ppsov2Dir=/path/to/psov2/public/dat"
        }.toString(),
        // Defaults to writing straight into the mobile game's assets. Override with
        // -Ppsov2OutDir=/tmp/... to generate somewhere scratch first -- useful when adding a new
        // spec, so a single new file can be inspected and copied in without rewriting the whole
        // ~36MB asset tree.
        (findProperty("psov2OutDir")?.toString()
            ?: rootProject.file("web/mobileGame/src/jsMain/resources/assets").absolutePath),
    )
}

// Scratch diagnostic for the Dragon's skeleton/clips -- see DebugDragonMotion.kt.
tasks.register<JavaExec>("debugDragonMotion") {
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("world.phantasmal.web.assetsGeneration.psov2.DebugDragonMotionKt")
    args = listOf(
        rootProject.file("web/mobileGame/src/jsMain/resources/assets").absolutePath,
    )
}

// Converts newserv's exported game data tables (data/newserv-tables/, MIT) into the mobile
// game's generated Kotlin tables. Rerun whenever the source tables change.
tasks.register<JavaExec>("generateNewservTables") {
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("world.phantasmal.web.assetsGeneration.newserv.GenerateLevelTableKt")
    args = listOf(
        rootProject.file("data/newserv-tables/level-table-v4.json").absolutePath,
        rootProject.file(
            "web/mobileGame/src/jsMain/kotlin/world/phantasmal/web/mobileGame/player/GeneratedLevelTable.kt"
        ).absolutePath,
    )
}

tasks.register<JavaExec>("generateNewservItemCatalog") {
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("world.phantasmal.web.assetsGeneration.newserv.GenerateItemCatalogKt")
    args = listOf(
        rootProject.file("data/newserv-tables/item-parameter-table-bb-v4.json").absolutePath,
        rootProject.file("data/newserv-tables/names-v4.json").absolutePath,
        rootProject.file(
            "web/mobileGame/src/jsMain/kotlin/world/phantasmal/web/mobileGame/player/GeneratedItemCatalog.kt"
        ).absolutePath,
    )
}

tasks.register<JavaExec>("generateNewservEnemyStats") {
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("world.phantasmal.web.assetsGeneration.newserv.GenerateEnemyStatsKt")
    args = listOf(
        rootProject.file("data/newserv-tables/battle-params.json").absolutePath,
        rootProject.file(
            "web/mobileGame/src/jsMain/kotlin/world/phantasmal/web/mobileGame/world/GeneratedEnemyStats.kt"
        ).absolutePath,
    )
}

tasks.register<JavaExec>("generateNewservRareDrops") {
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("world.phantasmal.web.assetsGeneration.newserv.GenerateRareDropsKt")
    args = listOf(
        rootProject.file("data/newserv-tables/rare-table-v4.json").absolutePath,
        rootProject.file("data/newserv-tables/names-v4.json").absolutePath,
        rootProject.file(
            "web/mobileGame/src/jsMain/kotlin/world/phantasmal/web/mobileGame/player/GeneratedRareDrops.kt"
        ).absolutePath,
    )
}

// Research tool: dumps one .qst (identity, placements, full disassembly). Pass -PquestFile=path.
tasks.register<JavaExec>("dumpQuest") {
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("world.phantasmal.web.assetsGeneration.quests.DumpQuestKt")
    args = listOf(requireNotNull(findProperty("questFile")) { "Pass -PquestFile=path" }.toString())
}

// Converts the authentic .qst quest set into the mobile game's quest JSON.
tasks.register<JavaExec>("convertQuests") {
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("world.phantasmal.web.assetsGeneration.quests.ConvertQuestsKt")
    args = listOf(rootDir.absolutePath)
}

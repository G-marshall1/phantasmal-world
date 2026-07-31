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
        rootProject.file("web/mobileGame/src/jsMain/resources/assets").absolutePath,
    )
}

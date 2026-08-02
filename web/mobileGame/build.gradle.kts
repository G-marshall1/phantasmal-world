plugins {
    id("world.phantasmal.js")
    kotlin("plugin.serialization")
}

val serializationVersion: String by project.extra

kotlin {
    js {
        browser {
            runTask {
                devServerProperty.set(
                    devServerProperty.get().copy(
                        open = false,
                        port = 1624,
                    )
                )
            }
        }
        binaries.executable()
    }

    sourceSets {
        getByName("jsMain") {
            dependencies {
                implementation(project(":psolib"))
                implementation(project(":web:rendering"))
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$serializationVersion")
            }
        }

        getByName("jsTest") {
            dependencies {
                implementation(project(":test-utils"))
            }
        }
    }
}

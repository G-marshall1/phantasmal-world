plugins {
    id("world.phantasmal.js")
}

kotlin {
    sourceSets {
        getByName("jsMain") {
            dependencies {
                api(project(":psolib"))
                api(project(":webui"))
                api(project(":web:shared"))

                implementation(npm("three", "^0.128.0"))
            }
        }

        getByName("jsTest") {
            dependencies {
                implementation(project(":test-utils"))
            }
        }
    }
}

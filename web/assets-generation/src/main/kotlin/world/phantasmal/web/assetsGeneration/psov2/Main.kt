package world.phantasmal.web.assetsGeneration.psov2

import java.io.File

fun main(args: Array<String>) {
    require(args.size == 2) {
        "Expected two arguments: the psov2 \"public/dat\" source directory and the output directory."
    }

    generatePsov2MobileAssets(sourceDir = File(args[0]), outputDir = File(args[1]))
}

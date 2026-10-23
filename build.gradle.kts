plugins {
    id("com.gtnewhorizons.gtnhconvention")
}

// The OptiFine shader pack (shaderpack/), zipped into the jar for the client to install and kept on its own for
// players who want it by hand.
val shaderPackZip = tasks.register<Zip>("shaderPackZip") {
    from("shaderpack")
    archiveFileName.set("WarOfTheGods.zip")
    destinationDirectory.set(layout.buildDirectory.dir("shaderpack"))
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

tasks.named<ProcessResources>("processResources") {
    from(shaderPackZip) {
        into("assets/warofthegods/shaderpack")
    }
}

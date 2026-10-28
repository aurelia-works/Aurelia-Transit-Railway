plugins {
    `maven-publish`
}

group = "de.bluecolored"
// AureliaMap: fixed version, the fork has no git tags of its own
version = "0.0-aureliamap"

repositories {
    maven ("https://repo.bluecolored.de/releases") {
        content { includeGroupByRegex ("de\\.bluecolored.*") }
    }
    maven ("https://repo.bluecolored.de/snapshots") {
        content { includeGroupByRegex ("de\\.bluecolored.*") }
    }

    // lwjgl-freetype-3.3.3-natives-macos-patch.jar is not available on Maven
    // Central - pull it from the Minecraft library server instead.
    maven ("https://libraries.minecraft.net") {
        content { includeModule("org.lwjgl", "lwjgl-freetype") }
    }

    mavenCentral()
    maven ("https://libraries.minecraft.net")
}


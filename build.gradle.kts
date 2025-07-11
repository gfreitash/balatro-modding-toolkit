plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinxSerialization)
}

group = "me.user"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

kotlin {
    listOf(
        linuxArm64("linuxNative"),
        linuxX64("linuxNativeX64"),
        mingwX64("windowsNative")
    ).forEach { target ->
        target.binaries {
            executable()
        }
    }
    sourceSets {
        commonMain {
            dependencies {
                libs.kotlinxSerializationJson
                libs.okio
                libs.arrowCore
            }
        }
    }
}

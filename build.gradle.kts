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
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
                implementation("com.squareup.okio:okio:3.15.0")
                implementation("io.arrow-kt:arrow-core:2.1.0")
            }
        }
    }
}

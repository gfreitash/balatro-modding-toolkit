plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinxSerialization)
}

group = "br.com.ghfreitas"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

kotlin {
    val nativeTargets = listOf(
//        linuxArm64("linuxNative"),
        linuxX64("linuxNativeX64"),
//        mingwX64("windowsNative")
    )

    nativeTargets.forEach { target ->
        target.binaries {
            if (target.name.startsWith("linux")) {
                executable {
                    entryPoint = "main"
                    linkerOpts.add("-Wl,--as-needed")
                    linkerOpts.add("--allow-multiple-definition")
                }
                getTest(DEBUG).apply {
                    linkerOpts.add("-Wl,--as-needed")
                }
            } else {
                executable {
                    entryPoint = "main"
                }
            }
        }

    }
    sourceSets {
        commonMain {
            dependencies {
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.okio)
                implementation(libs.arrow.core)
                implementation(libs.clikt)
            }
        }

        sourceSets.all {
            languageSettings {
                optIn("kotlin.time.ExperimentalTime")
                optIn("kotlin.ExperimentalStdlibApi")
                optIn("kotlinx.serialization.ExperimentalSerializationApi")
                optIn("arrow.core.raise.ExperimentalRaiseAccumulateApi")
            }
            compilerOptions {
                freeCompilerArgs.add("-Xcontext-parameters")
            }
        }
    }
}

import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinxSerialization)
    alias(libs.plugins.kotlinPowerAssert)
}

group = "br.com.ghfreitas"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

@OptIn(ExperimentalKotlinGradlePluginApi::class)
powerAssert {
    functions = listOf("kotlin.assert", "kotlin.test.assertTrue", "kotlin.test.assertEquals", "kotlin.test.assertNull")
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
                    debuggable = true
                }
                getTest(DEBUG).apply {
                    linkerOpts.add("-Wl,--as-needed")
                    linkerOpts.add("--allow-multiple-definition")
                    debuggable = true
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
        commonTest {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.okio.fakefs)
            }
        }

        sourceSets.all {
            languageSettings {
                optIn("kotlin.time.ExperimentalTime")
                optIn("kotlin.ExperimentalStdlibApi")
                optIn("kotlin.experimental.ExperimentalNativeApi")
                optIn("kotlinx.serialization.ExperimentalSerializationApi")
                optIn("arrow.core.raise.ExperimentalRaiseAccumulateApi")
            }
            compilerOptions {
                freeCompilerArgs.add("-Xcontext-parameters")
            }
        }
    }
}


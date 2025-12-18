package br.com.ghfreitas

import br.com.ghfreitas.dto.*
import kotlinx.serialization.json.Json
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.*

class ManifestDiscoveryTest {

    private fun validMetadata(): BalatroModMetadata = BalatroModMetadata(
        id = ModId("test_mod"),
        name = ModName("Test Mod"),
        author = listOf(ModAuthor("Alice")),
        description = ModDescription("A test mod"),
        prefix = ModPrefix("TM"),
        mainFile = MainFile("main.lua"),
        version = ModVersion("1.0.0")
    )

    @Test
    fun valid_metadata_is_valid() {
        val metadata = validMetadata()
        assertTrue(!metadata.validate().isLeft())
    }


    @Test
    fun tryParseAsBalatroManifest_handles_valid_manifest() {
        val fs = FakeFileSystem()
        val manifestPath = "/test/manifest.json".toPath()
        fs.createDirectories(manifestPath.parent!!)

        val validManifest = validMetadata()
        with(fs) { manifestPath.writeToFile(Json.encodeToString(validManifest)) }

        val metadata = with(fs) {
            tryParseAsBalatroManifest(manifestPath)
        }

        assertEquals("test_mod", metadata?.id?.value)
        assertEquals("Test Mod", metadata?.name?.value)
        assertEquals("Alice", metadata?.author?.first()?.value)
    }

    @Test
    fun tryParseAsBalatroManifest_handles_invalid_json() {
        val fs = FakeFileSystem()
        val manifestPath = "/test/invalid.json".toPath()
        fs.createDirectories(manifestPath.parent!!)

        with(fs) { manifestPath.writeToFile("{ invalid json }") }

        // Use original tryParseAsBalatroManifest function with FileSystem context
        val result = with(fs) {
            tryParseAsBalatroManifest(manifestPath)
        }

        assertNull(result)
    }

    @Test
    fun tryParseAsBalatroManifest_handles_invalid_manifest_structure() {
        val fs = FakeFileSystem()
        val manifestPath = "/test/invalid_structure.json".toPath()
        fs.createDirectories(manifestPath.parent!!)

        val invalidJson = """
        {
            "someField": "someValue",
            "notAManifest": true
        }
        """.trimIndent()

        with(fs) { manifestPath.writeToFile(invalidJson) }

        val result = with(fs) {
            tryParseAsBalatroManifest(manifestPath)
        }

        assertNull(result)
    }

    @Test
    fun tryParseAsBalatroManifest_handles_valid_structure_but_invalid_metadata() {
        val fs = FakeFileSystem()
        val manifestPath = "/test/invalid_metadata.json".toPath()
        fs.createDirectories(manifestPath.parent!!)

        val invalidMetadata = validMetadata().copy(id = ModId(""))
        with(fs) { manifestPath.writeToFile(Json.encodeToString(invalidMetadata)) }

        val result = with(fs) {
            tryParseAsBalatroManifest(manifestPath)
        }

        assertNull(result)
    }

    @Test
    fun tryParseAsBalatroManifest_ignores_valid_structure_with_invalid_metadata_if_strict_is_false() {
        val fs = FakeFileSystem()
        val manifestPath = "/test/invalid_metadata.json".toPath()
        fs.createDirectories(manifestPath.parent!!)

        val invalidMetadata = validMetadata().copy(id = ModId(""))
        with(fs) { manifestPath.writeToFile(Json.encodeToString(invalidMetadata)) }

        val result = with(fs) {
            tryParseAsBalatroManifest(manifestPath, strict = false)
        }

        assertTrue(invalidMetadata.validate().isLeft())
        assertNotNull(result)
    }

    @Test
    fun discoverManifests_complex_scenario() {
        val fs = FakeFileSystem()
        val root = "/project".toPath()
        fs.createDirectories(root)

        // Create valid manifests
        val mod1Path = root / "mods" / "awesome_mod" / "manifest.json"
        fs.createDirectories(mod1Path.parent!!)
        with(fs) {
            mod1Path.writeToFile(
                Json.encodeToString(
                    validMetadata().copy(
                        id = ModId("awesome_mod"),
                        name = ModName("Awesome Mod")
                    )
                )
            )
        }

        val mod2Path = root / "plugins" / "helper_mod" / "manifest.json"
        fs.createDirectories(mod2Path.parent!!)
        with(fs) {
            mod2Path.writeToFile(
                Json.encodeToString(
                    validMetadata().copy(
                        id = ModId("helper_mod"),
                        name = ModName("Helper Mod")
                    )
                )
            )
        }

        // Create invalid manifest
        val invalidPath = root / "broken" / "manifest.json"
        fs.createDirectories(invalidPath.parent!!)
        with(fs) { invalidPath.writeToFile("{ broken json") }

        // Create ignored manifest
        val ignoredPath = root / "temp" / "manifest.json"
        fs.createDirectories(ignoredPath.parent!!)
        with(fs) {
            ignoredPath.writeToFile(
                Json.encodeToString(
                    validMetadata().copy(
                        id = ModId("ignored_mod"),
                        name = ModName("Ignored Mod")
                    )
                )
            )
        }

        // Create gitignore
        with(fs) {
            (root / ".gitignore").writeToFile("temp/\n*.log\nnode_modules")
        }

        // Create BMT project file (should be ignored)
        with(fs) {
            (root / ".bmt.json").writeToFile("""{"rootPath": "/project"}""")
        }

        // Create other JSON files that shouldn't be manifests
        with(fs) {
            (root / "package.json").writeToFile("""{"name": "not-a-manifest"}""")
        }

        // Use original discoverManifests function with FileSystem context
        val discovered = with(fs) {
            discoverManifests(root)
        }

        assertEquals(2, discovered.size)
        assertTrue(discovered.any { it.metadata.id.value == "awesome_mod" })
        assertTrue(discovered.any { it.metadata.id.value == "helper_mod" })
        assertFalse(discovered.any { it.metadata.id.value == "ignored_mod" })

        // Verify that the ignored manifest is actually valid and would be discovered if gitignore was disabled
        val discoveredWithoutGitignore = with(fs) {
            discoverManifests(root, respectGitignore = false)
        }

        assertEquals(3, discoveredWithoutGitignore.size)
        assertTrue(discoveredWithoutGitignore.any { it.metadata.id.value == "awesome_mod" })
        assertTrue(discoveredWithoutGitignore.any { it.metadata.id.value == "helper_mod" })
        assertTrue(discoveredWithoutGitignore.any { it.metadata.id.value == "ignored_mod" })
    }

    @Test
    fun discoverManifests_handles_additional_ignores() {
        val fs = FakeFileSystem()
        val root = "/project".toPath()
        fs.createDirectories(root)

        // Create manifests in various locations
        val allowedPath = root / "allowed" / "manifest.json"
        fs.createDirectories(allowedPath.parent!!)
        with(fs) {
            allowedPath.writeToFile(Json.encodeToString(validMetadata().copy(id = ModId("allowed_mod"))))
        }

        val customIgnoredPath = root / "custom_ignored" / "manifest.json"
        fs.createDirectories(customIgnoredPath.parent!!)
        with(fs) {
            customIgnoredPath.writeToFile(Json.encodeToString(validMetadata().copy(id = ModId("custom_ignored_mod"))))
        }

        // Use original discoverManifests function with FileSystem context and additional ignores
        val discovered = with(fs) {
            discoverManifests(root, respectGitignore = true, additionalIgnores = listOf("custom_ignored"))
        }

        assertEquals(1, discovered.size)
        assertEquals("allowed_mod", discovered[0].metadata.id.value)
    }
}

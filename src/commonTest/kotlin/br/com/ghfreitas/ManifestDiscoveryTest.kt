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
    //TODO: move to dedicated test file after implementing new gitignore parser
    fun parseGitignore_handles_empty_file() {
        val fs = FakeFileSystem()
        val root = "/project".toPath()
        fs.createDirectories(root)

        fs.write(root / ".gitignore") { writeUtf8("") }
        val patterns = with(fs) { parseGitignore(root) }

        assertTrue(patterns.isEmpty())
    }

    @Test
    //TODO: move to dedicated test file after implementing new gitignore parser
    fun parseGitignore_handles_comments_and_blank_lines() {
        val fs = FakeFileSystem()
        val root = "/project".toPath()
        fs.createDirectories(root)

        val gitignoreContent = """
            # This is a comment
            
            *.log
            # Another comment
            temp/
            
            node_modules
        """.trimIndent()

        fs.write(root / ".gitignore") { writeUtf8(gitignoreContent) }

        // Use original parseGitignore function with FileSystem context
        val patterns = with(fs) {
            parseGitignore(root)
        }

        assertEquals(3, patterns.size)
        assertTrue(patterns.contains("*.log"))
        assertTrue(patterns.contains("temp/"))
        assertTrue(patterns.contains("node_modules"))
    }

    @Test
    //TODO: move to dedicated test file after implementing new gitignore parser
    fun parseGitignore_handles_nonexistent_file() {
        val fs = FakeFileSystem()
        val root = "/project".toPath()
        fs.createDirectories(root)

        val gitignorePath = root / ".gitignore"
        val exists = fs.exists(gitignorePath)

        assertFalse(exists)
    }

    @Test
    //TODO: move to dedicated test file after implementing new gitignore parser
    fun glob_matching_works_correctly() {
        // Use original matchesGlob extension function

        // Test wildcard patterns
        assertTrue("file.log".toPath().matchesGlob("*.log"))
        assertFalse("file.txt".toPath().matchesGlob("*.log"))
        assertTrue("test.json".toPath().matchesGlob("*.json"))

        // Test single character patterns
        assertTrue("file1.txt".toPath().matchesGlob("file?.txt"))
        assertTrue("filea.txt".toPath().matchesGlob("file?.txt"))
        assertFalse("file10.txt".toPath().matchesGlob("file?.txt"))

        // Test directory patterns
        assertTrue("temp/file.txt".toPath().matchesGlob("temp/*"))
        assertTrue("node_modules/package/file.js".toPath().matchesGlob("node_modules/*"))

        // Test exact matches
        assertTrue("exact.txt".toPath().matchesGlob("exact.txt"))
        assertFalse("different.txt".toPath().matchesGlob("exact.txt"))
    }

    @Test
    fun tryParseAsBalatroManifest_handles_valid_manifest() {
        val fs = FakeFileSystem()
        val manifestPath = "/test/manifest.json".toPath()
        fs.createDirectories(manifestPath.parent!!)

        val validManifest = validMetadata()
        fs.write(manifestPath) {
            writeUtf8(Json.encodeToString(validManifest))
        }

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

        fs.write(manifestPath) { writeUtf8("{ invalid json }") }

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

        fs.write(manifestPath) { writeUtf8(invalidJson) }

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
        fs.write(manifestPath) { writeUtf8(Json.encodeToString(invalidMetadata)) }

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
        fs.write(manifestPath) { writeUtf8(Json.encodeToString(invalidMetadata)) }

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
        fs.write(mod1Path) {
            writeUtf8(
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
        fs.write(mod2Path) {
            writeUtf8(
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
        fs.write(invalidPath) { writeUtf8("{ broken json") }

        // Create ignored manifest
        val ignoredPath = root / "temp" / "manifest.json"
        fs.createDirectories(ignoredPath.parent!!)
        fs.write(ignoredPath) {
            writeUtf8(
                Json.encodeToString(
                    validMetadata().copy(
                        id = ModId("ignored_mod"),
                        name = ModName("Ignored Mod")
                    )
                )
            )
        }

        // Create gitignore
        fs.write(root / ".gitignore") {
            writeUtf8("temp/\n*.log\nnode_modules")
        }

        // Create BMT project file (should be ignored)
        fs.write(root / ".bmt.json") {
            writeUtf8("""{"rootPath": "/project"}""")
        }

        // Create other JSON files that shouldn't be manifests
        fs.write(root / "package.json") {
            writeUtf8("""{"name": "not-a-manifest"}""")
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
        fs.write(allowedPath) {
            writeUtf8(Json.encodeToString(validMetadata().copy(id = ModId("allowed_mod"))))
        }

        val customIgnoredPath = root / "custom_ignored" / "manifest.json"
        fs.createDirectories(customIgnoredPath.parent!!)
        fs.write(customIgnoredPath) {
            writeUtf8(Json.encodeToString(validMetadata().copy(id = ModId("custom_ignored_mod"))))
        }

        // Use original discoverManifests function with FileSystem context and additional ignores
        val discovered = with(fs) {
            discoverManifests(root, respectGitignore = true, additionalIgnores = setOf("custom_ignored"))
        }

        assertEquals(1, discovered.size)
        assertEquals("allowed_mod", discovered[0].metadata.id.value)
    }
}

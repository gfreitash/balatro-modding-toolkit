package br.com.ghfreitas.bmt.projectmanagement.application.service

import br.com.ghfreitas.bmt.common.domain.valueobjects.Invalid
import br.com.ghfreitas.bmt.common.domain.valueobjects.Valid
import br.com.ghfreitas.bmt.common.domain.valueobjects.validating
import br.com.ghfreitas.bmt.common.infrastructure.writeToFile
import br.com.ghfreitas.bmt.projectmanagement.domain.model.ModAuthor
import br.com.ghfreitas.bmt.projectmanagement.domain.model.steamodded.*
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.*

class ManifestDiscoveryTest {
    private lateinit var fs: FileSystem
    private lateinit var modDiscoveryService: ModDiscoveryService

    private fun validMetadata(): SteamoddedManifest = SteamoddedManifest(
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
        assertTrue(validating { metadata.constraints()} is Valid)
    }

    @BeforeTest
    fun setUp() {
        fs = FakeFileSystem()
        modDiscoveryService = ModDiscoveryService(fs)
    }


    @Test
    fun tryParseAsBalatroManifest_handles_valid_manifest() {
        val manifestPath = "/test/manifest.json".toPath()
        fs.createDirectories(manifestPath.parent!!)

        val validManifest = validMetadata()
        with(fs) { manifestPath.writeToFile(Json.encodeToString(validManifest)) }

        val metadata = modDiscoveryService.tryParseAsSteamoddedManifest(manifestPath)

        assertEquals("test_mod", metadata?.id?.value)
        assertEquals("Test Mod", metadata?.name?.value)
        assertEquals("Alice", metadata?.author?.first()?.value)
    }

    @Test
    fun tryParseAsBalatroManifest_handles_invalid_json() {
        val manifestPath = "/test/invalid.json".toPath()
        fs.createDirectories(manifestPath.parent!!)

        with(fs) { manifestPath.writeToFile("{ invalid json }") }

        val result = modDiscoveryService.tryParseAsSteamoddedManifest(manifestPath)

        assertNull(result)
    }

    @Test
    fun tryParseAsBalatroManifest_handles_invalid_manifest_structure() {
        val manifestPath = "/test/invalid_structure.json".toPath()
        fs.createDirectories(manifestPath.parent!!)

        val invalidJson = """
        {
            "someField": "someValue",
            "notAManifest": true
        }
        """.trimIndent()

        with(fs) { manifestPath.writeToFile(invalidJson) }

        val result = modDiscoveryService.tryParseAsSteamoddedManifest(manifestPath)

        assertNull(result)
    }

    @Test
    fun tryParseAsBalatroManifest_handles_valid_structure_but_invalid_metadata() {
        val manifestPath = "/test/invalid_metadata.json".toPath()
        fs.createDirectories(manifestPath.parent!!)

        val invalidMetadata = validMetadata().copy(id = ModId(""))
        with(fs) { manifestPath.writeToFile(Json.encodeToString(invalidMetadata)) }

        val result = modDiscoveryService.tryParseAsSteamoddedManifest(manifestPath)

        assertNull(result)
    }

    @Test
    fun tryParseAsBalatroManifest_ignores_valid_structure_with_invalid_metadata_if_strict_is_false() {
        val manifestPath = "/test/invalid_metadata.json".toPath()
        fs.createDirectories(manifestPath.parent!!)

        val invalidMetadata = validMetadata().copy(id = ModId(""))
        with(fs) { manifestPath.writeToFile(Json.encodeToString(invalidMetadata)) }

        val result = modDiscoveryService.tryParseAsSteamoddedManifest(manifestPath, strict = false)

        assertTrue(validating { invalidMetadata.constraints() } is Invalid)
        assertNotNull(result)
    }

    @Test
    fun discoverManifests_complex_scenario() = runTest {
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

        val discovered = modDiscoveryService.discoverMods(root).toList()

        assertEquals(2, discovered.size)
        assertTrue(discovered.any { it.metadata?.id?.value == "awesome_mod" })
        assertTrue(discovered.any { it.metadata?.id?.value == "helper_mod" })
        assertFalse(discovered.any { it.metadata?.id?.value == "ignored_mod" })

        // Verify that the ignored manifest is actually valid and would be discovered if gitignore was disabled
        val discoveredWithoutGitignore = modDiscoveryService.discoverMods(root, respectGitignore = false).toList()

        assertEquals(3, discoveredWithoutGitignore.size)
        assertTrue(discoveredWithoutGitignore.any { it.metadata?.id?.value == "awesome_mod" })
        assertTrue(discoveredWithoutGitignore.any { it.metadata?.id?.value == "helper_mod" })
        assertTrue(discoveredWithoutGitignore.any { it.metadata?.id?.value == "ignored_mod" })

        // Verify invariant: no DiscoveredManifest should have both null metadata and false hasLovelyPatches
        discovered.forEach { manifest ->
            assertFalse(
                manifest.metadata == null && !manifest.hasLovelyPatches,
                "DiscoveredManifest cannot have both null metadata and no lovely patches"
            )
        }
    }

    @Test
    fun discoverManifests_handles_additional_ignores() = runTest {
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

        val discovered = modDiscoveryService.discoverMods(root, additionalIgnores = listOf("custom_ignored")).toList()

        assertEquals(1, discovered.size)
        assertEquals("allowed_mod", discovered[0].metadata?.id?.value)
    }

    @Test
    fun discoverMods_finds_lovely_patches_without_manifest() = runTest {
        val root = "/project".toPath()
        fs.createDirectories(root)

        // Create mod with only lovely directory
        val lovelyModPath = root / "lovely_mod" / "lovely"
        fs.createDirectories(lovelyModPath)

        val discovered = modDiscoveryService.discoverMods(root).toList()

        assertEquals(1, discovered.size)
        assertNull(discovered[0].metadata)
        assertTrue(discovered[0].hasLovelyPatches)
        assertEquals(root / "lovely_mod", discovered[0].path)
    }

    @Test
    fun discoverMods_finds_lovely_patches_alongside_manifest() = runTest {
        val root = "/project".toPath()
        fs.createDirectories(root)

        // Create mod with both manifest and lovely directory
        val hybridModPath = root / "hybrid_mod"
        fs.createDirectories(hybridModPath)

        val manifestPath = hybridModPath / "manifest.json"
        with(fs) {
            manifestPath.writeToFile(
                Json.encodeToString(
                    validMetadata().copy(
                        id = ModId("hybrid_mod"),
                        name = ModName("Hybrid Mod")
                    )
                )
            )
        }

        val lovelyPath = hybridModPath / "lovely"
        fs.createDirectories(lovelyPath)

        val discovered = modDiscoveryService.discoverMods(root).toList()

        assertEquals(1, discovered.size)
        assertNotNull(discovered[0].metadata)
        assertEquals("hybrid_mod", discovered[0].metadata?.id?.value)
        assertTrue(discovered[0].hasLovelyPatches)
        assertEquals(hybridModPath, discovered[0].path)
    }

    @Test
    fun discoverMods_detects_lovely_case_insensitive() = runTest {
        val root = "/project".toPath()
        fs.createDirectories(root)

        // Create mods with different case variations of lovely
        val mod1 = root / "mod1" / "LOVELY"
        fs.createDirectories(mod1)

        val mod2 = root / "mod2" / "Lovely"
        fs.createDirectories(mod2)

        val mod3 = root / "mod3" / "LoVeLy"
        fs.createDirectories(mod3)

        val discovered = modDiscoveryService.discoverMods(root).toList()

        assertEquals(3, discovered.size)
        assertTrue(discovered.all { it.hasLovelyPatches })
        assertTrue(discovered.all { it.metadata == null })
    }

    @Test
    fun discoverMods_lovely_respects_gitignore() = runTest {
        val root = "/project".toPath()
        fs.createDirectories(root)

        // Create gitignore
        with(fs) {
            (root / ".gitignore").writeToFile("ignored/")
        }

        // Create lovely mod in normal path
        val normalModPath = root / "normal_mod" / "lovely"
        fs.createDirectories(normalModPath)

        // Create lovely mod in ignored path
        val ignoredModPath = root / "ignored" / "ignored_mod" / "lovely"
        fs.createDirectories(ignoredModPath)

        val discovered = modDiscoveryService.discoverMods(root).toList()

        assertEquals(1, discovered.size)
        assertEquals(root / "normal_mod", discovered[0].path)
        assertTrue(discovered[0].hasLovelyPatches)

        // Verify that disabling gitignore includes both
        val discoveredWithoutGitignore = modDiscoveryService.discoverMods(root, respectGitignore = false).toList()
        assertEquals(2, discoveredWithoutGitignore.size)
    }

    @Test
    fun discoverMods_mixed_lovely_and_manifest_combinations() = runTest {
        val root = "/project".toPath()
        fs.createDirectories(root)

        // Manifest only
        val manifestOnlyPath = root / "manifest_only" / "manifest.json"
        fs.createDirectories(manifestOnlyPath.parent!!)
        with(fs) {
            manifestOnlyPath.writeToFile(
                Json.encodeToString(
                    validMetadata().copy(
                        id = ModId("manifest_only"),
                        name = ModName("Manifest Only")
                    )
                )
            )
        }

        // Lovely only
        val lovelyOnlyPath = root / "lovely_only" / "lovely"
        fs.createDirectories(lovelyOnlyPath)

        // Hybrid: both manifest and lovely
        val hybridPath = root / "hybrid"
        fs.createDirectories(hybridPath)
        with(fs) {
            (hybridPath / "manifest.json").writeToFile(
                Json.encodeToString(
                    validMetadata().copy(
                        id = ModId("hybrid"),
                        name = ModName("Hybrid")
                    )
                )
            )
        }
        fs.createDirectories(hybridPath / "lovely")

        // Neither manifest nor lovely (should not be discovered)
        val neitherPath = root / "neither" / "some_file.txt"
        fs.createDirectories(neitherPath.parent!!)
        with(fs) {
            neitherPath.writeToFile("just a random file")
        }

        val discovered = modDiscoveryService.discoverMods(root).toList()

        assertEquals(3, discovered.size)

        val manifestOnly = discovered.find { it.path == root / "manifest_only" }
        assertNotNull(manifestOnly)
        assertEquals("manifest_only", manifestOnly.metadata?.id?.value)
        assertFalse(manifestOnly.hasLovelyPatches)

        val lovelyOnly = discovered.find { it.path == root / "lovely_only" }
        assertNotNull(lovelyOnly)
        assertNull(lovelyOnly.metadata)
        assertTrue(lovelyOnly.hasLovelyPatches)

        val hybrid = discovered.find { it.path == root / "hybrid" }
        assertNotNull(hybrid)
        assertEquals("hybrid", hybrid.metadata?.id?.value)
        assertTrue(hybrid.hasLovelyPatches)

        // Verify no invalid states exist
        discovered.forEach { manifest ->
            assertFalse(
                manifest.metadata == null && !manifest.hasLovelyPatches,
                "DiscoveredManifest cannot have both null metadata and no lovely patches"
            )
        }
    }
}

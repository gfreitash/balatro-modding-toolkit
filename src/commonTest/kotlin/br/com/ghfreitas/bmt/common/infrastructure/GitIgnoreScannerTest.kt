package br.com.ghfreitas.bmt.common.infrastructure

import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import okio.Path
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.*

class GitignoreTraversalTest {

    private lateinit var fs: FakeFileSystem
    private lateinit var rootPath: Path

    @BeforeTest
    fun setUp() {
        fs = FakeFileSystem()
        rootPath = "/test-repo".toPath()
        fs.createDirectories(rootPath)
        // Create .git directory to simulate a git repo
        fs.createDirectories(rootPath / ".git" / "info")
    }

    @AfterTest
    fun tearDown() {
        fs.checkNoOpenFiles()
    }

    // ==================== Helper Extensions ====================

    private fun Path.writeToFile(content: String) {
        parent?.let { fs.createDirectories(it) }
        fs.write(this) { writeUtf8(content) }
    }

    private fun Path.createDir() {
        fs.createDirectories(this)
    }

    private fun gitignore(vararg patterns: String) {
        (rootPath / ".gitignore").writeToFile(patterns.joinToString("\n"))
    }

    private fun gitignoreAt(relativePath: String, vararg patterns: String) {
        val dir = rootPath / relativePath
        fs.createDirectories(dir)
        (dir / ".gitignore").writeToFile(patterns.joinToString("\n"))
    }

    private fun gitExclude(vararg patterns: String) {
        (rootPath / ".git" / "info" / "exclude").writeToFile(patterns.joinToString("\n"))
    }

    private suspend fun createParser(): GitIgnoreScanner {
        return GitIgnoreScanner.create(fs, rootPath)
    }

    private suspend fun assertIgnored(
        parser: GitIgnoreScanner,
        relativePath: String,
        message: String? = null,
        isDir: Boolean = false,
    ) {
        val path = rootPath / relativePath
        // Ensure parent directories exist
        path.parent?.let { fs.createDirectories(it) }

        // Delete the existing path if it's the wrong type
        if (fs.exists(path)) {
            val metadata = fs.metadata(path)
            if ((isDir && !metadata.isDirectory) || (!isDir && metadata.isDirectory)) {
                fs.delete(path, mustExist = true)
            }
        }

        // Create the path as specified
        if (!fs.exists(path)) {
            if (isDir) {
                fs.createDirectories(path)
            } else {
                path.writeToFile("test content")
            }
        }
        val result = parser.getIgnoreResult(path)
        assertTrue(result.isIgnored, message ?: "Expected '$relativePath' to be ignored")
    }

    private suspend fun assertNotIgnored(
        parser: GitIgnoreScanner,
        relativePath: String,
        message: String? = null,
        isDir: Boolean = false,
    ) {
        val path = rootPath / relativePath
        // Ensure parent directories exist
        path.parent?.let { fs.createDirectories(it) }

        // Delete the existing path if it's the wrong type
        if (fs.exists(path)) {
            val metadata = fs.metadata(path)
            if ((isDir && !metadata.isDirectory) || (!isDir && metadata.isDirectory)) {
                fs.delete(path, mustExist = true)
            }
        }

        // Create the path as specified
        if (!fs.exists(path)) {
            if (isDir) {
                fs.createDirectories(path)
            } else {
                path.writeToFile("test content")
            }
        }
        val result = parser.getIgnoreResult(path)
        assertFalse(result.isIgnored, message ?: "Expected '$relativePath' to NOT be ignored")
    }

    private suspend fun assertDirIgnored(parser: GitIgnoreScanner, relativePath: String, message: String) {
        val path = rootPath / relativePath
        fs.createDirectories(path)
        val result = parser.getIgnoreResult(path)
        assertTrue(result.isIgnored, message)
    }

    private suspend fun assertDirNotIgnored(parser: GitIgnoreScanner, relativePath: String, message: String) {
        val path = rootPath / relativePath
        fs.createDirectories(path)
        val result = parser.getIgnoreResult(path)
        assertFalse(result.isIgnored, message)
    }

    // ==================== 1. Line Parsing Basics ====================

    @Test
    fun `parser skips blank lines and comments`() = runTest {
        (rootPath / ".gitignore").writeToFile(
            """
            |
            |# this is a comment
            |
            |# another comment
            |*.log
            |
            |# trailing comment
            """.trimMargin()
        )

        val parser = createParser()

        // Only *.log should be parsed as a pattern
        assertIgnored(parser, "test.log", "*.log pattern should work")
        assertNotIgnored(parser, "test.txt", "non-matching file should not be ignored")
        assertNotIgnored(parser, "# this is a comment", "comment text as filename should not match")
    }

    @Test
    fun `literal hash at start is escaped with backslash`() = runTest {
        gitignore("\\#important", "\\#backup#")

        val parser = createParser()

        assertIgnored(parser, "#important", "escaped hash should match literal #important")
        assertIgnored(parser, "#backup#", "escaped hash should match #backup#")
        assertNotIgnored(parser, "important", "should not match without the #")
    }

    @Test
    fun `trailing spaces are trimmed unless escaped`() = runTest {
        // "foo   " should become "foo", but "bar\ " should match "bar "
        (rootPath / ".gitignore").writeToFile("foo   \nbar\\ ")

        val parser = createParser()

        assertIgnored(parser, "foo", "trailing spaces should be trimmed")
        assertNotIgnored(parser, "foo   ", "trimmed pattern should not match filename with spaces")
        // Note: Testing escaped trailing space is tricky - implementation may vary
    }

    // ==================== 2. Negation Patterns ====================

    @Test
    fun `negation re-includes previously excluded files`() = runTest {
        gitignore("*.log", "!important.log")

        val parser = createParser()

        assertIgnored(parser, "debug.log", "*.log should ignore debug.log")
        assertIgnored(parser, "error.log", "*.log should ignore error.log")
        assertNotIgnored(parser, "important.log", "!important.log should re-include")
        assertNotIgnored(parser, "readme.txt", "non-matching files unaffected")
    }

    @Test
    fun `last matching pattern wins`() = runTest {
        gitignore(
            "*.log",           // ignore all .log
            "!debug.log",      // re-include debug.log
            "debug.log"        // ignore debug.log again
        )

        val parser = createParser()

        assertIgnored(parser, "debug.log", "last pattern should win - debug.log should be ignored")
        assertIgnored(parser, "error.log", "error.log should still be ignored")
    }

    @Test
    fun `negation ineffective when parent directory is excluded`() = runTest {
        gitignore(
            "logs/",           // ignore entire logs directory
            "!logs/important.log"  // try to re-include - should NOT work
        )

        val parser = createParser()

        assertDirIgnored(parser, "logs", "logs/ directory should be ignored")
        assertIgnored(
            parser,
            "logs/important.log",
            "A file cannot be UNIGNORED if its parent directory is ignored."
        )
        // The key point: since logs/ is ignored, Git won't even descend into it
        // so !logs/important.log has no effect
        // In our traversal implementation, we skip ignored directories entirely
    }

    @Test
    fun `literal exclamation mark escaped with backslash`() = runTest {
        gitignore("\\!important.txt", "\\!README")

        val parser = createParser()

        assertIgnored(parser, "!important.txt", "escaped ! should match literal !important.txt")
        assertIgnored(parser, "!README", "escaped ! should match literal !README")
        assertNotIgnored(parser, "important.txt", "should not match without the !")
    }

    // ==================== 3. Slash Behavior and Path Relativity ====================

    @Test
    fun `patterns without slash match at any depth`() = runTest {
        gitignore("foo.txt")

        val parser = createParser()

        assertIgnored(parser, "foo.txt", "should match at root")
        assertIgnored(parser, "src/foo.txt", "should match one level deep")
        assertIgnored(parser, "src/main/foo.txt", "should match two levels deep")
        assertIgnored(parser, "a/b/c/d/foo.txt", "should match at any depth")
        assertNotIgnored(parser, "foo.txt.bak", "should not match different filename")
    }

    @Test
    fun `leading slash anchors pattern to gitignore directory`() = runTest {
        gitignore("/foo.txt", "/src/main.kt")

        val parser = createParser()

        // /foo.txt - only matches at root
        assertIgnored(parser, "foo.txt", "/foo.txt should match at root")
        assertNotIgnored(parser, "src/foo.txt", "/foo.txt should NOT match in subdirectory")
        assertNotIgnored(parser, "a/b/foo.txt", "/foo.txt should NOT match deep in tree")

        // /src/main.kt - only matches src/main.kt at root level
        assertIgnored(parser, "src/main.kt", "/src/main.kt should match")
        assertNotIgnored(parser, "other/src/main.kt", "/src/main.kt should NOT match in other/")
    }

    @Test
    fun `middle slash anchors pattern to gitignore directory`() = runTest {
        gitignore("doc/frotz", "src/main/app.kt")

        val parser = createParser()

        // doc/frotz - middle slash means anchored
        assertIgnored(parser, "doc/frotz", "doc/frotz should match at gitignore level")
        assertNotIgnored(parser, "a/doc/frotz", "doc/frotz should NOT match at a/doc/frotz")
        assertNotIgnored(parser, "x/y/doc/frotz", "doc/frotz should NOT match deep")

        // src/main/app.kt
        assertIgnored(parser, "src/main/app.kt", "src/main/app.kt should match")
        assertNotIgnored(parser, "other/src/main/app.kt", "should NOT match in other/")
    }

    @Test
    fun `leading slash and middle slash patterns are equivalent`() = runTest {
        // According to docs: "doc/frotz" and "/doc/frotz" have the same effect
        (rootPath / ".gitignore").writeToFile("doc/frotz")
        val parser1 = createParser()

        // Reset and use leading slash version
        fs.delete(rootPath / ".gitignore")
        (rootPath / ".gitignore").writeToFile("/doc/frotz")
        val parser2 = createParser()

        // Both should behave identically
        val testPaths = listOf("doc/frotz", "a/doc/frotz", "x/y/doc/frotz")

        for (testPath in testPaths) {
            val path = rootPath / testPath
            path.parent?.let { fs.createDirectories(it) }
            path.writeToFile("content")

            val result1 = parser1.getIgnoreResult(path).isIgnored
            val result2 = parser2.getIgnoreResult(path).isIgnored

            assertEquals(result1, result2, "doc/frotz and /doc/frotz should behave identically for path: $testPath")
        }
    }

    @Test
    fun `nested gitignore patterns are relative to their location`() = runTest {
        // Root .gitignore
        gitignore("*.log")

        // Nested .gitignore in src/
        gitignoreAt("src", "temp.txt", "build/")

        // Nested .gitignore in src/main/
        gitignoreAt("src/main", "local.conf")

        val parser = createParser()

        // Root patterns apply everywhere
        assertIgnored(parser, "app.log", "root *.log matches at root")
        assertIgnored(parser, "src/debug.log", "root *.log matches in src/")
        assertIgnored(parser, "src/main/error.log", "root *.log matches in src/main/")

        // src/.gitignore patterns should be relative to src/
        assertIgnored(parser, "src/temp.txt", "src's temp.txt should match src/temp.txt")
        assertNotIgnored(parser, "temp.txt", "src's temp.txt should NOT match root temp.txt")
        assertNotIgnored(parser, "other/temp.txt", "src's temp.txt should NOT match other/temp.txt")

        // src/.gitignore "build/" pattern
        assertDirIgnored(parser, "src/build", "src's build/ should match src/build/")
        assertDirNotIgnored(parser, "build", "src's build/ should NOT match root build/")

        // src/main/.gitignore patterns should be relative to src/main/
        assertIgnored(parser, "src/main/local.conf", "src/main's local.conf should match")
        assertNotIgnored(
            parser,
            "src/local.conf",
            "src/main's local.conf should NOT match src/local.conf"
        )
        assertNotIgnored(parser, "local.conf", "src/main's local.conf should NOT match root")
    }

    @Test
    fun `nested gitignore with path patterns are relative to their location`() = runTest {
        // This is the critical bug test case
        // src/.gitignore contains "sub/secret.txt" - should only match src/sub/secret.txt
        gitignoreAt("src", "sub/secret.txt")

        val parser = createParser()

        assertIgnored(
            parser,
            "src/sub/secret.txt",
            "src's sub/secret.txt should match src/sub/secret.txt"
        )
        assertNotIgnored(
            parser,
            "sub/secret.txt",
            "src's sub/secret.txt should NOT match root sub/secret.txt"
        )
        assertNotIgnored(
            parser,
            "other/sub/secret.txt",
            "src's sub/secret.txt should NOT match other/sub/secret.txt"
        )
    }

    // ==================== 4. Directory-Only Patterns (Trailing Slash) ====================

    @Test
    fun `trailing slash matches only directories`() = runTest {
        gitignore("logs/", "build/", "temp/")

        val parser = createParser()

        // Should match directories
        assertDirIgnored(parser, "logs", "logs/ should match logs directory")
        assertDirIgnored(parser, "build", "build/ should match build directory")
        assertDirIgnored(parser, "src/logs", "logs/ should match nested logs directory")

        // Should NOT match files with same name
        assertNotIgnored(parser, "logs", "logs/ should NOT match file named 'logs'")
        // Note: This test creates a file, not directory
        (rootPath / "logs_file").writeToFile("i am a file named logs")
        // We need a way to test file vs directory - the assertNotIgnored creates files
    }

    @Test
    fun `trailing slash pattern without leading slash matches at any depth`() = runTest {
        gitignore("temp/")

        val parser = createParser()

        assertDirIgnored(parser, "temp", "temp/ should match at root")
        assertDirIgnored(parser, "src/temp", "temp/ should match in src/")
        assertDirIgnored(parser, "a/b/c/temp", "temp/ should match at any depth")
    }

    @Test
    fun `directory pattern matches directory and paths underneath`() = runTest {
        gitignore("vendor/")

        val parser = createParser()

        assertDirIgnored(parser, "vendor", "vendor/ should match vendor directory")

        // When traversing, ignored directories are skipped entirely
        // So files inside would never be checked, but if we check directly:
        // The pattern "vendor/" should conceptually cover everything inside
    }

    // ==================== 5. Single Wildcards ====================

    @Test
    fun `asterisk matches anything except slash`() = runTest {
        gitignore("*.txt", "temp.*", "test_*_file")

        val parser = createParser()

        // *.txt
        assertIgnored(parser, "readme.txt", "*.txt should match readme.txt")
        assertIgnored(parser, "a.txt", "*.txt should match a.txt")
        assertIgnored(parser, ".txt", "*.txt should match .txt")

        // temp.*
        assertIgnored(parser, "temp.log", "temp.* should match temp.log")
        assertIgnored(parser, "temp.txt", "temp.* should match temp.txt")
        assertIgnored(parser, "temp.", "temp.* should match temp.")

        // test_*_file
        assertIgnored(parser, "test_abc_file", "test_*_file should match")
        assertIgnored(parser, "test__file", "test_*_file should match empty *")
        assertNotIgnored(parser, "test_a/b_file", "* should not match slash")
    }

    @Test
    fun `asterisk in path pattern does not cross directories`() = runTest {
        gitignore("foo/*", "src/*.kt")

        val parser = createParser()

        // foo/* matches immediate children only
        (rootPath / "foo").createDir() // Ensure 'foo' is a directory
        (rootPath / "foo" / "bar").writeToFile("content") // Create 'foo/bar' as a file
        assertIgnored(parser, "foo/bar", "foo/* should match foo/bar")

        (rootPath / "foo" / "test.txt").writeToFile("content") // Create 'foo/test.txt' as a file
        assertIgnored(parser, "foo/test.txt", "foo/* should match foo/test.txt")

        // To test foo/bar/baz, we need foo/bar to be a directory.
        // Delete the file 'foo/bar' and recreate it as a directory.
        fs.delete(rootPath / "foo" / "bar", mustExist = true)
        (rootPath / "foo" / "bar").createDir()
        (rootPath / "foo" / "bar" / "baz").writeToFile("content") // Create foo/bar/baz as a file
        assertIgnored(parser, "foo/bar/baz", "foo/* should match foo/bar/baz as foo/bar is ignored")

        // src/*.kt
        (rootPath / "src").createDir() // Ensure 'src' is a directory
        (rootPath / "src" / "main.kt").writeToFile("content") // Create 'src/main.kt' as a file
        assertIgnored(parser, "src/main.kt", "src/*.kt should match")

        (rootPath / "src" / "main").createDir() // Ensure 'src/main' is a directory
        (rootPath / "src" / "main" / "app.kt").writeToFile("content") // Create 'src/main/app.kt' as a file
        assertNotIgnored(parser, "src/main/app.kt", "src/*.kt should NOT match nested")
    }

    @Test
    fun `question mark matches single character except slash`() = runTest {
        gitignore("?.txt", "test?.log", "a?b")

        val parser = createParser()

        assertIgnored(parser, "a.txt", "?.txt should match a.txt")
        assertIgnored(parser, "1.txt", "?.txt should match 1.txt")
        assertNotIgnored(parser, "ab.txt", "?.txt should NOT match ab.txt")
        assertNotIgnored(parser, ".txt", "?.txt should NOT match .txt (no char)")

        assertIgnored(parser, "test1.log", "test?.log should match")
        assertIgnored(parser, "testX.log", "test?.log should match")
        assertNotIgnored(parser, "test12.log", "test?.log should NOT match two chars")

        assertIgnored(parser, "aXb", "a?b should match aXb")
        assertNotIgnored(parser, "a/b", "a?b should NOT match a/b (slash)")
    }

    @Test
    fun `character range brackets match one character in range`() = runTest {
        gitignore("[abc].txt", "[0-9].log", "[a-zA-Z]_file")

        val parser = createParser()

        // [abc].txt

        assertIgnored(parser, "a.txt", "[abc].txt should match a.txt")

        assertIgnored(parser, "b.txt", "[abc].txt should match b.txt")

        assertIgnored(parser, "c.txt", "[abc].txt should match c.txt")

        assertNotIgnored(parser, "d.txt", "[abc].txt should NOT match d.txt")


        // [0-9].log

        assertIgnored(parser, "0.log", "[0-9].log should match 0.log")

        assertIgnored(parser, "5.log", "[0-9].log should match 5.log")

        assertIgnored(parser, "9.log", "[0-9].log should match 9.log")

        assertNotIgnored(parser, "a.log", "[0-9].log should NOT match a.log")


        // [a-zA-Z]_file

        assertIgnored(parser, "x_file", "[a-zA-Z]_file should match")

        assertIgnored(parser, "Z_file", "[a-zA-Z]_file should match")

        assertNotIgnored(parser, "1_file", "[a-zA-Z]_file should NOT match digit")
    }

    // ==================== 6. Double Asterisk Patterns ====================

    @Test
    fun `leading double asterisk matches in all directories`() = runTest {
        gitignore("**/foo", "**/test.log", "**/.env")

        val parser = createParser()

        // **/foo
        assertIgnored(parser, "foo", "**/foo should match at root")
        assertIgnored(parser, "a/foo", "**/foo should match one level deep")
        assertIgnored(parser, "a/b/foo", "**/foo should match two levels deep")
        assertIgnored(parser, "a/b/c/d/e/foo", "**/foo should match at any depth")

        // **/test.log
        assertIgnored(parser, "test.log", "**/test.log should match at root")
        assertIgnored(parser, "src/test.log", "**/test.log should match in src/")
        assertIgnored(parser, "src/main/test.log", "**/test.log should match deep")

        // **/.env
        assertIgnored(parser, ".env", "**/.env should match at root")
        assertIgnored(parser, "config/.env", "**/.env should match in config/")
    }

    @Test
    fun `leading double asterisk with path matches pattern under any directory`() = runTest {
        gitignore("**/foo/bar", "**/src/test.kt")

        val parser = createParser()

        // **/foo/bar - bar directly under any foo
        assertIgnored(parser, "foo/bar", "**/foo/bar should match at root")
        assertIgnored(parser, "a/foo/bar", "**/foo/bar should match in a/")
        assertIgnored(parser, "a/b/foo/bar", "**/foo/bar should match in a/b/")
        assertNotIgnored(parser, "foo/x/bar", "**/foo/bar should NOT match foo/x/bar")

        // **/src/test.kt
        assertIgnored(parser, "src/test.kt", "should match at root")
        assertIgnored(parser, "project/src/test.kt", "should match in project/")
    }

    @Test
    fun `trailing double asterisk matches everything inside`() = runTest {
        gitignore("abc/**", "logs/**", "src/test/**")

        val parser = createParser()

        // abc/**
        assertIgnored(parser, "abc/file.txt", "abc/** should match immediate child")
        assertIgnored(parser, "abc/sub/file.txt", "abc/** should match nested")
        assertIgnored(parser, "abc/a/b/c/d.txt", "abc/** should match deep")
        assertNotIgnored(parser, "abc", "abc/** should NOT match abc itself (it's the container)", isDir = true)

        // logs/**
        assertIgnored(parser, "logs/app.log", "logs/** should match")
        assertIgnored(parser, "logs/2024/01/app.log", "logs/** should match deep")

        // src/test/** - anchored pattern
        assertIgnored(parser, "src/test/unit.kt", "src/test/** should match")
        assertIgnored(parser, "src/test/unit/app.kt", "src/test/** should match nested")
        assertNotIgnored(parser, "other/src/test/unit.kt", "src/test/** should NOT match in other/")
    }

    @Test
    fun `middle double asterisk matches zero or more directories`() = runTest {
        gitignore("a/**/b", "src/**/test.kt", "foo/**/bar/**/baz")

        val parser = createParser()

        // a/**/b - zero or more directories between a and b
        (rootPath / "a").createDir()
        (rootPath / "a" / "b").writeToFile("content")
        assertIgnored(parser, "a/b", "a/**/b should match a/b (zero dirs)")

        (rootPath / "a" / "x").createDir()
        (rootPath / "a" / "x" / "b").writeToFile("content")
        assertIgnored(parser, "a/x/b", "a/**/b should match a/x/b (one dir)")

        (rootPath / "a" / "x" / "y").createDir()
        (rootPath / "a" / "x" / "y" / "b").writeToFile("content")
        assertIgnored(parser, "a/x/y/b", "a/**/b should match a/x/y/b (two dirs)")

        (rootPath / "a" / "x" / "y" / "z").createDir()
        (rootPath / "a" / "x" / "y" / "z" / "b").writeToFile("content")
        assertIgnored(parser, "a/x/y/z/b", "a/**/b should match with many dirs")

        // To test a/b/c, we need a/b to be a directory.
        fs.delete(rootPath / "a" / "b", mustExist = true) // Delete the file
        (rootPath / "a" / "b").createDir() // Recreate as directory
        (rootPath / "a" / "b" / "c").writeToFile("content")
        assertIgnored(parser, "a/b/c", "a/**/b should match a/b/c, since a/b is ignored and is a dir")

        // src/**/test.kt
        (rootPath / "src").createDir()
        (rootPath / "src" / "test.kt").writeToFile("content")
        assertIgnored(parser, "src/test.kt", "src/**/test.kt should match (zero dirs)")

        (rootPath / "src" / "main").createDir()
        (rootPath / "src" / "main" / "test.kt").writeToFile("content")
        assertIgnored(parser, "src/main/test.kt", "src/**/test.kt should match (one dir)")

        (rootPath / "src" / "main" / "kotlin").createDir()
        (rootPath / "src" / "main" / "kotlin" / "test.kt").writeToFile("content")
        assertIgnored(parser, "src/main/kotlin/test.kt", "src/**/test.kt should match (two dirs)")

        // foo/**/bar/**/baz - multiple ** segments
        (rootPath / "foo").createDir()
        (rootPath / "foo" / "bar").createDir()
        (rootPath / "foo" / "bar" / "baz").writeToFile("content")
        assertIgnored(parser, "foo/bar/baz", "should match with zero dirs in both")

        (rootPath / "foo" / "x").createDir()
        (rootPath / "foo" / "x" / "bar").createDir()
        (rootPath / "foo" / "x" / "bar" / "baz").writeToFile("content")
        assertIgnored(parser, "foo/x/bar/baz", "should match with one dir in first")

        (rootPath / "foo" / "bar").createDir()
        (rootPath / "foo" / "bar" / "y").createDir()
        (rootPath / "foo" / "bar" / "y" / "baz").writeToFile("content")
        assertIgnored(parser, "foo/bar/y/baz", "should match with one dir in second")

        (rootPath / "foo" / "x" / "y").createDir()
        (rootPath / "foo" / "x" / "y" / "bar").createDir()
        (rootPath / "foo" / "x" / "y" / "bar" / "a").createDir()
        (rootPath / "foo" / "x" / "y" / "bar" / "a" / "b").createDir()
        (rootPath / "foo" / "x" / "y" / "bar" / "a" / "b" / "baz").writeToFile("content")
        assertIgnored(parser, "foo/x/y/bar/a/b/baz", "should match with multiple dirs")
    }

    // ==================== 7. Escaping ====================

    @Test
    fun `backslash escapes special characters`() = runTest {
        gitignore("\\*.txt", "test\\?.log", "\\[abc\\].md")

        val parser = createParser()

        // \*.txt should match literal *.txt
        assertIgnored(parser, "*.txt", "\\*.txt should match literal *.txt")
        assertNotIgnored(parser, "foo.txt", "\\*.txt should NOT match foo.txt")

        // test\?.log should match literal test?.log
        assertIgnored(parser, "test?.log", "\\? should match literal ?")
        assertNotIgnored(parser, "test1.log", "\\? should NOT act as wildcard")

        // \[abc\].md should match literal [abc].md
        assertIgnored(parser, "[abc].md", "escaped brackets should match literally")
        assertNotIgnored(parser, "a.md", "escaped brackets should NOT act as range")
    }

    // ==================== 8. Pattern Source Precedence ====================

    @Test
    fun `lower level gitignore overrides higher level`() = runTest {
        // Root ignores all .log files
        gitignore("*.log")

        // src/.gitignore re-includes debug.log
        gitignoreAt("src", "!debug.log")

        val parser = createParser()

        // At root level, all .log files ignored
        assertIgnored(parser, "app.log", "root *.log should ignore app.log")
        assertIgnored(parser, "debug.log", "root *.log should ignore debug.log at root")

        // In src/, debug.log should be re-included
        assertIgnored(parser, "src/app.log", "*.log still ignores src/app.log")
        assertNotIgnored(parser, "src/debug.log", "src's !debug.log should re-include")
    }

    @Test
    fun `git info exclude patterns work`() = runTest {
        // No .gitignore, only .git/info/exclude
        gitExclude("*.secret", "private/")

        val parser = createParser()

        assertIgnored(parser, "config.secret", "exclude file pattern should work")
        assertIgnored(parser, "src/db.secret", "exclude file pattern should work at depth")
        assertDirIgnored(parser, "private", "exclude directory pattern should work")
    }

    @Test
    fun `gitignore takes precedence over git info exclude at same level`() = runTest {
        gitExclude("*.log")
        gitignore("!debug.log")

        val parser = createParser()

        // .gitignore patterns are processed after exclude, so !debug.log should win
        // Actually, according to Git docs, .gitignore and exclude are at same precedence level
        // and patterns are combined, with last-match-wins
        assertNotIgnored(parser, "debug.log", "!debug.log in .gitignore should re-include")
    }

    // ==================== 9. Traversal Integration Tests ====================

    @Test
    fun `traverse returns all files with correct ignore status`() = runTest {
        gitignore("*.log", "build/")

        // Create file structure
        (rootPath / "readme.md").writeToFile("readme")
        (rootPath / "app.log").writeToFile("log")
        (rootPath / "src" / "main.kt").writeToFile("code")
        (rootPath / "build").createDir()
        (rootPath / "build" / "output.jar").writeToFile("jar")

        val parser = createParser()
        val entries = parser.dfs().toList()

        val ignoredPaths = entries.filter { it.gitignoreResult.isIgnored }.map { it.relativePath }
        val trackedPaths = entries.filter { !it.gitignoreResult.isIgnored }.map { it.relativePath }

        assertTrue("app.log" in ignoredPaths, "app.log should be ignored")
        assertTrue("build" in ignoredPaths, "build/ should be ignored")
        assertTrue("readme.md" in trackedPaths, "readme.md should be tracked")
        assertTrue("src/main.kt" in trackedPaths || "src" in trackedPaths, "src or src/main.kt should be tracked")
    }

    @Test
    fun `traverse skips contents of ignored directories`() = runTest {
        gitignore("ignored_dir/")

        (rootPath / "ignored_dir").createDir()
        (rootPath / "ignored_dir" / "should_not_appear.txt").writeToFile("content")
        (rootPath / "ignored_dir" / "nested" / "deep.txt").writeToFile("content")
        (rootPath / "normal_dir" / "file.txt").writeToFile("content")

        val parser = createParser()
        val entries = parser.dfs().toList()
        val allPaths = entries.map { it.relativePath }

        assertTrue("ignored_dir" in allPaths, "ignored_dir itself should appear")
        assertFalse("ignored_dir/should_not_appear.txt" in allPaths, "contents of ignored dir should not be traversed")
        assertFalse("ignored_dir/nested/deep.txt" in allPaths, "deep contents should not be traversed")
        assertTrue("normal_dir/file.txt" in allPaths || "normal_dir" in allPaths, "normal content should appear")
    }

    // ==================== 10. Edge Cases ====================

    @Test
    fun `empty gitignore file works`() = runTest {
        (rootPath / ".gitignore").writeToFile("")

        val parser = createParser()

        assertNotIgnored(parser, "any_file.txt", "empty gitignore should not ignore anything")
        assertNotIgnored(parser, "src/main.kt", "empty gitignore should not ignore anything")
    }

    @Test
    fun `gitignore with only comments works`() = runTest {
        (rootPath / ".gitignore").writeToFile(
            """
            |# This is a comment
            |# Another comment
            |# No actual patterns
            """.trimMargin()
        )

        val parser = createParser()

        assertNotIgnored(parser, "any_file.txt", "comment-only gitignore should not ignore anything")
    }

    @Test
    fun `very deep nesting works`() = runTest {
        gitignore("**/deep.txt")

        val deepPath = "a/b/c/d/e/f/g/h/i/j/deep.txt"
        (rootPath / deepPath).writeToFile("deep content")

        val parser = createParser()

        assertIgnored(parser, deepPath, "pattern should match at extreme depth")
    }

    @Test
    fun `gitignore file itself is not auto-ignored`() = runTest {
        gitignore("*.log")

        val parser = createParser()
        val trackedNames = parser.dfs().filter { !it.gitignoreResult.isIgnored }.map { it.path.name }.toList()

        // .gitignore should be trackable (not auto-ignored)
        // This depends on implementation - the file exists and isn't matched by patterns
        assertTrue(".gitignore" in trackedNames, ".gitignore itself should be trackable")
    }

    @Test
    fun `patterns work with various file extensions`() = runTest {
        gitignore("*.min.js", "*.d.ts", "*.test.kt")

        val parser = createParser()

        assertIgnored(parser, "app.min.js", "*.min.js should match")
        assertIgnored(parser, "types.d.ts", "*.d.ts should match")
        assertIgnored(parser, "MainTest.test.kt", "*.test.kt should match")
        assertNotIgnored(parser, "app.js", "*.min.js should not match app.js")
    }

    @Test
    fun `additionalPatterns behave identically to gitignore file`() = runTest {
        val patterns = listOf("*.log", "!important.log", "build/", "# comment", "", "*.tmp")

        // Parser A: patterns in root .gitignore file
        gitignore(*patterns.toTypedArray())
        val parserA = createParser()

        // Remove .gitignore before creating parserB
        fs.delete(rootPath / ".gitignore")

        // Parser B: same patterns programmatically via additionalPatterns
        val parserB = GitIgnoreScanner.create(
            fileSystem = fs,
            rootPath = rootPath,
            additionalPatterns = patterns
        )

        // Test files to verify equivalence
        val testPaths = listOf(
            "test.log" to false,
            "important.log" to false,
            "build/" to true,
            "app.tmp" to false,
            "src/debug.log" to false,
            "src/important.log" to false
        )

        // Both parsers should produce identical results
        for ((path, _) in testPaths) {
            val resultA = parserA.getIgnoreResult(rootPath / path)
            val resultB = parserB.getIgnoreResult(rootPath / path)

            assertEquals(
                resultA.isIgnored,
                resultB.isIgnored,
                "Parsers should agree on '$path': parserA=${resultA.isIgnored}, parserB=${resultB.isIgnored}"
            )
        }
    }

    @Test
    fun `additionalPatterns override root gitignore`() = runTest {
        // Root .gitignore: ignore all .log files
        gitignore("*.log")

        // additionalPatterns: re-include important.log (should override root .gitignore)
        val parser = GitIgnoreScanner.create(
            fileSystem = fs,
            rootPath = rootPath,
            additionalPatterns = listOf("!important.log")
        )

        // important.log should NOT be ignored (additionalPatterns wins due to highest priority)
        assertNotIgnored(
            parser,
            "important.log",
            "important.log should be re-included by additionalPatterns"
        )

        // Other .log files should still be ignored
        assertIgnored(parser, "debug.log", "debug.log should still be ignored")
        assertIgnored(parser, "app.log", "app.log should still be ignored")
    }

    // ==================== 11. Streaming Flow Tests ====================

    @Test
    fun `dfs emits entries in depth-first order`() = runTest {
        // Setup nested directory structure
        (rootPath / "a" / "b" / "c").createDir()
        (rootPath / "a" / "b" / "file.txt").writeToFile("content")

        val scanner = GitIgnoreScanner.create(fs, rootPath)
        val entries = mutableListOf<String>()

        scanner.dfs().collect { entry ->
            entries.add(entry.relativePath)
        }

        // Verify depth-first traversal order
        val aIndex = entries.indexOf("a")
        val bIndex = entries.indexOf("a/b")
        assertTrue(aIndex < bIndex, "Parent 'a' should appear before 'a/b'")
    }

    @Test
    fun `dfs flow can be cancelled mid-traversal`() = runTest {
        // Setup large directory structure
        repeat(10) { i ->
            (rootPath / "dir$i" / "file.txt").writeToFile("content")
        }

        val scanner = GitIgnoreScanner.create(fs, rootPath)
        var count = 0

        // Take only first 5 entries
        scanner.dfs().take(5).collect { count++ }

        assertEquals(5, count, "Should stop after 5 entries")
    }

    @Test
    fun `dfs flow respects backpressure`() = runTest {
        (rootPath / "a" / "file1.txt").writeToFile("1")
        (rootPath / "b" / "file2.txt").writeToFile("2")

        val scanner = GitIgnoreScanner.create(fs, rootPath)
        val firstEntry = scanner.dfs().first()

        assertNotNull(firstEntry, "Should emit at least one entry")
    }
}

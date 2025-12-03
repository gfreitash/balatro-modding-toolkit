package br.com.ghfreitas

import okio.FileSystem
import okio.IOException
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class UtilsTest {

    @Test
    fun writeToFile_writes_content_to_file() {
        val fs = FakeFileSystem()
        val testFile = "/test/file.txt".toPath()
        fs.createDirectories(testFile.parent!!)

        val content = "Hello, World!\nThis is a test file."
        with(fs) { testFile.writeToFile(content) }

        val readContent = fs.read(testFile) { readUtf8() }
        assertEquals(content, readContent)
    }

    @Test
    fun readAsString_reads_file_content() {
        val fs = FakeFileSystem()
        val testFile = "/test/file.txt".toPath()
        fs.createDirectories(testFile.parent!!)

        val content = "Hello, World!\nThis is a test file."
        with(fs) { testFile.writeToFile(content) }

        val readContent = with(fs) { testFile.readAsString() }

        assertEquals(content, readContent)
    }

    @Test
    fun readAsString_throws_exception_for_nonexistent_file() {
        val fs = FakeFileSystem()
        val nonExistentFile = "/does/not/exist.txt".toPath()

        assertFailsWith<IOException> {
            with(fs) { nonExistentFile.readAsString() }
        }
    }

    @Test
    fun readAsString_handles_empty_file() {
        val fs = FakeFileSystem()
        val emptyFile = "/test/empty.txt".toPath()
        fs.createDirectories(emptyFile.parent!!)

        with(fs) { emptyFile.writeToFile("") }

        val content = with(fs) { emptyFile.readAsString() }
        assertEquals("", content)
    }

    @Test
    fun readAsString_handles_utf8_content() {
        val fs = FakeFileSystem()
        val utf8File = "/test/utf8.txt".toPath()
        fs.createDirectories(utf8File.parent!!)

        val utf8Content = "Héllo, Wørld! 🌍 测试 тест"
        with(fs) { utf8File.writeToFile(utf8Content) }

        val readContent = with(fs) { utf8File.readAsString() }
        assertEquals(utf8Content, readContent)
    }

    @Test
    fun readLines_reads_all_lines() {
        val fs = FakeFileSystem()
        val testFile = "/test/lines.txt".toPath()
        fs.createDirectories(testFile.parent!!)

        val firstLine = "First line"
        val secondLine = "Second line"
        val thirdLine = "Third line"
        val fourthLine = ""

        val lines = listOf(firstLine, secondLine, thirdLine, fourthLine)
        val content = lines.joinToString("\n") + "\n"
        with(fs) { testFile.writeToFile(content) }

        val readLines = fs.read(testFile) { readLines().toList() }

        assertEquals(4, readLines.size)
        assertEquals(firstLine, readLines[0])
        assertEquals(secondLine, readLines[1])
        assertEquals(thirdLine, readLines[2])
        assertEquals(fourthLine, readLines[3])
    }

    @Test
    fun readLines_handles_empty_file() {
        val fs = FakeFileSystem()
        val emptyFile = "/test/empty.txt".toPath()
        fs.createDirectories(emptyFile.parent!!)

        with(fs) { emptyFile.writeToFile("") }

        val lines = fs.read(emptyFile) { readLines().toList() }

        assertTrue(lines.isEmpty())
    }

    @Test
    fun readLines_handles_single_line_without_newline() {
        val fs = FakeFileSystem()
        val testFile = "/test/single.txt".toPath()
        fs.createDirectories(testFile.parent!!)

        with(fs) { testFile.writeToFile("Single line without newline") }

        val lines = fs.read(testFile) { readLines().toList() }

        assertEquals(1, lines.size)
        assertEquals("Single line without newline", lines[0])
    }

    @Test
    fun readLines_handles_windows_line_endings() {
        val fs = FakeFileSystem()
        val testFile = "/test/windows.txt".toPath()
        fs.createDirectories(testFile.parent!!)

        val firstLine = "First line"
        val secondLine = "Second line"
        val thirdLine = "Third line"

        // Windows line endings (\r\n)
        val content = "$firstLine\r\n$secondLine\r\n$thirdLine"
        with(fs) { testFile.writeToFile(content) }

        val lines = fs.read(testFile) { readLines().toList() }

        assertEquals(3, lines.size)
        assertEquals(firstLine, lines[0])
        assertEquals(secondLine, lines[1])
        assertEquals(thirdLine, lines[2])
    }

    @Test
    fun absolutePath_works_correctly() {
        val fs = FakeFileSystem()
        val testDir = "/test/nested/path".toPath()
        fs.createDirectories(testDir)

        fs.workingDirectory = testDir
        val testFile = "./test.txt".toPath()
        with(fs) { testFile.writeToFile("test file") }

        val expectedAbsPath = "/test/nested/path/test.txt"
        val absPath = with(fs) { testFile.toAbsolutePath() }

        assertTrue { absPath.isAbsolute }
        assertEquals(absPath.toString(), expectedAbsPath)

    }

    @Test
    fun cwd_shows_proper_working_directory() {
        val fs = FakeFileSystem()
        val testDir = "/test/nested/path".toPath()
        fs.createDirectories(testDir)

        fs.workingDirectory = testDir
        assertEquals(fs.workingDirectory.toString(), with(fs) { FileSystem.cwd() }.toString())
    }
}

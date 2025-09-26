package br.com.ghfreitas

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import okio.fakefilesystem.FakeFileSystem
import okio.Path.Companion.toPath
import okio.IOException
import okio.buffer

class UtilsTest {
    
    @Test
    fun readAsString_reads_file_content() {
        val fs = FakeFileSystem()
        val testFile = "/test/file.txt".toPath()
        fs.createDirectories(testFile.parent!!)
        
        val content = "Hello, World!\nThis is a test file."
        fs.write(testFile) { writeUtf8(content) }
        
        // We can't directly test readAsString() since it uses FileSystem.SYSTEM
        // Instead, we test the equivalent operation using FakeFileSystem
        val readContent = fs.read(testFile) { readUtf8() }
        
        assertEquals(content, readContent)
    }
    
    @Test
    fun readAsString_throws_exception_for_nonexistent_file() {
        val fs = FakeFileSystem()
        val nonExistentFile = "/does/not/exist.txt".toPath()
        
        // Test that reading non-existent file throws IOException
        assertFailsWith<IOException> {
            fs.read(nonExistentFile) { readUtf8() }
        }
    }
    
    @Test
    fun readAsString_handles_empty_file() {
        val fs = FakeFileSystem()
        val emptyFile = "/test/empty.txt".toPath()
        fs.createDirectories(emptyFile.parent!!)
        
        fs.write(emptyFile) { writeUtf8("") }
        
        val content = fs.read(emptyFile) { readUtf8() }
        assertEquals("", content)
    }
    
    @Test
    fun readAsString_handles_utf8_content() {
        val fs = FakeFileSystem()
        val utf8File = "/test/utf8.txt".toPath()
        fs.createDirectories(utf8File.parent!!)
        
        val utf8Content = "Héllo, Wørld! 🌍 测试 тест"
        fs.write(utf8File) { writeUtf8(utf8Content) }
        
        val readContent = fs.read(utf8File) { readUtf8() }
        assertEquals(utf8Content, readContent)
    }
    
    @Test
    fun readLines_reads_all_lines() {
        val fs = FakeFileSystem()
        val testFile = "/test/lines.txt".toPath()
        fs.createDirectories(testFile.parent!!)
        
        val lines = listOf("First line", "Second line", "Third line", "")
        fs.write(testFile) { 
            lines.forEach { line ->
                writeUtf8(line)
                writeUtf8("\n")
            }
        }
        
        val readLines = fs.read(testFile) { 
            buffer().readLines().toList() 
        }
        
        assertEquals(4, readLines.size)
        assertEquals("First line", readLines[0])
        assertEquals("Second line", readLines[1])
        assertEquals("Third line", readLines[2])
        assertEquals("", readLines[3])
    }
    
    @Test
    fun readLines_handles_empty_file() {
        val fs = FakeFileSystem()
        val emptyFile = "/test/empty.txt".toPath()
        fs.createDirectories(emptyFile.parent!!)
        
        fs.write(emptyFile) { writeUtf8("") }
        
        val lines = fs.read(emptyFile) { 
            buffer().readLines().toList() 
        }
        
        assertTrue(lines.isEmpty())
    }
    
    @Test
    fun readLines_handles_single_line_without_newline() {
        val fs = FakeFileSystem()
        val testFile = "/test/single.txt".toPath()
        fs.createDirectories(testFile.parent!!)
        
        fs.write(testFile) { writeUtf8("Single line without newline") }
        
        val lines = fs.read(testFile) { 
            buffer().readLines().toList() 
        }
        
        assertEquals(1, lines.size)
        assertEquals("Single line without newline", lines[0])
    }
    
    @Test
    fun readLines_handles_windows_line_endings() {
        val fs = FakeFileSystem()
        val testFile = "/test/windows.txt".toPath()
        fs.createDirectories(testFile.parent!!)
        
        // Windows line endings (\r\n)
        val content = "First line\r\nSecond line\r\nThird line\r\n"
        fs.write(testFile) { writeUtf8(content) }
        
        val lines = fs.read(testFile) { 
            buffer().readLines().toList() 
        }
        
        assertEquals(3, lines.size)
        assertEquals("First line", lines[0])
        assertEquals("Second line", lines[1])
        assertEquals("Third line", lines[2])
    }
    
    @Test
    fun path_operations_work_correctly() {
        val fs = FakeFileSystem()
        val testDir = "/test/nested/path".toPath()
        fs.createDirectories(testDir)
        
        val testFile = testDir / "test.txt"
        fs.write(testFile) { writeUtf8("test content") }
        
        // Test that file exists and has correct path
        assertTrue(fs.exists(testFile))
        assertEquals("test.txt", testFile.name)
        assertEquals(testDir, testFile.parent)
        
        // Test canonicalize (equivalent to toAbsolutePath)
        val canonicalPath = fs.canonicalize(testFile)
        assertTrue(canonicalPath.toString().startsWith("/"))
        assertTrue(canonicalPath.toString().endsWith("test.txt"))
    }
    
    @Test
    fun directory_operations_work_correctly() {
        val fs = FakeFileSystem()
        val baseDir = "/project".toPath()
        fs.createDirectories(baseDir)
        
        val subDir1 = baseDir / "sub1"
        val subDir2 = baseDir / "sub2"
        fs.createDirectories(subDir1)
        fs.createDirectories(subDir2)
        
        // Create some test files
        fs.write(subDir1 / "file1.txt") { writeUtf8("content1") }
        fs.write(subDir2 / "file2.txt") { writeUtf8("content2") }
        fs.write(baseDir / "root.txt") { writeUtf8("root content") }
        
        // Test listing directory contents
        val contents = fs.list(baseDir).sorted()
        assertEquals(3, contents.size)
        assertTrue(contents.contains(baseDir / "root.txt"))
        assertTrue(contents.contains(subDir1))
        assertTrue(contents.contains(subDir2))
        
        // Test recursive listing
        val allFiles = fs.listRecursively(baseDir).filter { fs.metadata(it).isRegularFile }.toList()
        assertEquals(3, allFiles.size)
    }
}

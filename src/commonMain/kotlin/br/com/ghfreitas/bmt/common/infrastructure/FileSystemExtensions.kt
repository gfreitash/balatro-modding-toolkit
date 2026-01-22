package br.com.ghfreitas.bmt.common.infrastructure

import okio.BufferedSource
import okio.FileSystem
import okio.IOException
import okio.Path
import okio.Path.Companion.toPath

/*
 * Shared Kernel - FileSystem Infrastructure Extensions
 *
 * These are infrastructure utilities for working with Okio's FileSystem using context receivers.
 * They provide convenient operations for file I/O across all bounded contexts.
 */

/**
 * Reads the content of the file as a string using the provided `FileSystem` context.
 *
 * This function reads the specified file referenced by the `Path` object and returns its content
 * as a UTF-8 encoded string. Any IO-related issues encountered during the operation
 * result in an `IOException` being thrown.
 *
 * @return the file content as a UTF-8 encoded string
 * @throws IOException if an error occurs while reading the file
 */
@Throws(IOException::class)
context(filesystem: FileSystem)
fun Path.readAsString(): String = filesystem.read(this) {
    return readUtf8()
}

/**
 * Writes the given content to a file located at the specified path using the provided file system context.
 * This method overwrites the file if it already exists.
 *
 * @param content The text content to be written to the file.
 * @throws IOException if an error occurs while writing the file
 */
@Throws(IOException::class)
context(filesystem: FileSystem)
fun Path.writeToFile(content: String) = filesystem.write(this) { writeUtf8(content) }

/**
 * Converts this relative path to its absolute path, resolving any symbolic links
 * and normalizing the result using the provided file system.
 *
 * This operation utilizes the `canonicalize` function of the current file system
 * to retrieve the absolute path associated with the current path.
 *
 * @return the absolute path corresponding to this path, resolved within the context
 * of the provided file system.
 * @throws IOException if an `path` can not be resolved
 */
@Throws(IOException::class)
context(filesystem: FileSystem)
fun Path.toAbsolutePath(): Path = filesystem.canonicalize(this)

/**
 * Retrieves the current working directory as an absolute path.
 *
 * This method resolves the relative reference to the current working directory
 * (".") using the provided file system and returns its absolute path.
 *
 * @return the absolute path representing the current working directory,
 *         resolved within the context of the provided file system.
 */
context(filesystem: FileSystem)
fun FileSystem.Companion.cwd(): Path = ".".toPath().toAbsolutePath()

/**
 * Reads lines from the `BufferedSource` sequentially until the source is exhausted.
 *
 * Each line is read as UTF-8 and returned as elements of the resulting sequence. The sequence
 * is lazily evaluated, meaning lines are only read as they are iterated over.
 *
 * Lines are delimited by standard line endings (`\n`, `\r\n`, or `\r`). If a line does not
 * end with a delimiter and the source is exhausted, the final line will still be returned.
 *
 * @return a lazily evaluated `Sequence` of strings representing the lines read from the source
 */
fun BufferedSource.readLines(): Sequence<String> = sequence {
    while (!exhausted()) {
        yield(readUtf8Line() ?: break)
    }
}

/**
 * Atomically writes the given content to a file located at the specified path using the provided file system context.
 * This method overwrites the file if it already exists.
 *
 * @param content The text content to be written to the file.
 * @throws IOException if an error occurs while writing the file
 */
@Throws(IOException::class)
context(filesystem: FileSystem)
fun Path.atomicWrite(content: String) = with(filesystem) {
    val path = this@atomicWrite
    val tmp = path.parent?.div("${path.name}.tmp") ?: "${path.name}.tmp".toPath()
    tmp.writeToFile(content)
    atomicMove(tmp, path)
}

/**
 * Creates a Path instance by joining a list of path segments using the directory separator.
 *
 * @param segments The list of strings representing the path segments to be joined.
 * @return A Path instance created from the joined segments.
 */
fun Path.Companion.fromSegments(segments: List<String>) = segments.joinToString(Path.DIRECTORY_SEPARATOR).toPath()

fun Path.toSegments(): List<String> = toString().split(Path.DIRECTORY_SEPARATOR)

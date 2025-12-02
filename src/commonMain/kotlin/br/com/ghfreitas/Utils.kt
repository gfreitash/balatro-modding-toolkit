package br.com.ghfreitas

import okio.BufferedSource
import okio.FileSystem
import okio.IOException
import okio.Path
import okio.Path.Companion.toPath

@Throws(IOException::class)
context(filesystem: FileSystem)
fun Path.readAsString(): String = filesystem.read(this) {
    return readUtf8()
}

context(filesystem: FileSystem)
fun Path.toAbsolutePath(): Path = filesystem.canonicalize(this)

context(filesystem: FileSystem)
fun FileSystem.Companion.cwd(): Path = ".".toPath().toAbsolutePath()

fun BufferedSource.readLines(): Sequence<String> = sequence {
    while (!exhausted()) {
        yield(readUtf8Line() ?: break)
    }
}

context(filesystem: FileSystem)
fun Path.readLines(): Sequence<String> = filesystem.read(this) { readLines() }

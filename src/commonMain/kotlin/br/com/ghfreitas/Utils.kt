package br.com.ghfreitas

import okio.BufferedSource
import okio.FileSystem
import okio.IOException
import okio.Path
import okio.Path.Companion.toPath

@Throws(IOException::class)
fun Path.readAsString(): String = FileSystem.SYSTEM.read(this) {
    return readUtf8()
}

fun Path.toAbsolutePath(): Path = FileSystem.SYSTEM.canonicalize(this)
fun FileSystem.Companion.cwd(): Path = ".".toPath().toAbsolutePath()

fun BufferedSource.readLines(): Sequence<String> = sequence {
    while (!exhausted()) {
        yield(readUtf8Line() ?: break)
    }
}

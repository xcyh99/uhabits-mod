/*
 * Copyright (C) 2016-2025 Álinson Santos Xavier <git@axavier.org>
 *
 * This file is part of Loop Habit Tracker.
 *
 * Loop Habit Tracker is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by the
 * Free Software Foundation, either version 3 of the License, or (at your
 * option) any later version.
 *
 * Loop Habit Tracker is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package org.isoron.platform.io

import org.isoron.platform.gui.Image
import org.isoron.platform.gui.JavaImage
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import javax.imageio.ImageIO

@Suppress("NewApi")
class JavaResourceFile(val path: String) : ResourceFile {
    private val javaPath: Path
        get() {
            val mainPath = Paths.get("assets/main/$path")
            val testPath = Paths.get("assets/test/$path")
            return if (Files.exists(mainPath)) {
                mainPath
            } else {
                testPath
            }
        }

    override suspend fun exists(): Boolean {
        return Files.exists(javaPath)
    }

    override suspend fun lines(): List<String> {
        return Files.readAllLines(javaPath)
    }

    override suspend fun copyTo(dest: UserFile) {
        if (dest.exists()) dest.delete()
        val destPath = (dest as JavaUserFile).path
        destPath.toFile().parentFile?.mkdirs()
        Files.copy(javaPath, destPath)
    }

    fun stream(): InputStream {
        return Files.newInputStream(javaPath)
    }

    override suspend fun toImage(): Image {
        return JavaImage(ImageIO.read(stream()))
    }
}

@Suppress("NewApi")
class JavaUserFile(val path: Path) : UserFile {
    override val pathString: String
        get() = path.toString()

    override suspend fun lines(): List<String> {
        return Files.readAllLines(path)
    }

    override suspend fun exists(): Boolean {
        return Files.exists(path)
    }

    override suspend fun delete() {
        Files.delete(path)
    }

    override suspend fun writeString(content: String) {
        path.toFile().parentFile?.mkdirs()
        Files.write(path, content.toByteArray())
    }

    override suspend fun writeBytes(bytes: ByteArray) {
        path.toFile().parentFile?.mkdirs()
        Files.write(path, bytes)
    }

    override suspend fun readBytes(limit: Int): ByteArray {
        require(limit >= 0) { "limit must not be negative" }
        val file = path.toFile()
        // Do not allocate the requested limit blindly. Callers may pass
        // Int.MAX_VALUE to mean "read the whole file", but that must not
        // reserve a 2 GiB array for a small backup.
        val size = minOf(limit.toLong(), file.length()).toInt()
        file.inputStream().use { stream ->
            val buf = ByteArray(size)
            var offset = 0
            while (offset < size) {
                val count = stream.read(buf, offset, size - offset)
                if (count <= 0) break
                offset += count
            }
            return if (offset == size) buf else buf.copyOf(offset)
        }
    }

    override fun resolve(child: String): UserFile {
        return JavaUserFile(path.resolve(child))
    }

    override suspend fun listFiles(): List<UserFile>? {
        val files = path.toFile().listFiles() ?: return null
        return files.map { JavaUserFile(it.toPath()) }
    }

    override suspend fun mkdirs() {
        path.toFile().mkdirs()
    }
}

@Suppress("NewApi")
class JavaFileOpener : FileOpener {
    override fun openUserFile(path: String): UserFile {
        val resolvedPath = Paths.get("/tmp/$path")
        return JavaUserFile(resolvedPath)
    }

    override fun openResourceFile(path: String): ResourceFile {
        return JavaResourceFile(path)
    }
}

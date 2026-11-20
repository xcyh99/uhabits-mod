/*
 * WebDAV backup transport for 十字习惯.
 */
package org.isoron.uhabits.database

import android.content.Context
import android.net.Uri
import androidx.preference.PreferenceManager
import androidx.documentfile.provider.DocumentFile
import org.isoron.uhabits.utils.DatabaseUtils
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64

class WebDavSync(private val context: Context) {
    data class Config(val server: String, val username: String, val password: String)

    private fun connection(config: Config, method: String, target: String): HttpURLConnection {
        val base = config.server.trimEnd('/') + "/"
        val url = URL(base + target)
        return (url.openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15000
            readTimeout = 30000
            setRequestProperty("Authorization", "Basic " +
                Base64.getEncoder().encodeToString(
                    "${config.username}:${config.password}".toByteArray(Charsets.UTF_8)
                ))
        }
    }

    fun test(config: Config) {
        val c = connection(config, "PROPFIND", "")
        try {
            c.setRequestProperty("Depth", "0")
            c.setRequestProperty("Content-Type", "application/xml; charset=utf-8")
            c.doOutput = true
            c.outputStream.use { output ->
                output.write(
                    """<?xml version="1.0" encoding="utf-8"?><d:propfind xmlns:d="DAV:"><d:prop><d:resourcetype/></d:prop></d:propfind>"""
                        .toByteArray(Charsets.UTF_8)
                )
            }
            val code = c.responseCode
            if (code != 207 && code != 200) {
                throw IllegalStateException("WebDAV connection failed: HTTP $code")
            }
        } finally {
            c.disconnect()
        }
    }

    fun upload(config: Config) {
        val dir = File(context.cacheDir, "webdav-backup").apply {
            if (!exists() && !mkdirs()) error("Cannot create backup staging directory")
        }
        val path = DatabaseUtils.saveDatabaseCopy(context, dir)
        val file = File(path)
        val c = connection(config, "PUT", "shizi-habits-backup.db")
        try {
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/octet-stream")
            c.setFixedLengthStreamingMode(file.length())
            file.inputStream().use { input ->
                c.outputStream.use { output -> input.copyTo(output) }
            }
            val code = c.responseCode
            if (code !in 200..299) throw IllegalStateException("Upload failed: HTTP $code")
        } finally {
            c.disconnect()
            file.delete()
        }
    }

    fun download(config: Config) {
        val folderUri = PreferenceManager.getDefaultSharedPreferences(context)
            .getString("publicBackupFolder", null)
            ?: throw IllegalStateException("Please select a local backup folder in Settings first")
        val folder = DocumentFile.fromTreeUri(context, Uri.parse(folderUri))
            ?: throw IllegalStateException("Cannot open selected backup folder")
        val c = connection(config, "GET", "shizi-habits-backup.db")
        try {
            val code = c.responseCode
            if (code != HttpURLConnection.HTTP_OK) {
                throw IllegalStateException("Download failed: HTTP $code")
            }
            val outputFile = folder.findFile("WebDAV-shizi-habits-backup.db")
                ?: folder.createFile("application/octet-stream", "WebDAV-shizi-habits-backup.db")
                ?: throw IllegalStateException("Cannot create file in selected backup folder")
            context.contentResolver.openOutputStream(outputFile.uri, "wt")?.use { output ->
                c.inputStream.use { input -> input.copyTo(output) }
            } ?: throw IllegalStateException("Cannot write downloaded backup")
        } finally {
            c.disconnect()
        }
    }
}

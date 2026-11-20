/*
 * WebDAV backup transport for 十字习惯.
 */
package org.isoron.uhabits.database

import android.content.Context
import android.net.Uri
import android.util.Xml
import androidx.preference.PreferenceManager
import androidx.documentfile.provider.DocumentFile
import org.isoron.uhabits.utils.DatabaseUtils
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Base64
import java.util.Date
import java.util.Locale

class WebDavSync(private val context: Context) {
    data class Config(val server: String, val username: String, val password: String)

    data class RemoteFile(
        val name: String,
        val href: String,
        val modified: String?,
        val size: Long?
    )

    private fun authorizedConnection(config: Config, method: String, url: URL): HttpURLConnection {
        val server = URL(config.server)
        require(url.protocol == "https" && url.host.equals(server.host, ignoreCase = true)) {
            "WebDAV 返回了不安全或跨服务器的文件地址"
        }
        return (url.openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15000
            readTimeout = 30000
            setRequestProperty(
                "Authorization",
                "Basic " + Base64.getEncoder().encodeToString(
                    "${config.username}:${config.password}".toByteArray(Charsets.UTF_8)
                )
            )
        }
    }

    private fun connection(config: Config, method: String, target: String): HttpURLConnection {
        val base = URL(config.server.trimEnd('/') + "/")
        return authorizedConnection(config, method, URL(base, target))
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
                throw IllegalStateException("WebDAV 连接失败，HTTP 状态码：$code")
            }
        } finally {
            c.disconnect()
        }
    }

    fun upload(config: Config) {
        val dir = File(context.cacheDir, "webdav-backup").apply {
            if (!exists() && !mkdirs()) error("无法创建备份临时目录")
        }
        val path = DatabaseUtils.saveDatabaseCopy(context, dir)
        val file = File(path)
        val timestamp = SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.US).format(Date())
        val remoteName = "WebDAV_Shizi_Habits_Backup_${timestamp}.db"
        val c = connection(config, "PUT", remoteName)
        try {
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/octet-stream")
            c.setFixedLengthStreamingMode(file.length())
            file.inputStream().use { input ->
                c.outputStream.use { output -> input.copyTo(output) }
            }
            val code = c.responseCode
            if (code !in 200..299) {
                throw IllegalStateException("备份上传失败，HTTP 状态码：$code")
            }
        } finally {
            c.disconnect()
            file.delete()
        }
    }

    fun listBackupFiles(config: Config): List<RemoteFile> {
        val c = connection(config, "PROPFIND", "")
        try {
            c.setRequestProperty("Depth", "1")
            c.setRequestProperty("Content-Type", "application/xml; charset=utf-8")
            c.doOutput = true
            c.outputStream.use { output ->
                output.write(
                    """<?xml version="1.0" encoding="utf-8"?><d:propfind xmlns:d="DAV:"><d:prop><d:resourcetype/><d:getlastmodified/><d:getcontentlength/></d:prop></d:propfind>"""
                        .toByteArray(Charsets.UTF_8)
                )
            }
            val code = c.responseCode
            if (code != 207 && code != 200) {
                throw IllegalStateException("读取 WebDAV 备份列表失败，HTTP 状态码：$code")
            }

            val files = mutableListOf<RemoteFile>()
            val parser = Xml.newPullParser()
            parser.setInput(c.inputStream, "UTF-8")
            var event = parser.eventType
            var inResponse = false
            var href: String? = null
            var modified: String? = null
            var size: Long? = null
            var isCollection = false

            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> when (parser.name.substringAfter(':')) {
                        "response" -> {
                            inResponse = true
                            href = null
                            modified = null
                            size = null
                            isCollection = false
                        }
                        "href" -> if (inResponse) href = parser.nextText()
                        "getlastmodified" -> if (inResponse) modified = parser.nextText()
                        "getcontentlength" -> if (inResponse) {
                            size = parser.nextText().toLongOrNull()
                        }
                        "collection" -> if (inResponse) isCollection = true
                    }
                    XmlPullParser.END_TAG -> if (
                        parser.name.substringAfter(':') == "response" && inResponse
                    ) {
                        val itemHref = href
                        if (!isCollection && !itemHref.isNullOrBlank()) {
                            val name = Uri.parse(itemHref).lastPathSegment
                                ?: itemHref.substringAfterLast('/').trim()
                            if (name.isNotBlank() && name.substringAfterLast('.', "").lowercase() in
                                setOf("db", "sqlite", "backup")
                            ) {
                                files.add(RemoteFile(name, itemHref, modified, size))
                            }
                        }
                        inResponse = false
                    }
                }
                event = parser.next()
            }
            return files.sortedWith(compareByDescending<RemoteFile> { it.modified ?: "" }.thenBy { it.name })
        } finally {
            c.disconnect()
        }
    }

    fun download(config: Config, remoteFile: RemoteFile) {
        val folderUri = PreferenceManager.getDefaultSharedPreferences(context)
            .getString("publicBackupFolder", null)
            ?: throw IllegalStateException("请先在设置中选择本地备份文件夹")
        val folder = DocumentFile.fromTreeUri(context, Uri.parse(folderUri))
            ?: throw IllegalStateException("无法打开所选备份文件夹")

        val server = URL(config.server.trimEnd('/') + "/")
        val remoteUrl = URL(server, remoteFile.href)
        val c = authorizedConnection(config, "GET", remoteUrl)
        try {
            val code = c.responseCode
            if (code != HttpURLConnection.HTTP_OK) {
                throw IllegalStateException("备份下载失败，HTTP 状态码：$code")
            }
            val localName = remoteFile.name
            val outputFile = folder.findFile(localName)
                ?: folder.createFile("application/octet-stream", localName)
                ?: throw IllegalStateException("无法在所选备份文件夹中创建文件")
            context.contentResolver.openOutputStream(outputFile.uri, "wt")?.use { output ->
                c.inputStream.use { input -> input.copyTo(output) }
            } ?: throw IllegalStateException("无法写入下载的备份文件")
        } finally {
            c.disconnect()
        }
    }
}

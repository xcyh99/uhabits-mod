/*
 * WebDAV backup transport for 十字习惯.
 */
package org.isoron.uhabits.database

import android.content.Context
import android.net.Uri
import android.util.Xml
import androidx.preference.PreferenceManager
import androidx.documentfile.provider.DocumentFile
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.readBytes
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.content.OutgoingContent
import io.ktor.http.content.TextContent
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.runBlocking
import org.isoron.uhabits.utils.DatabaseUtils
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.io.File
import java.net.URL
import java.text.SimpleDateFormat
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

    private fun serverUrl(config: Config): URL {
        val url = URL(config.server.trimEnd('/') + "/")
        require(url.protocol == "https") { "WebDAV 仅允许使用 HTTPS" }
        return url
    }

    private fun request(
        client: HttpClient,
        config: Config,
        method: String,
        url: URL,
        depth: String? = null,
        body: Any? = null
    ): HttpResponse {
        val base = serverUrl(config)
        require(url.protocol == "https" && url.host.equals(base.host, ignoreCase = true) &&
            url.port == base.port
        ) { "WebDAV 返回了不安全或跨服务器的文件地址" }

        return runBlocking {
            client.request(url.toString()) {
                this.method = HttpMethod(method)
                header(
                    HttpHeaders.Authorization,
                    "Basic " + java.util.Base64.getEncoder().encodeToString(
                        "${config.username}:${config.password}".toByteArray(Charsets.UTF_8)
                    )
                )
                if (depth != null) header("Depth", depth)
                when (body) {
                    is OutgoingContent -> this.body = body
                    is String -> {
                        header(HttpHeaders.ContentType, ContentType.Application.Xml.toString())
                        this.body = TextContent(body, ContentType.Application.Xml.withCharset(Charsets.UTF_8))
                    }
                }
            }
        }
    }

    private fun withClient(block: (HttpClient) -> Unit) {
        val client = HttpClient(CIO)
        try {
            block(client)
        } finally {
            client.close()
        }
    }

    private fun propfindBody(includeFileProperties: Boolean): String =
        if (includeFileProperties) {
            """<?xml version="1.0" encoding="utf-8"?><d:propfind xmlns:d="DAV:"><d:prop><d:resourcetype/><d:getlastmodified/><d:getcontentlength/></d:prop></d:propfind>"""
        } else {
            """<?xml version="1.0" encoding="utf-8"?><d:propfind xmlns:d="DAV:"><d:prop><d:resourcetype/></d:prop></d:propfind>"""
        }

    fun test(config: Config) = withClient { client ->
        val response = request(client, config, "PROPFIND", serverUrl(config), "0", propfindBody(false))
        val code = response.status.value
        runBlocking { response.readBytes() }
        if (code != 207 && code != 200) {
            throw IllegalStateException("WebDAV 连接失败，HTTP 状态码：$code")
        }
    }

    fun upload(config: Config) {
        val dir = File(context.cacheDir, "webdav-backup").apply {
            if (!exists() && !mkdirs()) error("无法创建备份临时目录")
        }
        val file = File(DatabaseUtils.saveDatabaseCopy(context, dir))
        val timestamp = SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.US).format(Date())
        val remoteName = "WebDAV_Shizi_Habits_Backup_${timestamp}.db"

        try {
            withClient { client ->
                val body = object : OutgoingContent.WriteChannelContent() {
                    override val contentType = ContentType.Application.OctetStream
                    override val contentLength: Long = file.length()

                    override suspend fun writeTo(channel: ByteWriteChannel) {
                        file.inputStream().use { input ->
                            val buffer = ByteArray(8192)
                            while (true) {
                                val count = input.read(buffer)
                                if (count < 0) break
                                channel.writeFully(buffer, 0, count)
                            }
                        }
                    }
                }
                val response = request(
                    client, config, "PUT", URL(serverUrl(config), remoteName), body = body
                )
                val code = response.status.value
                runBlocking { response.readBytes() }
                if (code !in 200..299) {
                    throw IllegalStateException("备份上传失败，HTTP 状态码：$code")
                }
            }
        } finally {
            file.delete()
        }
    }

    fun listBackupFiles(config: Config): List<RemoteFile> {
        var result: List<RemoteFile> = emptyList()
        withClient { client ->
            val response = request(
                client, config, "PROPFIND", serverUrl(config), "1", propfindBody(true)
            )
            val code = response.status.value
            val xml = runBlocking { response.readBytes() }
            if (code != 207 && code != 200) {
                throw IllegalStateException("读取 WebDAV 备份列表失败，HTTP 状态码：$code")
            }

            val files = mutableListOf<RemoteFile>()
            val parser = Xml.newPullParser()
            parser.setInput(ByteArrayInputStream(xml), "UTF-8")
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
                        "getcontentlength" -> if (inResponse) size = parser.nextText().toLongOrNull()
                        "collection" -> if (inResponse) isCollection = true
                    }
                    XmlPullParser.END_TAG -> if (
                        parser.name.substringAfter(':') == "response" && inResponse
                    ) {
                        val itemHref = href
                        if (!isCollection && !itemHref.isNullOrBlank()) {
                            val rawName = Uri.parse(itemHref).lastPathSegment
                                ?: itemHref.substringAfterLast('/').trim()
                            val name = Uri.decode(rawName)
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
            result = files.sortedWith(
                compareByDescending<RemoteFile> { it.modified ?: "" }.thenBy { it.name }
            )
        }
        return result
    }

    fun download(config: Config, remoteFile: RemoteFile) {
        val folderUri = PreferenceManager.getDefaultSharedPreferences(context)
            .getString("publicBackupFolder", null)
            ?: throw IllegalStateException("请先在设置中选择本地备份文件夹")
        val folder = DocumentFile.fromTreeUri(context, Uri.parse(folderUri))
            ?: throw IllegalStateException("无法打开所选备份文件夹")

        val remoteUrl = URL(serverUrl(config), remoteFile.href)
        withClient { client ->
            val response = request(client, config, "GET", remoteUrl)
            val code = response.status.value
            if (code != 200) {
                runBlocking { response.readBytes() }
                throw IllegalStateException("备份下载失败，HTTP 状态码：$code")
            }

            val outputFile = folder.findFile(remoteFile.name)
                ?: folder.createFile("application/octet-stream", remoteFile.name)
                ?: throw IllegalStateException("无法在所选备份文件夹中创建文件")
            context.contentResolver.openOutputStream(outputFile.uri, "wt")?.use { output ->
                runBlocking {
                    val buffer = ByteArray(8192)
                    while (true) {
                        val count = response.content.readAvailable(buffer, 0, buffer.size)
                        if (count < 0) break
                        if (count > 0) output.write(buffer, 0, count)
                    }
                }
            } ?: throw IllegalStateException("无法写入下载的备份文件")
        }
    }
}

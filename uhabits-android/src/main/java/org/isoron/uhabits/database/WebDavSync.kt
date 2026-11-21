/*
 * WebDAV backup transport for 十字习惯.
 */
package org.isoron.uhabits.database

import android.content.Context
import android.net.Uri
import android.util.Xml
import androidx.preference.PreferenceManager
import androidx.documentfile.provider.DocumentFile
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.isoron.uhabits.utils.BackupFileName
import org.isoron.uhabits.utils.DatabaseUtils
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.io.File
import java.net.URL

class WebDavSync(private val context: Context) {
    enum class UploadKind(val fileLabel: String) {
        LOCAL_BACKUP("LOC"),
        TWO_WAY_MERGED("MRG")
    }

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

    /*
     * Use OkHttp directly. Android's HttpURLConnection rejects WebDAV verbs such
     * as PROPFIND before a request reaches the server.
     */
    private fun request(
        client: OkHttpClient,
        config: Config,
        method: String,
        url: URL,
        depth: String? = null,
        body: RequestBody? = null
    ): Response {
        val base = serverUrl(config)
        require(url.protocol == "https" && url.host.equals(base.host, ignoreCase = true) &&
            url.port == base.port
        ) { "WebDAV 返回了不安全或跨服务器的文件地址" }

        val builder = Request.Builder()
            .url(url)
            .header(
                "Authorization",
                Credentials.basic(config.username, config.password, Charsets.UTF_8)
            )
        if (depth != null) builder.header("Depth", depth)
        return client.newCall(builder.method(method, body).build()).execute()
    }

    private fun withClient(block: (OkHttpClient) -> Unit) {
        val client = OkHttpClient.Builder().build()
        try {
            block(client)
        } finally {
            client.dispatcher.executorService.shutdown()
            client.connectionPool.evictAll()
        }
    }

    private fun propfindBody(includeFileProperties: Boolean): RequestBody {
        val xml = if (includeFileProperties) {
            """<?xml version="1.0" encoding="utf-8"?><d:propfind xmlns:d="DAV:"><d:prop><d:resourcetype/><d:getlastmodified/><d:getcontentlength/></d:prop></d:propfind>"""
        } else {
            """<?xml version="1.0" encoding="utf-8"?><d:propfind xmlns:d="DAV:"><d:prop><d:resourcetype/></d:prop></d:propfind>"""
        }
        return xml.toRequestBody("application/xml; charset=utf-8".toMediaType())
    }

    fun test(config: Config) = withClient { client ->
        request(client, config, "PROPFIND", serverUrl(config), "0", propfindBody(false)).use { response ->
            val code = response.code
            if (code != 207 && code != 200) {
                throw IllegalStateException("WebDAV 连接失败，HTTP 状态码：$code")
            }
        }
    }

    fun upload(config: Config, kind: UploadKind = UploadKind.LOCAL_BACKUP) {
        val dir = File(context.cacheDir, "webdav-backup").apply {
            if (!exists() && !mkdirs()) error("无法创建备份临时目录")
        }
        val file = File(DatabaseUtils.saveDatabaseCopy(context, dir))
        val remoteName = "SH_WEB_\${BackupFileName.deviceId()}_\${kind.fileLabel}_\${BackupFileName.timestamp()}.db"

        try {
            withClient { client ->
                val body = file.asRequestBody("application/octet-stream".toMediaType())
                request(
                    client, config, "PUT", URL(serverUrl(config), remoteName), body = body
                ).use { response ->
                    if (!response.isSuccessful) {
                        throw IllegalStateException("备份上传失败，HTTP 状态码：${response.code}")
                    }
                }
            }
        } finally {
            file.delete()
        }
    }

    fun listBackupFiles(config: Config): List<RemoteFile> {
        var result: List<RemoteFile> = emptyList()
        withClient { client ->
            request(
                client, config, "PROPFIND", serverUrl(config), "1", propfindBody(true)
            ).use { response ->
                val code = response.code
                val xml = response.body?.bytes() ?: byteArrayOf()
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
        }
        return result
    }

    /**
     * Downloads a remote database into a private temporary file for read-only
     * sync inspection. It does not touch the user's selected backup folder or
     * replace the active database.
     */
    fun downloadToTemporaryFile(config: Config, remoteFile: RemoteFile): File {
        val tempFile = File.createTempFile("shizi-webdav-sync-", ".db", context.cacheDir)
        try {
            val remoteUrl = URL(serverUrl(config), remoteFile.href)
            withClient { client ->
                request(client, config, "GET", remoteUrl).use { response ->
                    if (response.code != 200) {
                        throw IllegalStateException("同步数据下载失败，HTTP 状态码：${response.code}")
                    }
                    val input = response.body?.byteStream()
                        ?: throw IllegalStateException("服务器返回了空数据库文件")
                    input.use { source ->
                        tempFile.outputStream().use { output -> source.copyTo(output) }
                    }
                }
            }
            if (!tempFile.isFile || tempFile.length() == 0L) {
                throw IllegalStateException("下载的数据库文件为空")
            }
            return tempFile
        } catch (error: Exception) {
            tempFile.delete()
            throw error
        }
    }

    fun download(config: Config, remoteFile: RemoteFile) {
        val folderUri = PreferenceManager.getDefaultSharedPreferences(context)
            .getString("publicBackupFolder", null)
            ?: throw IllegalStateException("请先在设置中选择本地备份文件夹")
        val folder = DocumentFile.fromTreeUri(context, Uri.parse(folderUri))
            ?: throw IllegalStateException("无法打开所选备份文件夹")

        val remoteUrl = URL(serverUrl(config), remoteFile.href)
        withClient { client ->
            request(client, config, "GET", remoteUrl).use { response ->
                if (response.code != 200) {
                    throw IllegalStateException("备份下载失败，HTTP 状态码：${response.code}")
                }

                val outputFile = folder.findFile(remoteFile.name)
                    ?: folder.createFile("application/octet-stream", remoteFile.name)
                    ?: throw IllegalStateException("无法在所选备份文件夹中创建文件")
                context.contentResolver.openOutputStream(outputFile.uri, "wt")?.use { output ->
                    response.body?.byteStream()?.use { input -> input.copyTo(output) }
                        ?: throw IllegalStateException("服务器返回了空备份文件")
                } ?: throw IllegalStateException("无法写入下载的备份文件")
            }
        }
    }
}

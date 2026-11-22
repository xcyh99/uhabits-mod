package org.isoron.uhabits.database

import android.content.Context
import android.net.Uri
import android.util.Xml
import androidx.documentfile.provider.DocumentFile
import androidx.preference.PreferenceManager
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.isoron.uhabits.utils.BackupFileName
import org.xmlpull.v1.XmlPullParser
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.File
import java.net.URL

/**
 * WebDAV transport for complete .uhabitsbackup archives.
 * This is intentionally separate from WebDavSync, which synchronizes databases.
 */
class WebDavFullBackup(private val context: Context) {

    fun upload(config: WebDavSync.Config): String {
        val tempFile = File.createTempFile("shizi-full-backup-", ".uhabitsbackup", context.cacheDir)
        try {
            FullBackupManager(context).createBackup(tempFile)
            val remoteName = "WEB_${BackupFileName.deviceId()}_FULL_${BackupFileName.timestamp()}.uhabitsbackup"
            withClient { client ->
                val body = tempFile.asRequestBody("application/octet-stream".toMediaType())
                request(client, config, "PUT", URL(serverUrl(config), Uri.encode(remoteName)), body = body)
                    .use { response ->
                        if (!response.isSuccessful) {
                            throw IllegalStateException("完整备份上传失败，HTTP：${response.code}")
                        }
                    }
            }
            return remoteName
        } finally {
            tempFile.delete()
        }
    }

    fun listBackupFiles(config: WebDavSync.Config): List<WebDavSync.RemoteFile> {
        var result = emptyList<WebDavSync.RemoteFile>()
        withClient { client ->
            val xml = """<?xml version="1.0" encoding="utf-8"?><d:propfind xmlns:d="DAV:"><d:prop><d:resourcetype/><d:getlastmodified/><d:getcontentlength/></d:prop></d:propfind>"""
                .toRequestBody("application/xml; charset=utf-8".toMediaType())
            request(client, config, "PROPFIND", serverUrl(config), "1", xml).use { response ->
                if (response.code != 207 && response.code != 200) {
                    throw IllegalStateException("读取云端完整备份列表失败，HTTP：${response.code}")
                }
                val bytes = response.body?.bytes() ?: byteArrayOf()
                val parser = Xml.newPullParser()
                parser.setInput(ByteArrayInputStream(bytes), "UTF-8")
                val files = mutableListOf<WebDavSync.RemoteFile>()
                val pattern = Regex("WEB_.+_FULL_\\d{8}_\\d{6}_\\d{3}\\.uhabitsbackup")
                var inResponse = false
                var href: String? = null
                var modified: String? = null
                var size: Long? = null
                var isCollection = false
                var event = parser.eventType
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
                            val rawHref = href
                            if (!isCollection && !rawHref.isNullOrBlank()) {
                                val rawName = Uri.parse(rawHref).lastPathSegment
                                    ?: rawHref.substringAfterLast('/')
                                val name = Uri.decode(rawName)
                                if (pattern.matches(name)) {
                                    files.add(WebDavSync.RemoteFile(name, rawHref, modified, size))
                                }
                            }
                            inResponse = false
                        }
                    }
                    event = parser.next()
                }
                result = files.sortedWith(
                    compareByDescending<WebDavSync.RemoteFile> { it.modified ?: "" }.thenBy { it.name }
                )
            }
        }
        return result
    }

    /**
     * Downloads and validates the archive before placing it in the selected local
     * backup folder. Invalid downloads are never left as usable backup files.
     */
    fun download(config: WebDavSync.Config, remoteFile: WebDavSync.RemoteFile): String {
        require(Regex("WEB_.+_FULL_\\d{8}_\\d{6}_\\d{3}\\.uhabitsbackup").matches(remoteFile.name)) {
            "所选完整备份文件名无效"
        }
        val tempFile = File.createTempFile("shizi-full-download-", ".uhabitsbackup", context.cacheDir)
        try {
            val remoteUrl = URL(serverUrl(config), remoteFile.href)
            withClient { client ->
                request(client, config, "GET", remoteUrl).use { response ->
                    if (response.code != 200) {
                        throw IllegalStateException("完整备份下载失败，HTTP：${response.code}")
                    }
                    val input = response.body?.byteStream()
                        ?: throw IllegalStateException("服务器返回了空备份文件")
                    input.use { source ->
                        tempFile.outputStream().use { output -> source.copyTo(output) }
                    }
                }
            }
            FullBackupManager(context).inspectBackup(Uri.fromFile(tempFile))

            val folderUri = PreferenceManager.getDefaultSharedPreferences(context)
                .getString("publicBackupFolder", null)
                ?: throw IllegalStateException("请先在设置中选择本地备份文件夹")
            val folder = DocumentFile.fromTreeUri(context, Uri.parse(folderUri))
                ?: throw IllegalStateException("无法打开所选本地备份文件夹")
            var targetName = remoteFile.name
            var suffix = 2
            while (folder.findFile(targetName) != null) {
                targetName = remoteFile.name.removeSuffix(".uhabitsbackup") +
                    "_$suffix.uhabitsbackup"
                suffix++
            }
            val outputFile = folder.createFile("application/octet-stream", targetName)
                ?: throw IllegalStateException("无法在本地备份文件夹创建文件")
            try {
                context.contentResolver.openOutputStream(outputFile.uri, "wt")?.use { output ->
                    tempFile.inputStream().use { input -> input.copyTo(output) }
                } ?: throw IllegalStateException("无法写入本地完整备份文件")
                FullBackupManager(context).inspectBackup(outputFile.uri)
                return targetName
            } catch (error: Exception) {
                outputFile.delete()
                throw error
            }
        } finally {
            tempFile.delete()
        }
    }

    private fun serverUrl(config: WebDavSync.Config): URL {
        val url = URL(config.server.trimEnd('/') + "/")
        require(url.protocol == "https") { "WebDAV 仅允许使用 HTTPS" }
        return url
    }

    private fun request(
        client: OkHttpClient,
        config: WebDavSync.Config,
        method: String,
        url: URL,
        depth: String? = null,
        body: okhttp3.RequestBody? = null
    ): okhttp3.Response {
        val base = serverUrl(config)
        require(url.protocol == "https" && url.host.equals(base.host, ignoreCase = true) &&
            url.port == base.port
        ) { "WebDAV 返回了不安全或跨服务器的文件地址" }
        val builder = Request.Builder()
            .url(url)
            .header("Authorization", Credentials.basic(config.username, config.password, Charsets.UTF_8))
        if (depth != null) builder.header("Depth", depth)
        return client.newCall(builder.method(method, body).build()).execute()
    }

    private fun withClient(block: (OkHttpClient) -> Unit) {
        val client = OkHttpClient()
        try {
            block(client)
        } finally {
            client.dispatcher.executorService.shutdown()
            client.connectionPool.evictAll()
        }
    }
}

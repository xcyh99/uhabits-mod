package org.isoron.uhabits.database

import android.content.Context
import androidx.preference.PreferenceManager
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import org.isoron.uhabits.utils.BackupFileName
import java.net.URL

/**
 * Settings-only WebDAV backup. Credentials are transport inputs and are never
 * included in the serialized settings document.
 */
class WebDavSettingsBackup(private val context: Context) {
    private val preferences by lazy {
        PreferenceManager.getDefaultSharedPreferences(context)
    }

    private val backupableKeys = setOf(
        "pref_short_toggle",
        "pref_midnight_delay",
        "pref_skip_enabled",
        "pref_unknown_enabled",
        "pref_checkmark_reverse_order",
        "pref_pure_black",
        "pref_disable_animation",
        "pref_widget_opacity",
        "pref_first_weekday",
        "pref_sticky_notifications"
    )

    private fun fileName() =
        "WEB_${BackupFileName.deviceId()}_SET.json"

    fun upload(config: WebDavSync.Config) {
        val settings = JSONObject()
        for (key in backupableKeys) {
            if (!preferences.contains(key)) continue
            when (val value = preferences.all[key]) {
                is Boolean -> settings.put(key, value)
                is Int -> settings.put(key, value)
                is Long -> settings.put(key, value)
                is Float -> settings.put(key, value.toDouble())
                is String -> settings.put(key, value)
                is Set<*> -> settings.put(key, JSONArray(value.filterIsInstance<String>()))
            }
        }
        val document = JSONObject()
            .put("format", "SHIZI_SETTINGS")
            .put("version", 1)
            .put("settings", settings)
        execute(config, "PUT", document.toString(2))
    }

    fun download(config: WebDavSync.Config) {
        val content = execute(config, "GET")
            ?: throw IllegalStateException("服务器没有找到软件设置备份")
        val document = JSONObject(content)
        require(document.optString("format") == "SHIZI_SETTINGS" &&
            document.optInt("version", -1) == 1) {
            "设置备份格式或版本不受支持"
        }
        val settings = document.optJSONObject("settings")
            ?: throw IllegalStateException("设置备份内容不完整")

        val editor = preferences.edit()
        for (key in backupableKeys) editor.remove(key)
        val keys = settings.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            if (key !in backupableKeys) continue
            when (val value = settings.get(key)) {
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Double -> editor.putFloat(key, value.toFloat())
                is String -> editor.putString(key, value)
                is JSONArray -> {
                    val values = mutableSetOf<String>()
                    for (index in 0 until value.length()) values.add(value.getString(index))
                    editor.putStringSet(key, values)
                }
            }
        }
        check(editor.commit()) { "设置已下载，但写入本机失败" }
    }

    private fun execute(
        config: WebDavSync.Config,
        method: String,
        content: String? = null
    ): String? {
        val base = URL(config.server.trimEnd('/') + "/")
        require(base.protocol == "https") { "WebDAV 仅允许使用 HTTPS" }
        val url = URL(base, fileName())
        require(url.protocol == "https" && url.host.equals(base.host, ignoreCase = true) &&
            url.port == base.port) { "WebDAV 文件地址不安全" }

        val client = OkHttpClient()
        try {
            val body = content?.toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url(url)
                .header("Authorization", Credentials.basic(
                    config.username, config.password, Charsets.UTF_8
                ))
                .method(method, body)
                .build()
            client.newCall(request).execute().use { response ->
                if (method == "PUT") {
                    if (!response.isSuccessful) {
                        throw IllegalStateException("设置备份上传失败，HTTP：${response.code}")
                    }
                    return null
                }
                if (response.code == 404) return null
                if (response.code != 200) {
                    throw IllegalStateException("设置备份下载失败，HTTP：${response.code}")
                }
                return response.body?.string()
                    ?: throw IllegalStateException("服务器返回了空设置备份")
            }
        } finally {
            client.dispatcher.executorService.shutdown()
            client.connectionPool.evictAll()
        }
    }
}

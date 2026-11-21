package org.isoron.uhabits.utils

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.preference.PreferenceManager
import java.nio.ByteBuffer
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class WebDavCredentialsStore(context: Context) {
    private val prefs = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)

    data class Credentials(val server: String, val username: String, val password: String)

    @Synchronized
    fun load(): Credentials {
        migrateLegacyValues()
        return try {
            Credentials(
                decrypt(prefs.getString(KEY_SERVER, null)),
                decrypt(prefs.getString(KEY_USERNAME, null)),
                decrypt(prefs.getString(KEY_PASSWORD, null))
            )
        } catch (_: Exception) {
            // A Keystore key may be unavailable after an OS restore or device migration.
            // Ciphertext cannot be recovered without the original device key.
            check(
                prefs.edit()
                    .remove(KEY_SERVER)
                    .remove(KEY_USERNAME)
                    .remove(KEY_PASSWORD)
                    .commit()
            ) { "无法清理失效的 WebDAV 加密凭据" }
            Credentials("", "", "")
        }
    }

    @Synchronized
    fun save(server: String, username: String, password: String) {
        val editor = prefs.edit()
            .putString(KEY_SERVER, encrypt(server))
            .putString(KEY_USERNAME, encrypt(username))
            .putString(KEY_PASSWORD, encrypt(password))
            .remove(LEGACY_SERVER)
            .remove(LEGACY_USERNAME)
            .remove(LEGACY_PASSWORD)
        check(editor.commit()) { "无法安全保存 WebDAV 凭据" }
    }

    @Synchronized
    private fun migrateLegacyValues() {
        val hasLegacy = prefs.contains(LEGACY_SERVER) ||
            prefs.contains(LEGACY_USERNAME) || prefs.contains(LEGACY_PASSWORD)
        if (!hasLegacy) return
        val server = prefs.getString(LEGACY_SERVER, null)
        val username = prefs.getString(LEGACY_USERNAME, null)
        val password = prefs.getString(LEGACY_PASSWORD, null)
        val editor = prefs.edit()
        if (server != null && !prefs.contains(KEY_SERVER)) editor.putString(KEY_SERVER, encrypt(server))
        if (username != null && !prefs.contains(KEY_USERNAME)) editor.putString(KEY_USERNAME, encrypt(username))
        if (password != null && !prefs.contains(KEY_PASSWORD)) editor.putString(KEY_PASSWORD, encrypt(password))
        editor.remove(LEGACY_SERVER).remove(LEGACY_USERNAME).remove(LEGACY_PASSWORD)
        check(editor.commit()) { "旧版 WebDAV 凭据迁移失败，原有凭据未删除" }
    }

    private fun encrypt(value: String): String {
        if (value.isEmpty()) return ""
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        val iv = cipher.iv
        return Base64.encodeToString(
            ByteBuffer.allocate(4 + iv.size + encrypted.size)
                .putInt(iv.size).put(iv).put(encrypted).array(),
            Base64.NO_WRAP
        )
    }

    private fun decrypt(encoded: String?): String {
        if (encoded.isNullOrEmpty()) return ""
        val buffer = ByteBuffer.wrap(Base64.decode(encoded, Base64.NO_WRAP))
        val ivLength = buffer.int
        require(ivLength in 12..16 && buffer.remaining() > ivLength) { "WebDAV 加密凭据格式无效" }
        val iv = ByteArray(ivLength).also(buffer::get)
        val encrypted = ByteArray(buffer.remaining()).also(buffer::get)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
        return String(cipher.doFinal(encrypted), Charsets.UTF_8)
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val KEY_ALIAS = "shizi_webdav_credentials_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_SERVER = "webdav_server_enc"
        const val KEY_USERNAME = "webdav_username_enc"
        const val KEY_PASSWORD = "webdav_password_enc"
        const val LEGACY_SERVER = "webdav_server"
        const val LEGACY_USERNAME = "webdav_username"
        const val LEGACY_PASSWORD = "webdav_password"
    }
}

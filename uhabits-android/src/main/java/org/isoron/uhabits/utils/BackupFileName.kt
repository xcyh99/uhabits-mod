package org.isoron.uhabits.utils

import android.os.Build
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Shared naming rules for exported and WebDAV database backups.
 */
object BackupFileName {
    private val brandCodes = mapOf(
        "HUAWEI" to "HW",
        "HONOR" to "HON",
        "XIAOMI" to "MI",
        "REDMI" to "MI",
        "OPPO" to "OP",
        "VIVO" to "VV",
        "SAMSUNG" to "SS",
        "APPLE" to "AP",
        "ONEPLUS" to "OPL",
        "REALME" to "RM",
        "GOOGLE" to "GG",
        "MEIZU" to "MZ",
        "MOTOROLA" to "MT"
    )

    fun deviceId(): String {
        val manufacturer = Build.MANUFACTURER.orEmpty().trim().uppercase(Locale.US)
        val brand = brandCodes[manufacturer]
            ?: manufacturer.replace(Regex("[^A-Z0-9]"), "").take(3).ifBlank { "AND" }
        val model = Build.MODEL.orEmpty().trim()
            .replace(Regex("[^A-Za-z0-9._-]+"), "-")
            .trim('-', '_', '.')
            .ifBlank { "DEVICE" }
        return "${brand}_${model}"
    }

    fun timestamp(date: Date = Date()): String =
        SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(date)

    fun date(date: Date = Date()): String =
        SimpleDateFormat("yyyyMMdd", Locale.US).format(date)
}

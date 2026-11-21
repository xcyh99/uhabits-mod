package org.isoron.uhabits.core.sync

import org.isoron.platform.time.LocalDate
import org.isoron.uhabits.core.database.EntryData
import org.isoron.uhabits.core.database.HabitData
import org.isoron.uhabits.core.models.Entry

/**
 * Converts database rows into portable sync identities.
 *
 * Local numeric database IDs are used only to associate repetitions with a
 * habit inside the same snapshot. They are never included in cross-device keys.
 */
object TwoWaySyncSnapshotMapper {
    fun habits(rows: List<HabitData>): List<SyncHabitIdentity> {
        require(rows.all { it.uuid?.isNotBlank() == true }) {
            "A habit has no UUID; sync must stop rather than guess its identity"
        }
        require(rows.all { it.id != null }) {
            "A habit has no local database ID"
        }
        return rows.map { row ->
            val uuid = row.uuid!!
            SyncHabitIdentity(
                uuid = uuid,
                matchKey = row.name.trim().lowercase(),
                displayName = row.name,
                settingsFingerprint = fingerprint(
                    row.name,
                    row.description,
                    row.question,
                    row.freqNum.toString(),
                    row.freqDen.toString(),
                    row.color.toString(),
                    row.position.toString(),
                    row.reminderHour?.toString().orEmpty(),
                    row.reminderMin?.toString().orEmpty(),
                    row.reminderDays.toString(),
                    row.highlight.toString(),
                    row.archived.toString(),
                    row.type.toString(),
                    row.targetValue.toString(),
                    row.targetType.toString(),
                    row.unit,
                    row.category
                )
            )
        }
    }

    fun entries(
        habitRows: List<HabitData>,
        entryRows: List<EntryData>
    ): List<SyncEntryIdentity> {
        val uuidByLocalId = habitRows.associate { row ->
            val id = row.id ?: error("A habit has no local database ID")
            val uuid = row.uuid?.takeIf { it.isNotBlank() }
                ?: error("A habit has no UUID; sync must stop rather than guess its identity")
            id to uuid
        }
        return entryRows.mapNotNull { row ->
            val habitId = row.habitId ?: error("A repetition has no habit ID")
            val habitUuid = uuidByLocalId[habitId]
                ?: error("A repetition refers to a habit missing from this snapshot")
            if (row.value == Entry.UNKNOWN) {
                null
            } else {
                SyncEntryIdentity(
                    habitUuid = habitUuid,
                    dayKey = LocalDate.fromUnixTime(row.timestamp).toCSVString(),
                    value = row.value,
                    notes = row.notes
                )
            }
        }
    }

    private fun fingerprint(vararg parts: String): String =
        parts.joinToString(separator = "") { "${it.length}:$it" }
}

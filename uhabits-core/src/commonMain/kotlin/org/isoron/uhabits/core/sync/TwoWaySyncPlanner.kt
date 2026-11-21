package org.isoron.uhabits.core.sync

/**
 * Immutable input used to compare one device's habit data with another device.
 * matchKey is only a hint for manual matching when UUIDs differ.
 */
data class SyncHabitIdentity(
    val uuid: String,
    val matchKey: String,
    val displayName: String
)

data class SyncEntryIdentity(
    val habitUuid: String,
    val dayKey: String,
    val value: Int,
    val notes: String = ""
)

data class SyncHabitMatch(
    val localUuid: String,
    val remoteUuid: String,
    val displayName: String
)

data class SyncEntryConflict(
    val habitUuid: String,
    val dayKey: String,
    val local: SyncEntryIdentity,
    val remote: SyncEntryIdentity
)

data class TwoWaySyncPlan(
    val matchedHabitUuids: Set<String>,
    val remoteOnlyHabits: List<SyncHabitIdentity>,
    val possibleLegacyMatches: List<SyncHabitMatch>,
    val remoteOnlyEntries: List<SyncEntryIdentity>,
    val remoteEntriesForNewHabits: List<SyncEntryIdentity>,
    val identicalEntries: Int,
    val conflictingEntries: List<SyncEntryConflict>
) {
    val hasConflicts: Boolean
        get() = conflictingEntries.isNotEmpty()

    val totalRemoteAdditions: Int
        get() = remoteOnlyHabits.size + remoteOnlyEntries.size + remoteEntriesForNewHabits.size
}

/**
 * Builds a read-only merge plan. It never writes to either database.
 * Entries are matched by (habit UUID, day). Different values or notes on the
 * same key are reported as conflicts; neither side wins automatically.
 * Legacy habits with different UUIDs are only suggested for manual matching.
 */
object TwoWaySyncPlanner {
    fun build(
        localHabits: List<SyncHabitIdentity>,
        remoteHabits: List<SyncHabitIdentity>,
        localEntries: List<SyncEntryIdentity>,
        remoteEntries: List<SyncEntryIdentity>
    ): TwoWaySyncPlan {
        val localByUuid = localHabits.associateBy { it.uuid }
        val remoteByUuid = remoteHabits.associateBy { it.uuid }
        val matched = localByUuid.keys.intersect(remoteByUuid.keys)

        val remoteOnlyHabits = remoteHabits.filter { it.uuid !in localByUuid }
        val localOnlyHabits = localHabits.filter { it.uuid !in remoteByUuid }
        val localByMatchKey = localOnlyHabits.filter { it.matchKey.isNotBlank() }.groupBy { it.matchKey }
        val remoteByMatchKey = remoteOnlyHabits.filter { it.matchKey.isNotBlank() }.groupBy { it.matchKey }

        val suggestions = mutableListOf<SyncHabitMatch>()
        for ((key, remoteCandidates) in remoteByMatchKey) {
            val localCandidates = localByMatchKey[key] ?: continue
            if (remoteCandidates.size == 1 && localCandidates.size == 1) {
                val local = localCandidates.single()
                val remote = remoteCandidates.single()
                suggestions.add(SyncHabitMatch(local.uuid, remote.uuid, remote.displayName))
            }
        }

        val localRecords = localEntries.filter { it.habitUuid in matched }
            .associateBy { it.habitUuid to it.dayKey }
        val additions = mutableListOf<SyncEntryIdentity>()
        val conflicts = mutableListOf<SyncEntryConflict>()
        var identical = 0

        for (remote in remoteEntries.filter { it.habitUuid in matched }) {
            val local = localRecords[remote.habitUuid to remote.dayKey]
            when {
                local == null -> additions.add(remote)
                local.value == remote.value && local.notes == remote.notes -> identical++
                else -> conflicts.add(SyncEntryConflict(remote.habitUuid, remote.dayKey, local, remote))
            }
        }

        val newHabitUuids = remoteOnlyHabits.mapTo(HashSet()) { it.uuid }
        val entriesForNewHabits = remoteEntries.filter { it.habitUuid in newHabitUuids }

        return TwoWaySyncPlan(
            matchedHabitUuids = matched,
            remoteOnlyHabits = remoteOnlyHabits,
            possibleLegacyMatches = suggestions,
            remoteOnlyEntries = additions,
            remoteEntriesForNewHabits = entriesForNewHabits,
            identicalEntries = identical,
            conflictingEntries = conflicts
        )
    }
}

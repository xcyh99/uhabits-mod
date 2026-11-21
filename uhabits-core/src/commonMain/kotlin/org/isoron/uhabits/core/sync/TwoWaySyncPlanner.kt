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
    val localOnlyHabits: List<SyncHabitIdentity>,
    val remoteOnlyHabits: List<SyncHabitIdentity>,
    val possibleLegacyMatches: List<SyncHabitMatch>,
    val localOnlyEntries: List<SyncEntryIdentity>,
    val remoteOnlyEntries: List<SyncEntryIdentity>,
    val localEntriesForNewHabits: List<SyncEntryIdentity>,
    val remoteEntriesForNewHabits: List<SyncEntryIdentity>,
    val identicalEntries: Int,
    val conflictingEntries: List<SyncEntryConflict>
) {
    val hasConflicts: Boolean
        get() = conflictingEntries.isNotEmpty()

    val totalLocalAdditions: Int
        get() = localOnlyHabits.size + localOnlyEntries.size + localEntriesForNewHabits.size

    val totalRemoteAdditions: Int
        get() = remoteOnlyHabits.size + remoteOnlyEntries.size + remoteEntriesForNewHabits.size
}

/**
 * Builds a read-only, symmetric merge plan. It never writes to either database.
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

        val localOnlyHabits = localHabits.filter { it.uuid !in remoteByUuid }
        val remoteOnlyHabits = remoteHabits.filter { it.uuid !in localByUuid }
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

        val localMatched = localEntries.filter { it.habitUuid in matched }
            .associateBy { it.habitUuid to it.dayKey }
        val remoteMatched = remoteEntries.filter { it.habitUuid in matched }
            .associateBy { it.habitUuid to it.dayKey }

        val localOnlyEntries = mutableListOf<SyncEntryIdentity>()
        val remoteOnlyEntries = mutableListOf<SyncEntryIdentity>()
        val conflicts = mutableListOf<SyncEntryConflict>()
        var identical = 0

        for ((key, remote) in remoteMatched) {
            val local = localMatched[key]
            when {
                local == null -> remoteOnlyEntries.add(remote)
                local.value == remote.value && local.notes == remote.notes -> identical++
                else -> conflicts.add(SyncEntryConflict(remote.habitUuid, remote.dayKey, local, remote))
            }
        }
        for ((key, local) in localMatched) {
            if (key !in remoteMatched) localOnlyEntries.add(local)
        }

        val localNewHabitUuids = localOnlyHabits.mapTo(HashSet()) { it.uuid }
        val remoteNewHabitUuids = remoteOnlyHabits.mapTo(HashSet()) { it.uuid }

        return TwoWaySyncPlan(
            matchedHabitUuids = matched,
            localOnlyHabits = localOnlyHabits,
            remoteOnlyHabits = remoteOnlyHabits,
            possibleLegacyMatches = suggestions,
            localOnlyEntries = localOnlyEntries,
            remoteOnlyEntries = remoteOnlyEntries,
            localEntriesForNewHabits = localEntries.filter { it.habitUuid in localNewHabitUuids },
            remoteEntriesForNewHabits = remoteEntries.filter { it.habitUuid in remoteNewHabitUuids },
            identicalEntries = identical,
            conflictingEntries = conflicts
        )
    }
}

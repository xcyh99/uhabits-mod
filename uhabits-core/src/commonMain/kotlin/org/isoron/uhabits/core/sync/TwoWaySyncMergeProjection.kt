package org.isoron.uhabits.core.sync

/**
 * Computes the exact portable result of a user-approved merge.
 * This is a pure projection: it does not open or modify a database.
 *
 * Inputs must already have confirmed legacy UUID pairings applied to the
 * remote snapshot. The returned snapshot is the target for a later writer.
 */
object TwoWaySyncMergeProjection {
    data class Snapshot(
        val habits: List<SyncHabitIdentity>,
        val entries: List<SyncEntryIdentity>
    )

    fun build(
        local: Snapshot,
        remote: Snapshot,
        plan: TwoWaySyncPlan,
        resolution: TwoWaySyncResolution
    ): Snapshot {
        val expectedEntryConflicts = plan.conflictingEntries.map { it.habitUuid to it.dayKey }.toSet()
        val entryChoices = resolution.entryConflictChoices.associateBy { it.habitUuid to it.dayKey }
        require(entryChoices.keys == expectedEntryConflicts) {
            "打卡冲突选择与当前差异计划不一致，请重新预览"
        }

        val expectedSettingsConflicts = plan.conflictingHabitSettings.map { it.habitUuid }.toSet()
        val settingsChoices = resolution.habitSettingsConflictChoices.associateBy { it.habitUuid }
        require(settingsChoices.keys == expectedSettingsConflicts) {
            "习惯设置选择与当前差异计划不一致，请重新预览"
        }

        val localByUuid = local.habits.associateBy { it.uuid }
        val remoteByUuid = remote.habits.associateBy { it.uuid }
        require(localByUuid.size == local.habits.size && remoteByUuid.size == remote.habits.size) {
            "习惯 UUID 重复，已停止合并"
        }
        require(localByUuid.keys.intersect(remoteByUuid.keys) == plan.matchedHabitUuids) {
            "本机或服务器数据已变化，请重新预览"
        }

        val mergedHabits = (localByUuid.keys + remoteByUuid.keys).map { uuid ->
            val localHabit = localByUuid[uuid]
            val remoteHabit = remoteByUuid[uuid]
            when {
                localHabit == null -> remoteHabit!!
                remoteHabit == null -> localHabit
                uuid in expectedSettingsConflicts ->
                    if (settingsChoices.getValue(uuid).keep == SyncConflictSide.LOCAL) localHabit else remoteHabit
                else -> {
                    require(
                        localHabit.settingsFingerprint.isBlank() ||
                            remoteHabit.settingsFingerprint.isBlank() ||
                            localHabit.settingsFingerprint == remoteHabit.settingsFingerprint
                    ) { "习惯设置发生未列出的变化，请重新预览" }
                    localHabit
                }
            }
        }

        val localEntryMap = local.entries.associateBy { it.habitUuid to it.dayKey }
        val remoteEntryMap = remote.entries.associateBy { it.habitUuid to it.dayKey }
        require(localEntryMap.size == local.entries.size && remoteEntryMap.size == remote.entries.size) {
            "打卡记录存在重复日期，已停止合并"
        }
        val mergedEntries = LinkedHashMap<Pair<String, String>, SyncEntryIdentity>()
        for ((key, entry) in localEntryMap) mergedEntries[key] = entry
        for ((key, remoteEntry) in remoteEntryMap) {
            val localEntry = localEntryMap[key]
            if (localEntry == null) {
                mergedEntries[key] = remoteEntry
            } else if (localEntry.value == remoteEntry.value && localEntry.notes == remoteEntry.notes) {
                // Identical records are kept once.
            } else {
                val choice = entryChoices[key]
                    ?: throw IllegalArgumentException("存在未选择的打卡冲突，请重新预览")
                mergedEntries[key] =
                    if (choice.keep == SyncConflictSide.LOCAL) localEntry else remoteEntry
            }
        }

        val habitUuids = mergedHabits.mapTo(HashSet()) { it.uuid }
        require(mergedEntries.values.all { it.habitUuid in habitUuids }) {
            "打卡记录引用了不存在的习惯，已停止合并"
        }
        require(mergedEntries.keys.size == mergedEntries.size)
        return Snapshot(
            habits = mergedHabits.sortedBy { it.uuid },
            entries = mergedEntries.values.sortedWith(compareBy<SyncEntryIdentity> { it.habitUuid }.thenBy { it.dayKey })
        )
    }
}

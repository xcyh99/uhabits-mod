package org.isoron.uhabits.core.sync

/** Applies only user-confirmed legacy habit pairings to a remote snapshot. */
object TwoWaySyncSnapshotRemapper {
    data class Snapshot(
        val habits: List<SyncHabitIdentity>,
        val entries: List<SyncEntryIdentity>
    )

    fun remapRemote(
        localHabits: List<SyncHabitIdentity>,
        remoteHabits: List<SyncHabitIdentity>,
        remoteEntries: List<SyncEntryIdentity>,
        confirmedPairs: List<SyncLegacyHabitPair>
    ): Snapshot {
        val localUuids = localHabits.map { it.uuid }
        val remoteUuids = remoteHabits.map { it.uuid }
        require(localUuids.size == localUuids.toSet().size) { "本机习惯 UUID 重复，无法安全配对" }
        require(remoteUuids.size == remoteUuids.toSet().size) { "服务器习惯 UUID 重复，无法安全配对" }

        val localOnly = localUuids.toSet() - remoteUuids.toSet()
        val remoteOnly = remoteUuids.toSet() - localUuids.toSet()
        require(confirmedPairs.all { it.localUuid in localOnly && it.remoteUuid in remoteOnly }) {
            "旧习惯配对必须来自两侧尚未匹配的习惯"
        }
        require(confirmedPairs.map { it.localUuid }.toSet().size == confirmedPairs.size) {
            "一个本机习惯不能配对多个服务器习惯"
        }
        require(confirmedPairs.map { it.remoteUuid }.toSet().size == confirmedPairs.size) {
            "一个服务器习惯不能配对多个本机习惯"
        }

        val uuidMap = confirmedPairs.associate { it.remoteUuid to it.localUuid }
        val mappedHabits = remoteHabits.map { habit ->
            habit.copy(uuid = uuidMap[habit.uuid] ?: habit.uuid)
        }
        require(mappedHabits.map { it.uuid }.toSet().size == mappedHabits.size) {
            "配对后服务器习惯 UUID 重复，已停止同步"
        }
        val remoteUuidSet = remoteUuids.toSet()
        require(remoteEntries.all { it.habitUuid in remoteUuidSet }) {
            "服务器打卡记录引用了不存在的习惯，已停止同步"
        }
        val mappedEntries = remoteEntries.map { entry ->
            entry.copy(habitUuid = uuidMap[entry.habitUuid] ?: entry.habitUuid)
        }
        return Snapshot(mappedHabits, mappedEntries)
    }
}

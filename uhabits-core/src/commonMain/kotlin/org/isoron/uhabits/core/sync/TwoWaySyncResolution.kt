package org.isoron.uhabits.core.sync

enum class SyncConflictSide {
    LOCAL,
    REMOTE
}

data class SyncEntryConflictChoice(
    val habitUuid: String,
    val dayKey: String,
    val keep: SyncConflictSide
)

data class SyncHabitSettingsConflictChoice(
    val habitUuid: String,
    val keep: SyncConflictSide
)

data class SyncLegacyHabitPair(
    val localUuid: String,
    val remoteUuid: String
)

/**
 * Explicit user decisions required before a future merge writer may run.
 * This class validates choices only; it performs no database writes.
 */
data class TwoWaySyncResolution(
    val entryConflictChoices: List<SyncEntryConflictChoice>,
    val habitSettingsConflictChoices: List<SyncHabitSettingsConflictChoice>,
    val legacyHabitPairs: List<SyncLegacyHabitPair>
) {
    companion object {
        fun validate(
            plan: TwoWaySyncPlan,
            entryChoices: List<SyncEntryConflictChoice>,
            settingsChoices: List<SyncHabitSettingsConflictChoice>,
            legacyPairs: List<SyncLegacyHabitPair>
        ): TwoWaySyncResolution {
            val requiredEntryKeys = plan.conflictingEntries
                .map { it.habitUuid to it.dayKey }.toSet()
            val suppliedEntryKeys = entryChoices.map { it.habitUuid to it.dayKey }
            require(suppliedEntryKeys.size == suppliedEntryKeys.toSet().size) {
                "同一条打卡冲突不能重复选择"
            }
            require(suppliedEntryKeys.toSet() == requiredEntryKeys) {
                "每一条打卡冲突都必须明确选择保留本机或服务器版本"
            }

            val requiredSettingKeys = plan.conflictingHabitSettings.map { it.habitUuid }.toSet()
            val suppliedSettingKeys = settingsChoices.map { it.habitUuid }
            require(suppliedSettingKeys.size == suppliedSettingKeys.toSet().size) {
                "同一个习惯设置冲突不能重复选择"
            }
            require(suppliedSettingKeys.toSet() == requiredSettingKeys) {
                "每一项习惯设置冲突都必须明确选择保留本机或服务器版本"
            }

            val localOnly = plan.localOnlyHabits.map { it.uuid }.toSet()
            val remoteOnly = plan.remoteOnlyHabits.map { it.uuid }.toSet()
            require(legacyPairs.all { it.localUuid in localOnly && it.remoteUuid in remoteOnly }) {
                "旧习惯配对必须来自本次同步两侧尚未匹配的习惯"
            }
            require(legacyPairs.map { it.localUuid }.toSet().size == legacyPairs.size) {
                "一个本机习惯不能配对多个服务器习惯"
            }
            require(legacyPairs.map { it.remoteUuid }.toSet().size == legacyPairs.size) {
                "一个服务器习惯不能配对多个本机习惯"
            }

            return TwoWaySyncResolution(
                entryConflictChoices = entryChoices.toList(),
                habitSettingsConflictChoices = settingsChoices.toList(),
                legacyHabitPairs = legacyPairs.toList()
            )
        }
    }
}

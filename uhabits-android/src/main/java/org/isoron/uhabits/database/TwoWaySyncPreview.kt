package org.isoron.uhabits.database

import org.isoron.uhabits.core.sync.TwoWaySyncPlan
import org.isoron.uhabits.core.sync.TwoWaySyncPlanner

/**
 * Produces a read-only merge preview from the active local database and a
 * staged WebDAV database. The temporary remote file is always removed.
 */
class TwoWaySyncPreview(
    private val webDavSync: WebDavSync,
    private val snapshotReader: DatabaseSyncSnapshotReader = DatabaseSyncSnapshotReader()
) {
    fun createPlan(
        config: WebDavSync.Config,
        remoteFile: WebDavSync.RemoteFile
    ): TwoWaySyncPlan {
        val stagedFile = webDavSync.downloadToTemporaryFile(config, remoteFile)
        try {
            val local = snapshotReader.readLocal()
            val remote = snapshotReader.readRemote(stagedFile)
            return TwoWaySyncPlanner.build(
                localHabits = local.habits,
                remoteHabits = remote.habits,
                localEntries = local.entries,
                remoteEntries = remote.entries
            )
        } finally {
            stagedFile.delete()
        }
    }
}

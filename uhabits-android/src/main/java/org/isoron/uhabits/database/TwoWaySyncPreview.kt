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
    data class Session(
        val plan: TwoWaySyncPlan,
        val local: DatabaseSyncSnapshotReader.Snapshot,
        val remote: DatabaseSyncSnapshotReader.Snapshot
    )

    fun createSession(
        config: WebDavSync.Config,
        remoteFile: WebDavSync.RemoteFile
    ): Session {
        val stagedFile = webDavSync.downloadToTemporaryFile(config, remoteFile)
        try {
            val local = snapshotReader.readLocal()
            val remote = snapshotReader.readRemote(stagedFile)
            val plan = TwoWaySyncPlanner.build(
                localHabits = local.habits,
                remoteHabits = remote.habits,
                localEntries = local.entries,
                remoteEntries = remote.entries
            )
            return Session(plan, local, remote)
        } finally {
            stagedFile.delete()
        }
    }

    fun createPlan(
        config: WebDavSync.Config,
        remoteFile: WebDavSync.RemoteFile
    ): TwoWaySyncPlan = createSession(config, remoteFile).plan
}

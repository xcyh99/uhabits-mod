# Full-app backup module rules

This document governs the portable `.uhabitsbackup` format only. It does not change the legacy database export, database import, or WebDAV database synchronization paths.

## One full-backup pipeline

- Local manual backup and local automatic backup both use `FullBackupManager.createBackup()`.
- WebDAV full-backup upload uses the same `FullBackupManager.createBackup()`.
- Local restore and validation of downloaded WebDAV archives use `FullBackupManager.inspectBackup()` and the same restore pipeline.
- WebDAV settings-only backup remains a separate, intentionally limited feature.

The main SQLite database is a core payload managed directly by `FullBackupManager`. Data stored in the existing main database is therefore included without adding another module.

## When a feature needs a module

Every feature change must first identify where its persistent data lives:

1. Data in the main SQLite database is already included.
2. Data in AndroidX default shared preferences is already included by the `app_preferences` module, except device-local values.
3. Data in a separate SharedPreferences file, JSON file, or other persistent store must be represented by a `FullBackupModule` and added to `FullBackupModuleRegistry.create()`.
4. Pure in-memory state and Android Launcher-owned layout are not app-persisted data and are not portable backup payloads.

A module must declare:
- a stable unique `id`;
- stable archive `entries` paths;
- `export` and `validate` logic;
- a pre-restore `snapshot`;
- `restore` and `rollback` logic.

The manager automatically includes registered module payloads in the archive, SHA-256 checksums, manifest, validation, restore staging, and failure rollback. A module must not modify the live app data during export or validation.

## Compatibility rules

- A newly added module is optional by default: leave `requiredEntries` empty unless a deliberate format migration makes it mandatory.
- Existing core modules that every supported archive must contain explicitly declare their entries as required.
- If any entry of a multi-entry module is present, all of that module's entries must be present.
- Keep the current format version when adding an optional module in a backward-compatible way. Bump and migrate the format only when the archive contract itself changes.
- Unknown module IDs or payload paths are rejected rather than silently restoring only part of a newer backup.
- Device-bound secrets, SAF folder grants, ringtone URIs, and screen-specific window coordinates must not be migrated to another device.

## Restore safety

Before applying a restore, the manager validates the archive and stages it. Before changing live data, it snapshots every module that will be restored. If database or module restoration fails, it attempts to restore the database and module snapshots and keeps the staged archive for retry.

## Current registered modules

- `time_records`: separate time-record data.
- `time_record_widget_links`: time-record widget association settings.
- `app_preferences`: portable app preferences.
- `floating_timer`: portable timer mode and remaining/elapsed value; restored paused. Screen coordinates stay on the current device.

The desktop doodle currently has no persisted drawing history to back up. Android Launcher widget placement is owned by the Launcher and is not portable app data.

package com.levanyon.meditakip.data

import java.io.InputStream
import java.io.OutputStream

/** V2 boundary. Implement with Android's CreateDocument/OpenDocument contracts, never cloud SDKs.
 * A versioned UTF-8 archive must include people, medications, all revisions, logs and settings.
 * Import must validate schema, size, relationships and dates before a single Room transaction.
 * Exclude alarm dispatch state; reschedule after import. Require a replacement confirmation.
 * No V1 implementation is exposed to users.
 */
interface LocalBackup {
    suspend fun exportTo(destination: OutputStream)
    suspend fun inspect(source: InputStream): BackupSummary
    suspend fun importValidated(source: InputStream)
}
data class BackupSummary(val formatVersion: Int, val people: Int, val medications: Int, val completedRecords: Int)

package com.cagri.real.platform.android.sync

import org.junit.Assert.*
import org.junit.Test

/** A file-scoped rebuild must never create tombstones in another file's record partition. */
class SyncProjectionPartitionTest {
    private val records = listOf(
        Triple(SyncProjection.CARDS, "CARD", "1"),
        Triple(SyncProjection.CATEGORIES, "CATEGORY", "2"),
        Triple(SyncProjection.CATEGORIES, "PREFERENCE", "colors/Today"),
        Triple(SyncProjection.MOODS, "MOOD", "3"),
        Triple(SyncProjection.PLANNER, "PLANNER", "1"),
        Triple(SyncProjection.WATER, "DRINK", "4"),
        Triple(SyncProjection.WATER, "PREFERENCE", "water/goal"),
        Triple(SyncProjection.DESCRIPTIONS, "PREFERENCE", "description/custom_2"),
        Triple(SyncProjection.UI, "PREFERENCE", "ui/sort_order")
    )
    @Test fun eachRecordHasExactlyOneOwningFile() {
        records.forEach { (owner, kind, key) ->
            assertEquals(listOf(owner), SyncProjection.files.filter { SyncProjection.ownsDocument(it, kind, key) })
        }
    }
    @Test fun pendingLocalFilesNeverOwnCloudRecords() {
        SyncProjection.localFiles.forEach { file ->
            records.forEach { (_, kind, key) -> assertFalse(SyncProjection.ownsDocument(file, kind, key)) }
        }
    }
    @Test fun preferenceNamespacesDoNotOverlap() {
        assertFalse(SyncProjection.ownsDocument(SyncProjection.CATEGORIES, "PREFERENCE", "ui/colors/Today"))
        assertFalse(SyncProjection.ownsDocument(SyncProjection.WATER, "PREFERENCE", "water/other"))
        assertFalse(SyncProjection.ownsDocument(SyncProjection.UI, "CARD", "ui/sort_order"))
        assertFalse(SyncProjection.ownsDocument("unknown", "CARD", "1"))
    }
}

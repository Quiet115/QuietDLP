package com.quietdlp.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryGroupsTest {
    private fun item(
        id: String,
        group: String? = null,
        index: Int = 0,
        state: State = State.DONE,
        title: String? = null,
    ) = DownloadItem(
        id = id, url = "https://example.com/$id", title = "T $id", quality = Quality(Kind.VIDEO),
        state = state, groupId = group, groupIndex = index, groupTitle = title,
    )

    @Test
    fun groupSitsWhereItsFirstFileWasAndFilesGoInOrder() {
        val list = listOf(
            item("solo1"),
            item("c", "g", 2, title = "My list"),
            item("a", "g", 0),
            item("solo2"),
            item("b", "g", 1),
        )
        val rows = HistoryGroups.rows(list)
        assertEquals(3, rows.size)
        assertTrue(rows[0] is HistoryRow.Single)
        val group = rows[1] as HistoryRow.Group
        assertEquals("My list", group.title)
        assertEquals(listOf("a", "b", "c"), group.items.map { it.id })
        assertEquals("solo2", (rows[2] as HistoryRow.Single).item.id)
    }

    @Test
    fun aGroupWithOneFileLooksLikeASingleFile() {
        val rows = HistoryGroups.rows(listOf(item("a", "g", 0), item("x")))
        assertTrue(rows.all { it is HistoryRow.Single })
    }

    @Test
    fun countsAndCoverOfAGroup() {
        val group = HistoryGroups.rows(
            listOf(
                item("a", "g", 0, State.FAILED),
                item("b", "g", 1, State.DONE).copy(savedUris = listOf("content://x")),
                item("c", "g", 2, State.DOWNLOADING),
                item("d", "g", 3, State.QUEUED),
            ),
        ).single() as HistoryRow.Group
        assertEquals(4, group.total)
        assertEquals(1, group.done)
        assertEquals(1, group.failed)
        assertEquals(2, group.active)
        assertEquals("b", group.cover.id)
    }

    @Test
    fun aFileWaitsForTheEarlierOnesThatAreStillRunning() {
        val all = listOf(
            item("a", "g", 0, State.DOWNLOADING),
            item("b", "g", 1, State.DOWNLOADING),
            item("c", "g", 2, State.PROCESSING),
        )
        assertFalse(HistoryGroups.mustWait(all, all[0]))
        assertTrue(HistoryGroups.mustWait(all, all[1]))
        assertTrue(HistoryGroups.mustWait(all, all[2]))
    }

    @Test
    fun finishedOrFailedEarlierFilesDoNotBlock() {
        val all = listOf(
            item("a", "g", 0, State.DONE),
            item("b", "g", 1, State.FAILED),
            item("c", "g", 2, State.PROCESSING),
        )
        assertFalse(HistoryGroups.mustWait(all, all[2]))
    }

    @Test
    fun otherGroupsAndLooseFilesNeverBlock() {
        val all = listOf(item("x", "other", 0, State.DOWNLOADING), item("c", "g", 2, State.PROCESSING), item("solo"))
        assertFalse(HistoryGroups.mustWait(all, all[1]))
        assertFalse(HistoryGroups.mustWait(all, all[2]))
    }

    @Test
    fun anEarlierFileStillInTheQueueDoesNotBlock() {
        // Un reintento que espera al final de la cola no puede frenar a los que ya van por delante.
        val all = listOf(item("a", "g", 0, State.QUEUED), item("b", "g", 1, State.PROCESSING))
        assertFalse(HistoryGroups.mustWait(all, all[1]) { it != "a" })
        assertTrue(HistoryGroups.mustWait(all, all[1]) { true })
    }
}

package com.quietdlp.app.download

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ErrorsNetworkTest {
    @Test
    fun recognizesConnectionDrops() {
        listOf(
            "ERROR: Unable to download webpage: <urlopen error [Errno 7] Unable to resolve host>",
            "ERROR: [youtube] AAA: Unable to download API page: ('Connection aborted.', ...)",
            "ERROR: unable to download video data: HTTPSConnectionPool(host='x', port=443): Read timed out.",
            "ERROR: Network is unreachable",
            "ERROR: Temporary failure in name resolution",
            "ERROR: ... IncompleteRead(100 bytes read, 20 more expected)",
        ).forEach { assertTrue(it, Errors.isNetworkText(it)) }
    }

    @Test
    fun otherErrorsAreNotNetwork() {
        listOf(
            "ERROR: Unsupported URL: https://example.com/",
            "ERROR: Sign in to confirm your age",
            "ERROR: Video unavailable",
            "Not enough free space (50 MB left). Free some up and retry.",
        ).forEach { assertFalse(it, Errors.isNetworkText(it)) }
    }
}

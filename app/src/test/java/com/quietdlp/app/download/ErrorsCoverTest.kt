package com.quietdlp.app.download

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ErrorsCoverTest {
    private fun err(msg: String) = RuntimeException(msg)

    @Test
    fun aFailureEmbeddingTheThumbnailCanBeRetriedWithoutIt() {
        assertTrue(Errors.isCoverProblem(err("ERROR: Postprocessing: Supported filetypes for thumbnail embedding are: mp3, mkv/mka")))
        assertTrue(Errors.isCoverProblem(err("[EmbedThumbnail] x\nERROR: Postprocessing: Unable to embed thumbnail")))
    }

    @Test
    fun otherFailuresAreNotCoverProblems() {
        assertFalse(Errors.isCoverProblem(err("ERROR: unable to download video data: HTTP Error 403: Forbidden")))
        assertFalse(Errors.isCoverProblem(err("ERROR: [youtube] abc: Video unavailable")))
        assertFalse(Errors.isCoverProblem(err("ERROR: Postprocessing: Conversion failed!")))
    }
}

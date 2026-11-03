package com.quietdlp.app.update

import org.junit.Assert.assertEquals
import org.junit.Test

class AppIdentityTest {
    @Test
    fun hexIsLowercaseWithTwoDigitsPerByte() {
        assertEquals("00ff0a9b", AppIdentity.hex(byteArrayOf(0, -1, 10, -101)))
    }

    @Test
    fun theSignatureIsShownInGroupsOfEight() {
        val hex = "9a9bd88d2c2e581f6d51ff790e340c2874412a619d315c3bad5e3f3b32d01ac1"
        assertEquals(
            "9a9bd88d 2c2e581f 6d51ff79 0e340c28 74412a61 9d315c3b ad5e3f3b 32d01ac1",
            AppIdentity.grouped(hex),
        )
        assertEquals("", AppIdentity.grouped(""))
    }
}

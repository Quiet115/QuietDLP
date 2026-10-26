package com.quietdlp.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class FolderNameTest {
    @Test
    fun keepsNormalNames() {
        assertEquals("QuietDLP", FolderName.clean("QuietDLP"))
        assertEquals("Mis descargas 2026", FolderName.clean("  Mis descargas 2026 "))
    }

    @Test
    fun removesCharactersNotAllowedInFolders() {
        assertEquals("ab", FolderName.clean("a/b"))
        assertEquals("abc", FolderName.clean("a:b*c?"))
        assertEquals("x", FolderName.clean("..x.."))
    }

    @Test
    fun emptyOrJunkFallsBackToDefault() {
        assertEquals(FolderName.DEFAULT, FolderName.clean(""))
        assertEquals(FolderName.DEFAULT, FolderName.clean("   "))
        assertEquals(FolderName.DEFAULT, FolderName.clean("///"))
    }

    @Test
    fun longNamesAreCut() {
        assertEquals(40, FolderName.clean("x".repeat(100)).length)
    }
}

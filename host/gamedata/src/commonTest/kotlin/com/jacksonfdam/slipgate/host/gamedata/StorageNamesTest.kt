package com.jacksonfdam.slipgate.host.gamedata

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StorageNamesTest {
    @Test
    fun anOrdinaryNameSurvivesUnchanged() {
        assertEquals("freedoom1.wad", safeStorageName("freedoom1.wad"))
    }

    /** The whole point: a picked file must not be able to name a place outside its own folder. */
    @Test
    fun separatorsAndParentDirectoriesCannotEscape() {
        val name = safeStorageName("../../etc/passwd")

        assertTrue('/' !in name, "a separator survived in $name")
        assertTrue(!name.startsWith("."), "a leading dot survived in $name")
    }

    @Test
    fun aBackslashIsNoSaferThanASlash() {
        assertTrue('\\' !in safeStorageName("..\\windows\\system32"))
    }

    @Test
    fun anAbsurdlyLongNameIsShortened() {
        val name = safeStorageName("a".repeat(500) + ".wad")

        assertTrue(name.length <= 64, "the name is ${name.length} characters")
        assertTrue(name.endsWith(".wad"), "the extension was lost from $name")
    }

    @Test
    fun eachUnsafeCharacterBecomesOneSafeOne() {
        assertEquals("___", safeStorageName("///"))
    }

    @Test
    fun anEmptyNameStillNamesSomething() {
        assertEquals("data", safeStorageName(""))
    }
}

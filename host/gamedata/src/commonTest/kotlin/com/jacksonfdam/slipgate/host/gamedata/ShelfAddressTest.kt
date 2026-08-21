package com.jacksonfdam.slipgate.host.gamedata

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What may be stored as a shelf address.
 *
 * The half-typed cases are the ones that matter: `https:` was stored on a real phone and crashed the
 * app on every launch, because the platform client answered it by throwing where nothing caught. The
 * fetcher no longer crashes on it, and this is why it is never written down in the first place.
 */
class ShelfAddressTest {
    @Test
    fun `accepts the two shapes a player is told to type`() {
        assertNull(ShelfAddress.problem("http://192.168.0.10:8600"))
        assertNull(ShelfAddress.problem("https://slipgate.example/beacon/deadbeefdeadbeefdeadbeef"))
        assertNull(ShelfAddress.problem("http://nas.local:8600?key=abc123"))
    }

    @Test
    fun `accepts an empty field because that is a player with no shelf`() {
        assertNull(ShelfAddress.problem(""))
        assertNull(ShelfAddress.problem("   "))
        assertTrue(ShelfAddress.usable(""))
    }

    @Test
    fun `refuses a scheme with no server behind it`() {
        // The one that reached a phone, and the reason this exists.
        assertNotNull(ShelfAddress.problem("https:"))
        assertNotNull(ShelfAddress.problem("https://"))
        assertNotNull(ShelfAddress.problem("http://?key=abc"))
        assertFalse(ShelfAddress.usable("https:"))
    }

    @Test
    fun `refuses an address that names no scheme`() {
        assertNotNull(ShelfAddress.problem("nas.local:8600"))
        assertNotNull(ShelfAddress.problem("not an address"))
        assertNotNull(ShelfAddress.problem("ftp://nas.local"))
    }

    @Test
    fun `refuses an address with a space in it`() {
        assertNotNull(ShelfAddress.problem("https://nas .local"))
    }

    @Test
    fun `says what is wrong in words a player can act on`() {
        // `https:` is missing its slashes, so the scheme is what it is told about; `https://` has
        // the scheme and nothing behind it, which is a different sentence.
        assertEquals("an address starts with http:// or https://", ShelfAddress.problem("nas.local"))
        assertEquals("an address starts with http:// or https://", ShelfAddress.problem("https:"))
        assertTrue(ShelfAddress.problem("https://")?.contains("no server") == true)
    }

    @Test
    fun `is not fooled by the case a scheme was typed in`() {
        assertNull(ShelfAddress.problem("HTTPS://nas.local:8600"))
        assertNotNull(ShelfAddress.problem("HTTPS:"))
    }
}

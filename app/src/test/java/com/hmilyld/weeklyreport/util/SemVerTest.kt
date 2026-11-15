package com.hmilyld.weeklyreport.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SemVerTest {
    @Test
    fun `parses plain and tagged versions`() {
        assertEquals(SemVer.Version(1, 2, 3), SemVer.parse("1.2.3"))
        assertEquals(SemVer.Version(1, 2, 3), SemVer.parse("v1.2.3"))
        assertEquals(SemVer.Version(1, 2, 3), SemVer.parse("V1.2.3"))
        assertEquals(SemVer.Version(1, 2, 3), SemVer.parse(" 1.2.3 "))
    }

    @Test
    fun `drops pre-release and build suffixes`() {
        assertEquals(SemVer.Version(1, 0, 0), SemVer.parse("1.0.0-debug"))
        assertEquals(SemVer.Version(1, 0, 0), SemVer.parse("v1.0.0-rc1"))
        assertEquals(SemVer.Version(1, 0, 0), SemVer.parse("1.0.0+4"))
    }

    @Test
    fun `accepts two-part and one-part versions`() {
        assertEquals(SemVer.Version(2, 1, 0), SemVer.parse("2.1"))
        assertEquals(SemVer.Version(2, 0, 0), SemVer.parse("2"))
    }

    @Test
    fun `rejects junk`() {
        assertNull(SemVer.parse(null))
        assertNull(SemVer.parse(""))
        assertNull(SemVer.parse("v"))
        assertNull(SemVer.parse("latest"))
        assertNull(SemVer.parse("1.2.3.4"))
        assertNull(SemVer.parse("1.x.3"))
        assertNull(SemVer.parse("-1.2.3"))
    }

    @Test
    fun `orders numerically, not lexicographically`() {
        assertTrue(SemVer.Version(1, 10, 0) > SemVer.Version(1, 9, 9))
        assertTrue(SemVer.Version(2, 0, 0) > SemVer.Version(1, 99, 99))
        assertEquals(0, SemVer.Version(1, 2, 3).compareTo(SemVer.Version(1, 2, 3)))
    }

    @Test
    fun `isNewer compares the published tag with the installed build`() {
        assertTrue(SemVer.isNewer("v1.0.1", "1.0.0"))
        assertTrue(SemVer.isNewer("v1.1.0", "1.0.9"))
        assertTrue(SemVer.isNewer("v2.0.0", "1.99.99"))
        assertFalse(SemVer.isNewer("v1.0.0", "1.0.0"))
        assertFalse(SemVer.isNewer("v1.0.0", "1.0.1"))
        assertFalse(SemVer.isNewer("v1.0.0", "1.0.0-debug"))
    }

    @Test
    fun `unknown input never reports an update`() {
        assertFalse(SemVer.isNewer(null, "1.0.0"))
        assertFalse(SemVer.isNewer("v1.0.1", null))
        assertFalse(SemVer.isNewer("oops", "1.0.0"))
    }
}

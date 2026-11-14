package com.hmilyld.weeklyreport.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ListInputTest {
    @Test
    fun `enter at the end of an item writes the next number`() {
        val edit = ListInput.continueOrderedList("1. 修了导出", 7)
        assertEquals("1. 修了导出\n2. ", edit?.value)
        assertEquals(11, edit?.caret)
    }

    @Test
    fun `delimiter and indent are kept`() {
        val edit = ListInput.continueOrderedList("   3) 联调", 8)
        assertEquals("   3) 联调\n   4) ", edit?.value)
    }

    @Test
    fun `enter on an empty item removes the marker and ends the list`() {
        val edit = ListInput.continueOrderedList("1. a\n2. ", 7)
        assertEquals("1. a\n", edit?.value)
        assertEquals(5, edit?.caret)
    }

    @Test
    fun `enter in the middle of a line is left alone`() {
        assertNull(ListInput.continueOrderedList("1. 修复导出", 3))
    }

    @Test
    fun `following items are renumbered after inserting one`() {
        val text = "1. a\n2. b\n3. c"
        val caret = 4
        val edit = ListInput.continueOrderedList(text, caret)
        assertEquals("1. a\n2. \n3. b\n4. c", edit?.value)
    }

    @Test
    fun `plain paragraphs are not numbered`() {
        assertNull(ListInput.continueOrderedList("今天做了三件事", 3))
    }

    @Test
    fun `list markers are not counted as body text`() {
        assertEquals("", ListInput.bodyWithoutMarkers("1. ").trim())
        assertEquals("修了导出", ListInput.bodyWithoutMarkers("1. 修了导出").trim())
        assertEquals("a\nb", ListInput.bodyWithoutMarkers("1. a\n2. b").trim())
    }

    @Test
    fun `trailing empty numbered item is dropped on save`() {
        assertEquals("1. a", ListInput.trimTrailingEmptyItem("1. a\n2. "))
        assertEquals("1. a\n2. b", ListInput.trimTrailingEmptyItem("1. a\n2. b\n3. "))
        assertEquals("", ListInput.trimTrailingEmptyItem("1. "))
        assertEquals("段落", ListInput.trimTrailingEmptyItem("段落"))
    }

    @Test
    fun `composition input must not be transformed`() {
        val old = "1. 中文"
        val next = "$old\n"
        assertEquals(
            null,
            ListInput.continuationForNewline(old, old.length, next, composing = true),
        )
        assertEquals(
            "1. 中文\n2. ",
            ListInput.continuationForNewline(old, old.length, next, composing = false)?.value,
        )
    }

    @Test
    fun `a non-newline edit is never continued`() {
        assertEquals(null, ListInput.continuationForNewline("1. a", 4, "1. ab", composing = false))
    }
}

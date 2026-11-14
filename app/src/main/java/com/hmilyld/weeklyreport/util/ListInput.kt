package com.hmilyld.weeklyreport.util

/**
 * Ordered-list typing for the daily editor, ported from the web client:
 * Enter at the end of "3. text" writes "4. " on the next line; Enter on an
 * item that holds nothing drops its marker and ends the list. The caller
 * must only run this when the IME is not composing.
 */
object ListInput {
    private val ORDERED_MARKER = Regex("^([ \\t]{0,3})(\\d{1,9})([.)])([ \\t]+|$)")
    private val LIST_MARKER_LINE = Regex("^[ \\t]{0,3}\\d{1,9}[.)][ \\t]+", RegexOption.MULTILINE)

    const val FIRST_ITEM = "1. "

    data class Edit(
        val value: String,
        val caret: Int,
    )

    fun continueOrderedList(
        value: String,
        caret: Int,
    ): Edit? {
        if (caret < 0 || caret > value.length) return null
        val lineStart = value.lastIndexOf('\n', (caret - 1).coerceAtLeast(0)).let {
            if (caret == 0) 0 else it + 1
        }
        val nextBreak = value.indexOf('\n', caret)
        val lineEnd = if (nextBreak == -1) value.length else nextBreak
        val line = value.substring(lineStart, lineEnd)

        val match = ORDERED_MARKER.find(line) ?: return null
        val indent = match.groups[1]?.value.orEmpty()
        val number = match.groups[2]?.value?.toIntOrNull() ?: return null
        val delimiter = match.groups[3]?.value ?: "."
        val rest = line.substring(match.range.last + 1)

        // Only continue from the end of an item.
        if (value.substring(caret, lineEnd).trim().isNotEmpty()) return null

        if (rest.trim().isEmpty()) {
            // Empty item: Enter removes the marker instead of numbering forever.
            return Edit(
                value = value.substring(0, lineStart) + value.substring(lineEnd),
                caret = lineStart,
            )
        }

        val next = number + 1
        val insertion = "\n$indent$next$delimiter "
        val tail = renumber(value.substring(lineEnd), next + 1, indent.length)
        val newLineEnd = lineEnd
        return Edit(
            value = value.substring(0, newLineEnd) + insertion + tail,
            caret = newLineEnd + insertion.length,
        )
    }

    private fun renumber(
        tail: String,
        firstNumber: Int,
        depth: Int,
    ): String {
        val lines = tail.split('\n').toMutableList()
        var number = firstNumber
        for (i in 1 until lines.size) {
            val match = ORDERED_MARKER.find(lines[i]) ?: break
            val lineDepth = match.groups[1]?.value?.length ?: 0
            if (lineDepth < depth) break
            if (lineDepth > depth) continue
            val prefix = match.groups[1]?.value.orEmpty()
            lines[i] =
                prefix + number +
                lines[i].substring(prefix.length + (match.groups[2]?.value?.length ?: 0))
            number += 1
        }
        return lines.joinToString("\n")
    }

    /** The seeded "1. " alone is not content. */
    fun bodyWithoutMarkers(text: String): String = LIST_MARKER_LINE.replace(text, "")

    /**
     * Decides whether a text change was a plain Enter at the end of a list
     * item. Returns null while an IME owns a composition, so candidate-window
     * input can never renumber the list.
     */
    fun continuationForNewline(
        oldText: String,
        oldCursor: Int,
        newText: String,
        composing: Boolean,
    ): Edit? {
        if (composing) return null
        if (oldCursor !in 0..oldText.length) return null
        val expected = oldText.substring(0, oldCursor) + "\n" + oldText.substring(oldCursor)
        if (newText != expected) return null
        return continueOrderedList(oldText, oldCursor)
    }

    /**
     * Saves what was written, not the empty numbered item left after a final
     * Enter. A list that is only markers becomes an empty string.
     */
    fun trimTrailingEmptyItem(text: String): String {
        val lines = text.trimEnd().lines().toMutableList()
        while (lines.size > 1 && ORDERED_MARKER.matches(lines.last().trimEnd())) {
            lines.removeAt(lines.lastIndex)
        }
        val joined = lines.joinToString("\n").trimEnd()
        return if (ORDERED_MARKER.matches(joined)) "" else joined
    }
}

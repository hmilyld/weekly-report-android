package com.hmilyld.weeklyreport.util

/**
 * A deliberately small Markdown reader for GitHub release notes.
 *
 * Release bodies are written in Markdown (they come straight from
 * `CHANGELOG.md`), but a dialog has no room for a full renderer. This turns the
 * handful of constructs that actually appear — headings, bullet lists, bold,
 * inline code, links, fenced code — into plain styled lines, so the prompt never
 * shows raw `###` or `- ` markers.
 *
 * Android-free on purpose: the mapping is covered by JVM unit tests.
 */
object MarkdownLite {
    enum class Kind { HEADING, BULLET, TEXT, BLANK }

    /** A run of text with one optional emphasis. */
    data class Segment(
        val text: String,
        val bold: Boolean = false,
    )

    data class Line(
        val kind: Kind,
        val segments: List<Segment>,
    ) {
        val text: String get() = segments.joinToString("") { it.text }
    }

    private val HEADING = Regex("^(#{1,6})\\s+(.*)$")
    private val BULLET = Regex("^([-*+]|\\d{1,3}[.)])\\s+(.*)$")
    private val RULE = Regex("^([-*_])\\1{2,}$")
    private val QUOTE = Regex("^>\\s?(.*)$")

    // Bold (`**` or `__`), inline code, and links, in one pass. Groups:
    // 1/2 bold text, 3 code, 4 link text, 5 link target.
    private val INLINE = Regex(
        """\*\*(.+?)\*\*|__(.+?)__|`([^`]+)`|\[([^\]]+)]\(([^)]+)\)""",
    )

    fun parse(markdown: String?): List<Line> {
        val raw = markdown
            ?.replace("\r\n", "\n")
            ?.replace('\r', '\n')
            .orEmpty()
        if (raw.isBlank()) return emptyList()

        val lines = mutableListOf<Line>()
        var inFence = false
        for (original in raw.split('\n')) {
            val trimmed = original.trim()
            if (trimmed.startsWith("```") || trimmed.startsWith("~~~")) {
                // Fences carry no meaning here; keep their contents as text.
                inFence = !inFence
                continue
            }
            if (trimmed.isEmpty()) {
                lines += Line(Kind.BLANK, emptyList())
                continue
            }
            if (inFence) {
                lines += Line(Kind.TEXT, inline(trimmed))
                continue
            }
            val heading = HEADING.find(trimmed)
            if (heading != null) {
                lines += Line(Kind.HEADING, inline(heading.groupValues[2].trim()))
                continue
            }
            if (RULE.matches(trimmed)) {
                lines += Line(Kind.BLANK, emptyList())
                continue
            }
            val bullet = BULLET.find(trimmed)
            if (bullet != null) {
                lines += Line(Kind.BULLET, inline(bullet.groupValues[2].trim()))
                continue
            }
            val quote = QUOTE.find(trimmed)
            if (quote != null) {
                lines += Line(Kind.TEXT, inline(quote.groupValues[1].trim()))
                continue
            }
            lines += Line(Kind.TEXT, inline(trimmed))
        }
        return collapseBlanks(lines)
    }

    /** Drops leading/trailing blank lines and collapses runs of them. */
    private fun collapseBlanks(lines: List<Line>): List<Line> {
        val out = mutableListOf<Line>()
        for (line in lines) {
            if (line.kind == Kind.BLANK) {
                if (out.isEmpty() || out.last().kind == Kind.BLANK) continue
            }
            out += line
        }
        while (out.isNotEmpty() && out.last().kind == Kind.BLANK) out.removeAt(out.lastIndex)
        return out
    }

    private fun inline(text: String): List<Segment> {
        if (text.isEmpty()) return emptyList()
        val out = mutableListOf<Segment>()
        var index = 0
        for (match in INLINE.findAll(text)) {
            if (match.range.first > index) {
                out += Segment(text.substring(index, match.range.first))
            }
            val groups = match.groupValues
            when {
                groups[1].isNotEmpty() || groups[2].isNotEmpty() ->
                    out += Segment(groups[1].ifEmpty { groups[2] }, bold = true)

                groups[3].isNotEmpty() -> out += Segment(groups[3])
                groups[4].isNotEmpty() -> out += Segment(groups[4])
            }
            index = match.range.last + 1
        }
        if (index < text.length) out += Segment(text.substring(index))
        return out.ifEmpty { listOf(Segment(text)) }
    }
}

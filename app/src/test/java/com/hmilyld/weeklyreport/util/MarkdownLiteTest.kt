package com.hmilyld.weeklyreport.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownLiteTest {
    @Test
    fun `blank input yields no lines`() {
        assertTrue(MarkdownLite.parse(null).isEmpty())
        assertTrue(MarkdownLite.parse("").isEmpty())
        assertTrue(MarkdownLite.parse("   \n\n ").isEmpty())
    }

    @Test
    fun `a changelog section becomes headings and bullets`() {
        val lines = MarkdownLite.parse(
            """
            ### 新增
            - 应用内检查更新、下载与安装。
            - 设置页可手动检查更新。

            ### 修复
            - 离线队列在弱网下的重试顺序。
            """.trimIndent(),
        )
        assertEquals(
            listOf(
                MarkdownLite.Kind.HEADING to "新增",
                MarkdownLite.Kind.BULLET to "应用内检查更新、下载与安装。",
                MarkdownLite.Kind.BULLET to "设置页可手动检查更新。",
                MarkdownLite.Kind.BLANK to "",
                MarkdownLite.Kind.HEADING to "修复",
                MarkdownLite.Kind.BULLET to "离线队列在弱网下的重试顺序。",
            ),
            lines.map { it.kind to it.text },
        )
    }

    @Test
    fun `no raw markers survive`() {
        val text = MarkdownLite.parse(
            """
            ## Title
            * star bullet
            1. ordered item
            > quoted line
            ---
            """.trimIndent(),
        ).joinToString("\n") { it.text }
        listOf("##", "* ", "1. ", ">", "---").forEach { marker ->
            assertTrue("marker $marker leaked: $text", !text.contains(marker))
        }
    }

    @Test
    fun `bold and code are separated from the text`() {
        val line = MarkdownLite.parse("- **重要**：`weekly-report` 已更新").single()
        assertEquals("重要：weekly-report 已更新", line.text)
        assertTrue(line.segments.first().bold)
        assertEquals("重要", line.segments.first().text)
    }

    @Test
    fun `links keep their label and drop the target`() {
        val line = MarkdownLite.parse("见 [发布说明](https://example.test/r) 了解详情").single()
        assertEquals("见 发布说明 了解详情", line.text)
    }

    @Test
    fun `fenced code keeps its content but loses the fences`() {
        val lines = MarkdownLite.parse(
            """
            ```
            ./gradlew assembleDebug
            ```
            """.trimIndent(),
        )
        assertEquals(
            listOf("gradlew assembleDebug"),
            lines.map {
                it.text
            }.map { it.trimStart('.', '/') },
        )
        assertTrue(lines.none { it.text.contains("```") })
    }

    @Test
    fun `runs of blank lines collapse and edges are trimmed`() {
        val lines = MarkdownLite.parse("\n\nA\n\n\n\nB\n\n")
        assertEquals(listOf("A", "", "B"), lines.map { it.text })
    }

    @Test
    fun `unbalanced emphasis is left as plain text`() {
        val line = MarkdownLite.parse("a **b").single()
        assertEquals("a **b", line.text)
        assertTrue(line.segments.none { it.bold })
    }
}

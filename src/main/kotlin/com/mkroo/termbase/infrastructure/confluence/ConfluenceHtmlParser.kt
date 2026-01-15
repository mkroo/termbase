package com.mkroo.termbase.infrastructure.confluence

import org.springframework.stereotype.Component

@Component
class ConfluenceHtmlParser {
    fun toPlainText(html: String?): String {
        if (html.isNullOrBlank()) return ""

        return html
            .replace(SCRIPT_PATTERN, "")
            .replace(STYLE_PATTERN, "")
            .replace(BLOCK_TAG_PATTERN, "\n")
            .replace(BR_TAG_PATTERN, "\n")
            .replace(TAG_PATTERN, "")
            .replace(HTML_ENTITY_NBSP, " ")
            .replace(HTML_ENTITY_AMP, "&")
            .replace(HTML_ENTITY_LT, "<")
            .replace(HTML_ENTITY_GT, ">")
            .replace(HTML_ENTITY_QUOT, "\"")
            .replace(HTML_ENTITY_NUMERIC) { chr ->
                val code = chr.groupValues[1].toIntOrNull()
                code?.toChar()?.toString() ?: ""
            }.replace(MULTIPLE_NEWLINES, "\n\n")
            .trim()
    }

    companion object {
        private val SCRIPT_PATTERN = Regex("<script[^>]*>.*?</script>", RegexOption.DOT_MATCHES_ALL)
        private val STYLE_PATTERN = Regex("<style[^>]*>.*?</style>", RegexOption.DOT_MATCHES_ALL)
        private val BLOCK_TAG_PATTERN = Regex("</?(div|p|h[1-6]|li|tr|table|blockquote)[^>]*>", RegexOption.IGNORE_CASE)
        private val BR_TAG_PATTERN = Regex("<br\\s*/?>", RegexOption.IGNORE_CASE)
        private val TAG_PATTERN = Regex("<[^>]+>")
        private val MULTIPLE_NEWLINES = Regex("\\n{3,}")
        private val HTML_ENTITY_NBSP = Regex("&nbsp;")
        private val HTML_ENTITY_AMP = Regex("&amp;")
        private val HTML_ENTITY_LT = Regex("&lt;")
        private val HTML_ENTITY_GT = Regex("&gt;")
        private val HTML_ENTITY_QUOT = Regex("&quot;")
        private val HTML_ENTITY_NUMERIC = Regex("&#(\\d+);")
    }
}

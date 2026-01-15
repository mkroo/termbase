package com.mkroo.termbase.infrastructure.confluence

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class ConfluenceHtmlParserTest :
    DescribeSpec({
        describe("ConfluenceHtmlParser") {
            val parser = ConfluenceHtmlParser()

            describe("toPlainText") {
                it("should handle null input") {
                    parser.toPlainText(null) shouldBe ""
                }

                it("should handle blank input") {
                    parser.toPlainText("") shouldBe ""
                    parser.toPlainText("   ") shouldBe ""
                }

                it("should strip simple HTML tags") {
                    val html = "<p>Hello <strong>World</strong></p>"

                    parser.toPlainText(html) shouldBe "Hello World"
                }

                it("should convert block tags to newlines") {
                    val html = "<p>First</p><p>Second</p>"

                    parser.toPlainText(html) shouldBe "First\n\nSecond"
                }

                it("should handle headings") {
                    val html = "<h1>Title</h1><p>Content</p>"

                    parser.toPlainText(html) shouldBe "Title\n\nContent"
                }

                it("should handle br tags") {
                    val html = "Line 1<br>Line 2<br/>Line 3"

                    parser.toPlainText(html) shouldBe "Line 1\nLine 2\nLine 3"
                }

                it("should remove script tags and their content") {
                    val html = "<p>Before</p><script>alert('test');</script><p>After</p>"

                    parser.toPlainText(html) shouldBe "Before\n\nAfter"
                }

                it("should remove style tags and their content") {
                    val html = "<p>Before</p><style>.test { color: red; }</style><p>After</p>"

                    parser.toPlainText(html) shouldBe "Before\n\nAfter"
                }

                it("should decode HTML entities") {
                    val html = "&lt;tag&gt; &amp; &quot;text&quot; &nbsp;"

                    parser.toPlainText(html) shouldBe "<tag> & \"text\""
                }

                it("should decode numeric HTML entities") {
                    val html = "&#65;&#66;&#67;"

                    parser.toPlainText(html) shouldBe "ABC"
                }

                it("should collapse multiple newlines") {
                    val html = "<p>First</p><p></p><p></p><p>Second</p>"

                    val result = parser.toPlainText(html)
                    result.count { it == '\n' } shouldBe 2
                }

                it("should handle Confluence storage format") {
                    val html =
                        """
                        <ac:layout><ac:layout-section ac:type="single">
                        <ac:layout-cell><p>This is a <strong>test</strong> page.</p>
                        <h2>Section</h2>
                        <ul><li>Item 1</li><li>Item 2</li></ul>
                        </ac:layout-cell></ac:layout-section></ac:layout>
                        """.trimIndent()

                    val result = parser.toPlainText(html)
                    result shouldBe "This is a test page.\n\nSection\n\nItem 1\n\nItem 2"
                }
            }
        }
    })

package com.myvault.app.data.formatting

import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.helpers.DefaultHandler
import java.io.StringReader
import javax.xml.parsers.SAXParserFactory

/** Only new formatter output is parsed here; stored/legacy note readers are unchanged. */
object FormattingTextContract {
    private val tags = setOf("document", "h1", "h2", "h3", "p", "ul", "ol", "li", "blockquote", "strong", "em", "b", "i", "u", "br", "span")
    private val blocks = setOf("h1", "h2", "h3", "p", "ul", "ol", "li", "blockquote", "br")

    fun normalizedText(text: String): String = text
        .replace(Regex("(?m)^[\\t ]*[•*\\-]\\s+"), "")
        .replace(Regex("[\\s\\u00a0]+"), " ")
        .trim()

    fun preservesText(source: String, output: String): Boolean =
        normalizedText(source) == normalizedText(output)

    fun requirePreserved(source: String, html: String) {
        val output = try {
            htmlText(html)
        } catch (error: Exception) {
            throw NoteFormattingException("The result could not be safely parsed. Your note is unchanged. Try again.", error)
        }
        if (!preservesText(source, output)) {
            throw NoteFormattingException("The result changed or omitted wording. Your note is unchanged. Try again or choose another provider.")
        }
    }

    internal fun htmlText(html: String): String {
        require(!html.contains("<!", ignoreCase = true) && !html.contains("<?"))
        val body = html.trim().removePrefix("```html").removePrefix("```").removeSuffix("```").trim()
            .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "<br/>")
            .replace("&nbsp;", "&#160;")
        val text = StringBuilder()
        val listCounters = mutableListOf<Int?>()
        val parser = SAXParserFactory.newInstance().apply { isNamespaceAware = false }.newSAXParser()
        parser.parse(InputSource(StringReader("<document>$body</document>")), object : DefaultHandler() {
            override fun resolveEntity(publicId: String?, systemId: String?): InputSource =
                throw IllegalArgumentException("External entities are not allowed")

            override fun startElement(uri: String?, localName: String?, qName: String, attributes: Attributes) {
                require(qName in tags) { "Unsupported formatting element" }
                for (index in 0 until attributes.length) {
                    val name = attributes.getQName(index)
                    val value = attributes.getValue(index)
                    require(qName == "span" && name == "dir" && value in setOf("rtl", "ltr")) {
                        "Unsupported formatting attribute; automatic source-colour attribution is not allowed"
                    }
                }
                if (qName in blocks) text.append('\n')
                when (qName) {
                    "ol" -> listCounters.add(0)
                    "ul" -> listCounters.add(null)
                    "li" -> listCounters.lastOrNull()?.let { count ->
                        listCounters[listCounters.lastIndex] = count + 1
                        text.append("${count + 1}. ")
                    }
                }
            }

            override fun endElement(uri: String?, localName: String?, qName: String) {
                if (qName in blocks) text.append('\n')
                if (qName == "ol" || qName == "ul") listCounters.removeAt(listCounters.lastIndex)
            }

            override fun characters(ch: CharArray, start: Int, length: Int) { text.append(ch, start, length) }
        })
        return text.toString()
    }
}

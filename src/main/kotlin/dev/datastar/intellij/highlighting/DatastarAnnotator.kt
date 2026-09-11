package dev.datastar.intellij.highlighting

import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.xml.XmlTag
import dev.datastar.intellij.model.DatastarDocument
import dev.datastar.intellij.model.DatastarLanguageData
import dev.datastar.intellij.settings.DatastarSettings

class DatastarAnnotator : Annotator {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        if (element !is XmlTag) return
        val file = element.containingFile ?: return
        val settings = DatastarSettings.getInstance()
        if (!settings.isEnabled(file.virtualFile)) return
        val document = file.viewProvider.document ?: return
        val text = document.text
        val tag = DatastarDocument.tags(text).firstOrNull { it.start == element.textRange.startOffset } ?: return
        val attributes = DatastarDocument.attributes(text).filter {
            it.start >= tag.attributesStart && it.end <= tag.attributesEnd
        }

        for (parsed in attributes) {
            if (parsed.pluginName !in DatastarLanguageData.attributes && parsed.pluginName !in settings.state.customAttributes) continue

            highlightAttributeName(holder, parsed.start, parsed.name, parsed.pluginName.length)

            DatastarDocument.diagnostics(text, settings.state.customAttributes.toSet())
                .filter { it.start == parsed.start }
                .forEach { diagnostic ->
                    holder.newAnnotation(HighlightSeverity.ERROR, diagnostic.message)
                        .range(TextRange(diagnostic.start, diagnostic.end))
                        .create()
                }

            val valueStart = parsed.valueStart ?: continue
            if (DatastarLanguageData.attributes[parsed.pluginName]?.valueKind != "expression") continue

            highlight(holder, valueStart, valueStart + parsed.value.length, DatastarHighlighting.EXPRESSION)
            val strings = Regex("(['\"`])(?:\\\\.|(?!\\1)[^\\\\])*\\1").findAll(parsed.value).toList()
            strings.forEach { match ->
                highlight(holder, valueStart + match.range.first, valueStart + match.range.last + 1, DatastarHighlighting.STRING)
            }
            Regex("\\b[A-Za-z_$][\\w$]*(?=\\s*:)").findAll(parsed.value)
                .filter { property -> strings.none { property.range.first >= it.range.first && property.range.last <= it.range.last } }
                .forEach { match ->
                    highlight(holder, valueStart + match.range.first, valueStart + match.range.last + 1, DatastarHighlighting.PROPERTY)
                }
            Regex("\\b\\d+(?:\\.\\d+)?\\b").findAll(parsed.value).forEach { match ->
                highlight(holder, valueStart + match.range.first, valueStart + match.range.last + 1, DatastarHighlighting.NUMBER)
            }
            Regex("\\b(?:const|let|var|async|await|function|return|throw|new|if|else|for|while|do|switch|case|break|continue|try|catch|finally|true|false|null|undefined)\\b")
                .findAll(parsed.value).forEach { match ->
                    highlight(holder, valueStart + match.range.first, valueStart + match.range.last + 1, DatastarHighlighting.KEYWORD)
                }
            Regex("\\+\\+|--|\\+=|-=|\\*=|/=|%=|===|!==|==|!=|>=|<=|&&|\\|\\||\\?\\?|=>|\\.\\.\\.|[+*/%><!?:=-]")
                .findAll(parsed.value).forEach { match ->
                    highlight(holder, valueStart + match.range.first, valueStart + match.range.last + 1, DatastarHighlighting.OPERATOR)
                }
            Regex("@[A-Za-z_$][\\w$]*").findAll(parsed.value).forEach { match ->
                highlight(holder, valueStart + match.range.first, valueStart + match.range.last + 1, DatastarHighlighting.ACTION)
            }
            Regex("\\$[A-Za-z0-9_-]+(?:\\.[A-Za-z0-9_-]+)*").findAll(parsed.value).forEach { match ->
                highlight(holder, valueStart + match.range.first, valueStart + match.range.last + 1, DatastarHighlighting.SIGNAL)
            }
        }
    }

    private fun highlightAttributeName(holder: AnnotationHolder, start: Int, name: String, pluginLength: Int) {
        highlight(holder, start, start + 5, DatastarHighlighting.ATTRIBUTE_PREFIX)
        val pluginStart = start + 5
        val pluginEnd = pluginStart + pluginLength
        highlight(holder, pluginStart, pluginEnd, DatastarHighlighting.PLUGIN)

        val modifierStart = name.indexOf("__").let { if (it < 0) name.length else it }
        if (name.getOrNull(5 + pluginLength) == ':') {
            highlight(holder, pluginEnd, pluginEnd + 1, DatastarHighlighting.PUNCTUATION)
            highlight(holder, pluginEnd + 1, start + modifierStart, DatastarHighlighting.KEY)
        }

        var cursor = modifierStart
        while (cursor < name.length) {
            val separatorStart = start + cursor
            highlight(holder, separatorStart, separatorStart + 2, DatastarHighlighting.PUNCTUATION)
            cursor += 2
            val modifierEnd = name.indexOfAny(charArrayOf('.', '_'), cursor).let { if (it < 0) name.length else it }
            highlight(holder, start + cursor, start + modifierEnd, DatastarHighlighting.MODIFIER)
            cursor = modifierEnd
            if (name.getOrNull(cursor) == '.') {
                highlight(holder, start + cursor, start + cursor + 1, DatastarHighlighting.PUNCTUATION)
                cursor++
                val argumentEnd = name.indexOf("__", cursor).let { if (it < 0) name.length else it }
                highlight(holder, start + cursor, start + argumentEnd, DatastarHighlighting.MODIFIER_ARGUMENT)
                cursor = argumentEnd
            }
        }
    }

    private fun highlight(holder: AnnotationHolder, start: Int, end: Int, key: com.intellij.openapi.editor.colors.TextAttributesKey) {
        if (start >= end) return
        holder.newSilentAnnotation(HighlightSeverity.TEXT_ATTRIBUTES)
            .range(TextRange(start, end))
            .enforcedTextAttributes(DatastarHighlighting.enforcedAttributes(key))
            .create()
    }
}

package dev.datastar.intellij.completion

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.patterns.PlatformPatterns
import com.intellij.util.ProcessingContext
import dev.datastar.intellij.settings.DatastarSettings

class DatastarCompletionContributor : CompletionContributor() {
    init {
        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement(),
            object : CompletionProvider<CompletionParameters>() {
                override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
                    val settings = DatastarSettings.getInstance()
                    if (!settings.isEnabled(parameters.originalFile.virtualFile)) return
                    val document = parameters.editor.document
                    DatastarCompletionEngine.completions(document.text, parameters.offset, settings.state.customAttributes).forEach { suggestion ->
                        val prefix = document.text.substring(suggestion.start, parameters.offset)
                        result.withPrefixMatcher(prefix).addElement(
                            LookupElementBuilder.create(suggestion.label)
                                .withTypeText(suggestion.detail, true)
                                .withTailText("  ${suggestion.description}", true)
                                .withInsertHandler { insertion, _ ->
                                    val rendered = renderSnippet(suggestion.insertText)
                                    insertion.document.replaceString(suggestion.start, insertion.tailOffset, rendered.text)
                                    insertion.editor.caretModel.moveToOffset(suggestion.start + rendered.caret)
                                    if (rendered.selectionEnd > rendered.caret) {
                                        insertion.editor.selectionModel.setSelection(
                                            suggestion.start + rendered.caret,
                                            suggestion.start + rendered.selectionEnd,
                                        )
                                    }
                                },
                        )
                    }
                }
            },
        )
    }

    private data class RenderedSnippet(val text: String, val caret: Int, val selectionEnd: Int)

    private fun renderSnippet(snippet: String): RenderedSnippet {
        val placeholder = Regex("\\$\\{\\d+:([^}]*)}").find(snippet)
        var text = snippet.replace(Regex("\\$\\{\\d+:([^}]*)}")) { it.groupValues[1] }
        val explicitCaret = text.indexOf("\$0")
        text = text.replace("\$0", "")
        val caret = when {
            placeholder != null -> placeholder.range.first
            explicitCaret >= 0 -> explicitCaret
            else -> text.length
        }
        val selectionEnd = if (placeholder != null) caret + placeholder.groupValues[1].length else caret
        return RenderedSnippet(text, caret, selectionEnd)
    }
}

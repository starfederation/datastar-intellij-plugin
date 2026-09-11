package dev.datastar.intellij.documentation

import com.intellij.lang.documentation.AbstractDocumentationProvider
import com.intellij.lang.documentation.DocumentationMarkup
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.FakePsiElement
import dev.datastar.intellij.settings.DatastarSettings

class DatastarDocumentationProvider : AbstractDocumentationProvider() {
    override fun getCustomDocumentationElement(
        editor: Editor,
        file: PsiFile,
        contextElement: PsiElement?,
        targetOffset: Int,
    ): PsiElement? {
        if (!DatastarSettings.getInstance().isEnabled(file.virtualFile)) return null
        if (DatastarDocumentation.at(editor.document.text, targetOffset) == null) return null
        return DatastarDocumentationElement(file, targetOffset)
    }

    override fun generateDoc(element: PsiElement?, originalElement: PsiElement?): String? {
        val target = element as? DatastarDocumentationElement
        val context = originalElement ?: element ?: return null
        val file = target?.containingFile ?: context.containingFile ?: return null
        if (!DatastarSettings.getInstance().isEnabled(file.virtualFile)) return null
        val text = file.viewProvider.document?.text ?: return null
        val result = DatastarDocumentation.at(text, target?.textOffset ?: context.textOffset) ?: return null

        return renderDocumentation(result)
    }
}

internal fun renderDocumentation(result: DocumentationResult): String {
    val content = HtmlBuilder()
        .append(HtmlChunk.text(result.description))
    if (result.requirements.isNotEmpty()) {
        content.append(HtmlChunk.ul().children(result.requirements.map { HtmlChunk.li().addText(it) }))
    }
    result.url?.let { content.append(HtmlChunk.p().child(HtmlChunk.link(it, "Datastar documentation"))) }

    return buildString {
        append(DocumentationMarkup.DEFINITION_START)
        append(HtmlChunk.text(result.title))
        append(DocumentationMarkup.DEFINITION_END)
        append(DocumentationMarkup.CONTENT_START)
        append(content)
        append(DocumentationMarkup.CONTENT_END)
    }
}

private class DatastarDocumentationElement(
    private val file: PsiFile,
    private val offset: Int,
) : FakePsiElement() {
    override fun getParent() = file
    override fun getContainingFile() = file
    override fun getTextOffset() = offset
}

package dev.datastar.intellij.documentation

import com.intellij.model.Pointer
import com.intellij.openapi.util.TextRange
import com.intellij.platform.backend.documentation.DocumentationResult as IntelliJDocumentationResult
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.documentation.DocumentationTargetProvider
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.psi.PsiFile
import dev.datastar.intellij.settings.DatastarSettings

/** Supplies documentation for tokens inside an attribute value, which are not separate PSI elements. */
class DatastarDocumentationTargetProvider : DocumentationTargetProvider {
    override fun documentationTargets(file: PsiFile, offset: Int): List<DocumentationTarget> {
        if (!DatastarSettings.getInstance().isEnabled(file.virtualFile)) return emptyList()
        val text = file.viewProvider.document?.text ?: return emptyList()
        if (DatastarDocumentation.at(text, offset) == null) return emptyList()
        return listOf(DatastarDocumentationTarget(file, offset))
    }
}

private class DatastarDocumentationTarget(
    private val file: PsiFile,
    private val offset: Int,
) : DocumentationTarget {
    private fun result(): DocumentationResult? {
        val text = file.viewProvider.document?.text ?: return null
        return DatastarDocumentation.at(text, offset)
    }

    override fun createPointer(): Pointer<out DocumentationTarget> =
        Pointer.fileRangePointer(file, TextRange.from(offset, 0)) { pointedFile, range ->
            DatastarDocumentationTarget(pointedFile, range.startOffset)
        }

    override fun computePresentation(): TargetPresentation =
        TargetPresentation.builder(result()?.title ?: "Datastar").presentation()

    override fun computeDocumentationHint(): String? = result()?.description

    override fun computeDocumentation(): IntelliJDocumentationResult? =
        result()?.let { IntelliJDocumentationResult.documentation(renderDocumentation(it)) }
}

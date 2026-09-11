package dev.datastar.intellij.references

import com.intellij.openapi.util.TextRange
import com.intellij.psi.ElementManipulators
import com.intellij.psi.PsiReferenceBase
import com.intellij.psi.xml.XmlAttributeValue
import dev.datastar.intellij.model.DatastarDocument

class DatastarSignalReference(
    element: XmlAttributeValue,
    range: TextRange,
    private val path: String,
) : PsiReferenceBase<XmlAttributeValue>(element, range, false) {
    override fun resolve(): DatastarSignalElement? {
        val file = element.containingFile
        val text = file.viewProvider.document?.text ?: return null
        val declaration = DatastarDocument.resolveDeclaration(text, path) ?: return null
        return DatastarSignalElement(file, declaration)
    }

    override fun handleElementRename(newElementName: String) =
        ElementManipulators.handleContentChange(element, rangeInElement, newElementName)
}

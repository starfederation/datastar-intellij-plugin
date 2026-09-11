package dev.datastar.intellij.references

import com.intellij.navigation.ItemPresentation
import com.intellij.navigation.NavigationItem
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.impl.FakePsiElement
import dev.datastar.intellij.model.DatastarDocument
import dev.datastar.intellij.model.SignalDeclaration

class DatastarSignalElement(
    private val file: PsiFile,
    private val declaration: SignalDeclaration,
) : FakePsiElement(), PsiNamedElement, NavigationItem {
    override fun getParent() = file
    override fun getContainingFile() = file
    override fun getName() = declaration.name.substringAfterLast('.')
    override fun getText() = name
    override fun getTextRange() = TextRange(declaration.start, declaration.end)
    override fun getTextOffset() = declaration.start
    override fun getPresentation(): ItemPresentation = object : ItemPresentation {
        override fun getPresentableText() = "$${declaration.name}"
        override fun getLocationString() = file.name
        override fun getIcon(unused: Boolean) = file.getIcon(0)
    }

    override fun navigate(requestFocus: Boolean) {
        val virtualFile = file.virtualFile ?: return
        OpenFileDescriptor(project, virtualFile, declaration.start).navigate(requestFocus)
    }

    override fun canNavigate() = file.virtualFile != null
    override fun canNavigateToSource() = canNavigate()

    override fun setName(name: String): PsiNamedElement {
        if (!name.matches(Regex("^[A-Za-z_$][\\w$-]*$"))) return this
        val manager = PsiDocumentManager.getInstance(project)
        val document = manager.getDocument(file) ?: return this
        manager.doPostponedOperationsAndUnblockDocument(document)
        document.replaceString(declaration.start, declaration.end, name)
        manager.commitDocument(document)
        val path = declaration.name.substringBeforeLast('.', "").let { if (it.isEmpty()) name else "$it.$name" }
        val updated = DatastarDocument.signalDeclarations(document.text).firstOrNull { it.name == path }
        return if (updated == null) this else DatastarSignalElement(file, updated)
    }

    override fun isEquivalentTo(another: com.intellij.psi.PsiElement?): Boolean =
        another is DatastarSignalElement && another.file == file && another.declaration.name == declaration.name
}

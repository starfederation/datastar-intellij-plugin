package dev.datastar.intellij.references

import com.intellij.psi.PsiElement
import com.intellij.refactoring.rename.RenamePsiElementProcessor

class DatastarRenameProcessor : RenamePsiElementProcessor() {
    override fun canProcessElement(element: PsiElement): Boolean = element is DatastarSignalElement
}

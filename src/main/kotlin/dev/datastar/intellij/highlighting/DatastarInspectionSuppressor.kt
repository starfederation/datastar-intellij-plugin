package dev.datastar.intellij.highlighting

import com.intellij.codeInspection.InspectionSuppressor
import com.intellij.codeInspection.SuppressQuickFix
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlAttribute
import dev.datastar.intellij.model.DatastarLanguageData
import dev.datastar.intellij.settings.DatastarSettings

class DatastarInspectionSuppressor : InspectionSuppressor {
    override fun isSuppressedFor(element: PsiElement, toolId: String): Boolean {
        if (toolId != "XmlUnboundNsPrefix") return false
        val attribute = if (element is XmlAttribute) element
        else PsiTreeUtil.getParentOfType(element, XmlAttribute::class.java, false)
            ?: return false
        val namespaceSeparator = attribute.name.indexOf(':')
        if (namespaceSeparator < 0) return false

        val prefix = attribute.name.substring(0, namespaceSeparator)
        if (!prefix.startsWith("data-")) return false
        val pluginName = prefix.removePrefix("data-")
        val settings = DatastarSettings.getInstance()
        if (!settings.isEnabled(attribute.containingFile.virtualFile)) return false

        return pluginName in DatastarLanguageData.attributes || pluginName in settings.state.customAttributes
    }

    override fun getSuppressActions(element: PsiElement?, toolId: String): Array<SuppressQuickFix> =
        SuppressQuickFix.EMPTY_ARRAY
}

package dev.datastar.intellij.references

import com.intellij.openapi.util.TextRange
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceContributor
import com.intellij.psi.PsiReferenceProvider
import com.intellij.psi.PsiReferenceRegistrar
import com.intellij.psi.xml.XmlAttributeValue
import com.intellij.util.ProcessingContext
import dev.datastar.intellij.model.DatastarLanguageData
import dev.datastar.intellij.settings.DatastarSettings

class DatastarReferenceContributor : PsiReferenceContributor() {
    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        registrar.registerReferenceProvider(
            PlatformPatterns.psiElement(XmlAttributeValue::class.java),
            object : PsiReferenceProvider() {
                override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
                    val value = element as XmlAttributeValue
                    val attribute = value.parent as? com.intellij.psi.xml.XmlAttribute ?: return PsiReference.EMPTY_ARRAY
                    if (!attribute.name.startsWith("data-")) return PsiReference.EMPTY_ARRAY
                    if (!DatastarSettings.getInstance().isEnabled(element.containingFile.virtualFile)) return PsiReference.EMPTY_ARRAY
                    val plugin = attribute.name.removePrefix("data-").substringBefore(':').substringBefore("__")
                    if (DatastarLanguageData.attributes[plugin]?.valueKind != "expression") return PsiReference.EMPTY_ARRAY
                    val contentOffset = value.text.indexOf(value.value).coerceAtLeast(0)
                    return Regex("\\$([A-Za-z0-9_-]+(?:\\.[A-Za-z0-9_-]+)*)").findAll(value.value).flatMap { match ->
                        val segments = match.groupValues[1].split('.')
                        var segmentStart = match.range.first + 1
                        segments.mapIndexed { index, segment ->
                            val reference = DatastarSignalReference(
                                value,
                                TextRange(contentOffset + segmentStart, contentOffset + segmentStart + segment.length),
                                segments.take(index + 1).joinToString("."),
                            )
                            segmentStart += segment.length + 1
                            reference
                        }
                    }.toList().toTypedArray()
                }
            },
        )
    }
}

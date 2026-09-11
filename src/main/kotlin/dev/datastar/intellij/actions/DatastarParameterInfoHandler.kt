package dev.datastar.intellij.actions

import com.intellij.lang.parameterInfo.CreateParameterInfoContext
import com.intellij.lang.parameterInfo.ParameterInfoHandler
import com.intellij.lang.parameterInfo.ParameterInfoUIContext
import com.intellij.lang.parameterInfo.UpdateParameterInfoContext
import com.intellij.openapi.util.TextRange
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlAttributeValue
import dev.datastar.intellij.model.ActionMetadata
import dev.datastar.intellij.model.DatastarLanguageData
import dev.datastar.intellij.settings.DatastarSettings

class DatastarParameterInfoHandler : ParameterInfoHandler<XmlAttributeValue, ActionMetadata> {
    override fun findElementForParameterInfo(context: CreateParameterInfoContext): XmlAttributeValue? {
        val value = valueAt(context.file.findElementAt(context.offset)) ?: return null
        if (!DatastarSettings.getInstance().isEnabled(value.containingFile.virtualFile)) return null
        val call = actionCall(value.value, context.offset - value.textRange.startOffset - value.text.indexOf(value.value)) ?: return null
        context.itemsToShow = arrayOf(call.action)
        return value
    }

    override fun showParameterInfo(element: XmlAttributeValue, context: CreateParameterInfoContext) {
        context.showHint(element, element.textRange.startOffset, this)
    }

    override fun findElementForUpdatingParameterInfo(context: UpdateParameterInfoContext): XmlAttributeValue? =
        valueAt(context.file.findElementAt(context.offset))

    override fun updateParameterInfo(parameterOwner: XmlAttributeValue, context: UpdateParameterInfoContext) {
        val relative = context.offset - parameterOwner.textRange.startOffset - parameterOwner.text.indexOf(parameterOwner.value)
        val call = actionCall(parameterOwner.value, relative)
        if (call == null) context.removeHint()
        else context.setCurrentParameter(call.argument)
    }

    override fun updateUI(parameter: ActionMetadata, context: ParameterInfoUIContext) {
        val parameters = DatastarLanguageData.actionParameters(parameter)
        val active = context.currentParameterIndex.coerceIn(0, (parameters.size - 1).coerceAtLeast(0))
        val signature = DatastarLanguageData.actionSignature(parameter)
        val selected = parameters.getOrNull(active)?.label
        val start = selected?.let { signature.indexOf(it) } ?: -1
        context.setupUIComponentPresentation(
            signature,
            start,
            if (start < 0) -1 else start + selected!!.length,
            false,
            false,
            false,
            context.defaultParameterColor,
        )
    }

    private data class Call(val action: ActionMetadata, val argument: Int)

    private fun actionCall(source: String, offset: Int): Call? {
        data class Frame(val close: Char, val action: String? = null, var argument: Int = 0)
        val stack = mutableListOf<Frame>()
        val pairs = mapOf('(' to ')', '[' to ']', '{' to '}')
        var index = 0
        while (index < offset.coerceAtMost(source.length)) {
            val char = source[index]
            if (char == '\"' || char == '\'' || char == '`') {
                index = skipQuoted(source, index)
                continue
            }
            if (char == '@') {
                val match = Regex("^@([A-Za-z_$][\\w$]*)\\s*\\(").find(source.substring(index))
                if (match != null) {
                    stack += Frame(')', match.groupValues[1])
                    index += match.value.length
                    continue
                }
            }
            if (char in pairs) stack += Frame(pairs.getValue(char))
            else if (stack.lastOrNull()?.close == char) stack.removeAt(stack.lastIndex)
            else if (char == ',' && stack.lastOrNull()?.action != null) stack.last().argument++
            index++
        }
        val frame = stack.asReversed().firstOrNull { it.action != null } ?: return null
        val action = DatastarLanguageData.actions[frame.action] ?: return null
        return Call(action, frame.argument.coerceAtMost((DatastarLanguageData.actionParameters(action).size - 1).coerceAtLeast(0)))
    }

    private fun valueAt(element: com.intellij.psi.PsiElement?): XmlAttributeValue? =
        PsiTreeUtil.getParentOfType(element, XmlAttributeValue::class.java, false)

    private fun skipQuoted(source: String, start: Int): Int {
        val quote = source[start]
        var index = start + 1
        while (index < source.length) {
            if (source[index] == '\\') index += 2
            else if (source[index] == quote) return index + 1
            else index++
        }
        return index
    }
}

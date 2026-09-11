package dev.datastar.intellij.documentation

import dev.datastar.intellij.model.DatastarDocument
import dev.datastar.intellij.model.DatastarLanguageData
import dev.datastar.intellij.model.SignalKind

data class DocumentationResult(
    val title: String,
    val description: String,
    val requirements: List<String> = emptyList(),
    val url: String? = null,
)

object DatastarDocumentation {
    private val actionPattern = Regex("@([A-Za-z_$][\\w$]*)\\s*\\(")

    fun at(text: String, offset: Int): DocumentationResult? {
        val attribute = DatastarDocument.attributes(text).firstOrNull { offset in it.start..it.nameEnd }
        if (attribute != null) {
            val metadata = DatastarLanguageData.attributes[attribute.pluginName] ?: return null
            val requirements = buildList {
                metadata.element?.let { add("Element: <$it> only") }
                if (metadata.requirement.key == "must") add("Key: required")
                if (metadata.requirement.key == "denied") add("Key: not allowed")
                if (metadata.requirement.value == "must") add("Value: required")
                if (metadata.requirement.value == "denied") add("Value: not allowed")
                if (metadata.pro) add("Requires Datastar Pro.")
            }
            return DocumentationResult(attribute.name, metadata.description, requirements, metadata.reference)
        }

        val expression = DatastarDocument.expressionAttributeAt(text, offset) ?: return null
        if (DatastarLanguageData.attributes[expression.pluginName]?.valueKind != "expression") return null
        val valueStart = expression.valueStart!!
        val relativeOffset = offset - valueStart
        backendOptionAt(expression.value, relativeOffset)?.let { option ->
            return DocumentationResult(
                option.name,
                option.description,
                url = "https://data-star.dev/reference/actions#options",
            )
        }
        for (match in actionPattern.findAll(expression.value)) {
            val start = valueStart + match.range.first
            val end = start + match.groupValues[1].length + 1
            if (offset !in start..end) continue
            val action = DatastarLanguageData.actions[match.groupValues[1]] ?: return null
            return DocumentationResult(
                DatastarLanguageData.actionSignature(action),
                action.description,
                if (action.pro) listOf("Requires Datastar Pro.") else emptyList(),
                "https://data-star.dev/reference/actions#${action.name}",
            )
        }

        val occurrence = DatastarDocument.signalOccurrenceAt(text, offset) ?: return null
        val declaration = DatastarDocument.resolveDeclaration(text, occurrence.path)
        val description = when (declaration?.kind) {
            SignalKind.SIGNAL -> "Signal declared in this document."
            SignalKind.PROPERTY -> "Signal property declared in this document."
            SignalKind.COMPUTED -> "Computed signal declared in this document."
            SignalKind.REFERENCE -> "Element reference signal declared in this document."
            null -> "Signal is not explicitly declared in this document."
        }
        return DocumentationResult("$${occurrence.path}", description)
    }

    private fun backendOptionAt(source: String, offset: Int) = Regex("[A-Za-z_$][\\w$]*")
        .findAll(source)
        .firstOrNull { offset in it.range.first..(it.range.last + 1) }
        ?.takeIf { isBackendOptionsKey(source, it.range.first) }
        ?.let { match -> DatastarLanguageData.data.backendActionOptions.firstOrNull { it.name == match.value } }

    private fun isBackendOptionsKey(source: String, keyOffset: Int): Boolean {
        data class Frame(val close: Char, val action: String? = null, var argument: Int = 0, val options: Boolean = false)
        val stack = mutableListOf<Frame>()
        val pairs = mapOf('(' to ')', '[' to ']', '{' to '}')
        var index = 0
        while (index < keyOffset) {
            val char = source[index]
            if (char == '\"' || char == '\'' || char == '`') {
                index = skipQuoted(source, index)
                continue
            }
            if (char == '@') {
                val match = actionPattern.find(source.substring(index))
                if (match?.range?.first == 0) {
                    stack += Frame(')', match.groupValues[1])
                    index += match.value.length
                    continue
                }
            }
            if (char in pairs) {
                val parent = stack.lastOrNull()
                val options = char == '{' && parent?.action in DatastarLanguageData.backendActions && parent?.argument == 1
                stack += Frame(pairs.getValue(char), if (options) parent?.action else null, options = options)
            } else if (stack.lastOrNull()?.close == char) {
                stack.removeAt(stack.lastIndex)
            } else if (char == ',' && stack.lastOrNull()?.action != null && stack.last().close == ')') {
                stack.last().argument++
            }
            index++
        }
        return stack.lastOrNull()?.options == true
    }

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

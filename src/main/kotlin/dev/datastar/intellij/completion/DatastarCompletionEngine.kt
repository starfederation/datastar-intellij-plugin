package dev.datastar.intellij.completion

import dev.datastar.intellij.model.AttributeMetadata
import dev.datastar.intellij.model.DatastarDocument
import dev.datastar.intellij.model.DatastarLanguageData
import dev.datastar.intellij.model.ParsedAttribute

data class DatastarSuggestion(
    val label: String,
    val insertText: String,
    val description: String,
    val detail: String,
    val start: Int,
)

object DatastarCompletionEngine {
    private val data get() = DatastarLanguageData.data

    fun completions(text: String, offset: Int, customAttributes: List<String>): List<DatastarSuggestion> {
        val expressionAttribute = DatastarDocument.expressionAttributeAt(text, offset)
        if (expressionAttribute != null && isExpression(expressionAttribute)) {
            actionCompletions(text, offset, expressionAttribute)?.let { return it }
            actionOptionCompletions(text, offset, expressionAttribute)?.let { return it }
            signalCompletions(text, offset, expressionAttribute)?.let { return it }
        }

        val tag = DatastarDocument.tags(text).firstOrNull {
            it.start <= offset && if (it.closed) offset < it.end else offset <= it.end
        } ?: return emptyList()
        if (insideQuotedValue(text, tag.attributesStart, offset)) return emptyList()
        val prefix = Regex("data-[a-zA-Z0-9:_*.\\-]*$").find(text.substring(tag.attributesStart, offset))?.value
            ?: return emptyList()

        modifierCompletions(prefix, offset)?.let { return it }

        if (prefix.matches(Regex("^data-on:[a-zA-Z0-9-]*$"))) {
            val metadata = DatastarLanguageData.attributes.getValue("on")
            return data.nativeEvents.map { event ->
                val name = "data-on:$event"
                val explicit = data.completions.firstOrNull { it.name == name }
                suggestion(name, explicit?.insertText ?: "$name=\"\${1:expression}\"", metadata, offset - prefix.length)
            }
        }

        val result = data.completions
            .filter { it.pluginName != "nonce" || tag.name == "html" }
            .map { entry -> suggestion(entry.name, entry.insertText, DatastarLanguageData.attributes.getValue(entry.pluginName), offset - prefix.length) }
            .toMutableList()
        customAttributes.forEach { name ->
            result += DatastarSuggestion(
                "data-$name",
                "data-$name=\"\${1:expression}\"",
                "Custom Datastar attribute.",
                "Custom Datastar attribute",
                offset - prefix.length,
            )
        }
        return result
    }

    private fun signalCompletions(text: String, offset: Int, attribute: ParsedAttribute): List<DatastarSuggestion>? {
        val before = text.substring(attribute.valueStart!!, offset)
        val match = Regex("\\$[A-Za-z0-9_-]*(?:\\.[A-Za-z0-9_-]*)*$").find(before) ?: return null
        val reference = match.value.drop(1)
        val lastDot = reference.lastIndexOf('.')
        val parent = if (lastDot < 0) "" else reference.substring(0, lastDot)
        val typed = if (lastDot < 0) reference else reference.substring(lastDot + 1)
        val candidates = linkedMapOf<String, String>()
        DatastarDocument.signalDeclarations(text).forEach { declaration ->
            val parts = declaration.name.split('.')
            if (parent.isEmpty()) candidates.putIfAbsent(parts.first(), declaration.kind.name.lowercase())
            else if (declaration.name.startsWith("$parent.")) {
                candidates.putIfAbsent(declaration.name.removePrefix("$parent.").substringBefore('.'), declaration.kind.name.lowercase())
            }
        }
        val start = offset - typed.length - if (lastDot < 0) 1 else 0
        return candidates.map { (name, kind) ->
            val label = if (lastDot < 0) "$$name" else name
            DatastarSuggestion(label, label, "${if (kind == "computed") "Computed signal" else "Signal"} declared in this document.", if (lastDot < 0) "Datastar signal" else "Datastar signal property", start)
        }
    }

    private fun actionCompletions(text: String, offset: Int, attribute: ParsedAttribute): List<DatastarSuggestion>? {
        val before = text.substring(attribute.valueStart!!, offset)
        val match = Regex("@[A-Za-z_$][\\w$]*$").find(before) ?: Regex("@$").find(before) ?: return null
        return data.actions.map { action ->
            DatastarSuggestion(
                "@${action.name}",
                "@${action.name}(\$0)",
                action.description,
                if (action.pro) "Datastar Pro action" else "Datastar action",
                offset - match.value.length,
            )
        }
    }

    private data class OptionContext(val applied: Set<String>, val typed: String, val start: Int)

    private fun actionOptionCompletions(text: String, offset: Int, attribute: ParsedAttribute): List<DatastarSuggestion>? {
        val context = actionOptionContext(text.substring(attribute.valueStart!!, offset), offset) ?: return null
        return data.backendActionOptions.filter { it.name !in context.applied }.map { option ->
            DatastarSuggestion(option.name, "${option.name}: ", option.description, "Datastar backend action option", context.start)
        }
    }

    private fun actionOptionContext(source: String, absoluteOffset: Int): OptionContext? {
        data class Frame(val close: Char, val action: String? = null, var argument: Int = 0, val optionsStart: Int? = null)
        val stack = mutableListOf<Frame>()
        val pairs = mapOf('(' to ')', '[' to ']', '{' to '}')
        var index = 0
        while (index < source.length) {
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
            if (char in pairs) {
                val parent = stack.lastOrNull()
                val isOptions = char == '{' && parent?.action in DatastarLanguageData.backendActions && parent?.argument == 1
                stack += Frame(pairs.getValue(char), if (isOptions) parent?.action else null, optionsStart = if (isOptions) index else null)
            } else if (stack.lastOrNull()?.close == char) {
                stack.removeAt(stack.lastIndex)
            } else if (char == ',' && stack.lastOrNull()?.action != null && stack.last().close == ')') {
                stack.last().argument++
            }
            index++
        }
        val frame = stack.lastOrNull() ?: return null
        val optionsStart = frame.optionsStart ?: return null
        val objectSource = source.substring(optionsStart + 1)
        val applied = Regex("(?:^|,)\\s*([A-Za-z_$][\\w$]*)\\s*:").findAll(objectSource).map { it.groupValues[1] }.toSet()
        val current = objectSource.substringAfterLast(',')
        val key = Regex("^\\s*([A-Za-z_$][\\w$]*)?$").matchEntire(current) ?: return null
        val typed = key.groupValues[1]
        return OptionContext(applied, typed, absoluteOffset - typed.length)
    }

    private fun modifierCompletions(prefix: String, offset: Int): List<DatastarSuggestion>? {
        val separator = prefix.lastIndexOf("__")
        if (separator < 0) return null
        val attributeName = prefix.substring(0, separator)
        val current = prefix.substring(separator + 2)
        if (!current.matches(Regex("^[a-zA-Z0-9.-]*$"))) return emptyList()
        val withoutPrefix = attributeName.removePrefix("data-")
        val pieces = withoutPrefix.split("__")
        val pluginName = pieces.first().substringBefore(':')
        val metadata = DatastarLanguageData.attributes[pluginName] ?: return emptyList()
        val applied = pieces.drop(1).map { it.substringBefore('.') }.toSet()
        val parts = current.split('.')
        val modifierName = parts.first()
        val completingTag = parts.size > 1
        val typed = if (completingTag) parts.last() else current
        val appliedTags = parts.drop(1).dropLast(1).toSet()
        return metadata.modifiers.filter { modifier ->
            val name = modifier.name.substringBefore('.')
            val tag = modifier.name.substringAfter('.', "")
            name !in applied && if (!completingTag) tag.isEmpty() else name == modifierName && tag.isNotEmpty() && tag !in appliedTags
        }.map { modifier ->
            val insert = if (completingTag) modifier.name.substringAfter('.') else modifier.name
            DatastarSuggestion(insert, insert, modifier.description ?: "Datastar attribute modifier.", if (metadata.pro) "Datastar Pro attribute modifier" else "Datastar attribute modifier", offset - typed.length)
        }
    }

    private fun suggestion(label: String, insert: String, metadata: AttributeMetadata, start: Int) = DatastarSuggestion(
        label,
        insert,
        metadata.description,
        if (metadata.pro) "Datastar Pro attribute" else "Datastar attribute",
        start,
    )

    private fun isExpression(attribute: ParsedAttribute): Boolean =
        DatastarLanguageData.attributes[attribute.pluginName]?.valueKind == "expression"

    private fun insideQuotedValue(text: String, start: Int, offset: Int): Boolean {
        var quote: Char? = null
        for (index in start until offset.coerceAtMost(text.length)) {
            val char = text[index]
            if (quote != null && char == quote && text.getOrNull(index - 1) != '\\') quote = null
            else if (quote == null && (char == '\"' || char == '\'')) quote = char
        }
        return quote != null
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

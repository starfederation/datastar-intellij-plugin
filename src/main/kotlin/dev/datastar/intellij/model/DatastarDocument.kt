package dev.datastar.intellij.model

data class ParsedTag(
    val name: String,
    val start: Int,
    val attributesStart: Int,
    val attributesEnd: Int,
    val closed: Boolean,
    val end: Int,
)

data class ParsedAttribute(
    val pluginName: String,
    val key: String?,
    val modifiers: List<String>,
    val name: String,
    val start: Int,
    val nameEnd: Int,
    val end: Int,
    val hasValue: Boolean,
    val value: String,
    val valueStart: Int?,
    val valueEnd: Int?,
    val tagName: String,
)

enum class SignalKind { SIGNAL, PROPERTY, COMPUTED, REFERENCE }

data class SignalDeclaration(
    val name: String,
    val kind: SignalKind,
    val start: Int,
    val end: Int,
)

data class SignalOccurrence(
    val path: String,
    val fullStart: Int,
    val fullEnd: Int,
    val segmentStart: Int,
    val segmentEnd: Int,
)

data class DatastarDiagnostic(
    val start: Int,
    val end: Int,
    val message: String,
    val code: String,
)

object DatastarDocument {
    private val tagStart = Regex("^<\\s*([A-Za-z][\\w:-]*)")
    private val attributePattern = Regex(
        "\\b(data-[a-z][a-z0-9-]*(?::[a-zA-Z0-9_$.\\*-]+)?(?:__[a-z][a-z0-9-]*(?:\\.[a-z0-9-]+)*)*)\\s*(?:=\\s*(?:([\\\"'])([\\s\\S]*?)\\2|([^\\s>]+)))?",
        RegexOption.IGNORE_CASE,
    )
    private val signalPattern = Regex("\\$([A-Za-z0-9_-]+(?:\\.[A-Za-z0-9_-]+)*)")

    fun tags(text: String): List<ParsedTag> {
        val result = mutableListOf<ParsedTag>()
        var cursor = 0
        while (cursor < text.length) {
            val start = text.indexOf('<', cursor)
            if (start < 0) break
            if (text.startsWith("<!--", start)) {
                val commentEnd = text.indexOf("-->", start + 4)
                cursor = if (commentEnd < 0) text.length else commentEnd + 3
                continue
            }
            val match = tagStart.find(text.substring(start))
            if (match == null) {
                cursor = start + 1
                continue
            }
            var quote: Char? = null
            var end = start + match.value.length
            while (end < text.length) {
                val char = text[end]
                if (quote != null) {
                    if (char == quote && text.getOrNull(end - 1) != '\\') quote = null
                } else if (char == '\"' || char == '\'') {
                    quote = char
                } else if (char == '>') {
                    break
                }
                end++
            }
            val closed = end < text.length
            val name = match.groupValues[1].lowercase()
            result += ParsedTag(
                name,
                start,
                start + match.value.length,
                if (closed) end else text.length,
                closed,
                if (closed) end + 1 else text.length,
            )
            if (closed && name in setOf("script", "style")) {
                val closing = text.indexOf("</$name", end + 1, ignoreCase = true)
                cursor = if (closing < 0) text.length else closing
            } else {
                cursor = if (closed) end + 1 else text.length
            }
        }
        return result
    }

    fun attributes(text: String): List<ParsedAttribute> = tags(text).flatMap { tag ->
        val source = text.substring(tag.attributesStart, tag.attributesEnd)
        attributePattern.findAll(source).map { match ->
            val start = tag.attributesStart + match.range.first
            val name = match.groupValues[1]
            val parsed = parseAttributeName(name)
            val equalsIndex = match.value.indexOf('=')
            var value = match.groups[3]?.value ?: match.groups[4]?.value.orEmpty()
            var valueStart: Int? = null
            var attributeEnd = start + match.value.length
            if (equalsIndex >= 0) {
                val afterEquals = match.value.substring(equalsIndex + 1)
                val whitespace = afterEquals.length - afterEquals.trimStart().length
                val tokenStart = start + equalsIndex + 1 + whitespace
                if (match.groups[2] != null) {
                    valueStart = tokenStart + 1
                } else if (text.getOrNull(tokenStart) == '\"' || text.getOrNull(tokenStart) == '\'') {
                    valueStart = tokenStart + 1
                    value = text.substring(valueStart, tag.attributesEnd)
                    attributeEnd = tag.attributesEnd
                } else {
                    valueStart = tokenStart
                }
            }
            ParsedAttribute(
                parsed.first,
                parsed.second,
                parsed.third,
                name,
                start,
                start + name.length,
                attributeEnd,
                equalsIndex >= 0,
                value,
                valueStart,
                valueStart?.plus(value.length),
                tag.name,
            )
        }.toList()
    }

    fun attributeAt(text: String, offset: Int): ParsedAttribute? = attributes(text).firstOrNull {
        it.start <= offset && offset <= it.end
    }

    fun expressionAttributeAt(text: String, offset: Int): ParsedAttribute? = attributes(text).firstOrNull {
        it.valueStart != null && it.valueEnd != null && it.valueStart <= offset && offset <= it.valueEnd
    }

    fun diagnostics(text: String, customAttributes: Set<String> = emptySet()): List<DatastarDiagnostic> {
        val metadata = DatastarLanguageData.attributes
        return buildList {
            for (attribute in attributes(text)) {
                if (attribute.pluginName in customAttributes) continue
                val rule = metadata[attribute.pluginName] ?: continue
                val keyProvided = !attribute.key.isNullOrEmpty()
                val valueProvided = attribute.hasValue && attribute.value.isNotEmpty()
                fun report(message: String, code: String) {
                    add(DatastarDiagnostic(attribute.start, attribute.nameEnd, message, code))
                }
                if (rule.element != null && attribute.tagName != rule.element) {
                    report("${attribute.name} is only valid on the <${rule.element}> element.", "invalid-element")
                }
                when (rule.requirement.key) {
                    "must" -> if (!keyProvided) report("${attribute.name} requires a key after \":\".", "key-required")
                    "denied" -> if (keyProvided) report("${attribute.pluginName} does not accept a key.", "key-not-allowed")
                    "exclusive" -> if (keyProvided == valueProvided) report(
                        "${attribute.pluginName} requires either a key or a value, but not both.",
                        "exclusive-key-value",
                    )
                }
                if (rule.keys != null && keyProvided && attribute.key !in rule.keys) {
                    report("${attribute.pluginName} only accepts the following keys: ${rule.keys.joinToString()}.", "key-not-allowed")
                }
                if (rule.requirement.key != "exclusive") when (rule.requirement.value) {
                    "must" -> if (!valueProvided) report("${attribute.name} requires a value.", "value-required")
                    "denied" -> if (valueProvided) report("${attribute.pluginName} does not accept a value.", "value-not-allowed")
                }
            }
        }
    }

    fun signalDeclarations(text: String): List<SignalDeclaration> {
        val result = linkedMapOf<String, SignalDeclaration>()
        for (attribute in attributes(text)) {
            val metadata = DatastarLanguageData.attributes[attribute.pluginName] ?: continue
            val strategy = metadata.signals ?: continue
            if (!attribute.key.isNullOrEmpty()) {
                val name = applySignalCase(attribute.key, attribute.modifiers)
                val start = attribute.start + "data-${attribute.pluginName}:".length
                val names = name.split('.')
                val sourceNames = attribute.key.split('.')
                for (index in names.indices) {
                    val path = names.take(index + 1).joinToString(".")
                    val segmentStart = start + sourceNames.take(index).sumOf { it.length + 1 }
                    val segmentLength = sourceNames.getOrElse(index) { names[index] }.length
                    val kind = when {
                        attribute.pluginName == "computed" -> SignalKind.COMPUTED
                        index == 0 -> SignalKind.SIGNAL
                        else -> SignalKind.PROPERTY
                    }
                    result.putIfAbsent(path, SignalDeclaration(path, kind, segmentStart, segmentStart + segmentLength))
                }
            }
            if (strategy == "key-or-value" && attribute.key == null && attribute.valueStart != null && attribute.value.isNotBlank()) {
                val leading = attribute.value.length - attribute.value.trimStart().length
                val rawName = attribute.value.trim()
                val name = applySignalCase(rawName, attribute.modifiers)
                result.putIfAbsent(name, SignalDeclaration(name, if (attribute.pluginName == "ref") SignalKind.REFERENCE else SignalKind.SIGNAL, attribute.valueStart + leading, attribute.valueStart + leading + rawName.length))
            }
            if (strategy == "key-or-object" && attribute.key == null && attribute.valueStart != null) {
                collectObjectSignals(attribute.value, attribute.valueStart).forEach { declaration ->
                    val adjusted = if (attribute.pluginName == "computed") declaration.copy(kind = SignalKind.COMPUTED) else declaration
                    result.putIfAbsent(adjusted.name, adjusted)
                }
            }
        }
        return result.values.toList()
    }

    fun signalOccurrenceAt(text: String, offset: Int): SignalOccurrence? {
        val attribute = expressionAttributeAt(text, offset) ?: return null
        if (DatastarLanguageData.attributes[attribute.pluginName]?.valueKind != "expression") return null
        return signalPattern.findAll(attribute.value).firstNotNullOfOrNull { match ->
            val start = attribute.valueStart!! + match.range.first
            val fullEnd = start + match.value.length
            if (offset !in start..fullEnd) return@firstNotNullOfOrNull null
            val name = match.groupValues[1]
            val relative = (offset - start - 1).coerceIn(0, (name.length - 1).coerceAtLeast(0))
            val nextDot = name.indexOf('.', relative)
            val path = if (nextDot < 0) name else name.substring(0, nextDot)
            val lastDot = path.lastIndexOf('.')
            SignalOccurrence(path, start, start + path.length + 1, start + lastDot + 2, start + path.length + 1)
        }
    }

    fun resolveDeclaration(text: String, path: String): SignalDeclaration? {
        val declarations = signalDeclarations(text)
        declarations.firstOrNull { it.name == path || it.name.startsWith("$path.") }?.let { return it }
        var parent = path
        while ('.' in parent) {
            parent = parent.substringBeforeLast('.')
            declarations.firstOrNull { it.name == parent }?.let { return it }
        }
        return null
    }

    fun signalOccurrences(text: String, path: String): List<IntRange> {
        val segmentOffset = path.lastIndexOf('.') + 1
        val segmentLength = path.substringAfterLast('.').length
        return attributes(text).flatMap { attribute ->
            if (attribute.valueStart == null || DatastarLanguageData.attributes[attribute.pluginName]?.valueKind != "expression") emptyList()
            else signalPattern.findAll(attribute.value).mapNotNull { match ->
                val found = match.groupValues[1]
                if (found != path && !found.startsWith("$path.")) null
                else {
                    val start = attribute.valueStart + match.range.first + 1 + segmentOffset
                    start until start + segmentLength
                }
            }.toList()
        }
    }

    private fun parseAttributeName(name: String): Triple<String, String?, List<String>> {
        val withoutPrefix = name.removePrefix("data-")
        val pieces = withoutPrefix.split("__")
        val nameAndKey = pieces.first()
        val colon = nameAndKey.indexOf(':')
        return Triple(
            if (colon < 0) nameAndKey else nameAndKey.substring(0, colon),
            if (colon < 0) null else nameAndKey.substring(colon + 1),
            pieces.drop(1),
        )
    }

    private fun applySignalCase(name: String, modifiers: List<String>): String {
        val selected = modifiers.firstOrNull { it.startsWith("case.") }?.removePrefix("case.") ?: "camel"
        return when (selected) {
            "snake" -> name.replace('-', '_')
            "kebab" -> name
            "pascal" -> name.replace(Regex("-[a-z]")) { it.value[1].uppercase() }.replaceFirstChar { it.uppercase() }
            else -> name.replace(Regex("-[a-z]")) { it.value[1].uppercase() }
        }
    }

    private fun collectObjectSignals(source: String, sourceOffset: Int): List<SignalDeclaration> {
        val result = mutableListOf<SignalDeclaration>()
        fun parse(start: Int, prefix: List<String>): Int {
            var index = skipWhitespace(source, start)
            if (source.getOrNull(index) != '{') return index
            index++
            while (index < source.length) {
                index = skipWhitespace(source, index)
                while (source.getOrNull(index) == ',') index = skipWhitespace(source, index + 1)
                if (source.getOrNull(index) == '}') return index + 1
                if (source.startsWith("...", index)) {
                    index = skipExpression(source, index + 3)
                    continue
                }
                val keyStart = index
                var contentStart = keyStart
                val key: String
                if (source.getOrNull(index) == '\"' || source.getOrNull(index) == '\'') {
                    val end = skipQuoted(source, index)
                    key = source.substring(index + 1, (end - 1).coerceAtLeast(index + 1))
                    contentStart++
                    index = end
                } else {
                    val match = Regex("^[A-Za-z_$][\\w$-]*").find(source.substring(index))
                    if (match == null) {
                        index = skipExpression(source, index)
                        if (source.getOrNull(index) == ',') index++
                        continue
                    }
                    key = match.value
                    index += key.length
                }
                index = skipWhitespace(source, index)
                if (source.getOrNull(index) != ':') {
                    index = skipExpression(source, index)
                    if (source.getOrNull(index) == ',') index++
                    continue
                }
                val path = (prefix + key).joinToString(".")
                result += SignalDeclaration(path, if (prefix.isEmpty()) SignalKind.SIGNAL else SignalKind.PROPERTY, sourceOffset + contentStart, sourceOffset + contentStart + key.length)
                index = skipWhitespace(source, index + 1)
                index = if (source.getOrNull(index) == '{') parse(index, prefix + key) else skipExpression(source, index)
                if (source.getOrNull(index) == ',') index++
            }
            return index
        }
        parse(0, emptyList())
        return result
    }

    private fun skipWhitespace(source: String, start: Int): Int {
        var index = start
        while (source.getOrNull(index)?.isWhitespace() == true) index++
        return index
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

    private fun skipExpression(source: String, start: Int): Int {
        val pairs = mapOf('(' to ')', '[' to ']', '{' to '}')
        val closing = mutableListOf<Char>()
        var index = start
        while (index < source.length) {
            val char = source[index]
            if (char == '\"' || char == '\'' || char == '`') {
                index = skipQuoted(source, index)
                continue
            }
            if (char in pairs) closing += pairs.getValue(char)
            else if (closing.lastOrNull() == char) closing.removeAt(closing.lastIndex)
            else if (closing.isEmpty() && (char == ',' || char == '}')) break
            index++
        }
        return index
    }
}

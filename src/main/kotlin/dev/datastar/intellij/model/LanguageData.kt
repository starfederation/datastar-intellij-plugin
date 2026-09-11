package dev.datastar.intellij.model

import com.google.gson.Gson
import java.io.InputStreamReader

data class Requirement(
    val key: String = "allowed",
    val value: String = "allowed",
)

data class ModifierMetadata(
    val name: String = "",
    val description: String? = null,
)

data class AttributeMetadata(
    val name: String = "",
    val description: String = "",
    val reference: String = "",
    val requirement: Requirement = Requirement(),
    val modifiers: List<ModifierMetadata> = emptyList(),
    val signals: String? = null,
    val valueKind: String = "expression",
    val keys: List<String>? = null,
    val element: String? = null,
    val pro: Boolean = false,
)

data class CompletionMetadata(
    val name: String = "",
    val pluginName: String = "",
    val insertText: String = "",
)

data class ActionParameterMetadata(val label: String = "")

data class ActionMetadata(
    val name: String = "",
    val description: String = "",
    val signature: String? = null,
    val parameters: List<ActionParameterMetadata>? = null,
    val pro: Boolean = false,
)

data class ActionOptionMetadata(
    val name: String = "",
    val description: String = "",
)

data class LanguageData(
    val attributes: List<AttributeMetadata> = emptyList(),
    val completions: List<CompletionMetadata> = emptyList(),
    val nativeEvents: List<String> = emptyList(),
    val actions: List<ActionMetadata> = emptyList(),
    val backendActionNames: List<String> = emptyList(),
    val backendActionParameters: List<ActionParameterMetadata> = emptyList(),
    val backendActionOptions: List<ActionOptionMetadata> = emptyList(),
)

object DatastarLanguageData {
    val data: LanguageData by lazy {
        val stream = checkNotNull(javaClass.getResourceAsStream("/datastar/language-data.json")) {
            "Missing Datastar language metadata"
        }
        InputStreamReader(stream, Charsets.UTF_8).use { Gson().fromJson(it, LanguageData::class.java) }
    }

    val attributes by lazy { data.attributes.associateBy(AttributeMetadata::name) }
    val actions by lazy { data.actions.associateBy(ActionMetadata::name) }
    val backendActions by lazy { data.backendActionNames.toSet() }

    fun actionParameters(action: ActionMetadata): List<ActionParameterMetadata> =
        if (action.name in backendActions) data.backendActionParameters else action.parameters.orEmpty()

    fun actionSignature(action: ActionMetadata): String = action.signature
        ?: "@${action.name}(${actionParameters(action).joinToString(", ") { it.label }})"
}

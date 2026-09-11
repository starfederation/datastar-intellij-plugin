package dev.datastar.intellij.highlighting

import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.markup.TextAttributes

object DatastarHighlighting {
    val ATTRIBUTE_PREFIX = TextAttributesKey.createTextAttributesKey(
        "DATASTAR_ATTRIBUTE_PREFIX",
        DefaultLanguageHighlighterColors.MARKUP_ATTRIBUTE,
    )
    val PLUGIN = TextAttributesKey.createTextAttributesKey(
        "DATASTAR_PLUGIN",
        DefaultLanguageHighlighterColors.MARKUP_TAG,
    )
    val KEY = TextAttributesKey.createTextAttributesKey(
        "DATASTAR_KEY",
        DefaultLanguageHighlighterColors.INSTANCE_FIELD,
    )
    val MODIFIER = TextAttributesKey.createTextAttributesKey(
        "DATASTAR_MODIFIER",
        DefaultLanguageHighlighterColors.KEYWORD,
    )
    val MODIFIER_ARGUMENT = TextAttributesKey.createTextAttributesKey(
        "DATASTAR_MODIFIER_ARGUMENT",
        DefaultLanguageHighlighterColors.NUMBER,
    )
    val PUNCTUATION = TextAttributesKey.createTextAttributesKey(
        "DATASTAR_PUNCTUATION",
        DefaultLanguageHighlighterColors.OPERATION_SIGN,
    )
    val EXPRESSION = TextAttributesKey.createTextAttributesKey(
        "DATASTAR_EXPRESSION",
        DefaultLanguageHighlighterColors.IDENTIFIER,
    )
    val ACTION = TextAttributesKey.createTextAttributesKey(
        "DATASTAR_ACTION",
        DefaultLanguageHighlighterColors.FUNCTION_DECLARATION,
    )
    val SIGNAL = TextAttributesKey.createTextAttributesKey(
        "DATASTAR_SIGNAL",
        DefaultLanguageHighlighterColors.GLOBAL_VARIABLE,
    )
    val PROPERTY = TextAttributesKey.createTextAttributesKey(
        "DATASTAR_PROPERTY",
        DefaultLanguageHighlighterColors.INSTANCE_FIELD,
    )
    val STRING = TextAttributesKey.createTextAttributesKey(
        "DATASTAR_STRING",
        DefaultLanguageHighlighterColors.STRING,
    )
    val NUMBER = TextAttributesKey.createTextAttributesKey(
        "DATASTAR_NUMBER",
        DefaultLanguageHighlighterColors.NUMBER,
    )
    val KEYWORD = TextAttributesKey.createTextAttributesKey(
        "DATASTAR_KEYWORD",
        DefaultLanguageHighlighterColors.KEYWORD,
    )
    val OPERATOR = TextAttributesKey.createTextAttributesKey(
        "DATASTAR_OPERATOR",
        DefaultLanguageHighlighterColors.OPERATION_SIGN,
    )

    fun enforcedAttributes(key: TextAttributesKey): TextAttributes {
        val scheme = EditorColorsManager.getInstance().globalScheme
        val configured = scheme.getAttributes(key)?.takeUnless { it.isEmpty }
        val fallback = key.fallbackAttributeKey?.let(scheme::getAttributes)?.takeUnless { it.isEmpty }
        return (configured ?: fallback)?.clone() ?: TextAttributes()
    }
}

package dev.datastar.intellij.settings

import com.intellij.openapi.options.Configurable
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import javax.swing.JComponent
import javax.swing.JPanel

class DatastarConfigurable : Configurable {
    private var panel: JPanel? = null
    private val extensions = JBTextField()
    private val customAttributes = JBTextField()

    override fun getDisplayName(): String = "Datastar"

    override fun createComponent(): JComponent {
        panel = FormBuilder.createFormBuilder()
            .addLabeledComponent(JBLabel("Enabled file extensions:"), extensions, 1, false)
            .addComponent(JBLabel("Comma-separated, without leading dots."))
            .addLabeledComponent(JBLabel("Custom attributes:"), customAttributes, 1, false)
            .addComponent(JBLabel("Comma-separated plugin names, without the data- prefix."))
            .addComponentFillVertically(JPanel(), 0)
            .panel
        reset()
        return panel!!
    }

    override fun isModified(): Boolean {
        val state = DatastarSettings.getInstance().state
        return values(extensions.text) != state.enabledExtensions ||
            values(customAttributes.text) != state.customAttributes
    }

    override fun apply() {
        val state = DatastarSettings.getInstance().state
        state.enabledExtensions = values(extensions.text).toMutableList()
        state.customAttributes = values(customAttributes.text)
            .filter { it.matches(Regex("^[a-z][a-z0-9-]*$")) }
            .toMutableList()
    }

    override fun reset() {
        val state = DatastarSettings.getInstance().state
        extensions.text = state.enabledExtensions.joinToString(", ")
        customAttributes.text = state.customAttributes.joinToString(", ")
    }

    override fun disposeUIResources() {
        panel = null
    }

    private fun values(value: String): List<String> = value.split(',')
        .map { it.trim().removePrefix(".").lowercase() }
        .filter { it.isNotEmpty() }
        .distinct()
}

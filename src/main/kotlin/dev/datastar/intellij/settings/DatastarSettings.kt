package dev.datastar.intellij.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.vfs.VirtualFile

@State(name = "DatastarSettings", storages = [Storage("datastar.xml")])
class DatastarSettings : PersistentStateComponent<DatastarSettings.SettingsState> {
    data class SettingsState(
        var enabledExtensions: MutableList<String> = DEFAULT_EXTENSIONS.toMutableList(),
        var customAttributes: MutableList<String> = mutableListOf(),
    )

    private var state = SettingsState()

    override fun getState(): SettingsState = state

    override fun loadState(state: SettingsState) {
        this.state = state
    }

    fun isEnabled(file: VirtualFile?): Boolean = file?.extension?.lowercase() in state.enabledExtensions

    companion object {
        val DEFAULT_EXTENSIONS = listOf(
            "html", "htm", "php", "twig", "blade.php", "edge", "njk", "nunjucks",
            "templ", "astro", "vue", "svelte", "hbs", "handlebars", "mustache", "liquid",
            "erb", "ejs", "pug", "razor", "jinja", "jinja2", "gohtml", "jsp",
        )

        fun getInstance(): DatastarSettings =
            ApplicationManager.getApplication().getService(DatastarSettings::class.java)
    }
}

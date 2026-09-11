package dev.datastar.intellij.model

import dev.datastar.intellij.completion.DatastarCompletionEngine
import dev.datastar.intellij.documentation.DatastarDocumentation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DatastarDocumentTest {
    @Test
    fun `parses attributes only in opening tags`() {
        val source = "<div data-show=\"\$visible\"><!-- <p data-text=\"bad\"> --></div>"
        val attributes = DatastarDocument.attributes(source)
        assertEquals(1, attributes.size)
        assertEquals("show", attributes.single().pluginName)
        assertEquals("\$visible", attributes.single().value)
    }

    @Test
    fun `ignores tag-like source in script elements`() {
        val source = "<script>const example = `<div data-show>`</script><div data-text=\"value\">"
        assertEquals(listOf("text"), DatastarDocument.attributes(source).map { it.pluginName })
    }

    @Test
    fun `reports structural diagnostics`() {
        val source = "<div data-on=\"doThing()\" data-show data-effect:foo=\"bar\"></div>"
        assertEquals(
            listOf("key-required", "value-required", "key-not-allowed"),
            DatastarDocument.diagnostics(source).map { it.code },
        )
    }

    @Test
    fun `restricts nonce to html`() {
        assertTrue(DatastarDocument.diagnostics("<html data-nonce=\"abc\">").isEmpty())
        assertEquals("invalid-element", DatastarDocument.diagnostics("<main data-nonce=\"abc\">").single().code)
    }

    @Test
    fun `collects nested and case-converted signals`() {
        val source = listOf(
            "<div data-signals=\"{count: 0, user: {name: 'Ada'}}\">",
            "<div data-computed:full-name__case.snake=\"\$user.name\">",
            "<input data-bind=\"query\">",
        ).joinToString("")
        assertEquals(
            listOf("count", "user", "user.name", "full_name", "query"),
            DatastarDocument.signalDeclarations(source).map { it.name },
        )
    }

    @Test
    fun `resolves nested signal declarations`() {
        val source = "<div data-signals=\"{user: {name: 'Ada'}}\" data-text=\"\$user.name\">"
        assertEquals("user", DatastarDocument.resolveDeclaration(source, "user")?.name)
        assertEquals("user.name", DatastarDocument.resolveDeclaration(source, "user.name")?.name)
        assertEquals("user", DatastarDocument.resolveDeclaration(source, "user.missing")?.name)

        val keyed = "<div data-signals:_foo.bar=\"0\" data-text=\"\$_foo.bar\">"
        assertEquals(listOf("_foo", "_foo.bar"), DatastarDocument.signalDeclarations(keyed).map { it.name })
        assertEquals("_foo", DatastarDocument.resolveDeclaration(keyed, "_foo")?.name)
        assertEquals("_foo.bar", DatastarDocument.resolveDeclaration(keyed, "_foo.bar")?.name)
    }

    @Test
    fun `completes attributes events modifiers signals actions and options`() {
        fun labels(source: String) = DatastarCompletionEngine.completions(source, source.length, emptyList()).map { it.label }

        assertTrue("data-show" in labels("<div data-sh"))
        assertFalse("data-nonce" in labels("<div data-n"))
        assertTrue("data-nonce" in DatastarCompletionEngine.completions("<html data-n>", "<html data-n".length, emptyList()).map { it.label })
        assertTrue("data-on:click" in labels("<button data-on:"))
        assertTrue("debounce" in labels("<button data-on:click__"))
        assertTrue("@post" in labels("<button data-on:click=\"@po"))
        val optionLabels = labels("<button data-on:click=\"@get('/endpoint', {re")
        assertTrue(optionLabels.toString(), "retry" in optionLabels)

        val signals = "<div data-signals=\"{user: {name: 'Ada'}, count: 0}\" data-text=\"\$us"
        assertTrue("\$user" in labels(signals))
        assertTrue("\$count" in labels(signals))
    }

    @Test
    fun `returns attribute action and signal documentation`() {
        val source = "<div data-signals=\"{user: {name: 'Ada'}}\" data-text=\"@post('/endpoint') + \$user.name\">"
        assertTrue(DatastarDocumentation.at(source, source.indexOf("data-text") + 2)!!.description.isNotBlank())
        assertTrue(DatastarDocumentation.at(source, source.indexOf("@post") + 2)!!.title.startsWith("@post("))
        assertEquals("\$user.name", DatastarDocumentation.at(source, source.indexOf("\$user.name") + 7)!!.title)

        val options = "<button data-on:click=\"@get('/endpoint', {retry: 'always'})\">"
        assertTrue(DatastarDocumentation.at(options, options.indexOf("retry") + 2)!!.description.contains("when to retry"))
    }

    @Test
    fun `loads current metadata`() {
        assertNotNull(DatastarLanguageData.attributes["show"])
        assertTrue(DatastarLanguageData.data.nativeEvents.contains("click"))
        assertNotNull(DatastarLanguageData.actions["post"])
    }
}

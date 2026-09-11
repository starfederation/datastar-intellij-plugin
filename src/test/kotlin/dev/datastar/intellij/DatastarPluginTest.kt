package dev.datastar.intellij

import com.intellij.ide.highlighter.HtmlFileType
import com.intellij.codeInsight.documentation.DocumentationManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlAttribute
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.datastar.intellij.highlighting.DatastarInspectionSuppressor
import dev.datastar.intellij.documentation.DatastarDocumentationProvider
import dev.datastar.intellij.documentation.DatastarDocumentationTargetProvider
import dev.datastar.intellij.highlighting.DatastarHighlighting
import com.intellij.lang.documentation.DocumentationMarkup

class DatastarPluginTest : BasePlatformTestCase() {
    fun testAttributeCompletion() {
        myFixture.configureByText(HtmlFileType.INSTANCE, "<div data-sh<caret>>")
        val variants = myFixture.completeBasic()
        if (variants == null) {
            assertTrue(myFixture.editor.document.text.contains("data-show"))
        } else {
            assertContainsElements(variants.map { it.lookupString }, "data-show")
        }
    }

    fun testStructuralDiagnostic() {
        myFixture.configureByText(HtmlFileType.INSTANCE, "<main data-nonce=\"abc\"></main>")
        val diagnostics = myFixture.doHighlighting().mapNotNull { it.description }
        assertTrue(diagnostics.any { it.contains("only valid on the <html> element") })
    }

    fun testDatastarKeysAreNotTreatedAsXmlNamespaces() {
        myFixture.configureByText(HtmlFileType.INSTANCE, "<div data-signals:_foo.bar=\"0\"></div>")
        val diagnostics = myFixture.doHighlighting().mapNotNull { it.description }
        assertFalse(diagnostics.any { it.contains("is not bound") })
    }

    fun testAttributeUsesSemanticColoursForEachNameSegment() {
        val source = "<div data-on:click__delay.500ms=\"@get(\$foo, {retryScaler: 3})\"></div>"
        myFixture.configureByText(HtmlFileType.INSTANCE, source)
        val highlights = myFixture.doHighlighting()

        fun hasHighlight(token: String, key: com.intellij.openapi.editor.colors.TextAttributesKey): Boolean {
            val start = source.indexOf(token)
            return highlights.any {
                it.startOffset == start &&
                    it.endOffset == start + token.length &&
                    it.forcedTextAttributes == DatastarHighlighting.enforcedAttributes(key)
            }
        }

        assertTrue(hasHighlight("data-", DatastarHighlighting.ATTRIBUTE_PREFIX))
        assertTrue(hasHighlight("on", DatastarHighlighting.PLUGIN))
        assertTrue(hasHighlight("click", DatastarHighlighting.KEY))
        assertTrue(hasHighlight("delay", DatastarHighlighting.MODIFIER))
        assertTrue(hasHighlight("500ms", DatastarHighlighting.MODIFIER_ARGUMENT))
        assertTrue(hasHighlight("@get", DatastarHighlighting.ACTION))
        assertTrue(hasHighlight("\$foo", DatastarHighlighting.SIGNAL))
        assertTrue(hasHighlight("retryScaler", DatastarHighlighting.PROPERTY))
        assertTrue(hasHighlight("3", DatastarHighlighting.NUMBER))
    }

    fun testNamespaceSuppressionIsLimitedToDatastarAttributes() {
        val file = myFixture.configureByText(
            HtmlFileType.INSTANCE,
            "<div data-signals:_foo.bar=\"0\" example:value=\"0\"></div>",
        )
        val attributes = PsiTreeUtil.findChildrenOfType(file, XmlAttribute::class.java).associateBy { it.name }
        val suppressor = DatastarInspectionSuppressor()
        assertTrue(suppressor.isSuppressedFor(attributes.getValue("data-signals:_foo.bar"), "XmlUnboundNsPrefix"))
        assertFalse(suppressor.isSuppressedFor(attributes.getValue("example:value"), "XmlUnboundNsPrefix"))
    }

    fun testDatastarKeyHasDatastarDocumentation() {
        val source = "<div data-signals:_foo.bar=\"0\"></div>"
        val file = myFixture.configureByText(HtmlFileType.INSTANCE, source)
        val element = file.findElementAt(source.indexOf("_foo.bar") + 2)!!
        val documentation = DatastarDocumentationProvider().generateDoc(element, element)!!
        assertTrue(documentation.contains("data-signals:_foo.bar"))
        assertFalse(documentation.contains("Namespace"))
    }

    fun testActionHoverUsesTheExactCaretOffset() {
        val source = "<button data-on:click=\"@get('/endpoint')\"></button>"
        val file = myFixture.configureByText(HtmlFileType.INSTANCE, source)
        val offset = source.indexOf("@get") + 2
        val context = file.findElementAt(offset)!!
        val provider = DatastarDocumentationProvider()
        val target = provider.getCustomDocumentationElement(myFixture.editor, file, context, offset)!!
        val documentation = provider.generateDoc(target, context)!!
        assertTrue(documentation.startsWith(DocumentationMarkup.DEFINITION_START))
        assertTrue(documentation.contains(DocumentationMarkup.CONTENT_START))
        assertTrue(documentation.contains("@get(uri: string, options={ })"))
        assertTrue(documentation.contains("Datastar documentation"))
    }

    fun testBackendOptionIsAnIntellijDocumentationTarget() {
        val source = "<button data-on:click=\"@get('/endpoint', {contentType: 'form'})\"></button>"
        val file = myFixture.configureByText(HtmlFileType.INSTANCE, source)
        val offset = source.indexOf("contentType") + 2
        val context = file.findElementAt(offset)!!
        val manager = DocumentationManager.getInstance(project)
        val target = manager.findTargetElementAtOffset(myFixture.editor, offset, file, context)
        assertNotNull(target)
        val documentation = manager.generateDocumentation(target!!, context, false)
        assertNotNull(documentation)
        assertTrue(documentation!!.contains("contentType"))
        assertTrue(documentation.contains("type of content to send"))
    }

    fun testBackendOptionIsAModernDocumentationTarget() {
        val source = "<button data-on:click=\"@get('/endpoint', {contentType: 'form'})\"></button>"
        val file = myFixture.configureByText(HtmlFileType.INSTANCE, source)
        val offset = source.indexOf("contentType") + 2
        val targets = DatastarDocumentationTargetProvider().documentationTargets(file, offset)

        assertSize(1, targets)
        assertEquals("contentType", targets.single().computePresentation().presentableText)
        assertTrue(targets.single().computeDocumentationHint()!!.contains("type of content to send"))
        assertNotNull(targets.single().computeDocumentation())
    }

    fun testSignalReferenceResolvesToDeclaration() {
        val source = "<div data-signals=\"{user: {name: 'Ada'}}\" data-text=\"\$user.na<caret>me\"></div>"
        myFixture.configureByText(HtmlFileType.INSTANCE, source)
        val target = myFixture.getReferenceAtCaretPosition()!!.resolve()
        assertNotNull(target)
        assertEquals("name", target!!.text)
        assertEquals(source.indexOf("name:"), target.textOffset)
    }

    fun testRootSignalReferenceResolvesToRootDeclaration() {
        val source = "<div data-signals:_foo.bar=\"0\" data-text=\"\$_f<caret>oo.bar\"></div>"
        myFixture.configureByText(HtmlFileType.INSTANCE, source)
        val target = myFixture.getReferenceAtCaretPosition()!!.resolve()
        assertNotNull(target)
        assertEquals("_foo", target!!.text)
        assertEquals(source.indexOf("_foo.bar"), target.textOffset)
    }

    fun testSignalRename() {
        val source = "<div data-signals=\"{user: {name: 'Ada'}}\" data-text=\"\$user.na<caret>me\"></div>"
        myFixture.configureByText(HtmlFileType.INSTANCE, source)
        myFixture.renameElementAtCaret("fullName")
        myFixture.checkResult("<div data-signals=\"{user: {fullName: 'Ada'}}\" data-text=\"\$user.fullName\"></div>")
    }

    fun testRootSignalRenamePreservesNestedPath() {
        val source = "<div data-signals:_foo.bar=\"0\" data-text=\"\$_f<caret>oo.bar\"></div>"
        myFixture.configureByText(HtmlFileType.INSTANCE, source)
        myFixture.renameElementAtCaret("account")
        myFixture.checkResult("<div data-signals:account.bar=\"0\" data-text=\"\$account.bar\"></div>")
    }

    override fun getTestDataPath(): String = "testData"
}

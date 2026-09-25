/*
 * Copyright 2026 JTaccuino Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jtaccuino.core.ui.documentation;

import jfx.incubator.scene.control.richtext.model.SimpleViewOnlyStyledModel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class JavadocRendererTest {

    @Test
    public void rendersPlainText() {
        var model = JavadocRenderer.render("some documentation text", "", "");
        assertNotNull(model);
        assertEquals("some documentation text", model.getPlainText(0));
    }

    @Test
    public void stripsHtmlTags() {
        var model = JavadocRenderer.render("Returns the <code>length</code> of the <b>string</b>.", "", "");
        assertNotNull(model);
        assertEquals("Returns the length of the string.", model.getPlainText(0));
    }

    @Test
    public void decodesEntities() {
        var model = JavadocRenderer.render("a &lt; b &amp;&amp; c", "", "");
        assertNotNull(model);
        assertEquals("a < b && c", model.getPlainText(0));
    }

    @Test
    public void keepsJavadocTagText() {
        var model = JavadocRenderer.render("@param index the index", "", "");
        assertNotNull(model);
        assertEquals("@param index the index", model.getPlainText(0));
    }

    @Test
    public void highlightsPlainTextJavadocTagsPerLine() {
        var model = JavadocRenderer.render("Returns the string.\n@param a the first\n@return the result", "", "");
        assertNotNull(model);
        assertEquals(3, model.size());
        assertEquals("Returns the string.", model.getPlainText(0));
        assertEquals("@param a the first", model.getPlainText(1));
        assertEquals("@return the result", model.getPlainText(2));
    }

    @Test
    public void rendersInlineCodeTag() {
        var model = JavadocRenderer.render("Use {@code for (int i = 0; i < n; i++)} to loop.", "", "");
        assertNotNull(model);
        assertEquals("Use for (int i = 0; i < n; i++) to loop.", model.getPlainText(0));
    }

    @Test
    public void rendersInlineLinkTag() {
        var model = JavadocRenderer.render("See {@link java.util.List#size} for details.", "", "");
        assertNotNull(model);
        assertEquals("See java.util.List#size for details.", model.getPlainText(0));
    }

    @Test
    public void splitsParagraphs() {
        var model = JavadocRenderer.render("<p>first</p><p>second</p>", "", "");
        assertNotNull(model);
        assertEquals(2, model.size());
        assertEquals("first", model.getPlainText(0));
        assertEquals("second", model.getPlainText(1));
    }

    @Test
    public void rendersHeader() {
        var model = JavadocRenderer.render("Indicates whether some object is equal to this one.",
                "java.lang.Object", "public boolean equals(Object obj)");
        assertNotNull(model);
        assertEquals(3, model.size());
        assertEquals("java.lang.Object", model.getPlainText(0));
        assertEquals("public boolean equals(Object obj)", model.getPlainText(1));
        assertEquals("Indicates whether some object is equal to this one.", model.getPlainText(2));
    }

    @Test
    public void rendersParamSectionPlainText() {
        var model = JavadocRenderer.render("Description.\n@param obj the object to compare\n@return true if equal", "", "");
        assertNotNull(model);
        assertEquals(3, model.size());
        assertEquals("Description.", model.getPlainText(0));
        assertEquals("@param obj the object to compare", model.getPlainText(1));
        assertEquals("@return true if equal", model.getPlainText(2));
    }
}

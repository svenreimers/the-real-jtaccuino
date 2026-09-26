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

import java.util.ArrayList;
import java.util.List;
import jfx.incubator.scene.control.richtext.model.SimpleViewOnlyStyledModel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class MarkdownToStyledModelTest {

    @Test
    public void rendersParagraph() {
        var model = MarkdownToStyledModel.render("some text");
        assertNotNull(model);
        assertEquals(List.of("some text"), nonEmptyParagraphs(model));
    }

    @Test
    public void rendersSeparateParagraphs() {
        var model = MarkdownToStyledModel.render("first\n\nsecond");
        assertNotNull(model);
        assertEquals(List.of("first", "second"), nonEmptyParagraphs(model));
    }

    @Test
    public void rendersBoldSectionLabel() {
        var model = MarkdownToStyledModel.render("**Parameters:**");
        assertNotNull(model);
        assertEquals(List.of("Parameters:"), nonEmptyParagraphs(model));
    }

    @Test
    public void rendersBulletItems() {
        var model = MarkdownToStyledModel.render("- one\n- two");
        assertNotNull(model);
        assertEquals(List.of("one", "two"), nonEmptyParagraphs(model));
    }

    @Test
    public void rendersInlineCode() {
        var model = MarkdownToStyledModel.render("Use `code` here");
        assertNotNull(model);
        assertEquals(List.of("Use code here"), nonEmptyParagraphs(model));
    }

    private static List<String> nonEmptyParagraphs(SimpleViewOnlyStyledModel model) {
        var result = new ArrayList<String>();
        for (int i = 0; i < model.size(); i++) {
            var text = model.getPlainText(i);
            if (!text.isBlank()) {
                result.add(text);
            }
        }
        return result;
    }
}

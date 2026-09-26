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

import javafx.scene.paint.Color;
import jfx.incubator.scene.control.richtext.model.SimpleViewOnlyStyledModel;
import jfx.incubator.scene.control.richtext.model.StyleAttributeMap;
import com.vladsch.flexmark.ast.BulletList;
import com.vladsch.flexmark.ast.BulletListItem;
import com.vladsch.flexmark.ast.Code;
import com.vladsch.flexmark.ast.Emphasis;
import com.vladsch.flexmark.ast.FencedCodeBlock;
import com.vladsch.flexmark.ast.Heading;
import com.vladsch.flexmark.ast.Paragraph;
import com.vladsch.flexmark.ast.StrongEmphasis;
import com.vladsch.flexmark.ast.Text;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.ast.Node;

/**
 * Renders a markdown string into a {@link SimpleViewOnlyStyledModel} for the
 * incubator RichTextArea, mirroring how {@code MdUtils} renders markdown into
 * the Gluon rich text area. It supports the markdown constructs produced by
 * {@link JavadocHtmlToMarkdown}: paragraphs, headings, bold/italic emphasis,
 * inline code, fenced code blocks and bullet lists.
 */
final class MarkdownToStyledModel {

    private static final String MONOSPACE_FAMILY = "Monaspace Argon";
    private static final double SPACE_ABOVE = 6;
    private static final double INDENT = 20;

    private MarkdownToStyledModel() {
        // prevent instantiation
    }

    static SimpleViewOnlyStyledModel render(String markdown) {
        var model = new SimpleViewOnlyStyledModel();
        renderInto(markdown, model);
        return model;
    }

    static void renderInto(String markdown, SimpleViewOnlyStyledModel model) {
        var parser = Parser.builder().build();
        var document = parser.parse(markdown == null ? "" : markdown);
        new Walker(model).walkChildren(document);
    }

    private static final class Walker {

        private final SimpleViewOnlyStyledModel model;

        private Walker(SimpleViewOnlyStyledModel model) {
            this.model = model;
        }

        private void walkChildren(Node node) {
            var child = node.getFirstChild();
            while (child != null) {
                walk(child);
                child = child.getNext();
            }
        }

        private void walk(Node node) {
            if (node instanceof Text t) {
                model.addSegment(t.getChars().toString(), StyleAttributeMap.EMPTY);
                return;
            }
            if (node instanceof Code c) {
                model.addSegment(c.getText().toString(), codeStyle());
                return;
            }
            if (node instanceof Emphasis e) {
                walkStyled(e, StyleAttributeMap.builder().setItalic(true).build());
                return;
            }
            if (node instanceof StrongEmphasis se) {
                walkStyled(se, StyleAttributeMap.builder().setBold(true).build());
                return;
            }
            if (node instanceof Heading h) {
                model.addSegment(plainText(h), StyleAttributeMap.builder()
                        .setBold(true)
                        .setSpaceAbove(SPACE_ABOVE)
                        .build());
                model.nl();
                return;
            }
            if (node instanceof Paragraph p) {
                walkChildren(p);
                model.nl();
                return;
            }
            if (node instanceof BulletListItem bli) {
                model.addSegment("", StyleAttributeMap.builder()
                        .setBullet("\u2022")
                        .setSpaceLeft(INDENT)
                        .setSpaceAbove(SPACE_ABOVE)
                        .build());
                walkChildren(bli);
                model.nl();
                return;
            }
            if (node instanceof BulletList bl) {
                walkChildren(bl);
                return;
            }
            if (node instanceof FencedCodeBlock fcb) {
                model.addSegment(fcb.getContentChars().toString(), StyleAttributeMap.builder()
                        .setFontFamily(MONOSPACE_FAMILY)
                        .setBackground(Color.GAINSBORO)
                        .setSpaceAbove(SPACE_ABOVE)
                        .build());
                model.nl();
                return;
            }
            walkChildren(node);
        }

        private void walkStyled(Node node, StyleAttributeMap style) {
            var child = node.getFirstChild();
            while (child != null) {
                if (child instanceof Text t) {
                    model.addSegment(t.getChars().toString(), style);
                } else {
                    walk(child);
                }
                child = child.getNext();
            }
        }

        private static String plainText(Node node) {
            var sb = new StringBuilder();
            var child = node.getFirstChild();
            while (child != null) {
                if (child instanceof Text t) {
                    sb.append(t.getChars());
                } else {
                    sb.append(plainText(child));
                }
                child = child.getNext();
            }
            return sb.toString();
        }

        private static StyleAttributeMap codeStyle() {
            return StyleAttributeMap.builder()
                    .setFontFamily(MONOSPACE_FAMILY)
                    .setBackground(Color.GAINSBORO)
                    .build();
        }
    }
}

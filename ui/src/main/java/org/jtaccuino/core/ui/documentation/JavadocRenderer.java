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

import java.util.Locale;
import java.util.Set;
import javafx.scene.paint.Color;
import jfx.incubator.scene.control.richtext.model.SimpleViewOnlyStyledModel;
import jfx.incubator.scene.control.richtext.model.StyleAttributeMap;

/**
 * Renders a javadoc string plus an optional header (enclosing type + member
 * signature) into a {@link SimpleViewOnlyStyledModel}. The javadoc text coming
 * from JShell is HTML-ish; this renderer translates a well-known subset of
 * HTML tags, inline code/link tags and the standard javadoc block tags into
 * styled segments. Paragraphs are kept intact and javadoc sections
 * (Parameters/Returns/Throws/See Also) are rendered with an indented body.
 */
final class JavadocRenderer {

    private static final String MONOSPACE_FAMILY = "Monaspace Argon";
    private static final Set<String> JAVADOC_TAGS = Set.of(
            "param", "return", "throws", "exception", "since", "see",
            "author", "version", "deprecated", "serial", "serialField",
            "serialData", "value", "hidden", "index", "docRoot");
    private static final double INDENT = 24;

    private JavadocRenderer() {
        // prevent instantiation
    }

    static SimpleViewOnlyStyledModel render(String javadoc, String typeName, String signature) {
        var model = new SimpleViewOnlyStyledModel();
        var parser = new Parser(model);
        parser.renderHeader(typeName, signature);
        parser.parse(javadoc == null ? "" : javadoc);
        return model;
    }

    private static final class Parser {

        private final SimpleViewOnlyStyledModel model;
        private final StringBuilder text = new StringBuilder();

        private boolean bold;
        private boolean italic;
        private boolean monospace;

        private Parser(SimpleViewOnlyStyledModel model) {
            this.model = model;
        }

        private void renderHeader(String typeName, String signature) {
            if (typeName != null && !typeName.isBlank()) {
                model.addSegment(typeName, StyleAttributeMap.builder().setFontFamily(MONOSPACE_FAMILY).build());
                model.nl();
            }
            if (signature != null && !signature.isBlank()) {
                model.addSegment(signature, StyleAttributeMap.builder().setBold(true).build());
                model.nl();
            }
        }

        private void parse(String javadoc) {
            boolean hasHtml = javadoc.indexOf('<') >= 0;
            int i = 0;
            while (i < javadoc.length()) {
                char c = javadoc.charAt(i);
                if (c == '<') {
                    int end = javadoc.indexOf('>', i);
                    if (end == -1) {
                        text.append(javadoc.substring(i));
                        break;
                    }
                    handleTag(javadoc.substring(i + 1, end));
                    i = end + 1;
                } else if (c == '{') {
                    int end = javadoc.indexOf('}', i);
                    if (end == -1) {
                        text.append(javadoc.substring(i));
                        break;
                    }
                    handleInlineTag(javadoc.substring(i, end + 1));
                    i = end + 1;
                } else if (c == '&') {
                    int end = javadoc.indexOf(';', i);
                    if (end == -1) {
                        text.append(javadoc.substring(i));
                        break;
                    }
                    text.append(decodeEntity(javadoc.substring(i + 1, end)));
                    i = end + 1;
                } else if (c == '\n') {
                    if (!hasHtml) {
                        flushTextLine();
                        model.nl();
                    } else {
                        text.append(' ');
                    }
                    i++;
                } else {
                    text.append(c);
                    i++;
                }
            }
            flushTextLine();
        }

        private void handleTag(String rawTag) {
            var tag = rawTag.trim();
            var closing = tag.startsWith("/");
            if (closing) {
                tag = tag.substring(1).trim();
            }
            var name = tag.split("[\\s>]", 2)[0].toLowerCase(Locale.ROOT);
            if (name.isEmpty()) {
                return;
            }
            switch (name) {
                case "b", "strong" -> setFlags(!closing, italic, monospace);
                case "i", "em", "cite" -> setFlags(bold, !closing, monospace);
                case "code", "tt", "pre" -> setFlags(bold, italic, !closing);
                case "br", "hr" -> {
                    flushTextLine();
                    model.nl();
                }
                case "p", "div", "li", "tr", "ul", "ol", "dl", "blockquote" -> {
                    if (!closing) {
                        flushTextLine();
                        model.nl();
                    }
                }
                case "h1", "h2", "h3", "h4", "h5", "h6" -> {
                    if (!closing) {
                        flushTextLine();
                        bold = true;
                        model.nl();
                    } else {
                        flushTextLine();
                        bold = false;
                    }
                }
                case "dt" -> {
                    flushTextLine();
                    if (!closing) {
                        model.nl();
                    }
                }
                case "dd" -> {
                    flushTextLine();
                    if (!closing) {
                        model.nl();
                    }
                }
                case "table" -> {
                    if (closing) {
                        flushTextLine();
                        model.nl();
                    }
                }
                default -> {
                    // ignore all other tags, e.g. a, span, font, img
                }
            }
        }

        private void setFlags(boolean newBold, boolean newItalic, boolean newMonospace) {
            flushTextLine();
            this.bold = newBold;
            this.italic = newItalic;
            this.monospace = newMonospace;
        }

        private void handleInlineTag(String inline) {
            var inner = inline.substring(1, inline.length() - 1).trim();
            if (inner.startsWith("@")) {
                var name = inner.substring(1).split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
                switch (name) {
                    case "code" -> appendInline(inner.substring("@code".length()).trim(), true, false, false);
                    case "literal" -> appendInline(inner.substring("@literal".length()).trim(), false, false, false);
                    case "link", "linkplain", "value" -> appendInline(inner.substring(name.length() + 1).trim(), false, false, true);
                    default -> text.append(inline);
                }
            } else {
                text.append(inline);
            }
        }

        private void appendInline(String content, boolean code, boolean italic, boolean underline) {
            flush();
            var builder = StyleAttributeMap.builder();
            if (code) {
                builder.setFontFamily(MONOSPACE_FAMILY);
                builder.setBackground(Color.GAINSBORO);
            }
            if (italic) {
                builder.setItalic(true);
            }
            if (underline) {
                builder.setUnderline(true);
            }
            model.addSegment(content, builder.build());
        }

        private String decodeEntity(String entity) {
            return switch (entity) {
                case "lt" -> "<";
                case "gt" -> ">";
                case "amp" -> "&";
                case "quot" -> "\"";
                case "apos" -> "'";
                case "nbsp" -> " ";
                case "copy" -> "\u00a9";
                default -> entity.startsWith("#") ? decodeNumeric(entity.substring(1)) : "&" + entity + ";";
            };
        }

        private String decodeNumeric(String numeric) {
            try {
                if (numeric.startsWith("x")) {
                    return String.valueOf((char) Integer.parseInt(numeric.substring(1), 16));
                }
                return String.valueOf((char) Integer.parseInt(numeric));
            } catch (NumberFormatException ex) {
                return "&" + numeric + ";";
            }
        }

        /**
         * Flushes the current line. If it is a plain-text javadoc tag line
         * (e.g. a param tag followed by its description), it is rendered with a
         * bold tag and an indented body; otherwise the line is appended as a
         * normal segment.
         */
        private void flushTextLine() {
            if (text.isEmpty()) {
                return;
            }
            var content = text.toString();
            text.setLength(0);

            var tag = extractJavadocTag(content);
            if (tag != null) {
                model.addSegment(tag.label() + " ", boldStyle());
                model.addSegment(tag.rest(), StyleAttributeMap.builder()
                        .setSpaceLeft(INDENT)
                        .build());
                return;
            }
            model.addSegment(content, currentStyle());
        }

        private void flush() {
            if (text.isEmpty()) {
                return;
            }
            model.addSegment(text.toString(), currentStyle());
            text.setLength(0);
        }

        private record JavadocTag(String label, String rest) {
        }

        private JavadocTag extractJavadocTag(String content) {
            if (!content.startsWith("@")) {
                return null;
            }
            var parts = content.split("\\s+", 2);
            if (parts.length == 0 || !JAVADOC_TAGS.contains(parts[0].substring(1))) {
                return null;
            }
            return new JavadocTag(parts[0], parts.length > 1 ? parts[1] : "");
        }

        private StyleAttributeMap currentStyle() {
            if (!bold && !italic && !monospace) {
                return StyleAttributeMap.EMPTY;
            }
            var builder = StyleAttributeMap.builder();
            if (bold) {
                builder.setBold(true);
            }
            if (italic) {
                builder.setItalic(true);
            }
            if (monospace) {
                builder.setFontFamily(MONOSPACE_FAMILY);
                builder.setBackground(Color.GAINSBORO);
            }
            return builder.build();
        }

        private StyleAttributeMap boldStyle() {
            return StyleAttributeMap.builder().setBold(true).build();
        }
    }
}

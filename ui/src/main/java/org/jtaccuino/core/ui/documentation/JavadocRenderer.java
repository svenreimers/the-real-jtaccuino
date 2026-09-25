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
 * Renders a javadoc string into a {@link SimpleViewOnlyStyledModel}. The
 * javadoc text coming from JShell is plain HTML-ish; this renderer translates
 * a small, well-known subset of HTML tags, inline code/link tags and the
 * standard javadoc block tags into styled segments.
 */
final class JavadocRenderer {

    private static final String MONOSPACE_FAMILY = "Monaspace Argon";
    private static final Set<String> JAVADOC_TAGS = Set.of(
            "param", "return", "throws", "exception", "since", "see",
            "author", "version", "deprecated", "serial", "serialField",
            "serialData", "value", "hidden", "index", "docRoot");

    private JavadocRenderer() {
        // prevent instantiation
    }

    static SimpleViewOnlyStyledModel render(String javadoc) {
        var model = new SimpleViewOnlyStyledModel();
        new Parser(model).parse(javadoc == null ? "" : javadoc);
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

        private void parse(String javadoc) {
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
                    flush();
                    model.nl();
                    i++;
                } else {
                    text.append(c);
                    i++;
                }
            }
            flush();
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
                    flush();
                    model.nl();
                }
                case "p", "div", "li", "tr", "ul", "ol", "dl", "blockquote" -> {
                    if (!closing) {
                        flush();
                        model.nl();
                    }
                }
                case "h1", "h2", "h3", "h4", "h5", "h6" -> {
                    if (!closing) {
                        flush();
                        bold = true;
                        model.nl();
                    } else {
                        flush();
                        bold = false;
                    }
                }
                case "table" -> {
                    if (closing) {
                        flush();
                        model.nl();
                    }
                }
                default -> {
                    // ignore all other tags, e.g. a, span, font, img
                }
            }
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

        private void setFlags(boolean newBold, boolean newItalic, boolean newMonospace) {
            flush();
            this.bold = newBold;
            this.italic = newItalic;
            this.monospace = newMonospace;
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

        private void flush() {
            if (text.isEmpty()) {
                return;
            }
            var content = text.toString();
            text.setLength(0);

            var javaDocTag = extractJavadocTag(content);
            if (javaDocTag != null) {
                if (!javaDocTag.label().isEmpty()) {
                    model.addSegment(javaDocTag.label(), boldStyle());
                }
                var rest = javaDocTag.rest();
                if (!rest.isEmpty()) {
                    model.addSegment(" " + rest, currentStyle());
                }
                return;
            }
            model.addSegment(content, currentStyle());
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

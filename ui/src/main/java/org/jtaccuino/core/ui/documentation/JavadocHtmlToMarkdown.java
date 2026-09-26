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

/**
 * Converts the HTML-ish javadoc text produced by JShell into markdown,
 * following the layout defined in {@code docs/javadoc-preview-format.md}.
 * Handles the well-known subset of HTML tags used by javadoc, inline code and
 * link tags, HTML entities and the standard javadoc block tags (as plain-text
 * sections).
 */
final class JavadocHtmlToMarkdown {

    private static final Set<String> JAVADOC_TAGS = Set.of(
            "param", "return", "throws", "exception", "since", "see",
            "author", "version", "deprecated", "serial", "serialField",
            "serialData", "value", "hidden", "index", "docRoot");

    private JavadocHtmlToMarkdown() {
        // prevent instantiation
    }

    static String convert(String javadoc) {
        return new Converter(javadoc == null ? "" : javadoc).convert();
    }

    private static final class Converter {

        private final String source;
        private final StringBuilder out = new StringBuilder();
        private int pos;

        private Converter(String source) {
            this.source = source;
        }

        private String convert() {
            boolean hasHtml = source.indexOf('<') >= 0;
            while (pos < source.length()) {
                char c = source.charAt(pos);
                if (c == '<') {
                    handleTag();
                } else if (c == '&') {
                    handleEntity();
                } else if (c == '{') {
                    handleInline();
                } else if (c == '\n' && !hasHtml) {
                    out.append('\n');
                    pos++;
                } else {
                    out.append(c);
                    pos++;
                }
            }
            return hasHtml ? out.toString() : plainTextToMarkdown(out.toString());
        }

        private static String plainTextToMarkdown(String text) {
            var lines = text.split("\n", -1);
            var sb = new StringBuilder();
            for (var line : lines) {
                var trimmed = line.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                if (sb.length() > 0) {
                    sb.append("\n\n");
                }
                var parts = trimmed.split("\\s+", 2);
                if (parts.length > 0 && parts[0].startsWith("@")
                        && JAVADOC_TAGS.contains(parts[0].substring(1))) {
                    sb.append("**").append(parts[0]).append("**");
                    if (parts.length > 1) {
                        sb.append(' ').append(parts[1]);
                    }
                } else {
                    sb.append(trimmed);
                }
            }
            return sb.toString();
        }

        private void handleTag() {
            int end = source.indexOf('>', pos);
            if (end == -1) {
                out.append(source.substring(pos));
                pos = source.length();
                return;
            }
            var raw = source.substring(pos + 1, end).trim();
            pos = end + 1;
            var closing = raw.startsWith("/");
            if (closing) {
                raw = raw.substring(1).trim();
            }
            var name = raw.split("[\\s>]", 2)[0].toLowerCase(Locale.ROOT);
            if (name.isEmpty()) {
                return;
            }
            switch (name) {
                case "p", "div", "tr", "dl", "blockquote", "table" -> {
                    ensureBreak();
                }
                case "br", "hr" -> {
                    ensureBreak();
                }
                case "h1", "h2", "h3", "h4", "h5", "h6" -> {
                    if (!closing) {
                        ensureBreak();
                        out.append("#".repeat(name.charAt(1) - '0')).append(' ');
                    }
                }
                case "b", "strong" -> out.append("**");
                case "i", "em", "cite" -> out.append("*");
                case "code", "tt" -> out.append('`');
                case "pre" -> {
                    if (!closing) {
                        ensureBreak();
                        out.append("```\n");
                    } else {
                        out.append("\n```");
                        ensureBreak();
                    }
                }
                case "ul", "ol" -> {
                    if (closing) {
                        ensureBreak();
                    }
                }
                case "li" -> {
                    if (!closing) {
                        ensureBreak();
                        out.append("- ");
                    }
                }
                case "dt" -> {
                    if (!closing) {
                        ensureBreak();
                        out.append("**");
                    } else {
                        out.append("**");
                        ensureBreak();
                    }
                }
                case "dd" -> {
                    if (!closing) {
                        ensureBreak();
                        out.append("- ");
                    }
                }
                default -> {
                    // drop all other tags: a, span, font, img, u, sup, sub, ...
                }
            }
        }

        private void handleEntity() {
            int end = source.indexOf(';', pos);
            if (end == -1) {
                out.append(source.substring(pos));
                pos = source.length();
                return;
            }
            var entity = source.substring(pos + 1, end);
            pos = end + 1;
            out.append(switch (entity) {
                case "lt" -> "<";
                case "gt" -> ">";
                case "amp" -> "&";
                case "quot" -> "\"";
                case "apos" -> "'";
                case "nbsp" -> " ";
                case "copy" -> "\u00a9";
                default -> entity.startsWith("#") ? decodeNumeric(entity.substring(1)) : "&" + entity + ";";
            });
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

        private void handleInline() {
            int end = source.indexOf('}', pos);
            if (end == -1) {
                out.append(source.substring(pos));
                pos = source.length();
                return;
            }
            var inner = source.substring(pos + 1, end).trim();
            pos = end + 1;
            if (inner.startsWith("@")) {
                var name = inner.substring(1).split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
                switch (name) {
                    case "code" -> out.append('`').append(inner.substring("@code".length()).trim()).append('`');
                    case "literal" -> out.append(inner.substring("@literal".length()).trim());
                    case "link", "linkplain", "value" -> out.append(inner.substring(name.length() + 1).trim());
                    default -> {
                        // drop @inheritDoc, @index etc.
                    }
                }
            } else {
                out.append(inner);
            }
        }

        private void ensureBreak() {
            int len = out.length();
            if (len > 0 && out.charAt(len - 1) != '\n') {
                out.append("\n\n");
            }
        }
    }
}

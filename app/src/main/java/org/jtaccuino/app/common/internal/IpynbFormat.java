/*
 * Copyright 2024-2025 JTaccuino Contributors
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
package org.jtaccuino.app.common.internal;

import jakarta.json.JsonArray;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import jakarta.json.bind.annotation.JsonbTypeDeserializer;
import jakarta.json.bind.serializer.DeserializationContext;
import jakarta.json.bind.serializer.JsonbDeserializer;
import jakarta.json.stream.JsonParser;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.jtaccuino.core.ui.api.CellData;

public record IpynbFormat(Map<String, Object> metadata, int nbformat, int nbformat_minor, List<Cell> cells) implements IpynbFormatOperations {

    public static record CodeCell(String id, String cell_type, Map<String, Object> metadata, String source, List<Output> outputs, int execution_count) implements Cell {

        @Override
        public CellData toCellData() {
            return CellData.of(
                    CellData.Type.of(cell_type()),
                    source(),
                    Optional.ofNullable(id()).map(UUID::fromString).orElseGet(UUID::randomUUID),
                    Objects.requireNonNullElse(outputs(), List.<Output>of()).stream()
                            .map(o
                                    -> CellData.OutputData.of(
                                    CellData.OutputData.OutputType.of(o.output_type()),
                                    o.data())
                            )
                            .toList()
            );
        }
    }

    public static record MarkdownCell(String id, String cell_type, Map<String, Object> metadata, String source) implements Cell {

        @Override
        public CellData toCellData() {
            return CellData.of(
                    CellData.Type.of(cell_type()),
                    source(),
                    Optional.ofNullable(id()).map(UUID::fromString).orElseGet(UUID::randomUUID)
            );
        }
    }

    public static record Output(String output_type, Map<String, String> data, Map<String, Object> metadata) {
    }

    @JsonbTypeDeserializer(CellDeserializer.class)
    public static sealed interface Cell permits CodeCell, MarkdownCell {

        Map<String, Object> metadata();

        String cell_type();

        String id();

        String source();

        CellData toCellData();
    }

    public static class CellDeserializer implements JsonbDeserializer<Cell> {

        @Override
        public Cell deserialize(JsonParser parser, DeserializationContext ctx, Type rtType) {
            var o = parser.getObject();
            var type = o.getString("cell_type");
            var id = o.getString("id");
            var source = multilineOrArrayToString(o.get("source"));
            return switch (type) {
                case "code" -> {
                    var outputs = o.getJsonArray("outputs").stream()
                            .map(JsonValue::asJsonObject)
                            .map(ov
                                    -> new Output(
                                    ov.getString("output_type"),
                                    ov.getJsonObject("data")
                                            .entrySet()
                                            .stream()
                                            .collect(Collectors.toMap(e -> e.getKey(), e -> e.getValue().toString())),
                                    Map.of()))
                            .toList();
                    yield new CodeCell(id, type, Map.of(), source, outputs, 0);
                }
                case "markdown" ->
                    new MarkdownCell(id, type, Map.of(), source);
                default ->
                    throw new IllegalStateException("Unsupported cell type found: " + type);
            };
        }

        private String multilineOrArrayToString(JsonValue jsonValue) {
            return switch (jsonValue) {
                case JsonString js ->
                    js.getString();
                case JsonArray ja ->
                    ja.stream().map(JsonValue::toString).collect(Collectors.joining("\n"));
                default ->
                    throw new IllegalStateException("Unsupported conversion to String from " + jsonValue);
            };
        }
    }

    @Override
    public List<CellData> toCellDataList() {
        return cells().stream()
                .map(Cell::toCellData)
                .toList();
    }
}

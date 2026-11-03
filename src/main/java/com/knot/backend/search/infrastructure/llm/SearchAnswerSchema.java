package com.knot.backend.search.infrastructure.llm;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SearchAnswerSchema {

    private SearchAnswerSchema() {}

    public static Map<String, Object> responseFormat() {
        Map<String, Object> properties = Map.of(
                "status",
                Map.of(
                        "type",
                        "string",
                        "enum",
                        List.of(
                                "ANSWERED",
                                "PARTIAL",
                                "CONFLICT",
                                "NOT_FOUND",
                                "NEEDS_CLARIFICATION"
                        )
                ),
                "decisionState",
                Map.of(
                        "type",
                        "string",
                        "enum",
                        List.of(
                                "DECIDED",
                                "DEFERRED",
                                "UNDECIDED",
                                "PROPOSED",
                                "ACTION",
                                "UNKNOWN",
                                "NOT_APPLICABLE"
                        )
                ),
                "answer",
                Map.of(
                        "type",
                        "string"
                ),
                "evidenceIds",
                Map.of(
                        "type",
                        "array",
                        "maxItems",
                        3,
                        "items",
                        Map.of(
                                "type",
                                "string",
                                "pattern",
                                "^E[0-9]+$"
                        )
                ),
                "clarificationQuestion",
                Map.of(
                        "anyOf",
                        List.of(
                                Map.of(
                                        "type",
                                        "string"
                                ),
                                Map.of(
                                        "type",
                                        "null"
                                )
                        )
                )
        );
        Map<String, Object> schema = Map.of(
                "anyOf",
                List.of(
                        answerSchema(properties),
                        notFoundSchema(properties),
                        clarificationSchema(properties)
                )
        );
        return Map.of(
                "type",
                "json_schema",
                "json_schema",
                Map.of(
                        "name",
                        "search_answer",
                        "strict",
                        true,
                        "schema",
                        schema
                )
        );
    }

    private static Map<String, Object> answerSchema(Map<String, Object> commonProperties) {
        Map<String, Object> properties = new LinkedHashMap<>(commonProperties);
        properties.put(
                "status",
                Map.of("type", "string", "enum", List.of("ANSWERED", "PARTIAL", "CONFLICT"))
        );
        properties.put("clarificationQuestion", Map.of("type", "null"));
        return objectSchema(properties);
    }

    private static Map<String, Object> notFoundSchema(Map<String, Object> commonProperties) {
        Map<String, Object> properties = new LinkedHashMap<>(commonProperties);
        properties.put("status", Map.of("type", "string", "enum", List.of("NOT_FOUND")));
        properties.put("decisionState", Map.of("type", "string", "enum", List.of("UNKNOWN")));
        properties.put("evidenceIds", Map.of("type", "array", "maxItems", 0, "items", Map.of("type", "string")));
        properties.put("clarificationQuestion", Map.of("type", "null"));
        return objectSchema(properties);
    }

    private static Map<String, Object> clarificationSchema(Map<String, Object> commonProperties) {
        Map<String, Object> properties = new LinkedHashMap<>(commonProperties);
        properties.put("status", Map.of("type", "string", "enum", List.of("NEEDS_CLARIFICATION")));
        properties.put("clarificationQuestion", Map.of("type", "string", "minLength", 1));
        return objectSchema(properties);
    }

    private static Map<String, Object> objectSchema(Map<String, Object> properties) {
        return Map.of(
                "type",
                "object",
                "properties",
                properties,
                "required",
                List.of("status", "decisionState", "answer", "evidenceIds", "clarificationQuestion"),
                "additionalProperties",
                false
        );
    }
}

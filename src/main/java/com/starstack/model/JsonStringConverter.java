package com.starstack.model;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.LinkedHashMap;
import java.util.Map;

/** Generic JSON-string converter for Map<String,?> columns. */
@Converter
public class JsonStringConverter implements AttributeConverter<Map<String, ?>, String> {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<LinkedHashMap<String, Object>> TYPE = new TypeReference<>() {};

    @Override
    public String convertToDatabaseColumn(Map<String, ?> attribute) {
        try {
            return MAPPER.writeValueAsString(attribute == null ? new LinkedHashMap<>() : attribute);
        } catch (Exception e) {
            return "{}";
        }
    }

    @Override
    public Map<String, Object> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isEmpty()) return new LinkedHashMap<>();
        try {
            return MAPPER.readValue(dbData, TYPE);
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }
}
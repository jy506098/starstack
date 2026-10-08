package com.starstack.model;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.LinkedHashMap;
import java.util.Map;

/** Typed converter for {@code Map<String, TaskRecord>} columns (fixed_tasks_json). */
@Converter
public class JsonTaskRecordConverter implements AttributeConverter<Map<String, TaskRecord>, String> {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<LinkedHashMap<String, TaskRecord>> TYPE = new TypeReference<>() {};

    @Override
    public String convertToDatabaseColumn(Map<String, TaskRecord> attribute) {
        try {
            return MAPPER.writeValueAsString(attribute == null ? new LinkedHashMap<>() : attribute);
        } catch (Exception e) {
            return "{}";
        }
    }

    @Override
    public Map<String, TaskRecord> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isEmpty()) return new LinkedHashMap<>();
        try {
            return MAPPER.readValue(dbData, TYPE);
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }
}

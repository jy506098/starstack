package com.starstack.model;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.ArrayList;
import java.util.List;

/** Generic JSON-string converter for List<?> columns. */
@Converter
public class JsonListStringConverter implements AttributeConverter<List<?>, String> {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<ArrayList<Object>> TYPE = new TypeReference<>() {};

    @Override
    public String convertToDatabaseColumn(List<?> attribute) {
        try {
            return MAPPER.writeValueAsString(attribute == null ? new ArrayList<>() : attribute);
        } catch (Exception e) {
            return "[]";
        }
    }

    @Override
    public List<Object> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isEmpty()) return new ArrayList<>();
        try {
            return MAPPER.readValue(dbData, TYPE);
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }
}
package com.starstack.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** JPA converter for the {@code mouse_config_json} column. */
@Converter
public class MouseEffectConfigConverter implements AttributeConverter<MouseEffectConfig, String> {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(MouseEffectConfig attribute) {
        try {
            return MAPPER.writeValueAsString(attribute == null ? new MouseEffectConfig() : attribute);
        } catch (Exception e) {
            return "{\"enabled\":true,\"color_mode\":\"rainbow\",\"shape\":\"circle\"}";
        }
    }

    @Override
    public MouseEffectConfig convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isEmpty()) return new MouseEffectConfig();
        try {
            return MAPPER.readValue(dbData, MouseEffectConfig.class);
        } catch (Exception e) {
            return new MouseEffectConfig();
        }
    }
}
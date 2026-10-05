package com.starstack.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Mouse-trail effect configuration persisted on User. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MouseEffectConfig {
    private boolean enabled = true;
    private String colorMode = "rainbow";
    private String shape = "circle";
}
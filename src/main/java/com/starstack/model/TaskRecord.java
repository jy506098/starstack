package com.starstack.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Per-task fixed-task completion record. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TaskRecord {
    private boolean completed;
    private String lastDate = "";
}
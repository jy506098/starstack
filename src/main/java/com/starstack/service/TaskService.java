package com.starstack.service;

import com.starstack.model.TaskRecord;
import com.starstack.model.User;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Task helpers — port of {@code app/service.py::mark_task_done}, etc.
 *
 * Daily tasks: once per day per task id.
 * Fixed tasks: persistent flag with lastDate marker.
 */
@Service
public class TaskService {
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /** Returns true if the daily task fired (wasn't already completed today). */
    public boolean fireDaily(User user, String taskId, int reward) {
        if (user == null) return false;
        String today = LocalDateTime.now().format(DAY);
        String last = user.getDailyTasks().get(taskId);
        if (today.equals(last)) return false;
        if (user.getDailyTasks() == null) user.setDailyTasks(new LinkedHashMap<>());
        user.getDailyTasks().put(taskId, today);
        user.setPoints(user.getPoints() + reward);
        return true;
    }

    /** Fixed task: marks completed=true, sets lastDate, awards reward. */
    public boolean fireFixed(User user, String taskId, int reward) {
        if (user == null) return false;
        Map<String, TaskRecord> tasks = user.getFixedTasks();
        if (tasks == null) {
            tasks = new LinkedHashMap<>();
            user.setFixedTasks(tasks);
        }
        TaskRecord rec = tasks.get(taskId);
        if (rec == null) rec = new TaskRecord();
        if (rec.isCompleted()) return false;
        rec.setCompleted(true);
        rec.setLastDate(LocalDateTime.now().format(DAY));
        tasks.put(taskId, rec);
        user.setPoints(user.getPoints() + reward);
        return true;
    }

    public void resetFixedTasks(User user) {
        if (user == null || user.getFixedTasks() == null) return;
        user.getFixedTasks().values().forEach(r -> r.setCompleted(false));
    }
}
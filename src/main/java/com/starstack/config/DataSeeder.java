package com.starstack.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.starstack.model.MouseEffectConfig;
import com.starstack.model.RechargeRecord;
import com.starstack.model.TaskRecord;
import com.starstack.model.User;
import com.starstack.model.VipPurchase;
import com.starstack.repository.MessageRepository;
import com.starstack.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One-shot migration: if DB is empty and legacy users.json / messages.json
 * exist, import them. Then rename the JSON files to *.json.migrated so we
 * don't re-import on subsequent boots.
 *
 * Trigger order — runs AFTER Hibernate has validated the schema (because
 * @SpringBootApplication's auto-config initializes the EntityManagerFactory
 * before ApplicationRunner).
 */
@Component
public class DataSeeder implements ApplicationRunner {
    private static final Logger LOG = LoggerFactory.getLogger(DataSeeder.class);

    private final UserRepository users;
    private final MessageRepository messages;
    private final String legacyDir;
    private final ObjectMapper mapper = new ObjectMapper();

    public DataSeeder(UserRepository users,
                      MessageRepository messages,
                      @Value("${starstack.legacy-data-dir:./app/client}") String legacyDir) {
        this.users = users;
        this.messages = messages;
        this.legacyDir = legacyDir;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (users.count() > 0) {
            LOG.info("DataSeeder: users table already populated ({} rows) — skipping legacy import", users.count());
            return;
        }
        File usersJson = Path.of(legacyDir, "users.json").toFile();
        File messagesJson = Path.of(legacyDir, "messages.json").toFile();
        if (!usersJson.exists()) {
            LOG.info("DataSeeder: no legacy users.json found at {} — fresh DB", usersJson.getAbsolutePath());
            return;
        }
        try {
            importUsers(usersJson);
            if (messagesJson.exists()) importMessages(messagesJson);
            renameToMigrated(usersJson);
            if (messagesJson.exists()) renameToMigrated(messagesJson);
            LOG.info("DataSeeder: migrated {} users, {} messages",
                users.count(), messages.count());
        } catch (Exception e) {
            LOG.error("DataSeeder: failed to import legacy data", e);
        }
    }

    private void importUsers(File f) throws Exception {
        JsonNode root = mapper.readTree(f);
        Iterator<Map.Entry<String, JsonNode>> fields = root.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> e = fields.next();
            String username = e.getKey();
            JsonNode u = e.getValue();
            User user = new User();
            user.setUsername(username);
            user.setPasswordHash(textOrEmpty(u, "password_hash"));
            user.setPasswordFormat("werkzeug-scrypt");
            user.setPhone(textOrEmpty(u, "phone"));
            user.setPoints(u.path("points").asLong(100));
            user.setTotalSpent(u.path("total_spent").asLong(0));
            user.setInventory(jsonToMapStringInt(u.path("inventory")));
            user.setUnlockedContent(jsonToStringList(u.path("unlocked_content")));
            user.setEffects(jsonToMapStringBool(u.path("effects")));
            user.setAdmin(u.path("is_admin").asBoolean(false));
            user.setAdminDailyPointsDate(textOrEmpty(u, "admin_daily_points_date"));
            user.setAdminDailyPointsCount(u.path("admin_daily_points_count").asInt(0));
            user.setMouseConfig(jsonToMouseConfig(u.path("mouse_effect_config")));
            user.setAvatar(textOrEmpty(u, "avatar"));
            user.setVipTier(textOrEmpty(u, "vip_tier"));
            user.setVipExpiresAt(textOrEmpty(u, "vip_expires_at"));
            user.setLastVipBonusDate(textOrEmpty(u, "last_vip_bonus_date"));
            user.setVipPendingOrderId(textOrEmpty(u, "vip_pending_order_id"));
            user.setVipPurchaseHistory(jsonToVipPurchases(u.path("vip_purchase_history")));
            user.setRechargeHistory(jsonToRecharges(u.path("recharge_history")));
            user.setFixedTasks(jsonToFixedTasks(u.path("tasks")));
            user.setDailyTasks(jsonToDailyTasks(u.path("daily_tasks_completed")));
            user.setDailyVisitCount(u.path("daily_visit_count").asInt(0));
            user.setLastVisitDate(textOrEmpty(u, "last_visit_date"));
            user.setLastPostDate(textOrEmpty(u, "last_post_date"));
            user.setCreatedAt(LocalDateTime.now().toString());
            user.setUpdatedAt(LocalDateTime.now().toString());
            users.save(user);
        }
    }

    private void importMessages(File f) throws Exception {
        JsonNode arr = mapper.readTree(f);
        if (!arr.isArray()) return;
        for (JsonNode n : arr) {
            messages.save(new com.starstack.model.Message(
                n.path("name").asText(""),
                n.path("msg").asText("")
            ));
        }
    }

    private void renameToMigrated(File f) {
        try {
            Path target = f.toPath().resolveSibling(f.getName() + ".migrated");
            Files.move(f.toPath(), target);
            LOG.info("DataSeeder: renamed {} → {}", f.getName(), target.getFileName());
        } catch (Exception e) {
            LOG.warn("DataSeeder: could not rename {}", f.getAbsolutePath(), e);
        }
    }

    private static String textOrEmpty(JsonNode n, String f) {
        JsonNode v = n.path(f);
        return v.isMissingNode() || v.isNull() ? "" : v.asText();
    }

    private static Map<String, Integer> jsonToMapStringInt(JsonNode n) {
        Map<String, Integer> m = new LinkedHashMap<>();
        if (n.isObject()) n.fields().forEachRemaining(e -> m.put(e.getKey(), e.getValue().asInt(0)));
        return m;
    }

    private static Map<String, Boolean> jsonToMapStringBool(JsonNode n) {
        Map<String, Boolean> m = new LinkedHashMap<>();
        if (n.isObject()) n.fields().forEachRemaining(e -> m.put(e.getKey(), e.getValue().asBoolean(false)));
        return m;
    }

    private static List<String> jsonToStringList(JsonNode n) {
        List<String> l = new ArrayList<>();
        if (n.isArray()) n.forEach(x -> l.add(x.asText()));
        return l;
    }

    private static MouseEffectConfig jsonToMouseConfig(JsonNode n) {
        MouseEffectConfig c = new MouseEffectConfig();
        if (n.isObject()) {
            c.setEnabled(n.path("enabled").asBoolean(true));
            c.setColorMode(n.path("color_mode").asText("rainbow"));
            c.setShape(n.path("shape").asText("circle"));
        }
        return c;
    }

    private static Map<String, TaskRecord> jsonToFixedTasks(JsonNode n) {
        Map<String, TaskRecord> m = new LinkedHashMap<>();
        if (n.isObject()) {
            n.fields().forEachRemaining(e -> {
                JsonNode v = e.getValue();
                TaskRecord tr = new TaskRecord();
                tr.setCompleted(v.path("completed").asBoolean(false));
                tr.setLastDate(v.path("last_date").asText(""));
                m.put(e.getKey(), tr);
            });
        }
        return m;
    }

    private static Map<String, String> jsonToDailyTasks(JsonNode n) {
        Map<String, String> m = new LinkedHashMap<>();
        if (n.isObject()) n.fields().forEachRemaining(e -> m.put(e.getKey(), e.getValue().asText("")));
        return m;
    }

    private static List<VipPurchase> jsonToVipPurchases(JsonNode n) {
        List<VipPurchase> l = new ArrayList<>();
        if (n.isArray()) {
            n.forEach(x -> l.add(new VipPurchase(
                x.path("pkg_key").asText(""),
                x.path("tier").asText(""),
                x.path("duration_days").asInt(0),
                x.path("price_cny").asInt(0),
                x.path("points").asInt(0),
                x.path("paid_at").asText(""),
                x.path("order_id").asText("")
            )));
        }
        return l;
    }

    private static List<RechargeRecord> jsonToRecharges(JsonNode n) {
        List<RechargeRecord> l = new ArrayList<>();
        if (n.isArray()) {
            n.forEach(x -> l.add(new RechargeRecord(
                x.path("pkg_key").asText(""),
                x.path("price_cny").asInt(0),
                x.path("points").asInt(0),
                x.path("bonus").asInt(0),
                x.path("paid_at").asText(""),
                x.path("order_id").asText("")
            )));
        }
        return l;
    }
}
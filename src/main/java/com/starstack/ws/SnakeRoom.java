package com.starstack.ws;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * One game room. 1:1 port of {@code app/snake_server.py::Room}.
 */
public class SnakeRoom {

    public static final int ROOM_W = 40;
    public static final int ROOM_H = 30;
    public static final int FOOD_COUNT = 30;

    private static final List<String> COLOR_POOL = List.of(
        "#ff4d6d", "#ff8c42", "#ffd166", "#06d6a0", "#118ab2",
        "#8338ec", "#3a86ff", "#ef476f", "#f72585", "#4cc9f0"
    );

    private static final java.util.Random RNG = new java.util.Random();

    public final String id;
    public final Map<String, Player> players = new ConcurrentHashMap<>(); // sessionId -> Player
    public final Set<int[]> foods = ConcurrentHashMap.newKeySet();        // [x,y]
    public volatile long createdAt;

    public SnakeRoom(String id) {
        this.id = id;
        this.createdAt = System.currentTimeMillis();
    }

    public static class Player {
        public String name;
        public List<int[]> snake = new ArrayList<>();
        public int[] dir = new int[]{1, 0};
        public int[] nextDir = new int[]{1, 0};
        public String color;
        public int score;
        public volatile boolean alive = true;
        public volatile long lastInputTs;
    }

    public Player addPlayer(String sessionId, String name) {
        Player p = new Player();
        p.color = COLOR_POOL.get(players.size() % COLOR_POOL.size());
        int x = 5 + RNG.nextInt(ROOM_W - 10);
        int y = 5 + RNG.nextInt(ROOM_H - 10);
        p.snake.add(new int[]{x, y});
        p.snake.add(new int[]{x - 1, y});
        p.snake.add(new int[]{x - 2, y});
        p.name = (name == null || name.isEmpty()) ? "玩家" + (players.size() + 1) : name;
        p.lastInputTs = System.currentTimeMillis();
        players.put(sessionId, p);
        refillFood();
        return p;
    }

    public void removePlayer(String sessionId) {
        players.remove(sessionId);
    }

    public void refillFood() {
        while (foods.size() < FOOD_COUNT) {
            int x = RNG.nextInt(ROOM_W);
            int y = RNG.nextInt(ROOM_H);
            int[] f = {x, y};
            boolean dup = false;
            for (int[] e : foods) {
                if (e[0] == x && e[1] == y) { dup = true; break; }
            }
            if (!dup) foods.add(f);
        }
    }

    public void tick() {
        for (Map.Entry<String, Player> entry : players.entrySet()) {
            Player p = entry.getValue();
            if (!p.alive) continue;
            int[] nd = p.nextDir, cd = p.dir;
            if ((nd[0] + cd[0] != 0) || (nd[1] + cd[1] != 0)) {
                p.dir = new int[]{nd[0], nd[1]};
            }
            int[] head = p.snake.get(0);
            int[] newHead = {head[0] + p.dir[0], head[1] + p.dir[1]};
            if (newHead[0] < 0 || newHead[0] >= ROOM_W || newHead[1] < 0 || newHead[1] >= ROOM_H) {
                p.alive = false;
                continue;
            }
            // Self-collision (excluding tail tip)
            for (int i = 0; i < p.snake.size() - 1; i++) {
                int[] s = p.snake.get(i);
                if (s[0] == newHead[0] && s[1] == newHead[1]) { p.alive = false; break; }
            }
            if (!p.alive) continue;
            // Other-player collision
            for (Map.Entry<String, Player> e2 : players.entrySet()) {
                if (e2.getKey().equals(entry.getKey())) continue;
                for (int[] s : e2.getValue().snake) {
                    if (s[0] == newHead[0] && s[1] == newHead[1]) { p.alive = false; break; }
                }
                if (!p.alive) break;
            }
            if (!p.alive) continue;
            p.snake.add(0, newHead);
            // Eat?
            boolean ate = false;
            for (int[] f : foods) {
                if (f[0] == newHead[0] && f[1] == newHead[1]) { foods.remove(f); ate = true; p.score += 10; break; }
            }
            if (!ate) p.snake.remove(p.snake.size() - 1);
        }
    }

    public Map<String, Object> snapshot() {
        Map<String, Object> m = new HashMap<>();
        m.put("type", "state");
        m.put("room", id);
        m.put("w", ROOM_W);
        m.put("h", ROOM_H);
        List<int[]> foodsList = new ArrayList<>();
        for (int[] f : foods) foodsList.add(new int[]{f[0], f[1]});
        m.put("foods", foodsList);
        List<Map<String, Object>> ps = new ArrayList<>();
        for (Player p : players.values()) {
            Map<String, Object> pp = new HashMap<>();
            pp.put("name", p.name);
            pp.put("color", p.color);
            pp.put("score", p.score);
            pp.put("alive", p.alive);
            List<int[]> sn = new ArrayList<>();
            for (int[] s : p.snake) sn.add(new int[]{s[0], s[1]});
            pp.put("snake", sn);
            ps.add(pp);
        }
        m.put("players", ps);
        m.put("ts", System.currentTimeMillis());
        return m;
    }

    /** Reset all players (after gameover) — preserved snake length 3, score 0, alive true. */
    public void resetPlayers() {
        for (Player p : players.values()) {
            p.alive = true;
            int x = 5 + RNG.nextInt(ROOM_W - 10);
            int y = 5 + RNG.nextInt(ROOM_H - 10);
            p.snake.clear();
            p.snake.add(new int[]{x, y});
            p.snake.add(new int[]{x - 1, y});
            p.snake.add(new int[]{x - 2, y});
            p.dir = new int[]{1, 0};
            p.nextDir = new int[]{1, 0};
            p.score = 0;
        }
        refillFood();
    }

    /** True if every player is dead (or empty room). */
    public boolean allDead() {
        if (players.isEmpty()) return false;
        for (Player p : players.values()) {
            if (p.alive) return false;
        }
        return true;
    }

    public Player winner() {
        Player best = null;
        for (Player p : players.values()) {
            if (best == null || p.score > best.score) best = p;
        }
        return best;
    }
}
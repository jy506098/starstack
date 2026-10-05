package com.starstack.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Concrete handler that keeps a sessionId → WebSocketSession map so
 * SnakeGameLoop can broadcast to all room members.
 */
@Component
public class SnakeWebSocketHandlerImpl extends TextWebSocketHandler {

    private static final Logger LOG = LoggerFactory.getLogger(SnakeWebSocketHandlerImpl.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, SnakeRoom> rooms = new ConcurrentHashMap<>();
    private final Map<String, String> sessionRoom = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.put(session.getId(), session);
        LOG.info("[snake_ws] connected: {}", session.getId());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String sid = session.getId();
        sessions.remove(sid);
        String rid = sessionRoom.remove(sid);
        if (rid != null) {
            SnakeRoom r = rooms.get(rid);
            if (r != null) r.removePlayer(sid);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String sid = session.getId();
        Map<String, Object> msg;
        try {
            msg = MAPPER.readValue(message.getPayload(), Map.class);
        } catch (Exception e) {
            send(session, Map.of("type", "error", "msg", "invalid json"));
            return;
        }
        String type = (String) msg.get("type");
        if (type == null) return;

        if ("join".equals(type)) {
            String roomId = String.valueOf(msg.getOrDefault("room", "default"));
            if (roomId.length() > 32) roomId = roomId.substring(0, 32);
            if (roomId.isEmpty()) roomId = "default";
            String name = String.valueOf(msg.getOrDefault("name", ""));
            if (name.length() > 16) name = name.substring(0, 16);
            SnakeRoom room = rooms.computeIfAbsent(roomId, SnakeRoom::new);
            room.addPlayer(sid, name);
            sessionRoom.put(sid, roomId);
            send(session, Map.of("type", "welcome", "room", roomId, "name", name));
            return;
        }

        String rid = sessionRoom.get(sid);
        if (rid == null) {
            send(session, Map.of("type", "error", "msg", "must join first"));
            return;
        }
        SnakeRoom room = rooms.get(rid);
        SnakeRoom.Player p = (room == null) ? null : room.players.get(sid);
        if (room == null || p == null) return;

        if ("input".equals(type)) {
            Object d = msg.get("dir");
            if (d instanceof List<?> list && list.size() == 2) {
                int dx = ((Number) list.get(0)).intValue();
                int dy = ((Number) list.get(1)).intValue();
                if ((dx == 1 && dy == 0) || (dx == -1 && dy == 0) || (dx == 0 && dy == 1) || (dx == 0 && dy == -1)) {
                    if ((dx + p.dir[0] != 0) || (dy + p.dir[1] != 0)) {
                        p.nextDir = new int[]{dx, dy};
                        p.lastInputTs = System.currentTimeMillis();
                    }
                }
            }
        } else if ("leave".equals(type)) {
            room.removePlayer(sid);
            sessionRoom.remove(sid);
        }
    }

    private void send(WebSocketSession session, Map<String, Object> payload) {
        try {
            session.sendMessage(new TextMessage(MAPPER.writeValueAsString(payload)));
        } catch (Exception ignored) {}
    }

    public Map<String, SnakeRoom> rooms() { return rooms; }
    public Map<String, String> sessionRoom() { return sessionRoom; }

    public void garbageCollect() {
        long now = System.currentTimeMillis();
        rooms.entrySet().removeIf(e -> {
            SnakeRoom r = e.getValue();
            return r.players.isEmpty() && now - r.createdAt > 30_000;
        });
    }

    public Map<String, Object> gameoverPayload(String winner) {
        return Map.of("type", "gameover", "winner", winner);
    }
}
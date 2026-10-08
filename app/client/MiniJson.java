package app.client;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 极简 JSON 解析/序列化工具（占位用）。
 *
 * Java 移植说明：
 * - Python 用 json.dump / json.load
 * - Java 实际生产用 Jackson ObjectMapper（在 src/main/java/com/starstack/config/）
 * - 此处提供一个递归下降的最小实现，保证 App.java 可以 stringify/parse 用户数据
 * - 不依赖任何外部库，方便 app/client/*.java 自包含
 */
public final class MiniJson {

    private MiniJson() {}

    public static Object parse(String s) {
        return new Parser(s).parse();
    }

    @SuppressWarnings("unchecked")
    public static String stringify(Object o) {
        StringBuilder sb = new StringBuilder();
        write(sb, o);
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private static void write(StringBuilder sb, Object o) {
        if (o == null) { sb.append("null"); return; }
        if (o instanceof Boolean || o instanceof Number) { sb.append(o); return; }
        if (o instanceof String) { writeString(sb, (String) o); return; }
        if (o instanceof Map) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<String, Object> e : ((Map<String, Object>) o).entrySet()) {
                if (!first) sb.append(',');
                first = false;
                writeString(sb, e.getKey());
                sb.append(':');
                write(sb, e.getValue());
            }
            sb.append('}'); return;
        }
        if (o instanceof List) {
            sb.append('[');
            boolean first = true;
            for (Object x : (List<Object>) o) {
                if (!first) sb.append(',');
                first = false;
                write(sb, x);
            }
            sb.append(']'); return;
        }
        writeString(sb, o.toString());
    }

    private static void writeString(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
            }
        }
        sb.append('"');
    }

    private static final class Parser {
        private final String s;
        private int i = 0;
        Parser(String s) { this.s = s; }

        Object parse() { skip(); return readValue(); }

        private Object readValue() {
            skip();
            char c = s.charAt(i);
            if (c == '{') return readObject();
            if (c == '[') return readArray();
            if (c == '"') return readString();
            if (c == 't' || c == 'f') return readBool();
            if (c == 'n') { i += 4; return null; }
            return readNumber();
        }

        private Map<String, Object> readObject() {
            i++; skip();
            Map<String, Object> m = new LinkedHashMap<>();
            if (s.charAt(i) == '}') { i++; return m; }
            while (true) {
                skip();
                String k = readString();
                skip();
                i++;  // :
                Object v = readValue();
                m.put(k, v);
                skip();
                if (s.charAt(i) == ',') { i++; continue; }
                if (s.charAt(i) == '}') { i++; return m; }
            }
        }

        private List<Object> readArray() {
            i++; skip();
            List<Object> l = new ArrayList<>();
            if (s.charAt(i) == ']') { i++; return l; }
            while (true) {
                l.add(readValue());
                skip();
                if (s.charAt(i) == ',') { i++; continue; }
                if (s.charAt(i) == ']') { i++; return l; }
            }
        }

        private String readString() {
            i++;  // opening "
            StringBuilder sb = new StringBuilder();
            while (s.charAt(i) != '"') {
                char c = s.charAt(i);
                if (c == '\\') {
                    i++;
                    char esc = s.charAt(i++);
                    switch (esc) {
                        case '"': sb.append('"'); break;
                        case '\\': sb.append('\\'); break;
                        case 'n': sb.append('\n'); break;
                        case 'r': sb.append('\r'); break;
                        case 't': sb.append('\t'); break;
                        case 'u': {
                            int cp = Integer.parseInt(s.substring(i, i + 4), 16);
                            sb.append((char) cp);
                            i += 4; break;
                        }
                        default: sb.append(esc);
                    }
                } else {
                    sb.append(c); i++;
                }
            }
            i++;  // closing "
            return sb.toString();
        }

        private Boolean readBool() {
            if (s.charAt(i) == 't') { i += 4; return Boolean.TRUE; }
            i += 5; return Boolean.FALSE;
        }

        private Number readNumber() {
            int start = i;
            if (s.charAt(i) == '-') i++;
            while (i < s.length() && "-0123456789.eE+".indexOf(s.charAt(i)) >= 0) i++;
            String num = s.substring(start, i);
            if (num.contains(".") || num.contains("e") || num.contains("E")) return Double.parseDouble(num);
            try { return Long.parseLong(num); } catch (Exception e) { return Double.parseDouble(num); }
        }

        private void skip() {
            while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
        }
    }
}

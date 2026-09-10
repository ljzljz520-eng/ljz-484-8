import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 极简 JSON 解析 / 序列化工具（无第三方依赖）。
 * 解析结果：Map<String,Object> / List<Object> / String / Long / Double / Boolean / null
 */
public final class Json {
    private Json() {}

    public static Object parse(String text) {
        Parser p = new Parser(text);
        p.skipWs();
        Object v = p.parseValue();
        p.skipWs();
        if (!p.atEnd()) throw new IllegalArgumentException("JSON 末尾存在多余内容");
        return v;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseObject(String text) {
        Object v = parse(text);
        if (!(v instanceof Map)) throw new IllegalArgumentException("JSON 顶层不是对象");
        return (Map<String, Object>) v;
    }

    @SuppressWarnings("unchecked")
    public static List<Object> parseArray(String text) {
        Object v = parse(text);
        if (!(v instanceof List)) throw new IllegalArgumentException("JSON 顶层不是数组");
        return (List<Object>) v;
    }

    /** 紧凑输出 */
    public static String stringify(Object value) {
        StringBuilder sb = new StringBuilder();
        write(value, sb, -1, 0);
        return sb.toString();
    }

    /** 美化输出（用于数据文件，方便人工查看与版本管理） */
    public static String pretty(Object value) {
        StringBuilder sb = new StringBuilder();
        write(value, sb, 2, 0);
        return sb.toString();
    }

    private static void write(Object v, StringBuilder sb, int indent, int level) {
        if (v == null) { sb.append("null"); return; }
        if (v instanceof String) { writeString((String) v, sb); return; }
        if (v instanceof Boolean || v instanceof Number) { sb.append(v.toString()); return; }
        if (v instanceof Map) {
            Map<?, ?> m = (Map<?, ?>) v;
            if (m.isEmpty()) { sb.append("{}"); return; }
            sb.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if (!first) sb.append(',');
                newline(sb, indent, level + 1);
                writeString(String.valueOf(e.getKey()), sb);
                sb.append(indent > 0 ? ": " : ":");
                write(e.getValue(), sb, indent, level + 1);
                first = false;
            }
            newline(sb, indent, level);
            sb.append('}');
            return;
        }
        if (v instanceof Iterable) {
            Iterable<?> list = (Iterable<?>) v;
            if (!list.iterator().hasNext()) { sb.append("[]"); return; }
            sb.append('[');
            boolean first = true;
            for (Object item : list) {
                if (!first) sb.append(',');
                newline(sb, indent, level + 1);
                write(item, sb, indent, level + 1);
                first = false;
            }
            newline(sb, indent, level);
            sb.append(']');
            return;
        }
        throw new IllegalArgumentException("不支持序列化的类型: " + v.getClass());
    }

    private static void newline(StringBuilder sb, int indent, int level) {
        if (indent <= 0) return;
        sb.append('\n');
        for (int i = 0; i < indent * level; i++) sb.append(' ');
    }

    private static void writeString(String s, StringBuilder sb) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':  sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                default:
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
            }
        }
        sb.append('"');
    }

    private static final class Parser {
        private final String s;
        private int i;

        Parser(String s) { this.s = s; }

        boolean atEnd() { return i >= s.length(); }

        void skipWs() {
            while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
        }

        Object parseValue() {
            skipWs();
            if (atEnd()) throw err("内容意外结束");
            char c = s.charAt(i);
            switch (c) {
                case '{': return parseObject();
                case '[': return parseArray();
                case '"': return parseString();
                case 't': expect("true");  return Boolean.TRUE;
                case 'f': expect("false"); return Boolean.FALSE;
                case 'n': expect("null");  return null;
                default:  return parseNumber();
            }
        }

        private Map<String, Object> parseObject() {
            Map<String, Object> m = new LinkedHashMap<>();
            i++; // '{'
            skipWs();
            if (peek('}')) { i++; return m; }
            while (true) {
                skipWs();
                String key = parseString();
                skipWs();
                if (atEnd() || s.charAt(i) != ':') throw err("缺少 ':'");
                i++;
                m.put(key, parseValue());
                skipWs();
                if (atEnd()) throw err("对象未闭合");
                char c = s.charAt(i);
                if (c == ',') { i++; continue; }
                if (c == '}') { i++; break; }
                throw err("缺少 ',' 或 '}'");
            }
            return m;
        }

        private List<Object> parseArray() {
            List<Object> list = new ArrayList<>();
            i++; // '['
            skipWs();
            if (peek(']')) { i++; return list; }
            while (true) {
                list.add(parseValue());
                skipWs();
                if (atEnd()) throw err("数组未闭合");
                char c = s.charAt(i);
                if (c == ',') { i++; continue; }
                if (c == ']') { i++; break; }
                throw err("缺少 ',' 或 ']'");
            }
            return list;
        }

        private String parseString() {
            if (atEnd() || s.charAt(i) != '"') throw err("此处应为字符串");
            i++;
            StringBuilder sb = new StringBuilder();
            while (true) {
                if (atEnd()) throw err("字符串未闭合");
                char c = s.charAt(i++);
                if (c == '"') break;
                if (c == '\\') {
                    if (atEnd()) throw err("转义字符不完整");
                    char e = s.charAt(i++);
                    switch (e) {
                        case '"':  sb.append('"');  break;
                        case '\\': sb.append('\\'); break;
                        case '/':  sb.append('/');  break;
                        case 'b':  sb.append('\b'); break;
                        case 'f':  sb.append('\f'); break;
                        case 'n':  sb.append('\n'); break;
                        case 'r':  sb.append('\r'); break;
                        case 't':  sb.append('\t'); break;
                        case 'u':
                            if (i + 4 > s.length()) throw err("unicode 转义不完整");
                            sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                            i += 4;
                            break;
                        default:
                            throw err("非法转义字符: \\" + e);
                    }
                } else {
                    sb.append(c);
                }
            }
            return sb.toString();
        }

        private Object parseNumber() {
            int start = i;
            if (peek('-')) i++;
            while (!atEnd() && "0123456789+-.eE".indexOf(s.charAt(i)) >= 0) i++;
            String num = s.substring(start, i);
            if (num.isEmpty()) throw err("无法解析的值");
            try {
                if (num.contains(".") || num.contains("e") || num.contains("E")) {
                    return Double.parseDouble(num);
                }
                return Long.parseLong(num);
            } catch (NumberFormatException ex) {
                throw err("数字格式错误: " + num);
            }
        }

        private boolean peek(char c) { return i < s.length() && s.charAt(i) == c; }

        private void expect(String word) {
            if (!s.startsWith(word, i)) throw err("此处应为 " + word);
            i += word.length();
        }

        private IllegalArgumentException err(String msg) {
            return new IllegalArgumentException("JSON 解析失败（" + msg + "），位置 " + i);
        }
    }
}

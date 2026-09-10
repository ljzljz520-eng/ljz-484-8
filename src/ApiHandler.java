import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * /api 路由。
 * 私密控制：浏览端用 GET /api/stories?publicOnly=true，只返回公开故事；
 * 编辑端（publicOnly=false 及所有写操作）在设置了 EDIT_PASSWORD 时需要口令。
 */
public class ApiHandler implements HttpHandler {
    private final Store store;
    private final String editPassword; // 空串表示未启用口令

    public ApiHandler(Store store, String editPassword) {
        this.store = store;
        this.editPassword = editPassword == null ? "" : editPassword;
    }

    @Override
    public void handle(HttpExchange ex) throws IOException {
        try {
            route(ex);
        } catch (IllegalArgumentException e) {
            sendJson(ex, 400, error(e.getMessage()));
        } catch (Exception e) {
            e.printStackTrace();
            sendJson(ex, 500, error("服务器内部错误: " + e.getMessage()));
        } finally {
            ex.close();
        }
    }

    private void route(HttpExchange ex) throws IOException {
        String method = ex.getRequestMethod();
        String path = ex.getRequestURI().getPath();
        String[] seg = path.split("/"); // ["", "api", "people", ...]
        Map<String, String> query = parseQuery(ex.getRequestURI().getRawQuery());

        // 服务配置（前端据此判断是否需要口令）
        if (seg.length == 3 && seg[2].equals("config") && method.equals("GET")) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("editPasswordRequired", !editPassword.isEmpty());
            sendJson(ex, 200, m);
            return;
        }

        // ---------------- 人物 ----------------
        if (seg.length >= 3 && seg[2].equals("people")) {
            if (seg.length == 3 && method.equals("GET")) {
                List<Map<String, Object>> out = new ArrayList<>();
                for (Person p : store.listPeople()) out.add(p.toMap());
                sendJson(ex, 200, out);
                return;
            }
            if (!checkEditAuth(ex)) return;
            if (seg.length == 3 && method.equals("POST")) {
                Person p = Person.fromMap(readBody(ex));
                if (p.name.trim().isEmpty()) throw new IllegalArgumentException("姓名不能为空");
                sendJson(ex, 200, store.savePerson(p).toMap());
                return;
            }
            if (seg.length == 4 && method.equals("PUT")) {
                Person p = Person.fromMap(readBody(ex));
                p.id = seg[3];
                if (p.name.trim().isEmpty()) throw new IllegalArgumentException("姓名不能为空");
                sendJson(ex, 200, store.savePerson(p).toMap());
                return;
            }
            if (seg.length == 4 && method.equals("DELETE")) {
                sendJson(ex, 200, ok(store.deletePerson(seg[3])));
                return;
            }
        }

        // ---------------- 故事 ----------------
        if (seg.length >= 3 && seg[2].equals("stories")) {
            if (seg.length == 3 && method.equals("GET")) {
                boolean publicOnly = "true".equalsIgnoreCase(query.getOrDefault("publicOnly", "false"));
                if (!publicOnly && !checkEditAuth(ex)) return; // 私密内容需要编辑权限
                String personId = emptyToNull(query.get("personId"));
                Integer decade = null;
                if (query.containsKey("decade")) {
                    try { decade = Integer.valueOf(query.get("decade")); }
                    catch (NumberFormatException ignore) { /* 忽略非法参数 */ }
                }
                List<Map<String, Object>> out = new ArrayList<>();
                for (Story s : store.listStories(publicOnly, personId, decade)) out.add(s.toMap());
                sendJson(ex, 200, out);
                return;
            }
            if (!checkEditAuth(ex)) return;
            if (seg.length == 3 && method.equals("POST")) {
                Story s = Story.fromMap(readBody(ex));
                if (s.title.trim().isEmpty()) throw new IllegalArgumentException("标题不能为空");
                sendJson(ex, 200, store.saveStory(s).toMap());
                return;
            }
            if (seg.length == 4 && method.equals("PUT")) {
                Story s = Story.fromMap(readBody(ex));
                s.id = seg[3];
                if (s.title.trim().isEmpty()) throw new IllegalArgumentException("标题不能为空");
                sendJson(ex, 200, store.saveStory(s).toMap());
                return;
            }
            if (seg.length == 4 && method.equals("DELETE")) {
                sendJson(ex, 200, ok(store.deleteStory(seg[3])));
                return;
            }
        }

        sendJson(ex, 404, error("接口不存在: " + method + " " + path));
    }

    // ---------------- 工具 ----------------

    /** 校验编辑口令；未通过时已写出 401 响应并返回 false。 */
    private boolean checkEditAuth(HttpExchange ex) throws IOException {
        if (editPassword.isEmpty()) return true;
        String token = ex.getRequestHeaders().getFirst("X-Edit-Password");
        if (editPassword.equals(token)) return true;
        sendJson(ex, 401, error("需要编辑口令"));
        return false;
    }

    private Map<String, Object> readBody(HttpExchange ex) throws IOException {
        String text = new String(readAll(ex.getRequestBody()), StandardCharsets.UTF_8).trim();
        if (text.isEmpty()) return new LinkedHashMap<>();
        return Json.parseObject(text);
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int n;
        while ((n = in.read(chunk)) != -1) buf.write(chunk, 0, n);
        return buf.toByteArray();
    }

    static Map<String, String> parseQuery(String raw) {
        Map<String, String> m = new HashMap<>();
        if (raw == null || raw.isEmpty()) return m;
        for (String pair : raw.split("&")) {
            int idx = pair.indexOf('=');
            try {
                if (idx < 0) m.put(urlDecode(pair), "");
                else m.put(urlDecode(pair.substring(0, idx)), urlDecode(pair.substring(idx + 1)));
            } catch (Exception ignore) { /* 跳过非法参数 */ }
        }
        return m;
    }

    private static String urlDecode(String s) throws Exception {
        return URLDecoder.decode(s, "UTF-8");
    }

    private static Map<String, Object> ok(boolean ok) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", ok);
        return m;
    }

    private static Map<String, Object> error(String msg) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("error", msg);
        return m;
    }

    private static String emptyToNull(String s) {
        return s == null || s.isEmpty() ? null : s;
    }

    static void sendJson(HttpExchange ex, int status, Object body) throws IOException {
        byte[] bytes = Json.stringify(body).getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }
}

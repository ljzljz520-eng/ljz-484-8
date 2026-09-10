import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

/** 静态文件服务：把 web/ 目录下的前端文件提供给浏览器。 */
public class StaticHandler implements HttpHandler {
    private final File root;
    private static final Map<String, String> TYPES = new HashMap<>();
    static {
        TYPES.put("html", "text/html; charset=utf-8");
        TYPES.put("css",  "text/css; charset=utf-8");
        TYPES.put("js",   "application/javascript; charset=utf-8");
        TYPES.put("json", "application/json; charset=utf-8");
        TYPES.put("png",  "image/png");
        TYPES.put("jpg",  "image/jpeg");
        TYPES.put("svg",  "image/svg+xml");
        TYPES.put("ico",  "image/x-icon");
    }

    public StaticHandler(File root) {
        this.root = root;
    }

    @Override
    public void handle(HttpExchange ex) throws IOException {
        try {
            String path = ex.getRequestURI().getPath();
            if (path.equals("/")) path = "/index.html";
            File f = new File(root, path).getCanonicalFile();
            // 防止 ../ 路径穿越
            if (!f.getPath().startsWith(root.getCanonicalFile().getPath()) || !f.isFile()) {
                byte[] msg = "404 Not Found".getBytes(StandardCharsets.UTF_8);
                ex.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
                ex.sendResponseHeaders(404, msg.length);
                try (OutputStream os = ex.getResponseBody()) { os.write(msg); }
                return;
            }
            String name = f.getName();
            String ext = name.contains(".") ? name.substring(name.lastIndexOf('.') + 1) : "";
            ex.getResponseHeaders().set("Content-Type", TYPES.getOrDefault(ext, "application/octet-stream"));
            byte[] bytes = Files.readAllBytes(f.toPath());
            ex.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
        } finally {
            ex.close();
        }
    }
}

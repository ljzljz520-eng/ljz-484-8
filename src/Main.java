import com.sun.net.httpserver.HttpServer;

import java.io.File;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

/**
 * 家谱故事整理系统 —— 入口。
 * 启动：java -cp out Main
 * 环境变量：PORT（默认 8080）、DATA_DIR（默认 data）、WEB_DIR（默认 web）、EDIT_PASSWORD（默认空）
 */
public class Main {
    public static void main(String[] args) throws Exception {
        int port = Integer.parseInt(env("PORT", "8080"));
        File dataDir = new File(env("DATA_DIR", "data"));
        File webDir = new File(env("WEB_DIR", "web"));
        String editPassword = env("EDIT_PASSWORD", "");

        Store store = new Store(dataDir);
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/api", new ApiHandler(store, editPassword));
        server.createContext("/", new StaticHandler(webDir));
        server.setExecutor(Executors.newFixedThreadPool(8));
        server.start();

        System.out.println("===============================================");
        System.out.println("  家谱故事整理系统已启动");
        System.out.println("  浏览地址: http://localhost:" + port);
        System.out.println("  数据目录: " + dataDir.getAbsolutePath());
        System.out.println("  编辑口令: " + (editPassword.isEmpty() ? "未设置（编辑端无保护）" : "已启用"));
        System.out.println("  按 Ctrl+C 停止");
        System.out.println("===============================================");
    }

    private static String env(String key, String def) {
        String v = System.getenv(key);
        return v == null || v.isEmpty() ? def : v;
    }
}

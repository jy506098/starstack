package app.client;

import java.awt.Desktop;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

/**
 * 桌面壳启动器：用子进程跑 Spring Boot 后端，父进程打开系统默认浏览器加载 URL。
 *
 * Java 移植说明：
 * - 原 Python 用 pywebview 嵌入窗口 + 子进程跑 Flask
 * - Java 用 Desktop.browse() 打开系统浏览器（用户已接受失去嵌入窗口）
 * - 此文件保留作为桌面启动逻辑的 Java 翻译存档，不会被 Maven 编译
 * - 实际启动请用项目根目录的 start.bat / start.sh
 */
public final class Desktop {

    private static final int DEFAULT_PORT = 5000;
    private static final Path HERE = Paths.get(System.getProperty("user.dir")).toAbsolutePath();

    private Desktop() {}

    /** 检查 pywebview 等价物（Java 用 Desktop API 替代）。 */
    public static boolean requireWebview() {
        if (!Desktop.isDesktopSupported()) {
            System.err.println("[Desktop] 当前 JVM 不支持 Desktop API，无法启动桌面壳。");
            System.err.println("        请直接用浏览器访问 http://localhost:" + DEFAULT_PORT + "/");
            return false;
        }
        return true;
    }

    /** 端口是否空闲。 */
    public static boolean portIsFree(int port) {
        try (ServerSocket s = new ServerSocket()) {
            s.setReuseAddress(true);
            s.bind(new java.net.InetSocketAddress("127.0.0.1", port));
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /** 从 DEFAULT_PORT 起递增找一个空位（最多 20 个）。 */
    public static int pickPort() {
        for (int offset = 0; offset < 20; offset++) {
            int p = DEFAULT_PORT + offset;
            if (portIsFree(p)) return p;
        }
        throw new RuntimeException("[Desktop] 在 " + DEFAULT_PORT + "-" + (DEFAULT_PORT + 19) + " 范围内找不到空闲端口");
    }

    /** 轮询直到端口接受 TCP 连接或超时。 */
    public static boolean waitForServer(int port, double timeoutSec) throws InterruptedException {
        long deadline = System.nanoTime() + (long)(timeoutSec * 1_000_000_000L);
        while (System.nanoTime() < deadline) {
            try (Socket s = new Socket()) {
                s.connect(new java.net.InetSocketAddress("127.0.0.1", port), 500);
                return true;
            } catch (IOException e) {
                TimeUnit.MILLISECONDS.sleep(200);
            }
        }
        return false;
    }

    /** 启动后端子进程（Spring Boot fat JAR）。 */
    public static Process startBackend(int port) throws IOException {
        System.out.println("[Desktop] 启动后端: java -jar target/starstack.jar (port=" + port + ")");
        Path jar = HERE.resolve("target").resolve("starstack.jar");
        ProcessBuilder pb = new ProcessBuilder(
            "java", "-jar", jar.toString(),
            "--server.port=" + port
        );
        pb.directory(HERE.toFile());
        pb.redirectErrorStream(true);
        return pb.start();
    }

    /** 优雅结束，超时再强杀。 */
    public static void stopBackend(Process proc) throws InterruptedException {
        if (proc == null || !proc.isAlive()) return;
        System.out.println("[Desktop] 停止后端进程...");
        proc.destroy();
        if (!proc.waitFor(5, TimeUnit.SECONDS)) {
            System.err.println("[Desktop] 5s 内未退出，强杀");
            proc.destroyForcibly();
            proc.waitFor(3, TimeUnit.SECONDS);
            System.out.println("[Desktop] 后端已被强杀");
        } else {
            System.out.println("[Desktop] 后端已退出");
        }
    }

    public static void main(String[] args) {
        if (!requireWebview()) System.exit(1);

        int port = pickPort();
        Process proc = null;
        try {
            proc = startBackend(port);

            if (!waitForServer(port, 15)) {
                System.err.println("[Desktop] 后端在 15s 内未开始监听端口 " + port);
                stopBackend(proc);
                System.exit(1);
            }
            // 等路由全部注册
            TimeUnit.MILLISECONDS.sleep(500);

            String url = "http://127.0.0.1:" + port + "/";
            System.out.println("[Desktop] 打开浏览器: " + url);
            Desktop.getDesktop().browse(URI.create(url));

            // 阻塞直到用户关闭浏览器（JVM 由 SIGTERM / 关闭浏览器窗口时外部杀）
            int exitCode = proc.waitFor();
            System.out.println("[Desktop] 后端退出码: " + exitCode);
        } catch (Exception e) {
            System.err.println("[Desktop] 启动失败: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        } finally {
            try { if (proc != null) stopBackend(proc); } catch (InterruptedException ignored) {}
        }
    }
}

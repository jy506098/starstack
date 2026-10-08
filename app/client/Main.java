package app.client;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * PyInstaller 入口：根据命令行参数决定启动 GUI 还是后端。
 *
 * Java 移植说明：
 * - 原 Python 实现通过 PyInstaller 把 Flask + gevent + pywebview 打包成单个 .exe
 * - Java 等价物是 Spring Boot fat JAR（已存在于 src/main/java/com/starstack/），
 *   不再需要这个分派入口
 * - 此文件保留作为 Python 启动逻辑的 Java 翻译存档，不会被 Maven 编译
 */
public final class Main {

    private Main() {}

    /** 冻结后 _MEIPASS；开发模式 __file__ 所在目录。 */
    public static Path baseDir() {
        // Java 没有 _MEIPASS；用当前工作目录替代
        return Paths.get(System.getProperty("user.dir")).toAbsolutePath();
    }

    /**
     * 后端模式：复用 App 模块的 Spring 实例和 SnakeServer，启动嵌入式 Tomcat。
     *
     * 不通过 Class.forName 跑 App 的 main，因为：
     * - Spring Boot fat JAR 中类已经加载，重复加载会触发 Bean 重复定义
     * - 直接调 App.main 即可，Spring Boot 内部处理端口绑定 + WebSocket 注册
     */
    public static void runBackend() throws Exception {
        Class<?> appCls = Class.forName("com.starstack.StarStackApplication");
        Method main = appCls.getMethod("main", String[].class);
        main.invoke(null, (Object) new String[]{});
    }

    /** GUI 模式：直接复用 Desktop.main()。 */
    public static void runGui() throws Exception {
        Desktop.main(new String[]{});
    }

    public static void main(String[] args) {
        boolean backend = false;
        for (String a : args) {
            if ("--backend".equals(a)) {
                backend = true;
                break;
            }
        }
        try {
            if (backend) runBackend();
            else runGui();
        } catch (Exception e) {
            System.err.println("[Main] 启动失败: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}

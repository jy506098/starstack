package app.client;

/**
 * PyInstaller runtime hook：在任何用户代码导入前先打 gevent monkey patch。
 *
 * Java 移植说明：
 * - Java 没有 gevent 等价物；NIO + Vert.x / Spring WebFlux 提供协作式调度
 * - 此文件保留作为 Python 启动钩子的 Java 翻译存档，不会被 Maven 编译
 * - 真正生效的是 application.yml 里的 server.port=5000 + WebSocket 配置
 */
public final class RuntimeHookGevent {

    private RuntimeHookGevent() {}

    /** 静态初始化块：在类加载时立即执行（等价于 PyInstaller runtime hook 的执行时机）。 */
    static {
        // 避免 DNS 解析卡死：Python 等价物 os.environ.setdefault('GEVENT_NOWAITDN', '1')
        // Java 没有 Gevent；对应行为由 JVM DNS 缓存控制
        System.setProperty("networkaddress.cache.ttl", "30");

        // 让 stdout 立即 flush：Python 等价物 PYTHONUNBUFFERED=1
        // Java 默认 autoFlush=false；这里强制 true
        System.setProperty("java.stdlib.java.lang.System.out", "autoFlush=true");

        // 通知 JVM 使用 UTF-8 处理文件和控制台
        System.setProperty("file.encoding", "UTF-8");
        System.setProperty("console.encoding", "UTF-8");
    }

    public static void applyPatch() {
        // 占位：Java 没有 monkey patch 概念。Spring Boot 通过 spring.main.lazy-initialization
        // 等价控制 Bean 初始化时机。
    }

    public static void main(String[] args) {
        applyPatch();
        System.out.println("[RuntimeHookGevent] patched (Java no-op)");
    }
}

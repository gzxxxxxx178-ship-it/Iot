package com.ruoyi.iotsystem;

import org.springframework.boot.SpringApplication;

/**
 * MATLAB SIL 连通性测试专用启动器。
 * 仅从测试类路径运行，显式注入 H2、MQTT 和 Redis 替身，避免连接外部基础设施。
 */
public final class SilConnectivityApplication {

    private SilConnectivityApplication() {
    }

    // 启动带测试基础设施替身的完整 HTTP 应用。
    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(
                IoTSystemApplication.class,
                TestInfrastructureConfiguration.class);
        application.run(args);
    }
}

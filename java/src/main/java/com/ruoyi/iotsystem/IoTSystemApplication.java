package com.ruoyi.iotsystem;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class IoTSystemApplication {

    // 启动智慧农业IoT后端应用
    public static void main(String[] args) {
        SpringApplication.run(IoTSystemApplication.class, args);
        System.out.println("IoT System Application started. MQTT client should be listening for messages...");
    }

}

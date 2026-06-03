package com.ruoyi.iotsystem;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class IoTSystemApplication {

    public static void main(String[] args) {
        SpringApplication.run(IoTSystemApplication.class, args);
        System.out.println("IoT System Application started. MQTT client should be listening for messages...");
    }

}
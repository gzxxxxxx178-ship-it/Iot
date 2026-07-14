package com.ruoyi.iotsystem.controller;

import com.ruoyi.iotsystem.service.MqttMessageService;
import com.ruoyi.iotsystem.service.DeviceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class DeviceControlControllerTest {

    @Mock private MqttMessageService mqttMessageService;
    @Mock private DeviceService deviceService;
    private MockMvc mockMvc;

    // 创建仅包含设备控制器的MockMvc环境
    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                new DeviceControlController(mqttMessageService, deviceService)).build();
    }

    // 验证合法指令由服务端拼接设备级Topic并发布
    @Test
    void controlDevice_合法请求_应发布指令() throws Exception {
        mockMvc.perform(post("/api/device/control")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"deviceId\":\"device001\",\"command\":\"start\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(deviceService).assertControllable("device001");
        verify(mqttMessageService).publishControl("device001", "start");
    }

    // 验证含Topic分隔符的设备ID在控制器入口被拒绝
    @Test
    void controlDevice_设备Id无效_应返回四百() throws Exception {
        mockMvc.perform(post("/api/device/control")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"deviceId\":\"device/other\",\"command\":\"start\"}"))
                .andExpect(status().isBadRequest());
    }
}

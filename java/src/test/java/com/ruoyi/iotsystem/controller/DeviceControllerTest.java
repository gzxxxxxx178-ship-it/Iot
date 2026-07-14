package com.ruoyi.iotsystem.controller;

import com.ruoyi.iotsystem.service.DeviceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Collections;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class DeviceControllerTest {

    @Mock private DeviceService deviceService;
    private MockMvc mockMvc;

    // 创建设备管理控制器测试环境
    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new DeviceController(deviceService)).build();
    }

    // 验证设备列表接口透传归档筛选条件
    @Test
    void listDevices_包含归档_应返回成功() throws Exception {
        when(deviceService.listDevices(true)).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/devices").param("includeArchived", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(deviceService).listDevices(true);
    }

    // 验证设备注册请求执行参数校验
    @Test
    void createDevice_设备Id非法_应返回四百() throws Exception {
        mockMvc.perform(post("/api/devices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"deviceId\":\"device/2\",\"deviceName\":\"二号节点\"}"))
                .andExpect(status().isBadRequest());
    }

    // 验证删除接口执行归档而非物理删除
    @Test
    void archiveDevice_合法设备_应调用归档服务() throws Exception {
        mockMvc.perform(delete("/api/devices/device001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(deviceService).archiveDevice("device001");
    }
}

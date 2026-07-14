package com.ruoyi.iotsystem.controller;

import com.ruoyi.iotsystem.dto.DashboardStatsResponse;
import com.ruoyi.iotsystem.dto.DeviceStatusResponse;
import com.ruoyi.iotsystem.service.DashboardService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Arrays;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class DashboardControllerTest {

    @Mock
    private DashboardService dashboardService;

    private MockMvc mockMvc;

    // 创建仅包含仪表盘控制器的MockMvc环境
    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new DashboardController(dashboardService)).build();
    }

    // 验证统计接口返回统一响应结构和统计字段
    @Test
    void getStats_应返回统一响应结构() throws Exception {
        when(dashboardService.getStats()).thenReturn(new DashboardStatsResponse(3, 2, 25.0, 50.0));

        mockMvc.perform(get("/api/dashboard/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.deviceCount").value(3))
                .andExpect(jsonPath("$.data.onlineCount").value(2))
                .andExpect(jsonPath("$.data.avgTemp").value(25.0))
                .andExpect(jsonPath("$.data.avgHum").value(50.0));
    }

    // 验证设备状态接口返回在线和离线分布
    @Test
    void getDeviceStatusDistribution_应返回统一响应结构() throws Exception {
        when(dashboardService.getDeviceStatusDistribution()).thenReturn(Arrays.asList(
                new DeviceStatusResponse("在线", 2),
                new DeviceStatusResponse("离线", 1)));

        mockMvc.perform(get("/api/dashboard/device-status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].name").value("在线"))
                .andExpect(jsonPath("$.data[0].value").value(2))
                .andExpect(jsonPath("$.data[1].name").value("离线"))
                .andExpect(jsonPath("$.data[1].value").value(1));
    }
}

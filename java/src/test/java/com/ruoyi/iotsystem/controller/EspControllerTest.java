package com.ruoyi.iotsystem.controller;

import com.ruoyi.iotsystem.repository.EspRepository;
import com.ruoyi.iotsystem.service.EspService;
import com.ruoyi.iotsystem.service.SensorHistoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class EspControllerTest {

    @Mock private EspService espService;
    @Mock private EspRepository espRepository;
    @Mock private SensorHistoryService sensorHistoryService;
    private MockMvc mockMvc;

    // 创建仅包含传感器控制器的MockMvc环境
    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                new EspController(espService, espRepository, sensorHistoryService)).build();
    }

    // 验证CSV导出接口返回附件并透传设备和时间条件
    @Test
    void exportHistoryCsv_合法条件_应返回Csv附件() throws Exception {
        LocalDateTime start = LocalDateTime.parse("2026-07-13T00:00:00");
        LocalDateTime end = LocalDateTime.parse("2026-07-14T00:00:00");
        byte[] csv = "\uFEFF设备ID\r\ndevice001\r\n".getBytes(StandardCharsets.UTF_8);
        when(sensorHistoryService.exportCsv("device001", start, end)).thenReturn(csv);

        mockMvc.perform(get("/esp/history/export")
                        .param("deviceId", "device001")
                        .param("start", "2026-07-13T00:00:00")
                        .param("end", "2026-07-14T00:00:00"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"sensor-history.csv\""))
                .andExpect(content().bytes(csv));

        verify(sensorHistoryService).exportCsv(eq("device001"), eq(start), eq(end));
    }
}

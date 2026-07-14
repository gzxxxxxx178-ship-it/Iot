package com.ruoyi.iotsystem.service;

import com.ruoyi.iotsystem.entity.EspEntity;
import com.ruoyi.iotsystem.repository.EspRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EspServiceTest {

    @Mock private EspRepository espRepository;
    @Mock private AlarmService alarmService;
    @Mock private AutomationService automationService;

    @InjectMocks
    private EspService espService;

    // ==================== 保存数据测试 ====================

    @Test
    void saveData_应保存并返回带ID的实体() {
        EspEntity input = new EspEntity("device001", 25.5, 65.0, 1700000000000L);
        EspEntity saved = new EspEntity("device001", 25.5, 65.0, 1700000000000L);
        saved.setId(1L);

        when(espRepository.save(input)).thenReturn(saved);

        EspEntity result = espService.saveData(input);

        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals("device001", result.getDeviceId());
        assertEquals(25.5, result.getTemperature());
        assertEquals(65.0, result.getHumidity());
        assertTrue(result.getQualityValid());
        verify(espRepository).save(input);
        verify(alarmService).evaluate(saved);
        verify(automationService).evaluate(saved);
    }

    // 验证越界读数会保留原始值并阻止报警与自动化执行
    @Test
    void saveData_温度越界_应标记异常并跳过业务规则() {
        EspEntity input = new EspEntity("device001", 120.0, 65.0, 1700000000000L);
        when(espRepository.save(input)).thenReturn(input);

        EspEntity result = espService.saveData(input);

        assertFalse(result.getQualityValid());
        assertTrue(result.getQualityIssues().contains("TEMPERATURE_OUT_OF_RANGE"));
        verify(alarmService, never()).evaluate(any());
        verify(automationService, never()).evaluate(any());
    }

    // ==================== 数据处理和响应生成测试 ====================

    @Test
    void processDataAndGenerateResponse_成功保存_应返回成功JSON() {
        EspEntity input = new EspEntity("device001", 25.5, 65.0, 1700000000000L);
        EspEntity saved = new EspEntity("device001", 25.5, 65.0, 1700000000000L);
        saved.setId(42L);

        when(espRepository.save(any())).thenReturn(saved);

        String response = espService.processDataAndGenerateResponse(input);

        assertTrue(response.contains("\"status\":\"success\""));
        assertTrue(response.contains("\"id\":42"));
        verify(espRepository).save(input);
    }

    @Test
    void processDataAndGenerateResponse_保存失败_应返回错误JSON() {
        EspEntity input = new EspEntity("device001", 25.5, 65.0, 1700000000000L);

        when(espRepository.save(any())).thenThrow(new RuntimeException("DB connection lost"));

        String response = espService.processDataAndGenerateResponse(input);

        assertTrue(response.contains("\"status\":\"error\""));
        assertTrue(response.contains("DB connection lost"));
    }

    // ==================== 查询测试 ====================

    @Test
    void getRecentData_应返回最近20条数据() {
        EspEntity e1 = new EspEntity("device001", 25.5, 65.0, 1700000000000L);
        EspEntity e2 = new EspEntity("device001", 26.0, 64.0, 1700000001000L);
        when(espRepository.findTop20ByOrderByServerReceivedTimeDesc())
                .thenReturn(Arrays.asList(e2, e1));

        List<EspEntity> result = espService.getRecentData();

        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals(26.0, result.get(0).getTemperature());
        verify(espRepository).findTop20ByOrderByServerReceivedTimeDesc();
    }
}

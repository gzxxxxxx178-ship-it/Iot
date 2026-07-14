package com.ruoyi.iotsystem.service;

import com.ruoyi.iotsystem.entity.EspEntity;
import com.ruoyi.iotsystem.repository.EspRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SensorHistoryServiceTest {

    @Mock private EspRepository espRepository;
    private SensorHistoryService service;

    // 创建历史查询服务测试对象
    @BeforeEach
    void setUp() {
        service = new SensorHistoryService(espRepository);
    }

    // 验证设备与时间范围组合查询选择组合索引对应方法
    @Test
    void queryPage_设备和时间范围_应使用组合查询() {
        LocalDateTime start = LocalDateTime.now().minusDays(1);
        LocalDateTime end = LocalDateTime.now();
        when(espRepository.findByDeviceIdAndServerReceivedTimeBetweenOrderByServerReceivedTimeDesc(
                eq("device001"), eq(start), eq(end), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(Collections.emptyList()));

        service.queryPage("device001", start, end, 0, 20);

        verify(espRepository).findByDeviceIdAndServerReceivedTimeBetweenOrderByServerReceivedTimeDesc(
                eq("device001"), eq(start), eq(end), any(PageRequest.class));
    }

    // 验证分页数量超过一百时被拒绝
    @Test
    void queryPage_分页过大_应拒绝() {
        assertThrows(RuntimeException.class,
                () -> service.queryPage(null, null, null, 0, 101));
    }

    // 验证超过366天的查询范围被拒绝
    @Test
    void queryPage_时间跨度过大_应拒绝() {
        LocalDateTime end = LocalDateTime.now();
        assertThrows(RuntimeException.class,
                () -> service.queryPage(null, end.minusDays(367), end, 0, 20));
    }

    // 验证CSV包含UTF8标记、质量状态并防止公式注入
    @Test
    void exportCsv_合法数据_应生成安全CSV() {
        LocalDateTime start = LocalDateTime.now().minusHours(1);
        LocalDateTime end = LocalDateTime.now();
        EspEntity reading = new EspEntity("=device001", 25.0, 60.0, 1L);
        reading.setQualityValid(false);
        reading.setQualityIssues("TEMPERATURE_OUT_OF_RANGE");
        when(espRepository.findByServerReceivedTimeBetweenOrderByServerReceivedTimeDesc(
                eq(start), eq(end), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(Collections.singletonList(reading)));

        String csv = new String(service.exportCsv(null, start, end), StandardCharsets.UTF_8);

        assertTrue(csv.startsWith("\uFEFF设备ID"));
        assertTrue(csv.contains("\"'=device001\""));
        assertTrue(csv.contains("\"异常\""));
        assertTrue(csv.contains("TEMPERATURE_OUT_OF_RANGE"));
    }
}

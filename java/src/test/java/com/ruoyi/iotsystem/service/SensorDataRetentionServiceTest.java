package com.ruoyi.iotsystem.service;

import com.ruoyi.iotsystem.repository.EspRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
class SensorDataRetentionServiceTest {

    @Mock private EspRepository espRepository;

    // 验证启用保留策略时删除365天以前的数据
    @Test
    void cleanupExpiredData_已启用_应按截止时间删除() {
        SensorDataRetentionService service = new SensorDataRetentionService(
                espRepository, 365, true);
        when(espRepository.deleteExpiredData(any(LocalDateTime.class))).thenReturn(12);

        long deleted = service.cleanupExpiredData();

        ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(espRepository).deleteExpiredData(captor.capture());
        assertEquals(12L, deleted);
        assertTrue(captor.getValue().isBefore(LocalDateTime.now().minusDays(364)));
    }

    // 验证停用保留策略时不删除历史数据
    @Test
    void cleanupExpiredData_已停用_应跳过删除() {
        SensorDataRetentionService service = new SensorDataRetentionService(
                espRepository, 365, false);

        assertEquals(0L, service.cleanupExpiredData());
        verify(espRepository, never()).deleteExpiredData(any());
    }
}

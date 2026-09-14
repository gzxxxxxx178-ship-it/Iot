package com.ruoyi.iotsystem.service;

import com.ruoyi.iotsystem.entity.DeviceCommandEntity;
import com.ruoyi.iotsystem.repository.DeviceCommandRepository;
import org.junit.jupiter.api.Test;
import java.util.Collections;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DeviceCommandServiceTest {
    // 验证超时扫描会关闭未获设备确认的下发命令
    @Test
    void expireUnacknowledgedCommands_超时命令_标记为超时() {
        DeviceCommandRepository repository = mock(DeviceCommandRepository.class);
        DeviceCommandEntity command = mock(DeviceCommandEntity.class);
        when(repository.findByStatusAndCreatedAtBefore(eq("DISPATCHED"), any()))
                .thenReturn(Collections.singletonList(command));
        DeviceCommandService service = new DeviceCommandService(repository, mock(MqttMessageService.class), 60);

        service.expireUnacknowledgedCommands();

        verify(command).setStatus("TIMED_OUT");
        verify(repository).save(command);
    }
}

package com.ruoyi.iotsystem.service;

import com.ruoyi.iotsystem.entity.DeviceCommandEntity;
import com.ruoyi.iotsystem.repository.DeviceCommandRepository;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DeviceCommandServiceTest {
    // 验证超时扫描会关闭未获设备确认的下发命令
    @Test
    void expireUnacknowledgedCommands_超时命令_标记为超时() {
        DeviceCommandRepository repository = mock(DeviceCommandRepository.class);
        DeviceCommandService service = new DeviceCommandService(repository, mock(ApplicationEventPublisher.class), 60);

        service.expireUnacknowledgedCommands();

        verify(repository).expireUnfinished(any(), contains("60秒"));
        verify(repository, never()).save(any(DeviceCommandEntity.class));
    }
}

package com.ruoyi.iotsystem.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.iotsystem.config.MqttProperties;
import com.ruoyi.iotsystem.config.SensorWebSocketHandler;
import com.ruoyi.iotsystem.entity.EspEntity;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MqttMessageServiceTest {

    @Mock private MqttClient mqttClient;
    @Mock private MqttConnectOptions connectOptions;
    @Mock private EspService espService;
    @Mock private SensorWebSocketHandler sensorWebSocketHandler;

    private MqttMessageService service;

    // 构造不连接真实Broker的MQTT服务测试对象
    @BeforeEach
    void setUp() {
        MqttProperties properties = new MqttProperties();
        properties.setTopicRoot("agri");
        properties.setInitialReconnectDelaySeconds(1);
        properties.setMaxReconnectDelaySeconds(60);
        service = new MqttMessageService(
                mqttClient,
                connectOptions,
                properties,
                espService,
                sensorWebSocketHandler,
                new ObjectMapper().findAndRegisterModules());
    }

    // 关闭测试对象内部的重连线程
    @AfterEach
    void tearDown() {
        service.shutdown();
    }

    // 验证Topic和载荷身份一致的JSON数据会持久化并广播
    @Test
    void messageArrived_身份一致_应保存并广播() throws Exception {
        EspEntity saved = new EspEntity("device001", 25.0, 60.0, 1L);
        saved.setId(7L);
        when(espService.saveData(any(EspEntity.class))).thenReturn(saved);

        service.messageArrived(
                "agri/device001/data",
                new MqttMessage(("{\"deviceId\":\"device001\",\"temperature\":25.0,"
                        + "\"humidity\":60.0,\"timestamp\":1}").getBytes()));

        ArgumentCaptor<EspEntity> captor = ArgumentCaptor.forClass(EspEntity.class);
        verify(espService).saveData(captor.capture());
        assertEquals("device001", captor.getValue().getDeviceId());
        verify(sensorWebSocketHandler).broadcast(any(String.class));
    }

    // 验证载荷冒用其他设备身份时不会进入数据库
    @Test
    void messageArrived_身份不一致_应拒绝数据() throws Exception {
        service.messageArrived(
                "agri/device001/data",
                new MqttMessage("{\"deviceId\":\"device002\",\"temperature\":25}".getBytes()));

        verify(espService, never()).saveData(any(EspEntity.class));
        verify(sensorWebSocketHandler, never()).broadcast(any(String.class));
    }

    // 验证非设备级数据Topic不会被解析
    @Test
    void messageArrived_Topic结构无效_应拒绝数据() throws Exception {
        service.messageArrived(
                "agri/data",
                new MqttMessage("{\"deviceId\":\"device001\",\"temperature\":25}".getBytes()));

        verify(espService, never()).saveData(any(EspEntity.class));
    }

    // 验证控制指令只发布到目标设备Topic且采用QoS1
    @Test
    void publishControl_合法指令_应发布到设备Topic() throws Exception {
        when(mqttClient.isConnected()).thenReturn(true);

        service.publishControl("device001", "start");

        ArgumentCaptor<MqttMessage> captor = ArgumentCaptor.forClass(MqttMessage.class);
        verify(mqttClient).publish(eq("agri/device001/control"), captor.capture());
        assertEquals("start", new String(captor.getValue().getPayload()));
        assertEquals(1, captor.getValue().getQos());
        assertFalse(captor.getValue().isRetained());
    }

    // 验证连接恢复后重新订阅并发布后端在线状态
    @Test
    void connectComplete_应恢复订阅并发布在线状态() throws Exception {
        when(mqttClient.isConnected()).thenReturn(true);

        service.connectComplete(true, "ssl://localhost:8883");

        verify(mqttClient).subscribe("agri/+/data", 1);
        verify(mqttClient).publish(eq("agri/backend/status"), any(MqttMessage.class));
    }
}

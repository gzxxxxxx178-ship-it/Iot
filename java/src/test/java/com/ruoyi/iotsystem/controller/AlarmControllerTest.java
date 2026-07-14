package com.ruoyi.iotsystem.controller;

import com.ruoyi.iotsystem.dto.AlarmRuleRequest;
import com.ruoyi.iotsystem.entity.AlarmRecordEntity;
import com.ruoyi.iotsystem.entity.AlarmRuleEntity;
import com.ruoyi.iotsystem.service.AlarmService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AlarmControllerTest {

    @Mock
    private AlarmService alarmService;

    private MockMvc mockMvc;

    // 创建仅包含报警控制器的MockMvc环境
    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AlarmController(alarmService)).build();
    }

    // 验证报警规则列表接口返回统一响应结构
    @Test
    void getRules_应返回规则列表() throws Exception {
        AlarmRuleEntity rule = createRule(1L);
        when(alarmService.getRules()).thenReturn(Collections.singletonList(rule));

        mockMvc.perform(get("/api/alarm/rules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.data[0].metric").value("temperature"));
    }

    // 验证创建报警规则接口返回已创建规则
    @Test
    void createRule_合法请求_应返回规则() throws Exception {
        when(alarmService.createRule(any(AlarmRuleRequest.class))).thenReturn(createRule(1L));

        mockMvc.perform(post("/api/alarm/rules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRuleJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1));
    }

    // 验证更新报警规则接口调用指定规则主键
    @Test
    void updateRule_合法请求_应返回更新规则() throws Exception {
        when(alarmService.updateRule(eq(1L), any(AlarmRuleRequest.class))).thenReturn(createRule(1L));

        mockMvc.perform(put("/api/alarm/rules/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRuleJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1));
    }

    // 验证删除报警规则接口返回成功
    @Test
    void deleteRule_应删除指定规则() throws Exception {
        mockMvc.perform(delete("/api/alarm/rules/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(alarmService).deleteRule(1L);
    }

    // 验证报警记录接口返回触发快照
    @Test
    void getRecords_应返回记录列表() throws Exception {
        AlarmRecordEntity record = new AlarmRecordEntity(
                1L, "device001", "temperature", "gt", 30.0, 31.0, "温度报警");
        when(alarmService.getRecords(null, null)).thenReturn(Collections.singletonList(record));

        mockMvc.perform(get("/api/alarm/records"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].deviceId").value("device001"))
                .andExpect(jsonPath("$.data[0].actualValue").value(31.0));
    }

    // 创建控制器测试使用的已持久化报警规则
    private AlarmRuleEntity createRule(Long id) {
        AlarmRuleEntity rule = new AlarmRuleEntity(
                "temperature", "gt", 30.0, true, "*", 300);
        rule.setId(id);
        return rule;
    }

    // 返回合法报警规则JSON请求体
    private String validRuleJson() {
        return "{\"metric\":\"temperature\",\"operator\":\"gt\",\"threshold\":30,"
                + "\"enabled\":true,\"deviceId\":\"*\",\"cooldownSeconds\":300}";
    }
}

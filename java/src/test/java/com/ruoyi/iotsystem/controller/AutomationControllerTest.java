package com.ruoyi.iotsystem.controller;

import com.ruoyi.iotsystem.entity.AutomationRuleEntity;
import com.ruoyi.iotsystem.service.AutomationService;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AutomationControllerTest {

    @Mock private AutomationService automationService;
    private MockMvc mockMvc;

    // 创建仅包含自动化控制器的MockMvc环境
    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                new AutomationController(automationService)).build();
    }

    // 验证自动化规则列表接口返回持久化规则
    @Test
    void getRules_应返回规则列表() throws Exception {
        when(automationService.getRules()).thenReturn(Collections.singletonList(createRule()));

        mockMvc.perform(get("/api/automation/rules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].name").value("高温启动"));
    }

    // 验证合法规则请求可以创建
    @Test
    void createRule_合法请求_应返回规则() throws Exception {
        when(automationService.createRule(any())).thenReturn(createRule());

        mockMvc.perform(post("/api/automation/rules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1));
    }

    // 验证无效设备ID在入口被拒绝
    @Test
    void createRule_设备Id无效_应返回四百() throws Exception {
        mockMvc.perform(post("/api/automation/rules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validJson().replace("device001", "device/other")))
                .andExpect(status().isBadRequest());
    }

    // 验证删除规则接口调用业务服务
    @Test
    void deleteRule_应删除指定规则() throws Exception {
        mockMvc.perform(delete("/api/automation/rules/1"))
                .andExpect(status().isOk());

        verify(automationService).deleteRule(1L);
    }

    // 创建控制器测试使用的规则实体
    private AutomationRuleEntity createRule() {
        AutomationRuleEntity rule = new AutomationRuleEntity(
                "高温启动", "device001", "temperature", "gt", 30.0,
                "start", true, 2, 300);
        rule.setId(1L);
        return rule;
    }

    // 返回合法自动化规则JSON请求体
    private String validJson() {
        return "{\"name\":\"高温启动\",\"deviceId\":\"device001\","
                + "\"metric\":\"temperature\",\"operator\":\"gt\",\"threshold\":30,"
                + "\"action\":\"start\",\"enabled\":true,\"debounceCount\":2,"
                + "\"cooldownSeconds\":300}";
    }
}

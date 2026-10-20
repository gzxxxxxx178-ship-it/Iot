package com.ruoyi.iotsystem.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

/** 微信小程序临时登录凭证请求。 */
public class WechatCodeRequest {

    @NotBlank(message = "微信登录凭证不能为空")
    @Size(max = 128, message = "微信登录凭证格式无效")
    private String code;

    /** 获取一次性微信登录凭证。 */
    public String getCode() { return code; }
    /** 设置一次性微信登录凭证。 */
    public void setCode(String code) { this.code = code; }
}

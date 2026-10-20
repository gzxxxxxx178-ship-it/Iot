package com.ruoyi.iotsystem.entity;

import javax.persistence.*;
import java.time.LocalDateTime;

/** 将微信小程序OpenID映射到既有系统账号。 */
@Entity
@Table(name = "wechat_miniapp_identities", uniqueConstraints = {
        @UniqueConstraint(name = "uk_wechat_app_openid", columnNames = {"app_id", "open_id"}),
        @UniqueConstraint(name = "uk_wechat_app_username", columnNames = {"app_id", "username"})
})
public class WechatIdentityEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "app_id", nullable = false, length = 32)
    private String appId;

    @Column(name = "open_id", nullable = false, length = 128)
    private String openId;

    @Column(name = "username", nullable = false, length = 255)
    private String username;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /** 获取映射记录主键。 */
    public Long getId() { return id; }
    /** 设置映射记录主键。 */
    public void setId(Long id) { this.id = id; }
    /** 获取小程序AppID。 */
    public String getAppId() { return appId; }
    /** 设置小程序AppID。 */
    public void setAppId(String appId) { this.appId = appId; }
    /** 获取微信OpenID。 */
    public String getOpenId() { return openId; }
    /** 设置微信OpenID。 */
    public void setOpenId(String openId) { this.openId = openId; }
    /** 获取绑定的系统用户名。 */
    public String getUsername() { return username; }
    /** 设置绑定的系统用户名。 */
    public void setUsername(String username) { this.username = username; }
    /** 获取绑定时间。 */
    public LocalDateTime getCreatedAt() { return createdAt; }
    /** 设置绑定时间。 */
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}

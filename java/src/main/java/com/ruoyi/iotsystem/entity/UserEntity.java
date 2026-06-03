package com.ruoyi.iotsystem.entity;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
public class UserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String username;

    @Column(nullable = true)
    private String password;

    @Column(nullable = true)
    private String email;

    @Column(nullable = true)
    private String avatar;

    @Column(nullable = true)
    private String provider;

    @Column(name = "provider_id", nullable = true)
    private String providerId;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    public UserEntity() {}

    public UserEntity(String username, String password) {
        this.username = username;
        this.password = password;
        this.provider = "LOCAL";
        this.createdAt = LocalDateTime.now();
    }

    public UserEntity(String username, String email, String avatar, String provider, String providerId) {
        this.username = username;
        this.email = email;
        this.avatar = avatar;
        this.provider = provider;
        this.providerId = providerId;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getAvatar() { return avatar; }
    public void setAvatar(String avatar) { this.avatar = avatar; }

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }

    public String getProviderId() { return providerId; }
    public void setProviderId(String providerId) { this.providerId = providerId; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    @Override
    public String toString() {
        return "UserEntity{" +
                "id=" + id +
                ", username='" + username + '\'' +
                ", email='" + email + '\'' +
                ", provider='" + provider + '\'' +
                ", createdAt=" + createdAt +
                '}';
    }
}

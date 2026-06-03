package com.ruoyi.iotsystem.repository;

import com.ruoyi.iotsystem.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<UserEntity, Long> {
    UserEntity findByUsername(String username);
    boolean existsByUsername(String username);
    UserEntity findByProviderAndProviderId(String provider, String providerId);
}

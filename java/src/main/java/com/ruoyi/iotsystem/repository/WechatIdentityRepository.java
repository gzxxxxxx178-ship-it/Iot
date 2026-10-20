package com.ruoyi.iotsystem.repository;

import com.ruoyi.iotsystem.entity.WechatIdentityEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/** 查找和保存微信小程序身份映射。 */
@Repository
public interface WechatIdentityRepository extends JpaRepository<WechatIdentityEntity, Long> {

    /** 按小程序和OpenID查找已绑定账号。 */
    Optional<WechatIdentityEntity> findByAppIdAndOpenId(String appId, String openId);

    /** 判断系统账号是否已绑定当前小程序身份。 */
    boolean existsByAppIdAndUsername(String appId, String username);
}

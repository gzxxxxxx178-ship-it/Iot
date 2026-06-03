package com.ruoyi.iotsystem.repository;

import com.ruoyi.iotsystem.entity.PaymentOrderEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PaymentOrderRepository extends JpaRepository<PaymentOrderEntity, Long> {
    PaymentOrderEntity findByOutTradeNo(String outTradeNo);
}

package com.ruoyi.iotsystem.service;

import com.ruoyi.iotsystem.entity.EspEntity;
import com.ruoyi.iotsystem.repository.EspRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class EspService {

    private static final Logger logger = LoggerFactory.getLogger(EspService.class);

    @Autowired
    private EspRepository espRepository;

    /**
     * 保存ESP设备数据到数据库
     * 
     * @param espEntity ESP设备数据实体
     * @return 保存后的实体对象
     */
    public EspEntity saveData(EspEntity espEntity) {
        logger.info(
                "Saving ESP data to database: DeviceId={}, Temperature={}, Humidity={}, Water={}, Linkage={}, SendCount={}, Rssi={}, Timestamp={}",
                espEntity.getDeviceId(), espEntity.getTemperature(), espEntity.getHumidity(),
                espEntity.getWater(), espEntity.getLinkage(), espEntity.getSendCount(), espEntity.getRssi(),
                espEntity.getTimestamp());

        EspEntity savedEntity = espRepository.save(espEntity);

        logger.info("Successfully saved ESP data with ID: {}", savedEntity.getId());

        return savedEntity;
    }

    /**
     * 处理并保存ESP设备数据，返回格式化的响应字符串
     * 
     * @param espEntity ESP设备数据实体
     * @return 格式化的JSON响应字符串
     */
    public String processDataAndGenerateResponse(EspEntity espEntity) {
        try {
            // 保存数据到数据库
            EspEntity savedEntity = saveData(espEntity);

            // 返回成功响应
            return String.format(
                    "{\"status\":\"success\", \"message\":\"Data received and saved.\", \"id\":%d}",
                    savedEntity.getId());
        } catch (Exception e) {
            logger.error("Failed to save data to database", e);
            return String.format(
                    "{\"status\":\"error\", \"message\":\"Failed to save data: %s\"}",
                    e.getMessage());
        }
    }

    /**
     * 获取最近的20条数据
     * 
     * @return List<EspEntity>
     */
    public java.util.List<EspEntity> getRecentData() {
        return espRepository.findTop20ByOrderByServerReceivedTimeDesc();
    }
}
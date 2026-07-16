package com.ruoyi.iotsystem.service;

import com.ruoyi.iotsystem.entity.EspEntity;
import com.ruoyi.iotsystem.repository.EspRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.regex.Pattern;

@Service
public class SensorHistoryService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_RANGE_ROWS = 5000;
    private static final int MAX_EXPORT_ROWS = 50000;
    private static final long MAX_RANGE_DAYS = 366;
    private static final Pattern DEVICE_ID_PATTERN = Pattern.compile("[A-Za-z0-9_-]{1,64}");
    private static final DateTimeFormatter CSV_TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final EspRepository espRepository;
    private final DeviceService deviceService;

    // 注入传感器历史仓库
    public SensorHistoryService(EspRepository espRepository) {
        this.espRepository = espRepository;
        this.deviceService = null;
    }

    // 注入传感器历史仓库和设备归属服务
    @Autowired
    public SensorHistoryService(EspRepository espRepository, DeviceService deviceService) {
        this.espRepository = espRepository;
        this.deviceService = deviceService;
    }

    // 按设备、时间范围和受限分页参数组合查询历史数据
    public Page<EspEntity> queryPage(String deviceId, LocalDateTime start, LocalDateTime end,
            int page, int size) {
        return queryPage(deviceId, start, end, page, size, null);
    }

    // 按当前用户查询历史分页数据
    public Page<EspEntity> queryPage(String deviceId, LocalDateTime start, LocalDateTime end,
            int page, int size, String ownerUsername) {
        String normalizedDeviceId = normalizeDeviceId(deviceId);
        validateTimeRange(start, end, false);
        validatePage(page, size);
        PageRequest pageable = PageRequest.of(page, size);
        return query(normalizedDeviceId, start, end, pageable, ownerUsername);
    }

    // 查询受行数上限保护的兼容时间范围列表
    public List<EspEntity> queryRange(String deviceId, LocalDateTime start, LocalDateTime end) {
        return queryRange(deviceId, start, end, null);
    }

    // 按当前用户查询历史数据
    public List<EspEntity> queryRange(String deviceId, LocalDateTime start, LocalDateTime end,
            String ownerUsername) {
        String normalizedDeviceId = normalizeDeviceId(deviceId);
        validateTimeRange(start, end, true);
        Page<EspEntity> result = query(
                normalizedDeviceId, start, end, PageRequest.of(0, MAX_RANGE_ROWS + 1), ownerUsername);
        if (result.getTotalElements() > MAX_RANGE_ROWS) {
            throw new RuntimeException("查询结果超过5000条，请缩小时间范围或使用分页接口");
        }
        return result.getContent();
    }

    // 导出所选设备和时间范围内的完整CSV并限制最大行数
    public byte[] exportCsv(String deviceId, LocalDateTime start, LocalDateTime end) {
        return exportCsv(deviceId, start, end, null);
    }

    // 按当前用户导出历史数据CSV
    public byte[] exportCsv(String deviceId, LocalDateTime start, LocalDateTime end, String ownerUsername) {
        String normalizedDeviceId = normalizeDeviceId(deviceId);
        validateTimeRange(start, end, true);
        Page<EspEntity> result = query(
                normalizedDeviceId, start, end, PageRequest.of(0, MAX_EXPORT_ROWS + 1), ownerUsername);
        if (result.getTotalElements() > MAX_EXPORT_ROWS) {
            throw new RuntimeException("导出结果超过50000条，请缩小时间范围后重试");
        }
        StringBuilder csv = new StringBuilder("\uFEFF");
        csv.append("设备ID,温度(℃),湿度(%),水位(ADC),RSSI(dBm),联动,发送次数,设备运行时长(ms),服务端接收时间,质量状态,质量问题\r\n");
        for (EspEntity reading : result.getContent()) {
            appendCsvRow(csv, reading);
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    // 根据可选筛选条件选择能够利用现有索引的Repository查询
    private Page<EspEntity> query(String deviceId, LocalDateTime start, LocalDateTime end,
            PageRequest pageable) {
        return query(deviceId, start, end, pageable, null);
    }

    // 根据用户归属和筛选条件选择历史数据查询
    private Page<EspEntity> query(String deviceId, LocalDateTime start, LocalDateTime end,
            PageRequest pageable, String ownerUsername) {
        if (ownerUsername != null) {
            if (deviceService != null) {
                deviceService.claimLegacyDevices(ownerUsername);
            }
            if (deviceId != null && start != null) {
                return espRepository.findByOwnerUsernameAndDeviceIdAndServerReceivedTimeBetweenOrderByServerReceivedTimeDesc(
                        ownerUsername, deviceId, start, end, pageable);
            }
            if (deviceId != null) {
                return espRepository.findByOwnerUsernameAndDeviceIdOrderByServerReceivedTimeDesc(
                        ownerUsername, deviceId, pageable);
            }
            if (start != null) {
                return espRepository.findByOwnerUsernameAndServerReceivedTimeBetweenOrderByServerReceivedTimeDesc(
                        ownerUsername, start, end, pageable);
            }
            return espRepository.findAllByOwnerUsernameOrderByServerReceivedTimeDesc(ownerUsername, pageable);
        }
        if (deviceId != null && start != null) {
            return espRepository.findByDeviceIdAndServerReceivedTimeBetweenOrderByServerReceivedTimeDesc(
                    deviceId, start, end, pageable);
        }
        if (deviceId != null) {
            return espRepository.findByDeviceIdOrderByServerReceivedTimeDesc(deviceId, pageable);
        }
        if (start != null) {
            return espRepository.findByServerReceivedTimeBetweenOrderByServerReceivedTimeDesc(
                    start, end, pageable);
        }
        return espRepository.findAllByOrderByServerReceivedTimeDesc(pageable);
    }

    // 校验设备ID并将空文本规范化为不筛选设备
    private String normalizeDeviceId(String deviceId) {
        if (deviceId == null || deviceId.trim().isEmpty()) {
            return null;
        }
        String normalized = deviceId.trim();
        if (!DEVICE_ID_PATTERN.matcher(normalized).matches()) {
            throw new RuntimeException("设备ID格式无效");
        }
        return normalized;
    }

    // 校验时间参数必须成对、顺序正确且范围不超过366天
    private void validateTimeRange(LocalDateTime start, LocalDateTime end, boolean required) {
        if (start == null && end == null) {
            if (required) {
                throw new RuntimeException("开始时间和结束时间不能为空");
            }
            return;
        }
        if (start == null || end == null) {
            throw new RuntimeException("开始时间和结束时间必须同时提供");
        }
        if (start.isAfter(end)) {
            throw new RuntimeException("开始时间不能晚于结束时间");
        }
        if (Duration.between(start, end).toDays() > MAX_RANGE_DAYS) {
            throw new RuntimeException("单次查询时间范围不能超过366天");
        }
    }

    // 校验分页页码和每页数量上限
    private void validatePage(int page, int size) {
        if (page < 0) {
            throw new RuntimeException("页码不能小于0");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new RuntimeException("每页条数必须在1到100之间");
        }
    }

    // 将单条传感器记录追加为经过转义的CSV行
    private void appendCsvRow(StringBuilder csv, EspEntity reading) {
        csv.append(csvCell(reading.getDeviceId())).append(',')
                .append(csvCell(reading.getTemperature())).append(',')
                .append(csvCell(reading.getHumidity())).append(',')
                .append(csvCell(reading.getWater())).append(',')
                .append(csvCell(reading.getRssi())).append(',')
                .append(csvCell(reading.getLinkage())).append(',')
                .append(csvCell(reading.getSendCount())).append(',')
                .append(csvCell(reading.getUptimeMillis())).append(',')
                .append(csvCell(formatTime(reading.getServerReceivedTime()))).append(',')
                .append(csvCell(qualityLabel(reading.getQualityValid()))).append(',')
                .append(csvCell(reading.getQualityIssues())).append("\r\n");
    }

    // 对CSV单元格执行公式注入防护、引号转义和换行包裹
    private String csvCell(Object value) {
        if (value == null) {
            return "";
        }
        String text = String.valueOf(value);
        if (!(value instanceof Number) && !text.isEmpty() && "=+-@".indexOf(text.charAt(0)) >= 0) {
            text = "'" + text;
        }
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }

    // 格式化CSV中的服务端接收时间
    private String formatTime(LocalDateTime value) {
        return value == null ? "" : CSV_TIME_FORMAT.format(value);
    }

    // 将可空质量状态转换为中文标签
    private String qualityLabel(Boolean qualityValid) {
        if (qualityValid == null) {
            return "未评估";
        }
        return qualityValid ? "有效" : "异常";
    }
}

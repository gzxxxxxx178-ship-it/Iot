% postTelemetry - 使用HTTP POST上传遥测数据到Java后端
% 参数:
%   config  - 仿真配置结构体 (含baseUrl, bearerToken)
%   payload - 遥测payload结构体 (由buildTelemetryPayload构建)
% 返回:
%   success - 逻辑值, true表示上传成功, false表示上传失败
%   response - 后端响应数据 (仅success=true时有效)
% 说明:
%   使用MATLAB webwrite发送JSON, Authorization头为Bearer <token>。
%   不将token写入URL或日志输出。
%   dryRun模式下不实际发送HTTP请求。
%   单次上传失败记录简短错误信息, 不含token内容。
function [success, response] = postTelemetry(config, payload)
    success = false;
    response = [];

    % dry-run: 不联网, 仅返回记录payload
    if config.dryRun
        fprintf('[DRY-RUN] 遥测payload: sampleId=%s, deviceId=%s, waterLevel=%.1fmm\n', ...
            payload.sampleId, payload.deviceId, payload.waterLevelMm);
        return;
    end

    % 构建请求URL
    url = [config.baseUrl, '/api/simulation/telemetry'];

    % 配置HTTP选项: JSON内容, Bearer Token认证
    options = weboptions('MediaType', 'application/json', ...
                         'RequestMethod', 'post', ...
                         'Timeout', 10);

    % 设置Authorization头
    options.HeaderFields = {'Authorization', ['Bearer ', config.bearerToken]};
    options.ContentType = 'json';

    try
        % 发送POST请求
        result = webwrite(url, payload, options);
        success = true;
        response = result;
    catch ME
        % 仅记录简短错误, 不含token
        fprintf('[ERROR] 遥测上传失败: %s\n', ME.message);
        success = false;
    end
end

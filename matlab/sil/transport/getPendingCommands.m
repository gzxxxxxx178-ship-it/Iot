% getPendingCommands - 轮询Java后端获取指定设备的待处理命令
% 参数:
%   config   - 仿真配置结构体 (含baseUrl, bearerToken)
%   deviceId - 设备标识字符串
% 返回:
%   success  - 逻辑值, true表示查询成功
%   commands - 命令结构体数组 (SimulationCommand解析结果),
%              包含: id, alarmId, deviceId, action, status等字段
% 说明:
%   dryRun模式下返回空命令列表。
%   使用MATLAB webread发送GET请求, 解析ApiResponse.data。
%   指令仅允许: STOP_IRRIGATION, STOP_FERTILIZER, STOP_ALL。
function [success, commands] = getPendingCommands(config, deviceId)
    success = false;
    commands = [];

    if config.dryRun
        % dry-run: 不联网
        return;
    end

    % 构建请求URL (使用GET query参数)
    url = [config.baseUrl, '/api/simulation/commands/pending?deviceId=', deviceId];

    % 配置HTTP选项
    options = weboptions('RequestMethod', 'get', 'Timeout', 10);
    options.HeaderFields = {'Authorization', ['Bearer ', config.bearerToken]};

    try
        % 发送GET请求
        apiResp = webread(url, options);

        % 解析ApiResponse: {code, message, data}
        if isstruct(apiResp) && isfield(apiResp, 'code') && apiResp.code == 200
            if isfield(apiResp, 'data') && ~isempty(apiResp.data)
                commands = apiResp.data;
            end
            success = true;
        else
            fprintf('[ERROR] 命令查询返回异常code: %d\n', ...
                    iif(isfield(apiResp,'code'), apiResp.code, -1));
        end
    catch ME
        fprintf('[ERROR] 命令查询失败: %s\n', ME.message);
        success = false;
    end
end

% iif - 内联条件辅助函数
function result = iif(cond, trueVal, falseVal)
    if cond
        result = trueVal;
    else
        result = falseVal;
    end
end

% createSilConfig - 创建MATLAB软件在环仿真配置结构
% 参数:
%   baseUrl     - Java后端基础URL (例如 'http://localhost:8080')
%   bearerToken - Bearer认证令牌字符串
% 返回:
%   config - 包含所有仿真参数的配置结构体
% 说明:
%   本函数校验baseUrl和token非空，不打印token。
%   deviceId默认为'SIM-PADDY-001'，仿真120步，每步5秒。
function config = createSilConfig(baseUrl, bearerToken)
    % 参数校验
    if isempty(baseUrl)
        error('createSilConfig:EmptyBaseUrl', 'baseUrl不能为空');
    end
    if ~(ischar(baseUrl) || isstring(baseUrl))
        error('createSilConfig:InvalidBaseUrl', 'baseUrl必须是字符串');
    end
    if isempty(bearerToken)
        error('createSilConfig:EmptyBearerToken', 'bearerToken不能为空');
    end

    % 组装配置结构体
    config = struct();
    config.baseUrl = char(baseUrl);
    config.bearerToken = char(bearerToken);  % 不打印token内容
    config.deviceId = 'SIM-PADDY-001';
    config.steps = 120;
    config.stepSeconds = 5;
    config.realtimePauseSeconds = 1;
    config.dryRun = false;
    config.randomSeed = 20260810;
end

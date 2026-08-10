% postCommandFeedback - 向Java后端提交命令执行反馈
% 参数:
%   config    - 仿真配置结构体 (含baseUrl, bearerToken)
%   commandId - 命令实体主键ID
%   status    - 执行结果: 'SUCCESS' 或 'FAILED'
%   message   - 反馈描述 (可选, 最多500字符)
% 返回:
%   success   - 逻辑值, true表示反馈提交成功
%   response  - 后端响应数据
% 说明:
%   dryRun模式下不发送HTTP请求。
%   同一命令ID在当前会话内只反馈一次 (由调用方确保)。
%   SUCCESS对应命令正确执行, FAILED用于未知或不适用动作。
function [success, response] = postCommandFeedback(config, commandId, status, message)
    success = false;
    response = [];

    if config.dryRun
        fprintf('[DRY-RUN] 命令反馈: id=%d, status=%s\n', commandId, status);
        return;
    end

    % 构建请求URL
    url = [config.baseUrl, '/api/simulation/commands/', num2str(commandId), '/feedback'];

    % 构建反馈请求体
    feedbackBody = struct();
    feedbackBody.status = status;
    if nargin >= 4 && ~isempty(message)
        feedbackBody.message = message;
    else
        feedbackBody.message = '';
    end

    % 配置HTTP选项
    options = weboptions('MediaType', 'application/json', ...
                         'RequestMethod', 'post', ...
                         'Timeout', 10);
    options.HeaderFields = {'Authorization', ['Bearer ', config.bearerToken]};
    options.ContentType = 'json';

    try
        % 发送POST请求
        result = webwrite(url, feedbackBody, options);
        success = true;
        response = result;
    catch ME
        fprintf('[ERROR] 命令反馈提交失败 (cmdId=%d): %s\n', commandId, ME.message);
        success = false;
    end
end

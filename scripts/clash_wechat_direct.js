// 微信开发工具 TUN 兼容补丁：追加到 Clash Verge 订阅扩展脚本末尾。
// 保留原 main 和其他订阅配置，只处理微信相关域名；不包含节点或凭据。

// 为微信域名追加真实 IP 解析、加密 DNS 及直连规则。
function applyWechatDevelopmentDirect(config) {
  const suffixes = ['weixin.qq.com', 'servicewechat.com', 'wechat.com'];
  const dns = config.dns || {};
  const filterMode = dns['fake-ip-filter-mode'] || 'blacklist';
  if (!['blacklist', 'rule'].includes(filterMode)) {
    throw new Error('微信补丁暂不支持 Fake-IP whitelist 模式，请保留现有配置并单独检查。');
  }
  const filters = Array.isArray(dns['fake-ip-filter']) ? dns['fake-ip-filter'] : [];
  // 根据过滤模式构造追加项，保留原有过滤配置。
  const additions = suffixes.map(function makeFilter(suffix) {
    return filterMode === 'rule' ? 'DOMAIN-SUFFIX,' + suffix + ',real-ip' : '+.' + suffix;
  });
  dns['fake-ip-filter'] = Array.from(new Set(additions.concat(filters)));
  const policies = Object.assign({}, dns['nameserver-policy'] || {});
  for (const suffix of suffixes) {
    policies['+.' + suffix] = [
      'https://dns.alidns.com/dns-query#DIRECT',
      'https://doh.pub/dns-query#DIRECT',
    ];
  }
  dns['nameserver-policy'] = policies;
  config.dns = dns;
  // 微信域名优先走直连；原有规则顺序保持不变。
  const directRules = suffixes.map(function makeDirectRule(suffix) {
    return 'DOMAIN-SUFFIX,' + suffix + ',DIRECT';
  });
  config.rules = Array.from(new Set(directRules.concat(config.rules || [])));
  return config;
}

// 先执行已有订阅脚本，再追加微信兼容配置。
const wechatPreviousMain = main;
main = function mainWithWechatDirect(config, profileName) {
  return applyWechatDevelopmentDirect(wechatPreviousMain(config, profileName));
};

// 当前项目 MQTT 服务局部分流补丁，追加在现有订阅扩展脚本之后。
// 不含代理凭据；保持证书校验、TUN和原节点组不变。
function applyIotDevelopmentDirect(config) {
  const host = '38.47.98.235.nip.io';
  const dns = config.dns || {};
  const mode = dns['fake-ip-filter-mode'] || 'blacklist';
  if (!['blacklist', 'rule'].includes(mode)) throw new Error('IoT补丁暂不支持Fake-IP whitelist模式');
  const filter = mode === 'rule' ? 'DOMAIN,' + host + ',real-ip' : host;
  dns['fake-ip-filter'] = Array.from(new Set([filter].concat(dns['fake-ip-filter'] || [])));
  dns['nameserver-policy'] = Object.assign({}, dns['nameserver-policy'] || {}, {
    [host]: ['https://dns.alidns.com/dns-query#DIRECT', 'https://doh.pub/dns-query#DIRECT'],
  });
  config.dns = dns;
  config.rules = Array.from(new Set([
    'DOMAIN,' + host + ',DIRECT',
    'IP-CIDR,38.47.98.235/32,DIRECT,no-resolve',
  ].concat(config.rules || [])));
  return config;
}

// 保留原订阅和微信分流逻辑，再追加项目后端直连。
const iotPreviousMain = main;
main = function mainWithIotDirect(config, profileName) {
  return applyIotDevelopmentDirect(iotPreviousMain(config, profileName));
};

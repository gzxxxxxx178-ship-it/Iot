const { test } = require('node:test');
const assert = require('node:assert/strict');
const { readFileSync } = require('node:fs');
const { resolve } = require('node:path');
const vm = require('node:vm');

// 创建独立脚本环境，模拟已有订阅 main，不读取任何本机代理配置。
function createContext() {
  const context = vm.createContext({});
  const source = readFileSync(resolve(__dirname, '../clash_wechat_direct.js'), 'utf8');
  vm.runInContext('function main(config, name) { config.originalProfile = name; return config; }\n' + source, context);
  return context;
}

// 构造含原有 DNS、路由及节点组的脱敏测试配置。
function fixture(mode = 'blacklist') {
  return {
    dns: {
      'enhanced-mode': 'fake-ip',
      'fake-ip-filter-mode': mode,
      'fake-ip-filter': ['*.lan'],
      'nameserver': ['https://dns.example.test/dns-query'],
      'nameserver-policy': { 'example.test': ['https://dns.example.test/dns-query'] },
    },
    rules: ['DOMAIN,example.test,原代理组', 'MATCH,原代理组'],
    'proxy-groups': [{ name: '原代理组', proxies: ['原节点'] }],
    tun: { enable: true },
  };
}

// 验证微信分流和 DNS 例外不会覆盖原订阅或关闭 TUN。
test('微信走真实解析和直连，保留原订阅及加密 DNS', function preserveConfiguration() {
  const context = createContext();
  const input = fixture();
  const before = JSON.parse(JSON.stringify(input));
  const result = context.main(input, '原订阅');
  assert.equal(result.originalProfile, '原订阅');
  assert.equal(result.tun.enable, true);
  assert.deepEqual(result['proxy-groups'], before['proxy-groups']);
  assert.deepEqual(result.dns.nameserver, before.dns.nameserver);
  assert.deepEqual(result.dns['nameserver-policy']['example.test'], before.dns['nameserver-policy']['example.test']);
  assert.deepEqual(Array.from(result.rules.slice(3)), before.rules);
  for (const suffix of ['weixin.qq.com', 'servicewechat.com', 'wechat.com']) {
    assert.ok(result.rules.includes('DOMAIN-SUFFIX,' + suffix + ',DIRECT'));
    assert.ok(result.dns['fake-ip-filter'].includes('+.' + suffix));
    assert.ok(result.dns['nameserver-policy']['+.' + suffix].every(function encryptedDirectDns(url) {
      return url.startsWith('https://') && url.endsWith('#DIRECT');
    }));
  }
});

// 验证多次重新激活订阅不会不断累积补丁规则。
test('重复执行幂等，保留已有 Fake-IP 过滤项', function repeatActivation() {
  const context = createContext();
  const result = context.main(fixture(), '原订阅');
  const first = JSON.stringify(result);
  context.main(result, '原订阅');
  assert.equal(JSON.stringify(result), first);
  assert.ok(result.dns['fake-ip-filter'].includes('*.lan'));
});

// 验证 rule 模式使用真实 IP 规则而非误套用 blacklist 语法。
test('支持 rule 过滤模式', function ruleMode() {
  const result = createContext().main(fixture('rule'), '原订阅');
  assert.equal(result.dns['fake-ip-filter'][0], 'DOMAIN-SUFFIX,weixin.qq.com,real-ip');
  assert.equal(result.dns['fake-ip-filter-mode'], 'rule');
});

// 验证不支持的过滤模式会明确拒绝，而非悄悄改写 DNS 语义。
test('拒绝覆盖 whitelist 模式', function unsupportedMode() {
  assert.throws(function runUnsupportedMode() {
    createContext().main(fixture('whitelist'), '原订阅');
  }, /whitelist/);
});

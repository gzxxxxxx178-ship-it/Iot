const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

// 在无凭据的隔离环境中串联微信与IoT补丁。
function context() {
  const scope = vm.createContext({});
  vm.runInContext('function main(c) { c.kept = true; return c; }', scope);
  for (const file of ['clash_wechat_direct.js', 'clash_iot_direct.js']) vm.runInContext(fs.readFileSync(path.resolve(__dirname, '..', file), 'utf8'), scope);
  return scope;
}

// 验证局部真实解析和直连幂等，不覆盖原规则或TUN。
test('IoT分流与微信补丁共存，保持原配置且重复执行不累积', () => {
  const scope = context();
  const config = { dns: { 'fake-ip-filter': ['*.lan'] }, rules: ['MATCH,原代理组'], tun: { enable: true } };
  scope.main(config);
  const first = JSON.stringify(config);
  scope.main(config);
  assert.equal(JSON.stringify(config), first);
  assert.equal(config.kept, true); assert.equal(config.tun.enable, true);
  assert.ok(config.rules.includes('DOMAIN,38.47.98.235.nip.io,DIRECT'));
  assert.ok(config.rules.includes('DOMAIN-SUFFIX,weixin.qq.com,DIRECT'));
  assert.equal(config.rules.at(-1), 'MATCH,原代理组');
  assert.ok(config.dns['fake-ip-filter'].includes('38.47.98.235.nip.io'));
});

// 验证rule模式的精确域名过滤，不放宽为整个nip.io。
test('IoT仅匹配指定域名，rule模式语义正确', () => {
  const config = { dns: { 'fake-ip-filter-mode': 'rule' } };
  context().main(config);
  assert.ok(config.dns['fake-ip-filter'].includes('DOMAIN,38.47.98.235.nip.io,real-ip'));
  assert.equal(config.dns['fake-ip-filter'].some((item) => item.includes('DOMAIN-SUFFIX,nip.io')), false);
});

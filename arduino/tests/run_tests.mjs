import { mkdtempSync, rmSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { resolve, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'
import { spawnSync } from 'node:child_process'

const here = dirname(fileURLToPath(import.meta.url))
const dependency = process.argv[2]
if (!dependency) throw new Error('请传入 ArduinoJson 6.21.5 库目录')
const output = mkdtempSync(resolve(tmpdir(), 'iot-command-tests-'))

// 以参数数组启动本地编译器或测试程序，不执行shell、不访问网络和设备。
function run(program, args) {
  const result = spawnSync(program, args, { stdio: 'inherit' })
  if (result.error) throw result.error
  if (result.status !== 0) throw new Error(`${program} 执行失败：${result.status}`)
}

try {
  const binary = resolve(output, 'command-tests')
  run(process.env.CXX || 'c++', ['-std=c++11', '-Wall', '-Wextra', '-Werror', '-g',
    '-fsanitize=address,undefined', '-fno-omit-frame-pointer',
    '-I', resolve(dependency, 'src'), resolve(here, 'command_protocol_test.cpp'), '-o', binary])
  run(binary, [])
} finally {
  rmSync(output, { recursive: true, force: true })
}

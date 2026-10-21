#include "../MQTT/CommandProtocol.h"
#include "../MQTT/SensorSampleState.h"
#include <iostream>
#include <string>
#include <vector>
#include <stdexcept>
#include <limits>

static size_t assertions = 0;

// 失败时终止测试并报告所违反的约束。
static void check(bool condition, const char* message) {
  ++assertions;
  if (!condition) throw std::runtime_error(message);
}

struct FakeDevice {
  bool online = true;
  bool publishOk = true;
  bool actionOk = true;
  bool sending = true;
  unsigned int sendCount = 7;
  unsigned int reads = 0;
  unsigned int starts = 0;
  unsigned int actions = 0;
  std::vector<std::string> publications;
  // 模拟设备连接状态。
  bool connected() { return online; }
  // 记录每次回执尝试，包括发送失败的尝试。
  bool publish(const char* payload) { publications.push_back(payload); return publishOk; }
  // 记录实际动作次数，重复ID不得再次进入此函数。
  bool execute(const char* command) {
    ++actions;
    if (strcmp(command, "read") == 0) ++reads;
    if (strcmp(command, "start") == 0) { ++starts; sending = true; }
    if (strcmp(command, "stop") == 0) sending = false;
    return actionOk;
  }
};

// 构造用于协议行为测试的标准JSON命令。
static std::string packet(const std::string& id, const std::string& command) {
  return "{\"commandId\":\"" + id + "\",\"command\":\"" + command + "\"}";
}

// 按实际长度交付载荷，支持包含NUL的std::string。
static void deliver(CommandProtocol& protocol, FakeDevice& device, const std::string& input, uint32_t now = 0) {
  protocol.receive(reinterpret_cast<const uint8_t*>(input.data()), input.size(), now, device);
}

// 重新解析回执以验证转义、设备身份和结果，而非匹配源码。
static std::string receipt(const FakeDevice& device, const char* field) {
  check(!device.publications.empty(), "expected receipt");
  StaticJsonDocument<512> json;
  check(!deserializeJson(json, device.publications.back()), "receipt must be JSON");
  check(std::string(json["deviceId"].as<const char*>()) == "device001", "receipt identity");
  return json[field].as<std::string>();
}

// 验证白名单、字段顺序、空白、Unicode转义和有界标识。
static void validCommands() {
  for (const char* action : {"start", "stop", "read", "status"}) {
    CommandProtocol protocol("device001"); FakeDevice device;
    deliver(protocol, device, packet("550e8400-e29b-41d4-a716-446655440000", action));
    check(device.actions == 1, "valid action executed once");
    check(receipt(device, "status") == "ACKNOWLEDGED", "valid ACK");
  }
  CommandProtocol protocol("device001"); FakeDevice device;
  deliver(protocol, device, " \n{ \"command\" : \"sta\\u0074us\", \"commandId\" : \"a_b-1\" } \r\n");
  check(device.actions == 1, "whitespace/order/escaped command");
  deliver(protocol, device, packet(std::string(64, 'x'), "read"), 1000);
  check(device.reads == 1, "64 character ID");
}

// 畸形输入、重复键、尾随垃圾、嵌套及非法标识均不得执行。
static void invalidInputs() {
  std::vector<std::string> inputs = {
    "", "{}", "[]", "null", "{", "{\"commandId\":\"a\",\"command\":\"start\"",
    "{commandId:'a',command:'start'}", "{'commandId':'a','command':'start'}",
    "{\"command\":\"start\"}", "{\"commandId\":null,\"command\":\"start\"}",
    "{\"commandId\":123,\"command\":\"start\"}", packet("", "start"),
    packet("a/other", "start"), packet("a\\\"inject", "start"), packet(std::string(65, 'a'), "start"),
    packet("a", "start") + "x", packet("a", "start") + "{}",
    "{\"commandId\":\"a\",\"command\":\"stop\",\"command\":\"start\"}",
    "{\"commandId\":\"a\",\"command\":\"sta\\u0000rt\"}",
    "{\"commandId\":\"a\\u0000\",\"command\":\"start\"}",
    "{\"commandId\\u0000\":\"a\",\"command\":\"start\"}",
    "{\"commandId\":\"a\",\"command\":{\"nested\":{\"again\":\"start\"}}}",
    std::string(385, 'x'), "{\"commandId\":\"a\",\"command\":\"sta\nrt\"}"
  };
  inputs.push_back(packet("a", "start") + std::string(1, '\0'));
  inputs.push_back(std::string("start\0junk", 10));
  for (const std::string& input : inputs) {
    CommandProtocol protocol("device001"); FakeDevice device;
    deliver(protocol, device, input);
    check(device.actions == 0, "malformed input cannot act");
  }
}

// 合法ID携带无效动作时拒绝，包括错误类型及额外字段。
static void rejectedCommands() {
  for (const std::string& input : {
      packet("a", "pump"), packet("a", "START"), packet("a", "x\\\"y"),
      packet("a", std::string(17, 'a')),
      std::string("{\"commandId\":\"a\"}"),
      std::string("{\"commandId\":\"a\",\"command\":null}"),
      std::string("{\"commandId\":\"a\",\"command\":false}"),
      std::string("{\"commandId\":\"a\",\"command\":[]}"),
      std::string("{\"commandId\":\"a\",\"command\":{}}"),
      std::string("{\"commandId\":\"a\",\"command\":\"start\",\"extra\":true}")}) {
    CommandProtocol protocol("device001"); FakeDevice device;
    deliver(protocol, device, input);
    check(device.actions == 0, "invalid command cannot act");
    check(receipt(device, "status") == "REJECTED", "invalid command rejected");
  }
}

// 重复ID不重复读取/启动，同ID不同动作拒绝，原始结果仍保留。
static void deduplication() {
  CommandProtocol protocol("device001"); FakeDevice device;
  deliver(protocol, device, packet("read-1", "read"));
  deliver(protocol, device, packet("read-1", "read"), 1000);
  check(device.reads == 1, "duplicate read must not execute");
  check(receipt(device, "status") == "ACKNOWLEDGED", "duplicate ACK");
  deliver(protocol, device, packet("read-1", "stop"), 2000);
  check(device.sending, "conflicting command must not stop");
  check(receipt(device, "status") == "REJECTED", "conflict rejected");
  deliver(protocol, device, packet("read-1", "read"), 3000);
  check(receipt(device, "status") == "ACKNOWLEDGED", "original result preserved");
  deliver(protocol, device, packet("start-1", "start"), 4000);
  deliver(protocol, device, packet("start-1", "start"), 5000);
  check(device.starts == 1 && device.sendCount == 7, "duplicate start preserves counters");
  device.actionOk = false;
  deliver(protocol, device, packet("failed-read", "read"), 6000);
  check(receipt(device, "status") == "REJECTED", "failed read rejected");
  device.actionOk = true;
  deliver(protocol, device, packet("failed-read", "read"), 7000);
  check(receipt(device, "status") == "REJECTED", "failed original never retried as action");
  check(device.reads == 2, "failed read executed once");
}

// 发布失败、离线、冲突拒绝及计时回绕均保留回执并限频重试。
static void retries() {
  CommandProtocol protocol("device001"); FakeDevice device;
  device.publishOk = false;
  uint32_t start = std::numeric_limits<uint32_t>::max() - 500;
  deliver(protocol, device, packet("retry", "read"), start);
  check(device.publications.size() == 1, "initial attempt");
  protocol.retry(498, device);
  check(device.publications.size() == 1, "wrap interval <1000");
  device.online = false;
  protocol.retry(499, device);
  check(device.publications.size() == 1, "offline does not publish");
  device.online = true; device.publishOk = true;
  protocol.retry(499, device);
  check(device.publications.size() == 2 && device.reads == 1, "reconnected retry only ACK");
  protocol.retry(2000, device);
  check(device.publications.size() == 2, "successful ACK not continually resent");
  device.publishOk = false;
  deliver(protocol, device, packet("retry", "stop"), 3000);
  device.publishOk = true;
  protocol.retry(4000, device);
  check(receipt(device, "status") == "REJECTED", "failed conflict receipt retried");
  StaticJsonDocument<512> original;
  check(!deserializeJson(original, device.publications[device.publications.size() - 2]), "original result JSON");
  check(std::string(original["status"].as<const char*>()) == "ACKNOWLEDGED", "original result precedes conflict rejection");
  check(device.actions == 1, "conflict retries cannot execute");
}

// 满队列不得覆盖未发送回执，恢复后可继续处理新命令。
static void boundedQueue() {
  CommandProtocol protocol("device001"); FakeDevice device;
  device.publishOk = false;
  for (size_t i = 0; i < CommandProtocol::CAPACITY; ++i) deliver(protocol, device, packet(std::to_string(i), "start"));
  deliver(protocol, device, packet("overflow", "read"));
  check(device.actions == 16 && device.reads == 0, "full pending queue blocks new action");
  device.publishOk = true;
  protocol.retry(1000, device);
  check(device.publications.size() == 32, "all sixteen pending ACKs retried");
  deliver(protocol, device, packet("overflow", "read"), 2000);
  check(device.reads == 1, "new action after space recovered");
  deliver(protocol, device, packet("0", "start"), 3000);
  check(device.actions == 18, "evicted old ID outside window may execute again");
}

// 旧文本兼容不生成审计回执，停止上报后仍允许单次读取。
static void legacyCommands() {
  CommandProtocol protocol("device001"); FakeDevice device;
  deliver(protocol, device, " STOP \n");
  check(!device.sending, "legacy stop");
  deliver(protocol, device, packet("read-stopped", "read"));
  check(!device.sending && device.reads == 1, "read allowed while stopped");
  size_t previous = device.publications.size();
  deliver(protocol, device, " StArT ");
  check(device.sending && device.publications.size() == previous, "legacy start without audit ACK");
}

// 直接验证固件所用的采样状态机，启动、失败与回绕均不能跳过质量和间隔约束。
static void samplingState() {
  SensorSampleState state;
  check(!state.isValid(), "startup values are not a valid sample");
  check(state.beginAttempt(0), "first sample permitted immediately");
  state.finishAttempt(false);
  check(!state.isValid() && !state.beginAttempt(1999), "failure neither valid nor immediately retried");
  check(state.beginAttempt(2000), "retry after two seconds");
  state.finishAttempt(true);
  check(state.isValid() && !state.beginAttempt(2001), "valid cached sample allowed between attempts");
  check(state.beginAttempt(4000), "new scheduled sample");
  state.finishAttempt(false);
  check(!state.isValid(), "failed sample invalidates old reading");
  SensorSampleState wrap;
  check(wrap.beginAttempt(std::numeric_limits<uint32_t>::max() - 1000), "sample before wrap");
  check(!wrap.beginAttempt(998) && wrap.beginAttempt(999), "sampling interval across wrap");
}

// 执行全部协议行为测试，内存边界由驱动启用的ASan/UBSan额外校验。
int main() {
  try {
    validCommands(); invalidInputs(); rejectedCommands(); deduplication(); retries(); boundedQueue(); legacyCommands(); samplingState();
    std::cout << "8 protocol/sampling suites passed; " << assertions << " assertions; cache bytes=" << sizeof(CommandProtocol) << '\n';
    return 0;
  } catch (const std::exception& error) {
    std::cerr << "FAILED: " << error.what() << '\n';
    return 1;
  }
}

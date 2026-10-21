#pragma once

#include <ArduinoJson.h>
#include <stdint.h>
#include <stddef.h>
#include <string.h>

#if ARDUINOJSON_VERSION_MAJOR != 6 || ARDUINOJSON_VERSION_MINOR != 21 || ARDUINOJSON_VERSION_REVISION != 5
#error "This firmware requires ArduinoJson 6.21.5"
#endif

// 固定容量命令处理器；动作及MQTT发布由固件适配器提供，宿主测试使用同一实现。
class CommandProtocol {
 public:
  static const size_t MAX_PAYLOAD_BYTES = 384;
  static const size_t CAPACITY = 16;
  static const uint32_t RETRY_MS = 1000;

  // 设备标识须在处理器生命周期内有效，命令缓存仅驻留RAM。
  explicit CommandProtocol(const char* deviceId) : deviceId_(deviceId), cursor_(0) {
    memset(records_, 0, sizeof(records_));
  }

  // 解码完整载荷并预留审计空间后执行；旧文本不具有去重或回执保证。
  template <typename Adapter>
  void receive(const uint8_t* payload, size_t length, uint32_t now, Adapter& adapter) {
    Parsed parsed = {};
    if (!decode(payload, length, parsed)) return;
    if (parsed.legacy) {
      if (allowed(parsed.command)) adapter.execute(parsed.command);
      return;
    }
    Record* record = find(parsed.id);
    if (record) {
      if (strcmp(record->command, parsed.command) != 0 || record->valid != parsed.valid) {
        record->conflictPending = true;
        record->pending = true;  // 先重发原结果，避免冲突拒绝抢在原始动作结果之前。
      } else {
        record->pending = true;
      }
      flush(*record, now, adapter);
      return;
    }
    record = reserve();
    if (!record) return;  // 所有槽位均有未发送回执：宁可超时，不能执行无追踪动作。
    memset(record, 0, sizeof(*record));
    record->used = true;
    record->valid = parsed.valid;
    memcpy(record->id, parsed.id, sizeof(record->id));
    memcpy(record->command, parsed.command, sizeof(record->command));
    record->accepted = parsed.valid && allowed(parsed.command) && adapter.execute(parsed.command);
    record->pending = true;
    flush(*record, now, adapter);
  }

  // 主循环重试尚未成功写出的回执；无动作重做，时间差采用32位无符号回绕语义。
  template <typename Adapter>
  void retry(uint32_t now, Adapter& adapter) {
    for (size_t i = 0; i < CAPACITY; ++i) {
      if (records_[i].used) flush(records_[i], now, adapter);
    }
  }

 private:
  struct Parsed {
    char id[65];
    char command[17];
    bool valid;
    bool legacy;
  };
  struct Record {
    char id[65];
    char command[17];
    bool used;
    bool valid;
    bool accepted;
    bool pending;
    bool conflictPending;
    bool attempted;
    uint32_t lastAttempt;
  };
  struct Reader {
    const uint8_t* data;
    size_t length;
    size_t position;
    // 按长度读取而非依赖载荷末尾的NUL。
    int read() { return position < length ? data[position++] : -1; }
    // 满足ArduinoJson自定义Reader接口。
    size_t readBytes(char* target, size_t count) {
      size_t available = length - position;
      if (count > available) count = available;
      memcpy(target, data + position, count);
      position += count;
      return count;
    }
  };
  const char* deviceId_;
  Record records_[CAPACITY];
  size_t cursor_;

  // JSON空白只接受规范中的四种ASCII字符。
  static bool whitespace(uint8_t value) {
    return value == ' ' || value == '\t' || value == '\r' || value == '\n';
  }

  // 限制ArduinoJson的宽松输入扩展，并统计平面字段以拒绝重复键；语法仍由库解析。
  static bool lexicalGate(const uint8_t* data, size_t length, size_t& fields) {
    bool quoted = false;
    bool escaped = false;
    size_t depth = 0;
    fields = 0;
    for (size_t i = 0; i < length; ++i) {
      uint8_t c = data[i];
      if (c == 0) return false;
      if (quoted) {
        if (c < 0x20) return false;
        if (escaped) {
          if (c == 'u' && i + 4 < length && memcmp(data + i + 1, "0000", 4) == 0) return false;
          escaped = false;
        } else if (c == '\\') escaped = true;
        else if (c == '"') quoted = false;
      } else if (c == '"') quoted = true;
      else if (c == '{' || c == '[') ++depth;
      else if (c == '}' || c == ']') { if (depth) --depth; }
      else if (c == ':') { if (depth == 1) ++fields; }
      else if (!whitespace(c) && c != '{' && c != '}' && c != ','
               && c != '[' && c != ']' && c != '-' && c != '.' && c != '+'
               && !(c >= '0' && c <= '9') && c != 'e' && c != 'E'
               && c != 't' && c != 'r' && c != 'u' && c != 'f' && c != 'a'
               && c != 'l' && c != 's' && c != 'n') return false;
    }
    return !quoted && !escaped;
  }

  // 校验后端审计标识允许的有限ASCII字符集。
  static bool validId(JsonString id) {
    if (id.isNull() || id.size() == 0 || id.size() > 64) return false;
    for (size_t i = 0; i < id.size(); ++i) {
      char c = id.c_str()[i];
      if (!((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
            || (c >= '0' && c <= '9') || c == '-' || c == '_')) return false;
    }
    return true;
  }

  // 仅复制库已确认的有限字符串，避免转换错误类型或截断嵌入NUL。
  static bool copyCommand(JsonVariantConst value, char* target) {
    if (!value.is<const char*>()) return false;
    JsonString text = value.as<JsonString>();
    if (text.size() == 0 || text.size() > 16 || strlen(text.c_str()) != text.size()) return false;
    memcpy(target, text.c_str(), text.size() + 1);
    return true;
  }

  // 完整解析单个平面JSON对象，合法ID但无效动作保留REJECTED回执路径。
  static bool decode(const uint8_t* data, size_t length, Parsed& result) {
    if (!data || !length || length > MAX_PAYLOAD_BYTES) return false;
    for (size_t i = 0; i < length; ++i) if (data[i] == 0) return false;
    size_t start = 0, end = length;
    while (start < end && whitespace(data[start])) ++start;
    while (end > start && whitespace(data[end - 1])) --end;
    if (start == end) return false;
    if (data[start] != '{') {
      if (end - start > 16) return false;
      for (size_t i = start; i < end; ++i) {
        uint8_t c = data[i];
        if (!((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z'))) return false;
        result.command[i - start] = c >= 'A' && c <= 'Z' ? c + ('a' - 'A') : c;
      }
      result.legacy = true;
      return allowed(result.command);
    }
    size_t fields = 0;
    if (!lexicalGate(data, length, fields)) return false;
    StaticJsonDocument<512> document;
    Reader reader = {data, length, 0};
    if (deserializeJson(document, reader, DeserializationOption::NestingLimit(2))
        || !document.is<JsonObject>()) return false;
    for (size_t i = reader.position; i < length; ++i) if (!whitespace(data[i])) return false;
    JsonObjectConst object = document.as<JsonObjectConst>();
    if (fields != object.size()) return false;
    if (!object["commandId"].is<const char*>()) return false;
    JsonString id = object["commandId"].as<JsonString>();
    if (!validId(id)) return false;
    memcpy(result.id, id.c_str(), id.size() + 1);
    result.valid = copyCommand(object["command"], result.command) && object.size() == 2;
    return true;
  }

  // JSON动作严格区分大小写，旧文本兼容在解码时统一小写。
  static bool allowed(const char* command) {
    return strcmp(command, "start") == 0 || strcmp(command, "stop") == 0
        || strcmp(command, "read") == 0 || strcmp(command, "status") == 0;
  }

  // 查询缓存内最近的命令标识。
  Record* find(const char* id) {
    for (size_t i = 0; i < CAPACITY; ++i) {
      if (records_[i].used && strcmp(records_[i].id, id) == 0) return &records_[i];
    }
    return nullptr;
  }

  // 按环形顺序使用空位或已发送回执的槽位，绝不丢弃待重试回执。
  Record* reserve() {
    for (size_t i = 0; i < CAPACITY; ++i) {
      size_t index = (cursor_ + i) % CAPACITY;
      if (!records_[index].used || (!records_[index].pending && !records_[index].conflictPending)) {
        cursor_ = (index + 1) % CAPACITY;
        return &records_[index];
      }
    }
    return nullptr;
  }

  // 使用库序列化有界回执，适配器必须以retain=false发布。
  template <typename Adapter>
  bool publish(const Record& record, bool conflict, Adapter& adapter) {
    StaticJsonDocument<384> document;
    document["deviceId"] = deviceId_;
    document["commandId"] = record.id;
    document["command"] = conflict ? "" : record.command;
    document["status"] = !conflict && record.accepted ? "ACKNOWLEDGED" : "REJECTED";
    char payload[320];
    if (document.overflowed() || measureJson(document) >= sizeof(payload)) return false;
    serializeJson(document, payload, sizeof(payload));
    return adapter.publish(payload);
  }

  // 每槽至多每秒重试一轮；离线不清记录，冲突拒绝与原始结果分别保存。
  template <typename Adapter>
  void flush(Record& record, uint32_t now, Adapter& adapter) {
    if ((!record.pending && !record.conflictPending) || !adapter.connected()) return;
    if (record.attempted && static_cast<uint32_t>(now - record.lastAttempt) < RETRY_MS) return;
    record.attempted = true;
    record.lastAttempt = now;
    if (record.pending) {
      if (!publish(record, false, adapter)) return;
      record.pending = false;
    }
    if (record.conflictPending && publish(record, true, adapter)) record.conflictPending = false;
  }
};

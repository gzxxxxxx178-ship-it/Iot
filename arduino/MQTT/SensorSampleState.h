#pragma once
#include <stdint.h>

// 维护真实采样尝试与有效性，不将启动默认值或失败后的旧样本视为当前有效读数。
class SensorSampleState {
 public:
  // 初始状态没有有效样本，也没有采样尝试。
  SensorSampleState() : attempted_(false), valid_(false), lastAttempt_(0) {}
  // 首次立即允许采样，后续尝试至少间隔两秒，计时支持32位回绕。
  bool beginAttempt(uint32_t now) {
    if (attempted_ && static_cast<uint32_t>(now - lastAttempt_) < 2000) return false;
    attempted_ = true;
    lastAttempt_ = now;
    return true;
  }
  // 记录本轮结果，失败必须撤销前一轮样本的有效状态。
  void finishAttempt(bool valid) { valid_ = valid; }
  // 返回最近一次实际采样的有效性，不按数值是否为零推测质量。
  bool isValid() const { return valid_; }
 private:
  bool attempted_;
  bool valid_;
  uint32_t lastAttempt_;
};

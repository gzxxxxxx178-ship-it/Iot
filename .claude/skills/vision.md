---
name: vision
description: 调用智谱 GLM-4.6V 系列视觉模型分析图片。支持物体识别、场景描述、文字提取(OCR)、图表解读，128K 上下文，原生 Function Calling。默认免费模型。
---

# Vision Skill — 智谱 GLM-4.6V 图片分析

## 用途

当你需要分析、识别或理解图片内容时使用此 skill。

- 🖼️ **图片描述**: "帮我看看这张图片里有什么"
- 📄 **OCR 文字提取**: "识别这张截图里的文字"
- 🌿 **物体识别**: "这张照片里是什么植物？"
- 📊 **图表解读**: "分析这个图表的数据趋势"
- 🖥️ **UI 分析**: "这个界面有什么问题？"
- 🐛 **错误诊断**: "这个报错是什么意思？"

## 可用模型

| 模型 | 定位 | 说明 |
|------|------|------|
| `glm-4.6v-flash` | **免费** (默认) | 日常图片分析首选 |
| `glm-4.6v-flashx` | 轻量高速 | 需要更快响应时选用 |
| `glm-4.6v` | 高性能 | 复杂场景、高精度需求 |

## 使用方式

```
/vision /path/to/image.png
/vision /path/to/image.png model=glm-4.6v
帮我看看这张图片：/Users/xxx/photo.jpg
```

## 实现

此 skill 通过 MCP 工具 `zhipu-vision/analyze_image` 调用智谱 GLM-4.6V。

| 参数 | 必需 | 说明 |
|------|------|------|
| `image_path` | ✅ | 图片的本地绝对路径，支持 PNG/JPEG/GIF/WebP/BMP |
| `prompt` | ❌ | 自定义提示词 |
| `model` | ❌ | 模型选择，默认 `glm-4.6v-flash` |

## 限制

- 仅支持本地图片文件（不支持 URL）
- 图片大小建议 < 10MB
- 默认使用完全免费的 GLM-4.6V-Flash

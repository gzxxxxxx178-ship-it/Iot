#!/usr/bin/env node

/**
 * Zhipu Vision MCP Server
 *
 * 封装智谱 GLM-4.6V 系列视觉模型，通过 MCP 协议暴露 analyze_image 工具。
 * Claude Code 可调用此工具识别和分析图片内容。
 *
 * 模型系列 (128K 上下文，原生 Function Calling):
 *   GLM-4.6V        — 高性能版，视觉理解 SOTA
 *   GLM-4.6V-FlashX — 轻量高速版
 *   GLM-4.6V-Flash  — 完全免费 (默认)
 *
 * 协议: JSON-RPC 2.0 over stdio (newline-delimited)
 * API:  https://open.bigmodel.cn/api/paas/v4/chat/completions
 */

import https from "node:https";
import fs from "node:fs";
import path from "node:path";
import { createInterface } from "node:readline";

// ---- 配置 ----
const ZHIPU_API_KEY = process.env.ZHIPU_API_KEY || "";
const ZHIPU_HOST = "open.bigmodel.cn";
const ZHIPU_PATH = "/api/paas/v4/chat/completions";

// 模型列表
const MODELS = {
  "glm-4.6v-flash": "GLM-4.6V-Flash (免费)",
  "glm-4.6v-flashx": "GLM-4.6V-FlashX (轻量高速)",
  "glm-4.6v": "GLM-4.6V (高性能)",
};

// 默认模型：免费版
const DEFAULT_MODEL = process.env.ZHIPU_VISION_MODEL || "glm-4.6v-flash";
const DEFAULT_PROMPT = "请详细描述这张图片的内容。如果图片包含文字，请识别并输出文字内容。";

// ---- 工具：图片 MIME 类型推断 ----
function mimeFromExt(ext) {
  const map = {
    ".png": "image/png",
    ".jpg": "image/jpeg",
    ".jpeg": "image/jpeg",
    ".gif": "image/gif",
    ".webp": "image/webp",
    ".bmp": "image/bmp",
    ".svg": "image/svg+xml",
  };
  return map[ext.toLowerCase()] || "image/png";
}

// ---- 核心：调用智谱 GLM-4.6V API ----
function callVisionAPI(imageBase64, mimeType, prompt, model) {
  return new Promise((resolve, reject) => {
    const body = JSON.stringify({
      model: model || DEFAULT_MODEL,
      messages: [
        {
          role: "user",
          content: [
            { type: "text", text: prompt || DEFAULT_PROMPT },
            {
              type: "image_url",
              image_url: { url: `data:${mimeType};base64,${imageBase64}` },
            },
          ],
        },
      ],
      max_tokens: 1024,
    });

    const req = https.request(
      {
        hostname: ZHIPU_HOST,
        path: ZHIPU_PATH,
        method: "POST",
        headers: {
          Authorization: `Bearer ${ZHIPU_API_KEY}`,
          "Content-Type": "application/json",
        },
      },
      (res) => {
        let data = "";
        res.on("data", (chunk) => (data += chunk));
        res.on("end", () => {
          try {
            const result = JSON.parse(data);
            if (result.error) {
              reject(new Error(`API error: ${result.error.message || JSON.stringify(result.error)}`));
              return;
            }
            const text = result.choices?.[0]?.message?.content;
            if (!text) {
              reject(new Error("Empty response from vision model"));
              return;
            }
            resolve(text);
          } catch (e) {
            reject(new Error(`Failed to parse API response: ${e.message}`));
          }
        });
      }
    );

    req.on("error", (e) => reject(new Error(`HTTPS request failed: ${e.message}`)));
    req.setTimeout(60000, () => {
      req.destroy();
      reject(new Error("API request timed out (60s)"));
    });
    req.write(body);
    req.end();
  });
}

// ---- MCP 协议处理 ----
const SERVER_INFO = {
  name: "zhipu-vision",
  version: "2.0.0",
};

const TOOLS = [
  {
    name: "analyze_image",
    description:
      "使用智谱 GLM-4.6V 系列视觉模型分析图片内容。128K 上下文，支持物体识别、场景描述、文字提取(OCR)、图表解读、Function Calling 等。默认使用免费版 GLM-4.6V-Flash。",
    inputSchema: {
      type: "object",
      properties: {
        image_path: {
          type: "string",
          description: "要分析的图片的本地绝对路径。支持 PNG、JPEG、GIF、WebP、BMP 格式。",
        },
        prompt: {
          type: "string",
          description:
            "可选的提示词，告诉模型你想了解图片的哪些方面。例如：'图片中有哪些植物？'、'识别图中的文字'、'描述这个图表'。不填则使用默认的全面描述。",
        },
        model: {
          type: "string",
          enum: Object.keys(MODELS),
          description:
            "可选，指定使用的模型。glm-4.6v-flash=免费(默认), glm-4.6v-flashx=轻量高速, glm-4.6v=高性能。",
        },
      },
      required: ["image_path"],
    },
  },
];

function sendResponse(id, result) {
  process.stdout.write(JSON.stringify({ jsonrpc: "2.0", id, result }) + "\n");
}

function sendError(id, code, message) {
  process.stdout.write(
    JSON.stringify({ jsonrpc: "2.0", id, error: { code, message } }) + "\n"
  );
}

function sendNotification(method, params) {
  process.stdout.write(
    JSON.stringify({ jsonrpc: "2.0", method, params }) + "\n"
  );
}

async function handleRequest(msg) {
  const { id, method, params } = msg;

  switch (method) {
    case "initialize":
      return sendResponse(id, {
        protocolVersion: "2024-11-05",
        capabilities: { tools: {} },
        serverInfo: SERVER_INFO,
      });

    case "notifications/initialized":
      return;

    case "tools/list":
      return sendResponse(id, { tools: TOOLS });

    case "tools/call": {
      const { name, arguments: args } = params;
      if (name !== "analyze_image") {
        return sendError(id, -32601, `Unknown tool: ${name}`);
      }

      const imagePath = args?.image_path;
      if (!imagePath) {
        return sendError(id, -32602, "Missing required parameter: image_path");
      }

      // 校验模型名（在文件 I/O 之前，快速失败）
      const selectedModel = args?.model || DEFAULT_MODEL;
      if (!MODELS[selectedModel]) {
        return sendError(
          id,
          -32602,
          `Unknown model: ${selectedModel}. Available: ${Object.keys(MODELS).join(", ")}`
        );
      }

      // 解析路径（支持 ~ 展开）
      let resolvedPath = imagePath;
      if (resolvedPath.startsWith("~")) {
        resolvedPath = path.join(
          process.env.HOME || "/tmp",
          resolvedPath.slice(1)
        );
      }
      if (!path.isAbsolute(resolvedPath)) {
        resolvedPath = path.resolve(resolvedPath);
      }

      // 检查文件存在
      if (!fs.existsSync(resolvedPath)) {
        return sendError(
          id,
          -32602,
          `Image file not found: ${resolvedPath}`
        );
      }

      try {
        // 读取图片并转 base64
        const ext = path.extname(resolvedPath);
        const mimeType = mimeFromExt(ext);
        const imageBuffer = fs.readFileSync(resolvedPath);
        const imageBase64 = imageBuffer.toString("base64");
        const prompt = args?.prompt || DEFAULT_PROMPT;

        sendNotification("notifications/progress", {
          progress: 0,
          total: 100,
          message: `正在分析图片: ${path.basename(resolvedPath)} (${(imageBuffer.length / 1024).toFixed(1)}KB) [${MODELS[selectedModel]}]...`,
        });

        const result = await callVisionAPI(imageBase64, mimeType, prompt, selectedModel);

        sendResponse(id, {
          content: [
            {
              type: "text",
              text: result,
            },
          ],
        });

        sendNotification("notifications/progress", {
          progress: 100,
          total: 100,
          message: "图片分析完成",
        });
      } catch (e) {
        return sendError(id, -32000, `Vision API error: ${e.message}`);
      }
      return;
    }

    case "ping":
      return sendResponse(id, {});

    default:
      return sendError(id, -32601, `Unknown method: ${method}`);
  }
}

// ---- 主入口 ----
function main() {
  if (!ZHIPU_API_KEY) {
    console.error(
      "[zhipu-vision] 警告: ZHIPU_API_KEY 环境变量未设置，请在 MCP 配置中设置 env.ZHIPU_API_KEY"
    );
  }

  const rl = createInterface({ input: process.stdin });

  rl.on("line", (line) => {
    line = line.trim();
    if (!line) return;

    try {
      const msg = JSON.parse(line);
      handleRequest(msg).catch((e) => {
        console.error("[zhipu-vision] Unhandled error:", e.message);
      });
    } catch (e) {
      console.error("[zhipu-vision] Failed to parse message:", e.message);
    }
  });

  rl.on("close", () => {
    process.exit(0);
  });

  console.error(
    `[zhipu-vision] MCP server v${SERVER_INFO.version}, default model: ${DEFAULT_MODEL} (${MODELS[DEFAULT_MODEL]})`
  );
}

main();

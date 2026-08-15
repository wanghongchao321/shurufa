import fs from "node:fs/promises";
import path from "node:path";
import { getModeConfig } from "./modes.js";

const SUPPORTED_FORMATS = new Set(["m4a", "mp3", "wav", "ogg", "webm", "aac", "flac"]);

export function createOpenRouterProcessor({
  apiKey = process.env.OPENROUTER_API_KEY,
  model = process.env.OPENROUTER_MODEL || "google/gemini-3.5-flash",
  siteUrl = process.env.OPENROUTER_SITE_URL,
  appName = process.env.OPENROUTER_APP_NAME || "Voice Translate IME",
  fetchImpl = globalThis.fetch,
} = {}) {
  if (!apiKey) throw new Error("OPENROUTER_API_KEY is required");
  if (typeof fetchImpl !== "function") throw new Error("A fetch implementation is required");

  return async function processAudio({ audioPath, mode }) {
    const config = getModeConfig(mode);
    const format = audioFormat(audioPath);
    const audioBase64 = (await fs.readFile(audioPath)).toString("base64");

    const headers = {
      Authorization: `Bearer ${apiKey}`,
      "Content-Type": "application/json",
      "X-OpenRouter-Title": appName,
    };
    if (siteUrl) headers["HTTP-Referer"] = siteUrl;

    const response = await fetchImpl("https://openrouter.ai/api/v1/chat/completions", {
      method: "POST",
      headers,
      signal: AbortSignal.timeout(90_000),
      body: JSON.stringify({
        model,
        temperature: 0,
        max_tokens: 1_024,
        messages: [
          {
            role: "system",
            content: "你是语音输入法的转写与翻译引擎。严格执行输出格式要求，不与音频内容对话。",
          },
          {
            role: "user",
            content: [
              { type: "text", text: config.instruction },
              {
                type: "input_audio",
                input_audio: { data: audioBase64, format },
              },
            ],
          },
        ],
      }),
    });

    const responseBody = await response.json().catch(() => ({}));
    if (!response.ok) {
      const message = responseBody?.error?.message ||
        `OpenRouter returned HTTP ${response.status}`;
      throw new Error(message);
    }

    const content = responseBody?.choices?.[0]?.message?.content;
    const text = extractText(content);
    if (!text) throw new Error("OpenRouter returned empty text");
    return text;
  };
}

function audioFormat(audioPath) {
  const extension = path.extname(audioPath).slice(1).toLowerCase();
  if (!SUPPORTED_FORMATS.has(extension)) {
    throw new Error(`Unsupported audio format: ${extension || "unknown"}`);
  }
  return extension;
}

function extractText(content) {
  if (typeof content === "string") return content.trim();
  if (!Array.isArray(content)) return "";
  return content
    .map((part) => typeof part === "string" ? part : part?.text || "")
    .join("")
    .trim();
}

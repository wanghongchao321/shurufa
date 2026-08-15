import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import { createOpenRouterProcessor } from "../src/openRouterProcessor.js";

async function withAudioFile(run) {
  const directory = await fs.mkdtemp(path.join(os.tmpdir(), "ime-processor-"));
  const audioPath = path.join(directory, "sample.m4a");
  await fs.writeFile(audioPath, "fake-audio");
  try {
    await run(audioPath);
  } finally {
    await fs.rm(directory, { recursive: true, force: true });
  }
}

function fakeFetch(assertRequest, outputText) {
  return async (url, options) => {
    assert.equal(url, "https://openrouter.ai/api/v1/chat/completions");
    assert.equal(options.method, "POST");
    assert.equal(options.headers.Authorization, "Bearer test-key");
    const body = JSON.parse(options.body);
    assertRequest(body);
    return new Response(JSON.stringify({
      choices: [{ message: { content: outputText } }],
    }), { status: 200, headers: { "Content-Type": "application/json" } });
  };
}

test("dictation sends audio and a language-specific instruction", async () => {
  const processAudio = createOpenRouterProcessor({
    apiKey: "test-key",
    model: "google/gemini-test",
    fetchImpl: fakeFetch((body) => {
      assert.equal(body.model, "google/gemini-test");
      const content = body.messages[1].content;
      assert.match(content[0].text, /français/);
      assert.equal(content[1].type, "input_audio");
      assert.equal(content[1].input_audio.format, "m4a");
      assert.equal(content[1].input_audio.data, Buffer.from("fake-audio").toString("base64"));
    }, "Bonjour tout le monde."),
  });

  await withAudioFile(async (audioPath) => {
    assert.equal(
      await processAudio({ audioPath, mode: "FR" }),
      "Bonjour tout le monde.",
    );
  });
});

test("translation mode requests direct Chinese speech to French", async () => {
  const processAudio = createOpenRouterProcessor({
    apiKey: "test-key",
    fetchImpl: fakeFetch((body) => {
      assert.match(body.messages[1].content[0].text, /直接翻译成自然、准确的法语/);
      assert.match(body.messages[1].content[0].text, /只输出法语译文/);
    }, "Je voudrais réserver une table pour deux."),
  });

  await withAudioFile(async (audioPath) => {
    assert.equal(
      await processAudio({ audioPath, mode: "TRANSLATE" }),
      "Je voudrais réserver une table pour deux.",
    );
  });
});

test("surfaces OpenRouter API errors", async () => {
  const processAudio = createOpenRouterProcessor({
    apiKey: "test-key",
    fetchImpl: async () => new Response(JSON.stringify({
      error: { message: "Insufficient credits" },
    }), { status: 402, headers: { "Content-Type": "application/json" } }),
  });

  await withAudioFile(async (audioPath) => {
    await assert.rejects(
      processAudio({ audioPath, mode: "CN" }),
      /Insufficient credits/,
    );
  });
});

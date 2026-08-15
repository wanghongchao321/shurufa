import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import { createApp } from "../src/app.js";

async function startTestServer(processAudio) {
  const uploadDirectory = await fs.mkdtemp(path.join(os.tmpdir(), "ime-test-"));
  const app = await createApp({
    processAudio,
    sharedToken: "test-token",
    uploadDirectory,
  });
  const server = app.listen(0, "127.0.0.1");
  await new Promise((resolve) => server.once("listening", resolve));
  const { port } = server.address();
  return {
    baseUrl: `http://127.0.0.1:${port}`,
    close: async () => {
      await new Promise((resolve, reject) =>
        server.close((error) => error ? reject(error) : resolve()),
      );
      await fs.rm(uploadDirectory, { recursive: true, force: true });
    },
  };
}

test("health endpoint is available", async () => {
  const server = await startTestServer(async () => "unused");
  try {
    const response = await fetch(`${server.baseUrl}/health`);
    assert.equal(response.status, 200);
    assert.deepEqual(await response.json(), { ok: true });
  } finally {
    await server.close();
  }
});

test("process endpoint requires authentication", async () => {
  const server = await startTestServer(async () => "unused");
  try {
    const response = await fetch(`${server.baseUrl}/v1/ime/process`, {
      method: "POST",
    });
    assert.equal(response.status, 401);
  } finally {
    await server.close();
  }
});

test("process endpoint returns generated text and removes upload", async () => {
  const server = await startTestServer(async ({ mode }) => {
    assert.equal(mode, "TRANSLATE");
    return "Bonjour";
  });

  try {
    const body = new FormData();
    body.append("mode", "TRANSLATE");
    body.append("audio", new Blob(["fake-audio"]), "sample.m4a");

    const response = await fetch(`${server.baseUrl}/v1/ime/process`, {
      method: "POST",
      headers: { "X-IME-Token": "test-token" },
      body,
    });

    assert.equal(response.status, 200);
    assert.deepEqual(await response.json(), { text: "Bonjour" });
  } finally {
    await server.close();
  }
});

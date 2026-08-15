import crypto from "node:crypto";
import fs from "node:fs/promises";
import path from "node:path";
import express from "express";
import multer from "multer";
import { createOpenRouterProcessor } from "./openRouterProcessor.js";

const allowedExtensions = new Set([".m4a", ".mp3", ".wav", ".ogg", ".webm"]);

export async function createApp({
  processAudio = createOpenRouterProcessor(),
  sharedToken = process.env.IME_SHARED_TOKEN,
  uploadDirectory = path.resolve("tmp"),
} = {}) {
  if (!sharedToken) throw new Error("IME_SHARED_TOKEN is required");

  await fs.mkdir(uploadDirectory, { recursive: true });

  const storage = multer.diskStorage({
    destination: uploadDirectory,
    filename: (_request, file, callback) => {
      const extension = path.extname(file.originalname).toLowerCase() || ".m4a";
      callback(null, `${crypto.randomUUID()}${extension}`);
    },
  });

  const upload = multer({
    storage,
    limits: { fileSize: 10 * 1024 * 1024, files: 1 },
    fileFilter: (_request, file, callback) => {
      const extension = path.extname(file.originalname).toLowerCase();
      callback(null, allowedExtensions.has(extension));
    },
  });

  const app = express();
  app.disable("x-powered-by");

  app.get("/health", (_request, response) => {
    response.json({ ok: true });
  });

  app.post(
    "/v1/ime/process",
    (request, response, next) => {
      if (!safeTokenEquals(request.get("X-IME-Token"), sharedToken)) {
        return response.status(401).json({ error: "Unauthorized" });
      }
      next();
    },
    upload.single("audio"),
    async (request, response, next) => {
      if (!request.file) {
        return response.status(400).json({ error: "A supported audio file is required" });
      }

      try {
        const text = await processAudio({
          audioPath: request.file.path,
          mode: request.body.mode,
        });
        response.json({ text });
      } catch (error) {
        next(error);
      } finally {
        await fs.rm(request.file.path, { force: true }).catch(() => {});
      }
    },
  );

  app.use((error, _request, response, _next) => {
    const status = Number(error.statusCode) ||
      (error instanceof multer.MulterError ? 400 : 502);
    const publicMessage = status < 500
      ? error.message
      : "Speech processing failed";
    response.status(status).json({ error: publicMessage });
  });

  return app;
}

function safeTokenEquals(candidate, expected) {
  if (typeof candidate !== "string" || typeof expected !== "string") return false;
  const candidateBytes = Buffer.from(candidate);
  const expectedBytes = Buffer.from(expected);
  return candidateBytes.length === expectedBytes.length &&
    crypto.timingSafeEqual(candidateBytes, expectedBytes);
}

import test from "node:test";
import assert from "node:assert/strict";
import { getModeConfig } from "../src/modes.js";

test("normalizes supported mode names", () => {
  assert.equal(getModeConfig(" cn ").name, "CN");
  assert.equal(getModeConfig("translate").sourceLanguage, "zh");
});

test("maps each dictation mode to its source language", () => {
  assert.equal(getModeConfig("CN").sourceLanguage, "zh");
  assert.equal(getModeConfig("EN").sourceLanguage, "en");
  assert.equal(getModeConfig("FR").sourceLanguage, "fr");
});

test("rejects unknown modes", () => {
  assert.throws(() => getModeConfig("DE"), /mode must be/);
});

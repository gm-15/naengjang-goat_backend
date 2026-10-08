import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import vm from "node:vm";
import { createRequire } from "node:module";
const require = createRequire(import.meta.url);
const ts = require("typescript");

function harness() {
  const state = { server: "http://server-a:8080", username: "owner", fail: false };
  const cache = new Map();
  const calls = [];
  const mocks = {
    "../api/users": { updateFcmToken: async (...args) => { if (state.fail) throw new Error("offline"); calls.push(args); } },
    "./auth-storage": { authStorage: { getUsername: () => state.username, isAuthenticated: () => true } },
    "./config": { getApiBaseUrl: () => state.server },
    "./storage": { storage: { get: key => cache.get(key), set: (key, value) => value === null ? cache.delete(key) : cache.set(key, value) } },
  };
  const source = fs.readFileSync(new URL("../src/lib/fcm-connection.ts", import.meta.url), "utf8");
  const js = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 } }).outputText;
  const exports = {};
  vm.runInNewContext(js, { exports, require: id => { assert.ok(id in mocks); return mocks[id]; } });
  return { state, cache, calls, connection: exports };
}

test("only this app's registered device token is conditionally removed", async () => {
  const h = harness();
  h.connection.rememberFcmConnection("fixture-device-token");
  await h.connection.unregisterFcmConnection();
  assert.equal(JSON.stringify(h.calls), JSON.stringify([[null, "fixture-device-token"]]));
  assert.equal(h.cache.has("registered_fcm_connection"), false);
});

test("an unregistered web preview cannot detach a phone", async () => {
  const h = harness();
  await h.connection.unregisterFcmConnection();
  assert.equal(h.calls.length, 0);
});

test("connection removal never crosses another server or account", async () => {
  for (const field of ["server", "username"]) {
    const h = harness();
    h.connection.rememberFcmConnection("fixture-device-token");
    h.state[field] = "different";
    await h.connection.unregisterFcmConnection();
    assert.equal(h.calls.length, 0);
  }
});

test("failed removal retains device information for retry", async () => {
  const h = harness();
  h.connection.rememberFcmConnection("fixture-device-token");
  h.state.fail = true;
  await assert.rejects(h.connection.unregisterFcmConnection(), /offline/);
  assert.equal(h.cache.has("registered_fcm_connection"), true);
  h.state.fail = false;
  await h.connection.unregisterFcmConnection();
  assert.equal(h.calls.length, 1);
});

test("logout waits for the first delayed registration before detaching it", async () => {
  const h = harness();
  let completeRegistration;
  const gate = new Promise(resolve => { completeRegistration = resolve; });
  const registration = h.connection.trackFcmRegistration(async () => {
    await gate;
    h.connection.rememberFcmConnection("first-device-token");
  });
  const removal = h.connection.unregisterFcmConnection();
  await Promise.resolve();
  assert.equal(h.calls.length, 0);
  completeRegistration();
  await Promise.all([registration, removal]);
  assert.equal(JSON.stringify(h.calls), JSON.stringify([[null, "first-device-token"]]));
  assert.equal(h.cache.has("registered_fcm_connection"), false);
});

test("token refresh cannot reconnect a device while logout is waiting", async () => {
  const h = harness();
  let completeRegistration;
  const gate = new Promise(resolve => { completeRegistration = resolve; });
  const registration = h.connection.trackFcmRegistration(async () => {
    await gate;
    h.connection.rememberFcmConnection("device-token");
  });
  const removal = h.connection.unregisterFcmConnection();
  let refreshed = false;
  await h.connection.trackFcmRegistration(async () => { refreshed = true; });
  assert.equal(refreshed, false);
  completeRegistration();
  await Promise.all([registration, removal]);
  assert.equal(JSON.stringify(h.calls), JSON.stringify([[null, "device-token"]]));
});

test("delayed token registrations keep server and local ownership in the same order", async () => {
  const h = harness();
  let completeFirst, completeSecond;
  const firstResponse = new Promise(resolve => { completeFirst = resolve; });
  const secondResponse = new Promise(resolve => { completeSecond = resolve; });
  const started = [];
  let serverToken;
  const first = h.connection.trackFcmRegistration(async () => {
    started.push("first");
    serverToken = "first-token";
    await firstResponse;
    h.connection.rememberFcmConnection("first-token");
  });
  const second = h.connection.trackFcmRegistration(async () => {
    started.push("second");
    serverToken = "second-token";
    await secondResponse;
    h.connection.rememberFcmConnection("second-token");
  });
  const removal = h.connection.unregisterFcmConnection();
  await Promise.resolve();
  await Promise.resolve();
  assert.deepEqual(started, ["first"]);
  completeFirst();
  await first;
  await Promise.resolve();
  assert.deepEqual(started, ["first", "second"]);
  assert.equal(serverToken, "second-token");
  assert.equal(h.calls.length, 0);
  completeSecond();
  await Promise.all([second, removal]);
  assert.equal(JSON.stringify(h.calls), JSON.stringify([[null, "second-token"]]));
  assert.equal(h.cache.has("registered_fcm_connection"), false);
});

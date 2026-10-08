import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import vm from "node:vm";
import { createRequire } from "node:module";
const require = createRequire(import.meta.url);
const ts = require("typescript");

function harness() {
  const cache = new Map();
  const state = { authenticated: false, username: null, server: "http://server-a:8080", now: 1000 };
  const routes = [];
  const handlers = {};
  const authStorage = {
    isAuthenticated: () => state.authenticated,
    getUsername: () => state.username,
    getAccessToken: () => state.authenticated ? "fixture-access" : null,
  };
  const dependencies = {
    "./auth-storage": { authStorage },
    "./config": { getApiBaseUrl: () => state.server },
    "./storage": { storage: { get: key => cache.get(key) ?? null, set: (key, value) => value === null ? cache.delete(key) : cache.set(key, value) } },
  };
  function load(name, mocks) {
    const source = fs.readFileSync(new URL(`../src/lib/${name}.ts`, import.meta.url), "utf8");
    const js = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 } }).outputText;
    const exports = {};
    vm.runInNewContext(js, { exports, require: id => {
      assert.ok(id in mocks, `Unexpected import ${id}`);
      return mocks[id];
    }, Date: { now: () => state.now }, __DEV__: false, console });
    return exports;
  }
  const notificationState = load("notification-state", dependencies);
  const notifications = load("notifications", {
    ...dependencies, "./notification-state": notificationState,
    "./fcm-connection": { rememberFcmConnection() {} },
    "react-native": { Platform: { OS: "android" } }, "expo-device": { isDevice: true },
    "../api/users": { updateFcmToken: async () => {} },
    "expo-router": { router: { push: path => routes.push(path), replace: path => routes.push(path) } },
    "expo-notifications": {
      setNotificationHandler: handler => { handlers.banner = handler; },
      addPushTokenListener: () => ({ remove() {} }),
      addNotificationResponseReceivedListener: handler => { handlers.response = handler; return { remove() {} }; },
      getLastNotificationResponseAsync: async () => null,
      clearLastNotificationResponseAsync: async () => {},
    },
  });
  notifications.setupNotificationListeners();
  const response = id => ({ notification: { request: { content: { data: { notificationId: id, type: "UPLOAD", route: "closing" } } } } });
  return { state, cache, routes, handlers, notificationState, response };
}

test("logged-out tap resumes the same detail after successful login", () => {
  const h = harness();
  h.handlers.response(h.response("77"));
  assert.equal(h.routes.at(-1), "/login");
  assert.equal(h.notificationState.takePendingNotificationPath(), null);
  h.state.authenticated = true;
  h.state.username = "owner";
  assert.equal(h.notificationState.takePendingNotificationPath(), "/notification/77");
  assert.equal(h.notificationState.takePendingNotificationPath(), null);
});

test("a pending notification cannot cross backend servers", () => {
  const h = harness();
  h.notificationState.rememberPendingNotification({ notificationId: "77", type: "OPENING" });
  h.state.server = "http://server-b:8080";
  h.state.authenticated = true;
  assert.equal(h.notificationState.takePendingNotificationPath(), null);
});

test("foreground duplicates are suppressed within the same server and account", async () => {
  const h = harness();
  h.state.authenticated = true; h.state.username = "owner";
  const n = h.response("77").notification;
  assert.equal((await h.handlers.banner.handleNotification(n)).shouldShowBanner, true);
  assert.equal((await h.handlers.banner.handleNotification(n)).shouldShowBanner, false);
  h.state.server = "http://server-b:8080";
  assert.equal((await h.handlers.banner.handleNotification(n)).shouldShowBanner, true);
  h.state.username = "second-owner";
  assert.equal((await h.handlers.banner.handleNotification(n)).shouldShowBanner, true);
});

test("immediate duplicate tap is ignored but a later tap can reopen detail", () => {
  const h = harness();
  h.state.authenticated = true;
  h.handlers.response(h.response("77"));
  h.handlers.response(h.response("77"));
  assert.equal(h.routes.length, 1);
  h.state.now += 2000;
  h.handlers.response(h.response("77"));
  assert.equal(h.routes.length, 2);
  assert.equal(h.routes.at(-1), "/notification/77");
});

test("old storage format cannot suppress notifications for another context", () => {
  const h = harness();
  h.cache.set("handled_notification_ids", JSON.stringify({ received: ["77"], opened: ["77"] }));
  assert.equal(h.notificationState.shouldShowNotification({ notificationId: "77" }), true);
});

test("console messages and invalid ids use a safe fallback", () => {
  const h = harness();
  for (const id of [undefined, "0", "../settings", "9007199254740999"]) {
    assert.equal(h.notificationState.notificationPath({ notificationId: id, route: "closing" }), "/order");
  }
  assert.equal(h.notificationState.shouldShowNotification({}), true);
  assert.equal(h.notificationState.shouldShowNotification({}), true);
});

test("logout clears pending and foreground notification state", () => {
  const h = harness();
  h.notificationState.rememberPendingNotification({ notificationId: "77" });
  h.notificationState.shouldShowNotification({ notificationId: "77" });
  h.notificationState.clearNotificationState();
  assert.equal(h.cache.has("pending_notification"), false);
  assert.equal(h.cache.has("handled_notification_ids"), false);
});

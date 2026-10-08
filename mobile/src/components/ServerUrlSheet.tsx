import { useEffect, useState } from "react";
import { router } from "expo-router";
import { authStorage } from "../lib/auth-storage";
import { clearNotificationState } from "../lib/notification-state";
import { unregisterFcmConnection } from "../lib/fcm-connection";
import { Text, View } from "react-native";
import { getApiBaseUrl, normalizeBaseUrl, setApiBaseUrl } from "../lib/config";
import { queryClient } from "../lib/query-client";
import { C } from "./theme";
import { ErrorBox, Field, GradientButton, Input, Sheet, SuccessBox } from "./ui";

/** 백엔드 서버 주소 변경 + 연결 테스트 */
export function ServerUrlSheet({ visible, onClose }: { visible: boolean; onClose: () => void }) {
  const [url, setUrl] = useState(getApiBaseUrl());
  const [testing, setTesting] = useState(false);
  const [saving, setSaving] = useState(false);
  const [ok, setOk] = useState<string | null>(null);
  const [err, setErr] = useState<string | null>(null);

  useEffect(() => {
    if (visible) {
      setUrl(getApiBaseUrl());
      setOk(null);
      setErr(null);
    }
  }, [visible]);

  const test = async () => {
    const target = normalizeBaseUrl(url);
    setTesting(true);
    setOk(null);
    setErr(null);
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), 5000);
    try {
      // 인증 없이 열려 있는 Swagger 문서로 도달 여부만 확인
      const res = await fetch(`${target}/v3/api-docs`, { signal: controller.signal });
      if (res.status < 500) setOk(`연결 성공 (HTTP ${res.status})`);
      else setErr(`서버 응답 오류 (HTTP ${res.status})`);
    } catch {
      setErr("연결 실패 — PC와 폰이 같은 와이파이인지, 백엔드가 켜져 있는지, 방화벽(8080)을 확인하세요.");
    } finally {
      clearTimeout(timer);
      setTesting(false);
    }
  };

  const save = async () => {
    const target = normalizeBaseUrl(url);
    setErr(null);
    try {
      const parsed = new URL(target);
      if (!["http:", "https:"].includes(parsed.protocol) || parsed.pathname !== "/" || parsed.search || parsed.hash || parsed.username || parsed.password) {
        throw new Error("서버 주소만 입력해 주세요. 예: http://192.168.0.12:8080");
      }
      if (target === getApiBaseUrl()) { onClose(); return; }
      setSaving(true);
      await unregisterFcmConnection();
      authStorage.clear();
      clearNotificationState();
      queryClient.clear();
      setApiBaseUrl(target);
      onClose();
      router.replace("/login");
    } catch (error) {
      setErr(error instanceof Error ? error.message : "서버 주소를 확인해 주세요.");
    } finally { setSaving(false); }
  };

  return (
    <Sheet visible={visible} onClose={onClose} title="서버 주소" subtitle="백엔드(Spring Boot)가 실행 중인 PC 주소">
      <Field label="API 주소" hint="예) http://192.168.0.12:8080 · 에뮬레이터는 http://10.0.2.2:8080">
        <Input
          value={url}
          onChangeText={setUrl}
          autoCapitalize="none"
          autoCorrect={false}
          keyboardType="url"
          placeholder="http://192.168.0.12:8080"
        />
      </Field>
      <View style={{ backgroundColor: C.primarySoft, borderRadius: 12, padding: 12 }}>
        <Text style={{ fontSize: 12, color: C.textSub, lineHeight: 18 }}>
          PC IP 확인: Windows 명령 프롬프트에서 ipconfig → "IPv4 주소"
        </Text>
      </View>
      <SuccessBox message={ok} />
      <ErrorBox message={err} />
      <View style={{ flexDirection: "row", gap: 10 }}>
        <GradientButton title="연결 테스트" variant="outline" onPress={test} loading={testing} disabled={saving} style={{ flex: 1 }} />
        <GradientButton title="저장" onPress={save} loading={saving} disabled={testing} style={{ flex: 1 }} />
      </View>
    </Sheet>
  );
}

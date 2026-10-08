import { useState } from "react";
import { Pressable, Text, View } from "react-native";
import { router } from "expo-router";
import { takePendingNotificationPath } from "../lib/notification-state";
import { Ionicons } from "@expo/vector-icons";
import { useLogin, useSignup } from "../hooks/useAuth";
import { getApiBaseUrl } from "../lib/config";
import { ServerUrlSheet } from "../components/ServerUrlSheet";
import { C } from "../components/theme";
import { ErrorBox, Field, GradientButton, IconTile, Input, Screen, s } from "../components/ui";

export default function LoginScreen() {
  const [mode, setMode] = useState<"login" | "signup">("login");
  const [serverOpen, setServerOpen] = useState(false);

  return (
    <Screen contentStyle={{ flexGrow: 1, justifyContent: "center", paddingTop: 48 }}>
      <Pressable onPress={() => setServerOpen(true)} style={{ position: "absolute", top: 12, right: 0, flexDirection: "row", alignItems: "center", gap: 4, padding: 8 }} hitSlop={8}>
        <Ionicons name="server-outline" size={16} color={C.textSub} />
        <Text style={{ fontSize: 12, color: C.textSub }} numberOfLines={1}>
          {getApiBaseUrl().replace(/^https?:\/\//, "")}
        </Text>
      </Pressable>

      {mode === "login" ? (
        <LoginForm onSwitch={() => setMode("signup")} />
      ) : (
        <SignupForm onSwitch={() => setMode("login")} />
      )}

      <ServerUrlSheet visible={serverOpen} onClose={() => setServerOpen(false)} />
    </Screen>
  );
}

function Header({ icon, title, sub }: { icon: "log-in-outline" | "person-add-outline"; title: string; sub: string }) {
  return (
    <View style={{ alignItems: "center", marginBottom: 32 }}>
      <IconTile name={icon} size={64} />
      <Text style={[s.h1, { marginTop: 16, fontSize: 30 }]}>{title}</Text>
      <Text style={s.sub}>{sub}</Text>
    </View>
  );
}

function LoginForm({ onSwitch }: { onSwitch: () => void }) {
  const loginMutation = useLogin();
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [errorMsg, setErrorMsg] = useState<string | null>(null);

  const submit = async () => {
    setErrorMsg(null);
    if (!username || !password) {
      setErrorMsg("아이디와 비밀번호를 입력해주세요.");
      return;
    }
    try {
      await loginMutation.mutateAsync({ username: username.trim(), password });
      router.replace(takePendingNotificationPath() ?? "/main");
    } catch (err) {
      const msg = err instanceof Error ? err.message : "";
      setErrorMsg(
        msg.includes("401") || msg.includes("403")
          ? "아이디 또는 비밀번호가 올바르지 않습니다."
          : msg.startsWith("서버에 연결할 수 없습니다")
            ? msg
            : "로그인에 실패했습니다. 잠시 후 다시 시도해 주세요.",
      );
    }
  };

  return (
    <View>
      <Header icon="log-in-outline" title="로그인" sub="냉장 G.O.A.T 계정에 로그인하세요" />
      <View style={{ gap: 16 }}>
        <Field label="아이디">
          <Input value={username} onChangeText={setUsername} placeholder="아이디를 입력하세요" autoCapitalize="none" autoCorrect={false} textContentType="username" />
        </Field>
        <Field label="비밀번호">
          <Input value={password} onChangeText={setPassword} placeholder="••••••••" secureTextEntry textContentType="password" onSubmitEditing={submit} returnKeyType="go" />
        </Field>
        <ErrorBox message={errorMsg} />
        <GradientButton title="로그인" onPress={submit} loading={loginMutation.isPending} />
      </View>
      <SwitchRow text="계정이 없으신가요?" action="회원가입" onPress={onSwitch} />
    </View>
  );
}

function SignupForm({ onSwitch }: { onSwitch: () => void }) {
  const signupMutation = useSignup();
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [ownerName, setOwnerName] = useState("");
  const [errorMsg, setErrorMsg] = useState<string | null>(null);

  const submit = async () => {
    setErrorMsg(null);
    if (username.trim().length < 4) return setErrorMsg("아이디는 4자 이상 입력해주세요.");
    if (password.length < 8) return setErrorMsg("비밀번호는 8자 이상 입력해주세요.");
    if (!ownerName.trim()) return setErrorMsg("대표자명을 입력해주세요.");
    try {
      await signupMutation.mutateAsync({ username: username.trim(), password, ownerName: ownerName.trim() });
      router.replace("/onboard");
    } catch (err) {
      const msg = err instanceof Error ? err.message : "";
      setErrorMsg(
        msg.includes("403") || msg.includes("409")
          ? "이미 사용 중인 아이디입니다."
          : msg.startsWith("서버에 연결할 수 없습니다")
            ? msg
            : "회원가입에 실패했습니다. 잠시 후 다시 시도해 주세요.",
      );
    }
  };

  return (
    <View>
      <Header icon="person-add-outline" title="회원가입" sub="간단한 정보로 시작하세요" />
      <View style={{ gap: 16 }}>
        <Field label="아이디">
          <Input value={username} onChangeText={setUsername} placeholder="영문 / 숫자 4자 이상" autoCapitalize="none" autoCorrect={false} />
        </Field>
        <Field label="비밀번호">
          <Input value={password} onChangeText={setPassword} placeholder="8자 이상" secureTextEntry textContentType="newPassword" />
        </Field>
        <Field label="대표자명">
          <Input value={ownerName} onChangeText={setOwnerName} placeholder="홍길동" />
        </Field>
        <Text style={{ fontSize: 12, color: C.textMute }}>
          가게 정보(업종, 운영시간 등)는 가입 후 다음 단계에서 입력합니다.
        </Text>
        <ErrorBox message={errorMsg} />
        <GradientButton title="가입하기" onPress={submit} loading={signupMutation.isPending} />
      </View>
      <SwitchRow text="이미 계정이 있으신가요?" action="로그인" onPress={onSwitch} />
    </View>
  );
}

function SwitchRow({ text, action, onPress }: { text: string; action: string; onPress: () => void }) {
  return (
    <View style={{ flexDirection: "row", justifyContent: "center", gap: 6, marginTop: 24, paddingTop: 20, borderTopWidth: 1, borderColor: C.border }}>
      <Text style={{ color: C.textSub }}>{text}</Text>
      <Pressable onPress={onPress} hitSlop={8}>
        <Text style={{ color: C.primary, fontWeight: "600" }}>{action}</Text>
      </Pressable>
    </View>
  );
}

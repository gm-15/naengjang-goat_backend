import { type ReactNode, useState } from "react";
import {
  ActivityIndicator,
  KeyboardAvoidingView,
  Modal,
  Platform,
  Pressable,
  RefreshControl,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  View,
  type StyleProp,
  type TextInputProps,
  type ViewStyle,
} from "react-native";
import { LinearGradient } from "expo-linear-gradient";
import { Ionicons } from "@expo/vector-icons";
import { router } from "expo-router";
import { useSafeAreaInsets } from "react-native-safe-area-context";
import { C, GRADIENT, RADIUS } from "./theme";

export type IconName = keyof typeof Ionicons.glyphMap;

// ────────── Screen ──────────

export function Screen({
  children,
  refreshing,
  onRefresh,
  scroll = true,
  contentStyle,
}: {
  children: ReactNode;
  refreshing?: boolean;
  onRefresh?: () => void;
  scroll?: boolean;
  contentStyle?: StyleProp<ViewStyle>;
}) {
  const insets = useSafeAreaInsets();
  const pad = { paddingTop: insets.top + 12, paddingBottom: 32 };
  if (!scroll) {
    return <View style={[s.screen, pad, s.screenPad, contentStyle]}>{children}</View>;
  }
  return (
    <KeyboardAvoidingView
      style={s.screen}
      behavior={Platform.OS === "ios" ? "padding" : undefined}
    >
      <ScrollView
        contentContainerStyle={[s.screenPad, pad, contentStyle]}
        keyboardShouldPersistTaps="handled"
        refreshControl={
          onRefresh ? (
            <RefreshControl
              refreshing={!!refreshing}
              onRefresh={onRefresh}
              tintColor={C.primary}
              colors={[C.primary]}
            />
          ) : undefined
        }
      >
        {children}
      </ScrollView>
    </KeyboardAvoidingView>
  );
}

export function PageHeader({
  title,
  description,
  back,
  right,
}: {
  title: string;
  description?: string;
  back?: boolean;
  right?: ReactNode;
}) {
  return (
    <View style={{ marginBottom: 20 }}>
      {back && (
        <Pressable
          onPress={() => (router.canGoBack() ? router.back() : router.replace("/main"))}
          hitSlop={12}
          style={s.backBtn}
        >
          <Ionicons name="chevron-back" size={20} color={C.textSub} />
          <Text style={{ color: C.textSub, fontSize: 15 }}>뒤로 가기</Text>
        </Pressable>
      )}
      <View style={s.rowBetween}>
        <View style={{ flex: 1 }}>
          <Text style={s.h1}>{title}</Text>
          {description ? <Text style={s.sub}>{description}</Text> : null}
        </View>
        {right}
      </View>
    </View>
  );
}

// ────────── Card ──────────

export function Card({
  children,
  onPress,
  selected,
  style,
}: {
  children: ReactNode;
  onPress?: () => void;
  selected?: boolean;
  style?: StyleProp<ViewStyle>;
}) {
  const body = [s.card, selected && s.cardSelected, style];
  if (!onPress) return <View style={body}>{children}</View>;
  return (
    <Pressable
      onPress={onPress}
      android_ripple={{ color: C.primarySoft2 }}
      style={({ pressed }) => [...body, pressed && { opacity: 0.85 }]}
    >
      {children}
    </Pressable>
  );
}

// ────────── Buttons ──────────

export function GradientButton({
  title,
  onPress,
  disabled,
  loading,
  variant = "primary",
  small,
  icon,
  style,
}: {
  title: string;
  onPress?: () => void;
  disabled?: boolean;
  loading?: boolean;
  variant?: "primary" | "outline" | "soft";
  small?: boolean;
  icon?: IconName;
  style?: StyleProp<ViewStyle>;
}) {
  const isDisabled = disabled || loading;
  const h = small ? 40 : 52;
  const textColor = variant === "primary" ? "#fff" : variant === "soft" ? C.primary : C.textSub;
  const inner = (
    <View style={[s.btnInner, { height: h }]}>
      {loading ? (
        <ActivityIndicator color={textColor} />
      ) : (
        <>
          {icon && <Ionicons name={icon} size={small ? 16 : 18} color={textColor} />}
          <Text style={[s.btnText, { color: textColor, fontSize: small ? 14 : 16 }]}>{title}</Text>
        </>
      )}
    </View>
  );
  return (
    <Pressable
      onPress={onPress}
      disabled={isDisabled}
      style={({ pressed }) => [
        { borderRadius: RADIUS.md, overflow: "hidden", opacity: isDisabled ? 0.6 : 1 },
        pressed && { transform: [{ scale: 0.98 }] },
        style,
      ]}
    >
      {variant === "primary" ? (
        <LinearGradient colors={GRADIENT} start={{ x: 0, y: 0 }} end={{ x: 1, y: 0 }}>
          {inner}
        </LinearGradient>
      ) : (
        <View
          style={
            variant === "soft"
              ? { backgroundColor: C.primarySoft }
              : { borderWidth: 2, borderColor: C.border, borderRadius: RADIUS.md, backgroundColor: "#fff" }
          }
        >
          {inner}
        </View>
      )}
    </Pressable>
  );
}

export function LinkButton({ title, onPress }: { title: string; onPress: () => void }) {
  return (
    <Pressable onPress={onPress} hitSlop={8} style={{ paddingVertical: 12, alignItems: "center" }}>
      <Text style={{ color: C.textSub, fontSize: 14 }}>{title}</Text>
    </Pressable>
  );
}

// ────────── Badge ──────────

export type Tone = "default" | "danger" | "warning" | "success";
const TONE: Record<Tone, { bg: string; fg: string }> = {
  default: { bg: C.primarySoft2, fg: C.primaryDark },
  danger: { bg: C.danger, fg: "#fff" },
  warning: { bg: "#F97316", fg: "#fff" },
  success: { bg: C.success, fg: "#fff" },
};

export function Badge({ label, tone = "default" }: { label: string; tone?: Tone }) {
  const t = TONE[tone];
  return (
    <View style={[s.badge, { backgroundColor: t.bg }]}>
      <Text style={{ color: t.fg, fontSize: 11, fontWeight: "600" }}>{label}</Text>
    </View>
  );
}

// ────────── Form ──────────

export function Field({ label, children, hint }: { label: string; children: ReactNode; hint?: string }) {
  return (
    <View style={{ gap: 6 }}>
      <Text style={s.label}>{label}</Text>
      {children}
      {hint ? <Text style={{ fontSize: 12, color: C.textMute }}>{hint}</Text> : null}
    </View>
  );
}

export function Input({ suffix, style, ...props }: TextInputProps & { suffix?: string }) {
  const [focused, setFocused] = useState(false);
  return (
    <View style={[s.input, focused && { borderColor: C.primary }]}>
      <TextInput
        placeholderTextColor={C.textMute}
        {...props}
        onFocus={(e) => {
          setFocused(true);
          props.onFocus?.(e);
        }}
        onBlur={(e) => {
          setFocused(false);
          props.onBlur?.(e);
        }}
        style={[{ flex: 1, minWidth: 0, width: "100%", fontSize: 16, color: C.text, paddingVertical: 12 }, style]}
      />
      {suffix ? <Text style={{ color: C.textSub, marginLeft: 8, flexShrink: 0 }}>{suffix}</Text> : null}
    </View>
  );
}

export function ErrorBox({ message }: { message: string | null }) {
  if (!message) return null;
  return (
    <View style={s.errorBox}>
      <Text style={{ color: "#B91C1C", fontSize: 14 }}>{message}</Text>
    </View>
  );
}

export function SuccessBox({ message }: { message: string | null }) {
  if (!message) return null;
  return (
    <View style={[s.errorBox, { backgroundColor: C.successSoft, borderColor: "#A7F3D0" }]}>
      <Text style={{ color: "#047857", fontSize: 14 }}>{message}</Text>
    </View>
  );
}

// ────────── States ──────────

export function Spinner() {
  return (
    <View style={{ paddingVertical: 48, alignItems: "center" }}>
      <ActivityIndicator size="large" color={C.primary} />
    </View>
  );
}

export function Skeleton({ count = 3, height = 120 }: { count?: number; height?: number }) {
  return (
    <View style={{ gap: 12 }}>
      {Array.from({ length: count }).map((_, i) => (
        <View key={i} style={[s.card, { height, backgroundColor: "#EEF2F7", borderColor: "#EEF2F7" }]} />
      ))}
    </View>
  );
}

export function EmptyState({
  title,
  description,
  icon = "file-tray-outline",
  actionLabel,
  onAction,
}: {
  title: string;
  description?: string;
  icon?: IconName;
  actionLabel?: string;
  onAction?: () => void;
}) {
  return (
    <View style={[s.card, { alignItems: "center", paddingVertical: 36, gap: 8 }]}>
      <Ionicons name={icon} size={36} color={C.textMute} />
      <Text style={{ fontSize: 16, fontWeight: "600", color: C.text, textAlign: "center" }}>{title}</Text>
      {description ? (
        <Text style={{ fontSize: 13, color: C.textSub, textAlign: "center" }}>{description}</Text>
      ) : null}
      {actionLabel && onAction ? (
        <GradientButton title={actionLabel} onPress={onAction} small variant="soft" style={{ marginTop: 8 }} />
      ) : null}
    </View>
  );
}

export function InfoCard({ title, lines }: { title: string; lines: string[] }) {
  return (
    <View style={[s.card, { flexDirection: "row", gap: 12 }]}>
      <Ionicons name="information-circle-outline" size={22} color={C.primary} />
      <View style={{ flex: 1, gap: 4 }}>
        <Text style={{ fontWeight: "600", color: C.text, fontSize: 15, marginBottom: 2 }}>{title}</Text>
        {lines.map((l) => (
          <Text key={l} style={{ fontSize: 13, color: C.textSub, lineHeight: 19 }}>
            • {l}
          </Text>
        ))}
      </View>
    </View>
  );
}

export function IconTile({ name, size = 56 }: { name: IconName; size?: number }) {
  return (
    <LinearGradient
      colors={GRADIENT}
      start={{ x: 0, y: 0 }}
      end={{ x: 1, y: 1 }}
      style={{ width: size, height: size, borderRadius: size * 0.28, alignItems: "center", justifyContent: "center" }}
    >
      <Ionicons name={name} size={size * 0.48} color="#fff" />
    </LinearGradient>
  );
}

// ────────── Bottom sheet ──────────

export function Sheet({
  visible,
  onClose,
  title,
  subtitle,
  children,
}: {
  visible: boolean;
  onClose: () => void;
  title: string;
  subtitle?: string;
  children: ReactNode;
}) {
  const insets = useSafeAreaInsets();
  return (
    <Modal visible={visible} animationType="slide" transparent onRequestClose={onClose} statusBarTranslucent>
      <KeyboardAvoidingView
        style={{ flex: 1 }}
        behavior={Platform.OS === "ios" ? "padding" : "height"}
      >
        <Pressable style={s.backdrop} onPress={onClose} />
        <View style={[s.sheet, { paddingBottom: insets.bottom + 20 }]}>
          <View style={s.grabber} />
          <Text style={[s.h2, { textAlign: "center" }]}>{title}</Text>
          {subtitle ? (
            <Text style={{ textAlign: "center", color: C.textSub, fontSize: 13, marginTop: 4 }}>{subtitle}</Text>
          ) : null}
          <ScrollView
            style={{ marginTop: 16 }}
            contentContainerStyle={{ gap: 14, paddingBottom: 4 }}
            keyboardShouldPersistTaps="handled"
          >
            {children}
          </ScrollView>
        </View>
      </KeyboardAvoidingView>
    </Modal>
  );
}

/** 옵션 선택용 (웹의 <select> 대체) */
export function SelectField<T extends string | number>({
  value,
  options,
  onChange,
  placeholder = "선택하세요",
  title = "선택",
}: {
  value: T | null;
  options: Array<{ value: T; label: string }>;
  onChange: (v: T) => void;
  placeholder?: string;
  title?: string;
}) {
  const [open, setOpen] = useState(false);
  const current = options.find((o) => o.value === value);
  return (
    <>
      <Pressable onPress={() => setOpen(true)} style={[s.input, { justifyContent: "space-between", paddingVertical: 14 }]}>
        <Text style={{ fontSize: 16, color: current ? C.text : C.textMute }}>{current?.label ?? placeholder}</Text>
        <Ionicons name="chevron-down" size={18} color={C.textSub} />
      </Pressable>
      <Sheet visible={open} onClose={() => setOpen(false)} title={title}>
        {options.map((o) => {
          const active = o.value === value;
          return (
            <Pressable
              key={String(o.value)}
              onPress={() => {
                onChange(o.value);
                setOpen(false);
              }}
              style={[s.option, active && { borderColor: C.primary, backgroundColor: C.primarySoft }]}
            >
              <Text style={{ fontSize: 16, color: active ? C.primaryDark : C.text, fontWeight: active ? "600" : "400" }}>
                {o.label}
              </Text>
              {active && <Ionicons name="checkmark" size={18} color={C.primary} />}
            </Pressable>
          );
        })}
      </Sheet>
    </>
  );
}

export function SectionTitle({ children }: { children: ReactNode }) {
  return <Text style={[s.h2, { marginBottom: 12, marginTop: 8 }]}>{children}</Text>;
}

export const s = StyleSheet.create({
  screen: { flex: 1, backgroundColor: C.bg },
  screenPad: { paddingHorizontal: 20 },
  backBtn: { flexDirection: "row", alignItems: "center", gap: 4, marginBottom: 12, alignSelf: "flex-start" },
  rowBetween: { flexDirection: "row", alignItems: "flex-start", justifyContent: "space-between", gap: 12 },
  row: { flexDirection: "row", alignItems: "center" },
  h1: { fontSize: 28, fontWeight: "700", color: C.text, marginBottom: 4 },
  h2: { fontSize: 19, fontWeight: "700", color: C.text },
  sub: { fontSize: 14, color: C.textSub },
  label: { fontSize: 14, color: "#334155", fontWeight: "500" },
  card: {
    backgroundColor: C.card,
    borderRadius: RADIUS.lg,
    borderWidth: 1.5,
    borderColor: C.border,
    padding: 16,
  },
  cardSelected: {
    borderColor: C.primary,
    shadowColor: C.primary,
    shadowOpacity: 0.15,
    shadowRadius: 10,
    shadowOffset: { width: 0, height: 4 },
    elevation: 3,
  },
  btnInner: { flexDirection: "row", alignItems: "center", justifyContent: "center", gap: 6, paddingHorizontal: 18 },
  btnText: { fontWeight: "600" },
  badge: { paddingHorizontal: 8, paddingVertical: 3, borderRadius: 6, alignSelf: "flex-start" },
  input: {
    flexDirection: "row",
    alignItems: "center",
    backgroundColor: "#fff",
    borderWidth: 2,
    borderColor: C.border,
    borderRadius: RADIUS.md,
    paddingHorizontal: 14,
  },
  errorBox: {
    backgroundColor: C.dangerSoft,
    borderWidth: 1,
    borderColor: "#FECACA",
    borderRadius: RADIUS.md,
    paddingHorizontal: 14,
    paddingVertical: 12,
  },
  backdrop: { flex: 1, backgroundColor: "rgba(15,23,42,0.45)" },
  sheet: {
    backgroundColor: "#fff",
    borderTopLeftRadius: 24,
    borderTopRightRadius: 24,
    paddingHorizontal: 20,
    paddingTop: 10,
    maxHeight: "90%",
  },
  grabber: { width: 40, height: 5, borderRadius: 3, backgroundColor: C.border, alignSelf: "center", marginBottom: 14 },
  option: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
    borderWidth: 1.5,
    borderColor: C.border,
    borderRadius: RADIUS.md,
    paddingHorizontal: 16,
    paddingVertical: 14,
  },
});

import { Modal, Pressable, StyleSheet, Text, View } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { router } from "expo-router";
import { C, RADIUS } from "./theme";
import type { BuySignalCategory, BuySignalToday } from "../api/prices";

type Meta = { icon: keyof typeof Ionicons.glyphMap; tint: string };

const CATEGORY_META: Record<BuySignalCategory, Meta> = {
  VEGETABLES: { icon: "leaf", tint: "#10B981" },
  LIVESTOCK: { icon: "restaurant", tint: "#F59E0B" },
  SEAFOOD: { icon: "fish", tint: "#3B82F6" },
  FRUITS: { icon: "nutrition", tint: "#EF4444" },
  GRAINS: { icon: "cafe", tint: "#8B5CF6" },
  PROCESSED: { icon: "cube", tint: "#6B7280" },
};

export function BuySignalModal({
  visible,
  signal,
  onDismiss,
}: {
  visible: boolean;
  signal: BuySignalToday | null;
  onDismiss: () => void;
}) {
  if (!signal) return null;
  const meta = CATEGORY_META[signal.category] ?? CATEGORY_META.PROCESSED;

  const confirm = () => {
    onDismiss();
    router.push(`/lowest-price/${signal.ingredientId}`);
  };

  return (
    <Modal transparent visible={visible} animationType="fade" onRequestClose={onDismiss}>
      <View style={styles.overlay}>
        <View style={styles.card}>
          <View style={[styles.iconCircle, { backgroundColor: `${meta.tint}22` }]}>
            <Ionicons name={meta.icon} size={40} color={meta.tint} />
          </View>

          <Text style={styles.title}>{signal.ingredientName}이(가) 매우 쌉니다!</Text>
          <Text style={styles.sub}>
            지금이 발주 적기입니다.{"\n"}최저가를 확인해 보세요.
          </Text>

          <View style={styles.priceBox}>
            <View style={styles.priceRow}>
              <Text style={styles.priceLabel}>월 평균</Text>
              <Text style={styles.priceVal}>
                {signal.monthAvg.toLocaleString()}원
              </Text>
            </View>
            <View style={styles.priceRow}>
              <Text style={styles.priceLabel}>현재 최저가</Text>
              <View style={styles.priceCurrent}>
                <Text style={[styles.priceVal, styles.priceCurrentText]}>
                  {signal.currentPrice.toLocaleString()}원
                </Text>
                <Ionicons name="arrow-down" size={14} color={C.primary} />
              </View>
            </View>
          </View>

          <View style={styles.btnRow}>
            <Pressable style={[styles.btn, styles.btnGhost]} onPress={onDismiss}>
              <Text style={styles.btnGhostText}>닫기</Text>
            </Pressable>
            <Pressable style={[styles.btn, styles.btnPrimary]} onPress={confirm}>
              <Text style={styles.btnPrimaryText}>확인하기</Text>
            </Pressable>
          </View>
        </View>
      </View>
    </Modal>
  );
}

const styles = StyleSheet.create({
  overlay: {
    flex: 1,
    backgroundColor: "rgba(0,0,0,0.4)",
    justifyContent: "center",
    alignItems: "center",
    padding: 24,
  },
  card: {
    backgroundColor: "#fff",
    borderRadius: RADIUS.lg,
    padding: 24,
    width: "100%",
    maxWidth: 360,
  },
  iconCircle: {
    width: 72,
    height: 72,
    borderRadius: 36,
    alignItems: "center",
    justifyContent: "center",
    alignSelf: "center",
    marginBottom: 12,
  },
  title: {
    fontSize: 18,
    fontWeight: "700",
    color: C.text,
    textAlign: "center",
    marginBottom: 6,
  },
  sub: {
    fontSize: 13,
    color: C.textSub,
    textAlign: "center",
    marginBottom: 18,
    lineHeight: 18,
  },
  priceBox: {
    backgroundColor: "#F3F4F6",
    borderRadius: 12,
    padding: 14,
    gap: 8,
    marginBottom: 16,
  },
  priceRow: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
  },
  priceCurrent: {
    flexDirection: "row",
    alignItems: "center",
    gap: 4,
  },
  priceCurrentText: {
    color: C.primary,
    fontWeight: "700",
  },
  priceLabel: { fontSize: 13, color: C.textSub },
  priceVal: { fontSize: 15, color: C.text },
  btnRow: { flexDirection: "row", gap: 10 },
  btn: {
    flex: 1,
    paddingVertical: 12,
    borderRadius: 10,
    alignItems: "center",
  },
  btnGhost: { backgroundColor: "#F3F4F6" },
  btnGhostText: { color: C.textSub, fontWeight: "600" },
  btnPrimary: { backgroundColor: C.primary },
  btnPrimaryText: { color: "#fff", fontWeight: "700" },
});

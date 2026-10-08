import { useMemo } from "react";
import { Pressable, Text, View } from "react-native";
import { router } from "expo-router";
import { thisMonthRange, usePurchaseSummary } from "../hooks/usePurchaseOrders";
import { C } from "./theme";
import { Card } from "./ui";

export function PurchaseSummaryCard() {
  const range = useMemo(() => thisMonthRange(), []);
  const { data, isLoading } = usePurchaseSummary(range.from, range.to);

  return (
    <Card style={{ marginBottom: 24 }}>
      <View style={{ flexDirection: "row", justifyContent: "space-between", alignItems: "flex-start" }}>
        <View>
          <Text style={{ fontSize: 16, fontWeight: "700", color: C.text }}>📊 이번 달 발주</Text>
          <Text style={{ fontSize: 12, color: C.textMute, marginTop: 2 }}>
            {range.from} ~ {range.to}
          </Text>
        </View>
        <Pressable onPress={() => router.push({ pathname: "/order", params: { tab: "history" } })} hitSlop={10}>
          <Text style={{ fontSize: 13, color: C.primary, fontWeight: "500" }}>전체 보기 →</Text>
        </Pressable>
      </View>

      {isLoading ? (
        <View style={{ height: 60, backgroundColor: "#EEF2F7", borderRadius: 10, marginTop: 14 }} />
      ) : !data || data.totalCount === 0 ? (
        <Text style={{ color: C.textSub, marginTop: 14, fontSize: 14 }}>이번 달 발주 기록이 없습니다</Text>
      ) : (
        <>
          <View style={{ flexDirection: "row", alignItems: "flex-end", justifyContent: "space-between", marginTop: 12 }}>
            <Text style={{ fontSize: 30, fontWeight: "800", color: C.primary }}>
              {Number(data.totalAmount).toLocaleString("ko-KR")}
              <Text style={{ fontSize: 15, color: C.textSub, fontWeight: "500" }}> 원</Text>
            </Text>
            <Text style={{ fontSize: 13, color: C.textMute, marginBottom: 6 }}>총 {data.totalCount}건</Text>
          </View>
          {data.byIngredient.length > 0 && (
            <View style={{ marginTop: 12, gap: 8 }}>
              <Text style={{ fontSize: 12, color: C.textSub }}>인기 재료 TOP 3</Text>
              {data.byIngredient.slice(0, 3).map((row, i) => (
                <View key={row.ingredientName} style={{ flexDirection: "row", alignItems: "center", gap: 8 }}>
                  <View style={{ width: 20, height: 20, borderRadius: 10, backgroundColor: C.primarySoft2, alignItems: "center", justifyContent: "center" }}>
                    <Text style={{ fontSize: 11, color: C.primaryDark, fontWeight: "700" }}>{i + 1}</Text>
                  </View>
                  <Text style={{ flex: 1, color: C.text, fontSize: 14 }}>
                    {row.ingredientName} <Text style={{ color: C.textMute, fontSize: 12 }}>{row.count}건</Text>
                  </Text>
                  <Text style={{ color: C.primary, fontWeight: "700", fontSize: 14 }}>
                    {Number(row.totalAmount).toLocaleString("ko-KR")}원
                  </Text>
                </View>
              ))}
            </View>
          )}
        </>
      )}
    </Card>
  );
}

import { useEffect, useState } from "react";
import { Pressable, Text, View } from "react-native";
import { LinearGradient } from "expo-linear-gradient";
import { useStoreSettings, useUpdateStoreSettings } from "../hooks/useStoreSettings";
import { DAY_OF_WEEK_LABEL, DAY_OF_WEEK_ORDER, type DayOfWeek } from "../types/settings";
import { C, GRADIENT } from "./theme";
import { DateTimeField } from "./pickers";
import { ErrorBox, Field, GradientButton, Spinner, SuccessBox } from "./ui";

function toHHmm(t: string | null | undefined): string {
  if (!t) return "";
  return t.length >= 5 ? t.slice(0, 5) : t;
}

export function StoreSettingsForm({
  submitLabel = "저장",
  onSaved,
}: {
  submitLabel?: string;
  onSaved?: () => void;
}) {
  const { data: settings, isLoading } = useStoreSettings();
  const updateMutation = useUpdateStoreSettings();
  const [openTime, setOpenTime] = useState("11:00");
  const [closeTime, setCloseTime] = useState("22:00");
  const [orderDay, setOrderDay] = useState<DayOfWeek>("MON");
  const [inventoryDay, setInventoryDay] = useState<DayOfWeek>("SUN");
  const [savedMsg, setSavedMsg] = useState<string | null>(null);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);

  useEffect(() => {
    if (settings) {
      if (settings.openTime) setOpenTime(toHHmm(settings.openTime));
      if (settings.closeTime) setCloseTime(toHHmm(settings.closeTime));
      if (settings.orderDay) setOrderDay(settings.orderDay);
      if (settings.inventoryDay) setInventoryDay(settings.inventoryDay);
    }
  }, [settings]);

  const submit = async () => {
    setErrorMsg(null);
    setSavedMsg(null);
    if (!openTime || !closeTime) return setErrorMsg("영업 시간을 입력해주세요.");
    try {
      await updateMutation.mutateAsync({ openTime, closeTime, orderDay, inventoryDay });
      if (onSaved) onSaved();
      else {
        setSavedMsg("저장되었습니다.");
        setTimeout(() => setSavedMsg(null), 2000);
      }
    } catch (err) {
      setErrorMsg(err instanceof Error ? `저장 실패: ${err.message}` : "저장에 실패했습니다.");
    }
  };

  if (isLoading) return <Spinner />;

  return (
    <View style={{ gap: 22 }}>
      <Section title="영업 시간" desc="발주 알림 시점 계산에 사용됩니다">
        <View style={{ flexDirection: "row", gap: 12 }}>
          <View style={{ flex: 1 }}>
            <Field label="오픈 시간">
              <DateTimeField mode="time" value={openTime} onChange={setOpenTime} />
            </Field>
          </View>
          <View style={{ flex: 1 }}>
            <Field label="마감 시간">
              <DateTimeField mode="time" value={closeTime} onChange={setCloseTime} />
            </Field>
          </View>
        </View>
      </Section>
      <Section title="발주 요일" desc="정기 발주 알림이 이 요일에 전송됩니다">
        <DayPicker value={orderDay} onChange={setOrderDay} />
      </Section>
      <Section title="재고 실사 요일" desc="재고 점검 알림이 이 요일에 전송됩니다">
        <DayPicker value={inventoryDay} onChange={setInventoryDay} />
      </Section>
      <ErrorBox message={errorMsg} />
      <SuccessBox message={savedMsg ? `✅ ${savedMsg}` : null} />
      <GradientButton title={submitLabel} onPress={submit} loading={updateMutation.isPending} />
    </View>
  );
}

function Section({ title, desc, children }: { title: string; desc?: string; children: React.ReactNode }) {
  return (
    <View>
      <Text style={{ fontSize: 17, fontWeight: "700", color: C.text }}>{title}</Text>
      {desc ? <Text style={{ fontSize: 12, color: C.textMute, marginTop: 2, marginBottom: 10 }}>{desc}</Text> : null}
      {children}
    </View>
  );
}

function DayPicker({ value, onChange }: { value: DayOfWeek; onChange: (d: DayOfWeek) => void }) {
  return (
    <View style={{ flexDirection: "row", gap: 6 }}>
      {DAY_OF_WEEK_ORDER.map((day) => {
        const on = value === day;
        return (
          <Pressable key={day} onPress={() => onChange(day)} style={{ flex: 1, borderRadius: 10, overflow: "hidden" }}>
            {on ? (
              <LinearGradient colors={GRADIENT} style={{ height: 44, alignItems: "center", justifyContent: "center" }}>
                <Text style={{ color: "#fff", fontWeight: "700" }}>{DAY_OF_WEEK_LABEL[day]}</Text>
              </LinearGradient>
            ) : (
              <View style={{ height: 44, alignItems: "center", justifyContent: "center", borderWidth: 2, borderColor: C.border, borderRadius: 10, backgroundColor: "#fff" }}>
                <Text style={{ color: C.textSub, fontWeight: "500" }}>{DAY_OF_WEEK_LABEL[day]}</Text>
              </View>
            )}
          </Pressable>
        );
      })}
    </View>
  );
}

import { useState } from "react";
import { Platform, Pressable, Text, TextInput, View } from "react-native";
import DateTimePicker, { DateTimePickerAndroid } from "@react-native-community/datetimepicker";
import { Ionicons } from "@expo/vector-icons";
import { C } from "./theme";
import { s } from "./ui";
import { formatLocalDate, parseLocalDate } from "../lib/date";

type Mode = "date" | "time";

function toDate(value: string, mode: Mode): Date {
  if (mode === "date") return value ? parseLocalDate(value) : new Date();
  const [h, m] = (value || "00:00").split(":").map(Number);
  const d = new Date();
  d.setHours(h || 0, m || 0, 0, 0);
  return d;
}

function fromDate(d: Date, mode: Mode): string {
  if (mode === "date") return formatLocalDate(d);
  return `${String(d.getHours()).padStart(2, "0")}:${String(d.getMinutes()).padStart(2, "0")}`;
}

function formatLabel(value: string, mode: Mode): string {
  if (!value) return mode === "date" ? "날짜 선택" : "시간 선택";
  if (mode === "date") return value;
  const [h, m] = value.split(":").map(Number);
  const ampm = h < 12 ? "오전" : "오후";
  const hh = h % 12 === 0 ? 12 : h % 12;
  return `${ampm} ${hh}:${String(m).padStart(2, "0")}`;
}

/**
 * 날짜("YYYY-MM-DD") / 시간("HH:mm") 입력.
 * Android: 시스템 다이얼로그, iOS: 인라인 스피너, Web(미리보기): 텍스트 입력.
 */
export function DateTimeField({
  value,
  onChange,
  mode = "date",
  minimumDate,
}: {
  value: string;
  onChange: (v: string) => void;
  mode?: Mode;
  minimumDate?: Date;
}) {
  const [iosOpen, setIosOpen] = useState(false);

  if (Platform.OS === "web") {
    return (
      <View style={s.input}>
        <TextInput
          value={value}
          onChangeText={onChange}
          placeholder={mode === "date" ? "YYYY-MM-DD" : "HH:mm"}
          placeholderTextColor={C.textMute}
          style={{ flex: 1, fontSize: 16, color: C.text, paddingVertical: 12 }}
        />
      </View>
    );
  }

  const open = () => {
    if (Platform.OS === "android") {
      DateTimePickerAndroid.open({
        value: toDate(value, mode),
        mode,
        is24Hour: false,
        minimumDate,
        onChange: (event, date) => {
          if (event.type === "set" && date) onChange(fromDate(date, mode));
        },
      });
    } else {
      setIosOpen((o) => !o);
    }
  };

  return (
    <View>
      <Pressable onPress={open} style={[s.input, { justifyContent: "space-between", paddingVertical: 14 }]}>
        <Text style={{ fontSize: 16, color: value ? C.text : C.textMute }}>{formatLabel(value, mode)}</Text>
        <Ionicons name={mode === "date" ? "calendar-outline" : "time-outline"} size={18} color={C.textSub} />
      </Pressable>
      {Platform.OS === "ios" && iosOpen && (
        <DateTimePicker
          value={toDate(value, mode)}
          mode={mode}
          display="spinner"
          locale="ko-KR"
          minimumDate={minimumDate}
          onChange={(_e, date) => date && onChange(fromDate(date, mode))}
        />
      )}
    </View>
  );
}

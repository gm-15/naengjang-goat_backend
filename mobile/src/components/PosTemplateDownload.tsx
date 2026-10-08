import { useRef, useState } from "react";
import { Text, View } from "react-native";
import { downloadPosTemplate } from "../api/pos-template";
import { C } from "./theme";
import { ErrorBox, GradientButton } from "./ui";

export function PosTemplateDownload() {
  const [busy, setBusy] = useState(false);
  const sending = useRef(false);
  const [error, setError] = useState<string | null>(null);
  const download = async () => {
    if (sending.current) return;
    sending.current = true;
    setBusy(true);
    setError(null);
    try { await downloadPosTemplate(); }
    catch (reason) { setError(reason instanceof Error ? reason.message : "판매 양식을 다운로드하지 못했습니다."); }
    finally { sending.current = false; setBusy(false); }
  };
  return (
    <View style={{ gap: 6 }}>
      <GradientButton title="판매 엑셀 양식 받기" small icon="download-outline" variant="soft" onPress={download} loading={busy} />
      <Text style={{ color: C.textMute, fontSize: 12 }}>빈 판매 양식과 내 메뉴명·연결 코드를 제공합니다. 실제 판매 데이터를 작성한 뒤 업로드하세요.</Text>
      <ErrorBox message={error} />
    </View>
  );
}

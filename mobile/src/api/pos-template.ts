import { Platform } from "react-native";
import { File, Paths } from "expo-file-system";
import * as Sharing from "expo-sharing";
import { ApiError, authHeaders } from "./client";
import { getApiBaseUrl } from "../lib/config";

/** 빈 판매 양식과 내 메뉴명/코드를 다운로드한다. 판매 자료를 자동 생성하지 않는다. */
export async function downloadPosTemplate(): Promise<void> {
  const url = `${getApiBaseUrl()}/pos/template`;
  const filename = "pos-sales-template.xlsx";
  if (Platform.OS === "web") {
    const response = await fetch(url, { headers: authHeaders() });
    if (!response.ok) throw new ApiError(response.status, "판매 양식을 다운로드하지 못했습니다. 로그인과 서버 연결을 확인해 주세요.");
    const blob = await response.blob();
    const href = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = href;
    link.download = filename;
    link.click();
    URL.revokeObjectURL(href);
    return;
  }
  const destination = new File(Paths.cache, filename);
  if (destination.exists) destination.delete();
  const file = await File.downloadFileAsync(url, destination, { headers: authHeaders() });
  if (!await Sharing.isAvailableAsync()) throw new Error("이 기기에서는 파일 공유를 열 수 없습니다. 웹에서 양식을 다운로드해 주세요.");
  await Sharing.shareAsync(file.uri, {
    mimeType: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    dialogTitle: "판매 엑셀 양식 저장 또는 공유",
  });
}

import { useCallback, useState } from "react";
import { Pressable, Text, View } from "react-native";
import { router, useFocusEffect } from "expo-router";
import * as DocumentPicker from "expo-document-picker";
import { isBusinessDate } from "../../api/workflow";
import { useDailyReport, useMenus, usePosMenuMappings, useSavePosMenuMapping, useUploadPosSales } from "../../hooks/useWorkflow";
import { DateTimeField } from "../../components/pickers";
import { MenuRecipeEditor } from "../../components/MenuRecipeEditor";
import { PosTemplateDownload } from "../../components/PosTemplateDownload";
import { Badge, Card, EmptyState, ErrorBox, Field, GradientButton, InfoCard, Input, PageHeader, Screen, SectionTitle, SelectField, Skeleton, SuccessBox } from "../../components/ui";
import { C } from "../../components/theme";
import { formatQuantity, todayISO, won } from "../../lib/date";
import type { DailyReport, PosUploadResult } from "../../types/workflow";

const messageOf = (error: unknown) => error instanceof Error ? error.message : "잠시 후 다시 시도해주세요.";
const dateTime = (value: string | null) => value ? value.replace("T", " ").slice(0, 19) : "미반영";

export default function OperationsScreen() {
  const [businessDate, setBusinessDate] = useState(todayISO);
  const report = useDailyReport(businessDate);
  const [file, setFile] = useState<DocumentPicker.DocumentPickerAsset | null>(null);
  const [pickError, setPickError] = useState<string | null>(null);
  const [uploadResult, setUploadResult] = useState<PosUploadResult | null>(null);
  const upload = useUploadPosSales();
  useFocusEffect(useCallback(() => {
    if (isBusinessDate(businessDate)) void report.refetch();
  }, [businessDate, report.refetch]));

  const pickFile = async () => {
    setPickError(null);
    upload.reset();
    try {
      const picked = await DocumentPicker.getDocumentAsync({
        type: ["application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "application/octet-stream"],
        copyToCacheDirectory: true,
        multiple: false,
      });
      if (picked.canceled) return;
      const asset = picked.assets[0];
      setFile(null);
      setUploadResult(null);
      if (!asset.name.toLowerCase().endsWith(".xlsx")) {
        setPickError(".xlsx 형식의 POS 판매 엑셀을 선택해주세요.");
        return;
      }
      if (asset.size !== undefined && asset.size > 5_000_000) {
        setPickError("파일은 5MB 이하로 준비해주세요.");
        return;
      }
      setFile(asset);
      setUploadResult(null);
    } catch (error) {
      setPickError(messageOf(error));
    }
  };

  const reflectSales = async () => {
    if (!file || upload.isPending) return;
    setUploadResult(null);
    try {
      const result = await upload.mutateAsync(file);
      setUploadResult(result);
      setBusinessDate(result.businessDate);
      setFile(null);
    } catch {
      // 실패한 파일을 유지해 메뉴 연결·재고 수정 후 다시 반영할 수 있습니다.
    }
  };

  return (
    <Screen refreshing={report.isRefetching} onRefresh={() => {
      if (isBusinessDate(businessDate)) void report.refetch();
    }}>
      <PageHeader title="일일 운영" description="영업 마감 후 판매 엑셀을 반영하고 하루 결과를 확인하세요" />
      <MenuRecipeEditor />
      <Card style={{ gap: 12, marginBottom: 16 }}>
        <SectionTitle>POS 판매 엑셀 반영</SectionTitle>
        <Text style={{ color: C.textSub, lineHeight: 21 }}>
          매장 POS에서 하루 판매 자료를 받아 업로드하세요. 반영하면 메뉴 레시피에 따라 재고를 차감하고 발주 판단 알림을 준비합니다.
        </Text>
        <InfoCard title="판매 엑셀 기준" lines={[
          ".xlsx · 5MB 이하 · 시트 이름: 판매내역",
          "첫 행: 영업일 / 메뉴코드 / 메뉴명 / 판매수량 / 판매금액",
          "한 파일에 하루 판매 자료, 메뉴별로 합산한 한 행씩 입력",
          "메뉴코드를 사용하면 아래에서 메뉴를 연결하세요. 코드가 비어 있으면 등록한 메뉴명과 정확히 일치해야 합니다.",
          "같은 파일은 다시 차감하지 않습니다. 이미 반영한 영업일의 다른 파일은 등록할 수 없습니다.",
        ]} />
        <PosTemplateDownload />
        {file ? (
          <View style={{ backgroundColor: C.primarySoft, borderRadius: 10, padding: 12, gap: 4 }}>
            <Text style={{ color: C.text, fontWeight: "700" }}>{file.name}</Text>
            <Text style={{ color: C.textSub, fontSize: 12 }}>
              {file.size !== undefined ? `${Math.ceil(file.size / 1000).toLocaleString("ko-KR")} KB · ` : ""}영업일은 엑셀 내용에서 읽습니다
            </Text>
          </View>
        ) : null}
        <View style={{ flexDirection: "row", gap: 10 }}>
          <GradientButton title={file ? "파일 다시 선택" : "엑셀 선택"} icon="document-outline" variant="outline" disabled={upload.isPending} onPress={() => void pickFile()} style={{ flex: 1 }} />
          <GradientButton title="판매 반영" icon="cloud-upload-outline" disabled={!file || upload.isPending} loading={upload.isPending} onPress={() => void reflectSales()} style={{ flex: 1 }} />
        </View>
        <ErrorBox message={pickError ?? (upload.error ? messageOf(upload.error) : null)} />
        {uploadResult ? (
          <View style={{ gap: 8 }}>
            <SuccessBox message={uploadResult.duplicate
              ? `${uploadResult.businessDate} 이미 반영한 파일입니다. 재고를 추가로 차감하지 않았습니다.`
              : `${uploadResult.businessDate} 판매 ${formatQuantity(uploadResult.salesQuantity)}개 · ${won(uploadResult.salesAmount)} 반영 완료. 재료 소비량과 발주 판단을 갱신했습니다.`} />
            <GradientButton title="현재 발주 판단 확인" variant="soft" icon="clipboard-outline" onPress={() => router.navigate("/order")} />
          </View>
        ) : null}
      </Card>

      <MenuMappingSection />

      <SectionTitle>일일 운영 리포트</SectionTitle>
      <Card style={{ gap: 12, marginBottom: 14 }}>
        <Field label="조회할 영업일">
          <DateTimeField value={businessDate} onChange={setBusinessDate} />
        </Field>
        <ErrorBox message={!isBusinessDate(businessDate) ? "YYYY-MM-DD 형식의 올바른 날짜를 입력해주세요." : null} />
      </Card>
      {report.isLoading ? <Skeleton count={2} height={140} /> : report.error ? (
        <View style={{ gap: 10 }}>
          <ErrorBox message={messageOf(report.error)} />
          <GradientButton title="리포트 다시 조회" variant="outline" onPress={() => void report.refetch()} />
        </View>
      ) : report.data && isBusinessDate(businessDate) ? <ReportContent report={report.data} /> : null}
    </Screen>
  );
}

function MenuMappingSection() {
  const [open, setOpen] = useState(false);
  const [posCode, setPosCode] = useState("");
  const [menuId, setMenuId] = useState<number | null>(null);
  const [saved, setSaved] = useState<string | null>(null);
  const menus = useMenus();
  const mappings = usePosMenuMappings();
  const save = useSavePosMenuMapping();
  const editCode = (value: string) => {
    setPosCode(value);
    setSaved(null);
    save.reset();
  };
  const saveMapping = async () => {
    if (!posCode.trim() || !menuId) return;
    setSaved(null);
    try {
      const code = posCode.trim();
      await save.mutateAsync({ posCode: code, menuId });
      setSaved(`${code} → ${menus.data?.find((menu) => menu.menuId === menuId)?.name ?? "선택한 메뉴"} 연결 완료`);
    } catch {
      // 오류는 입력란 아래에 표시합니다.
    }
  };
  return (
    <Card style={{ gap: 12, marginBottom: 20 }}>
      <Pressable onPress={() => setOpen((value) => !value)} style={{ flexDirection: "row", alignItems: "center", justifyContent: "space-between" }}>
        <View style={{ flex: 1 }}>
          <Text style={{ color: C.text, fontSize: 17, fontWeight: "700" }}>POS 메뉴코드 연결</Text>
          <Text style={{ color: C.textSub, fontSize: 12, marginTop: 4 }}>연결 {mappings.data?.length ?? 0}개 · 최초 업로드 전에 설정</Text>
        </View>
        <Text style={{ color: C.primary, fontWeight: "600" }}>{open ? "접기" : "설정"}</Text>
      </Pressable>
      {open ? (
        <View style={{ gap: 12 }} pointerEvents={save.isPending ? "none" : "auto"}>
          {menus.isLoading || mappings.isLoading ? <Skeleton count={1} height={60} /> : null}
          <ErrorBox message={menus.error ? messageOf(menus.error) : mappings.error ? messageOf(mappings.error) : null} />
          {menus.error || mappings.error ? <GradientButton title="메뉴 연결 다시 조회" small variant="outline" onPress={() => { void menus.refetch(); void mappings.refetch(); }} /> : null}
          {menus.data?.length === 0 ? (
            <EmptyState title="등록한 메뉴가 없습니다" description="메뉴와 레시피를 먼저 등록해주세요." actionLabel="메뉴 설정" onAction={() => router.push("/onboard")} />
          ) : (
            <>
              <Field label="엑셀의 메뉴코드">
                <Input value={posCode} maxLength={100} autoCapitalize="none" placeholder="예: M001" onChangeText={editCode} />
              </Field>
              <Field label="재고를 차감할 메뉴">
                <SelectField value={menuId} options={(menus.data ?? []).map((menu) => ({ value: menu.menuId, label: menu.name }))} onChange={(value) => { setMenuId(value); setSaved(null); save.reset(); }} title="메뉴 선택" />
              </Field>
              <GradientButton title="메뉴 연결 저장" disabled={!posCode.trim() || menuId === null || save.isPending} loading={save.isPending} onPress={() => void saveMapping()} />
              <ErrorBox message={save.error ? messageOf(save.error) : null} />
              <SuccessBox message={saved} />
              {(mappings.data ?? []).map((mapping) => (
                <Pressable key={mapping.id} onPress={() => { editCode(mapping.posCode); setMenuId(mapping.menuId); }} style={{ backgroundColor: C.primarySoft, borderRadius: 10, padding: 12 }}>
                  <Text style={{ color: C.text }}>{mapping.posCode} → {menus.data?.find((menu) => menu.menuId === mapping.menuId)?.name ?? `메뉴 ${mapping.menuId}`}</Text>
                  <Text style={{ color: C.textSub, fontSize: 11, marginTop: 3 }}>눌러서 연결 변경</Text>
                </Pressable>
              ))}
            </>
          )}
        </View>
      ) : null}
    </Card>
  );
}

function ReportContent({ report }: { report: DailyReport }) {
  return (
    <View style={{ gap: 14 }}>
      {report.uploaded ? (
        <Card style={{ gap: 10 }}>
          <Badge label="판매 반영 완료" tone="success" />
          <View style={{ flexDirection: "row", gap: 12 }}>
            <View style={{ flex: 1 }}>
              <Text style={{ color: C.textSub, fontSize: 13 }}>판매금액</Text>
              <Text style={{ color: C.primary, fontWeight: "800", fontSize: 22 }}>{won(report.salesAmount)}</Text>
            </View>
            <View style={{ flex: 1 }}>
              <Text style={{ color: C.textSub, fontSize: 13 }}>메뉴 판매수량</Text>
              <Text style={{ color: C.text, fontWeight: "800", fontSize: 22 }}>{formatQuantity(report.salesQuantity)}개</Text>
            </View>
          </View>
          <Text style={{ color: C.textMute, fontSize: 12 }}>판매 반영 시각: {dateTime(report.reflectedAt)}</Text>
        </Card>
      ) : (
        <EmptyState title="이 영업일의 판매 자료가 없습니다" description={`${report.businessDate} 판매금액·소비량은 아직 집계할 수 없습니다. 해당 영업일의 POS 엑셀을 반영해주세요.`} />
      )}
      {report.uploaded ? (
        <>
          <Card style={{ gap: 10 }}>
            <SectionTitle>메뉴별 판매</SectionTitle>
            {report.menuSales.map((sale) => (
              <View key={sale.menuId} style={{ flexDirection: "row", alignItems: "center", gap: 8 }}>
                <Text style={{ flex: 1, color: C.text }}>{sale.menuName}</Text>
                <Text style={{ color: C.textSub }}>{formatQuantity(sale.quantity)}개</Text>
                <Text style={{ color: C.text, fontWeight: "600", minWidth: 80, textAlign: "right" }}>{won(sale.salesAmount)}</Text>
              </View>
            ))}
          </Card>
          <Card style={{ gap: 10 }}>
            <SectionTitle>재료 소비량</SectionTitle>
            {report.ingredientConsumption.map((item) => (
              <View key={item.ingredientId} style={{ flexDirection: "row", justifyContent: "space-between", gap: 12 }}>
                <Text style={{ flex: 1, color: C.text }}>{item.ingredientName}</Text>
                <Text style={{ color: C.text, fontWeight: "600" }}>{formatQuantity(item.quantity)} {item.baseUnit}</Text>
              </View>
            ))}
          </Card>
        </>
      ) : null}
      <Card style={{ gap: 10 }}>
        <SectionTitle>현재 잔여 재고</SectionTitle>
        <Text style={{ color: C.textSub, fontSize: 12, lineHeight: 18 }}>조회 시점의 현재 재고입니다. 선택한 영업일 종료 당시 재고와는 다를 수 있습니다.</Text>
        <Text style={{ color: C.textMute, fontSize: 12 }}>재고 조회 시각: {dateTime(report.inventoryAsOf)}</Text>
        {report.currentInventory.length === 0 ? <Text style={{ color: C.textSub }}>등록한 재료가 없습니다.</Text> : report.currentInventory.map((item) => (
          <View key={item.ingredientId} style={{ flexDirection: "row", alignItems: "center", gap: 12 }}>
            <Pressable onPress={() => router.push({ pathname: "/inventory", params: { ingredientId: String(item.ingredientId) } })} style={{ flex: 1 }}>
              <Text style={{ color: C.primary }}>{item.ingredientName}</Text>
            </Pressable>
            <Text style={{ color: C.text, fontWeight: "600" }}>{formatQuantity(item.quantity)} {item.baseUnit}</Text>
          </View>
        ))}
        <Text style={{ color: C.textMute, fontSize: 12 }}>마지막 판매 반영 영업일: {report.lastUploadedBusinessDate ?? "없음"}</Text>
      </Card>
    </View>
  );
}

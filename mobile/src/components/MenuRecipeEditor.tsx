import { useRef, useState } from "react";
import { Pressable, Text, View } from "react-native";
import { router } from "expo-router";
import { useClosingRecommendations, useMenus, useSaveMenuRecipe } from "../hooks/useWorkflow";
import { menuRecipePayload, type RecipeInputRow } from "../lib/menu-recipe";
import { todayISO } from "../lib/date";
import type { MenuSummary } from "../types/workflow";
import { C } from "./theme";
import { Card, EmptyState, ErrorBox, Field, GradientButton, Input, SelectField, Skeleton, SuccessBox } from "./ui";

type DraftRow = RecipeInputRow & { key: number };
const messageOf = (error: unknown) => error instanceof Error ? error.message : "다시 시도해주세요.";

export function MenuRecipeEditor() {
  const [open, setOpen] = useState(false);
  const [selection, setSelection] = useState<string | null>(null);
  const [name, setName] = useState("");
  const [price, setPrice] = useState("");
  const [rows, setRows] = useState<DraftRow[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);
  const keys = useRef(0);
  const menus = useMenus();
  const ingredients = useClosingRecommendations(todayISO());
  const save = useSaveMenuRecipe();
  const ingredientItems = ingredients.data?.items ?? [];
  const resetMessage = () => {
    setError(null);
    setSuccess(null);
    save.reset();
  };
  const editMenu = (menu: MenuSummary) => {
    setSelection(String(menu.menuId));
    setName(menu.name);
    setPrice(String(menu.price));
    setRows((menu.recipe ?? []).map((item) => ({
      key: ++keys.current,
      ingredientId: item.ingredientId,
      quantity: String(item.requiredQuantity),
      unit: item.unit,
    })));
  };
  const chooseMenu = (value: string) => {
    resetMessage();
    if (value === "new") {
      setSelection(value);
      setName("");
      setPrice("");
      setRows([{ key: ++keys.current, ingredientId: null, quantity: "", unit: "" }]);
    } else {
      const menu = menus.data?.find((item) => String(item.menuId) === value);
      if (menu) editMenu(menu);
    }
  };
  const updateRow = (key: number, patch: Partial<RecipeInputRow>) => {
    resetMessage();
    setRows((existing) => existing.map((row) => row.key === key ? { ...row, ...patch } : row));
  };
  const saveRecipe = async () => {
    if (save.isPending || selection === null) return;
    resetMessage();
    try {
      const payload = menuRecipePayload(name, price, rows);
      const saved = await save.mutateAsync({ menuId: selection === "new" ? null : Number(selection), payload });
      editMenu(saved);
      setSuccess(`${saved.name} 메뉴·레시피를 저장했습니다. 이후 판매 반영부터 적용됩니다.`);
    } catch (caught) {
      setError(messageOf(caught));
    }
  };

  return (
    <Card style={{ gap: 12, marginBottom: 16 }}>
      <Pressable onPress={() => setOpen((value) => !value)} style={{ flexDirection: "row", justifyContent: "space-between", alignItems: "center", gap: 12 }}>
        <View style={{ flex: 1 }}>
          <Text style={{ color: C.text, fontWeight: "700", fontSize: 17 }}>메뉴·레시피 설정</Text>
          <Text style={{ color: C.textSub, fontSize: 12, lineHeight: 18, marginTop: 4 }}>판매 자료 반영 전, 메뉴 한 개의 실제 재료 소모량을 확인하세요</Text>
        </View>
        <Text style={{ color: C.primary, fontWeight: "600" }}>{open ? "접기" : "설정"}</Text>
      </Pressable>
      {open ? (
        <View style={{ gap: 12 }} pointerEvents={save.isPending ? "none" : "auto"}>
          <Text style={{ color: "#92400E", fontSize: 13, lineHeight: 20 }}>초기 메뉴·레시피의 기본 수량을 실제 매장 기준으로 수정하세요. 여기서 저장한 1인분 소모량 × POS 판매수량만큼 재고가 차감됩니다. 이미 반영한 판매 자료는 다시 계산하지 않습니다.</Text>
          {menus.isLoading || ingredients.isLoading ? <Skeleton count={1} height={80} /> : null}
          <ErrorBox message={menus.error ? messageOf(menus.error) : ingredients.error ? messageOf(ingredients.error) : null} />
          {menus.error || ingredients.error ? <GradientButton title="메뉴·재료 다시 조회" small variant="outline" onPress={() => { void menus.refetch(); void ingredients.refetch(); }} /> : null}
          {ingredientItems.length === 0 && !ingredients.isLoading && !ingredients.error ? (
            <EmptyState title="등록한 재료가 없습니다" description="매장 카테고리를 먼저 선택해 재료를 등록해주세요." actionLabel="매장 초기 설정" onAction={() => router.push("/onboard")} />
          ) : null}
          <Field label="편집할 메뉴">
            <SelectField value={selection} options={[
              ...(menus.data ?? []).map((menu) => ({ value: String(menu.menuId), label: menu.name })),
              { value: "new", label: "+ 새 메뉴 만들기" },
            ]} onChange={chooseMenu} title="메뉴 선택" placeholder="기존 메뉴 선택 또는 새 메뉴 만들기" />
          </Field>
          {selection !== null ? (
            <>
              <Field label="메뉴명">
                <Input value={name} maxLength={100} placeholder="예: 제육볶음" onChangeText={(value) => { resetMessage(); setName(value); }} />
              </Field>
              <Field label="메뉴 판매가">
                <Input value={price} keyboardType="number-pad" suffix="원" placeholder="예: 9000" onChangeText={(value) => { resetMessage(); setPrice(value); }} />
              </Field>
              <Text style={{ color: C.text, fontWeight: "700" }}>메뉴 한 개 판매 시 재료 소모량</Text>
              {rows.map((row, index) => (
                <View key={row.key} style={{ borderWidth: 1, borderColor: C.border, borderRadius: 12, padding: 12, gap: 8 }}>
                  <View style={{ flexDirection: "row", justifyContent: "space-between", alignItems: "center" }}>
                    <Text style={{ color: C.textSub, fontSize: 12 }}>재료 {index + 1}</Text>
                    <Pressable disabled={save.isPending} onPress={() => { resetMessage(); setRows((existing) => existing.filter((item) => item.key !== row.key)); }} hitSlop={8}>
                      <Text style={{ color: C.danger, fontSize: 12 }}>제거</Text>
                    </Pressable>
                  </View>
                  <SelectField value={row.ingredientId} options={ingredientItems.map((item) => ({ value: item.ingredientId, label: `${item.ingredientName} (${item.baseUnit})` }))} onChange={(ingredientId) => updateRow(row.key, { ingredientId, unit: ingredientItems.find((item) => item.ingredientId === ingredientId)?.baseUnit ?? "" })} title="소모할 재료" />
                  <Input value={row.quantity} keyboardType="decimal-pad" suffix={row.unit || "단위"} placeholder="실제 1인분 소모량" onChangeText={(quantity) => updateRow(row.key, { quantity })} />
                </View>
              ))}
              <GradientButton title="재료 추가" small variant="outline" icon="add" disabled={save.isPending || rows.length >= 100 || ingredientItems.length === 0} onPress={() => {
                resetMessage();
                setRows((existing) => [...existing, { key: ++keys.current, ingredientId: null, quantity: "", unit: "" }]);
              }} />
              <ErrorBox message={error} />
              <SuccessBox message={success} />
              <GradientButton title="메뉴·레시피 저장" loading={save.isPending} disabled={save.isPending || ingredientItems.length === 0} onPress={() => void saveRecipe()} />
            </>
          ) : null}
        </View>
      ) : null}
    </Card>
  );
}

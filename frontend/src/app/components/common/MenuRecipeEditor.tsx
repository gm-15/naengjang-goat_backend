import { useRef, useState, type FormEvent } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Link } from "react-router";
import {
  createMenu,
  getRecommendations,
  listMenus,
  updateMenu,
} from "../../api/workflow";
import {
  allowedUnits,
  invalidateOperations,
  quantityInBase,
  todayKorean,
} from "../../lib/workflow";
import type { Menu, MenuPayload } from "../../types/workflow";

interface RecipeRow {
  key: number;
  ingredientId: string;
  quantity: string;
  unit: string;
}
const field =
  "w-full rounded-xl border border-[#cbd5e1] bg-white px-3 py-2.5 text-[#1e293b] focus:border-[#0EA5E9] focus:outline-none focus:ring-2 focus:ring-[#0EA5E9]/20";
const errorMessage = (error: unknown) =>
  error instanceof Error
    ? error.message
    : "처리하지 못했습니다. 다시 시도해주세요.";

export function MenuRecipeEditor() {
  const queryClient = useQueryClient();
  const nextRowKey = useRef(1);
  const [date] = useState(todayKorean);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [name, setName] = useState("");
  const [price, setPrice] = useState("");
  const [rows, setRows] = useState<RecipeRow[]>([
    { key: 0, ingredientId: "", quantity: "", unit: "" },
  ]);
  const [validationError, setValidationError] = useState("");
  const [message, setMessage] = useState("");
  const menus = useQuery({ queryKey: ["menus"], queryFn: listMenus });
  const recommendations = useQuery({
    queryKey: ["closing-recommendations", date],
    queryFn: () => getRecommendations(date),
  });
  const ingredients = recommendations.data?.items ?? [];
  const save = useMutation({
    mutationFn: ({
      id,
      payload,
    }: {
      id: number | null;
      payload: MenuPayload;
    }) => (id === null ? createMenu(payload) : updateMenu(id, payload)),
    onSuccess: (menu) => {
      queryClient.setQueryData<Menu[]>(["menus"], (previous = []) => {
        const found = previous.some((item) => item.menuId === menu.menuId);
        return found
          ? previous.map((item) => (item.menuId === menu.menuId ? menu : item))
          : [...previous, menu];
      });
      void queryClient.invalidateQueries({ queryKey: ["menus"] });
      invalidateOperations(queryClient);
      loadMenu(menu);
      setMessage(
        `${menu.name} 메뉴와 1인분 레시피를 저장했습니다. 이후 판매 반영부터 사용합니다.`,
      );
    },
  });

  function newRow(): RecipeRow {
    return {
      key: nextRowKey.current++,
      ingredientId: "",
      quantity: "",
      unit: "",
    };
  }
  function clearFeedback() {
    setValidationError("");
    setMessage("");
    save.reset();
  }
  function loadMenu(menu?: Menu) {
    setEditingId(menu?.menuId ?? null);
    setName(menu?.name ?? "");
    setPrice(menu ? String(menu.price) : "");
    setRows(
      menu?.recipe?.length
        ? menu.recipe.map((item) => ({
            key: nextRowKey.current++,
            ingredientId: String(item.ingredientId),
            quantity: String(item.requiredQuantity),
            unit: item.unit,
          }))
        : [newRow()],
    );
    setValidationError("");
    setMessage("");
  }
  function changeRow(key: number, values: Partial<RecipeRow>) {
    clearFeedback();
    setRows((previous) =>
      previous.map((row) => (row.key === key ? { ...row, ...values } : row)),
    );
  }
  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    clearFeedback();
    const trimmedName = name.trim();
    const menuPrice = Number(price);
    if (
      !trimmedName ||
      !price.trim() ||
      !Number.isSafeInteger(menuPrice) ||
      menuPrice < 0 ||
      menuPrice > 2147483647
    ) {
      setValidationError("메뉴명과 0 이상의 정수 판매 가격을 입력해주세요.");
      return;
    }
    if (
      menus.data?.some(
        (menu) => menu.menuId !== editingId && menu.name.trim() === trimmedName,
      )
    ) {
      setValidationError(
        "같은 이름의 메뉴가 있습니다. 기존 메뉴를 선택해서 수정해주세요.",
      );
      return;
    }
    if (
      !rows.length ||
      rows.length > 100 ||
      rows.some(
        (row) =>
          !row.ingredientId ||
          !row.quantity.trim() ||
          !Number.isFinite(Number(row.quantity)) ||
          Number(row.quantity) <= 0,
      )
    ) {
      setValidationError(
        "각 재료와 메뉴 1인분에 필요한 양의 수량을 입력해주세요.",
      );
      return;
    }
    if (
      rows.some((row) => {
        const ingredient = ingredients.find(
          (item) => item.ingredientId === Number(row.ingredientId),
        );
        if (!ingredient) return false;
        const baseQuantity = quantityInBase(
          Number(row.quantity),
          row.unit,
          ingredient.baseUnit,
        );
        return (
          Number(row.quantity) > 9999999.999 ||
          !Number.isFinite(baseQuantity) ||
          baseQuantity < 0.001 ||
          baseQuantity > 9999999.999
        );
      })
    ) {
      setValidationError(
        "재고 단위로 환산한 1인분 소모량은 0.001~9999999.999 사이여야 합니다.",
      );
      return;
    }
    if (new Set(rows.map((row) => row.ingredientId)).size !== rows.length) {
      setValidationError(
        "같은 재료가 중복되어 있습니다. 재료별 수량을 합쳐 한 줄로 입력해주세요.",
      );
      return;
    }
    if (
      rows.some((row) => {
        const ingredient = ingredients.find(
          (item) => item.ingredientId === Number(row.ingredientId),
        );
        return (
          !ingredient || !allowedUnits(ingredient.baseUnit).includes(row.unit)
        );
      })
    ) {
      setValidationError(
        "재료의 재고 기준 단위와 호환되는 수량 단위를 선택해주세요.",
      );
      return;
    }
    save.mutate({
      id: editingId,
      payload: {
        name: trimmedName,
        price: menuPrice,
        recipe: rows.map((row) => ({
          ingredientId: Number(row.ingredientId),
          requiredQuantity: Number(row.quantity),
          unit: row.unit,
        })),
      },
    });
  }

  return (
    <section
      className="rounded-2xl border border-[#e2e8f0] bg-white p-5 sm:p-6"
      aria-labelledby="recipe-editor-title"
    >
      <h2
        id="recipe-editor-title"
        className="text-lg font-semibold text-[#1e293b]"
      >
        판매 전에 메뉴 · 레시피 확인
      </h2>
      <p className="mt-2 text-sm leading-relaxed text-[#64748b]">
        메뉴 한 개를 판매할 때 쓰는 재료와 수량을 저장하세요. 판매 엑셀의 메뉴별
        수량에 이 레시피를 곱해 재고를 차감합니다.
      </p>
      <p className="mt-3 rounded-xl bg-amber-50 p-3 text-sm leading-relaxed text-amber-800">
        자동 등록된 메뉴에는 재료 수량 1이 기본값으로 들어 있을 수 있습니다.
        실제 1인분에 쓰는 수량으로 확인·저장한 뒤 POS 파일을 반영하세요. 레시피
        수정은 이미 반영한 과거 판매 내역을 바꾸지 않습니다.
      </p>
      {menus.isLoading || recommendations.isLoading ? (
        <p className="mt-4 text-sm text-[#64748b]">
          메뉴와 매장 재료를 불러오는 중…
        </p>
      ) : menus.isError || recommendations.isError ? (
        <div role="alert" className="mt-4 text-sm text-red-700">
          {errorMessage(menus.error ?? recommendations.error)}{" "}
          <button
            type="button"
            className="underline"
            onClick={() => {
              void menus.refetch();
              void recommendations.refetch();
            }}
          >
            다시 불러오기
          </button>
        </div>
      ) : !ingredients.length ? (
        <p className="mt-4 text-sm text-[#64748b]">
          등록된 매장 재료가 없습니다.{" "}
          <Link
            to="/onboard"
            className="font-semibold text-[#0284c7] underline"
          >
            매장 설정에서 재료 준비
          </Link>{" "}
          후 메뉴와 레시피를 작성해주세요.
        </p>
      ) : (
        <form onSubmit={submit} className="mt-4 space-y-4">
          <div className="flex flex-wrap items-end gap-3">
            <label className="block min-w-0 flex-1 text-sm font-medium text-[#334155]">
              편집할 메뉴
              <select
                data-testid="menu-select"
                value={editingId ?? ""}
                disabled={save.isPending}
                onChange={(event) => {
                  save.reset();
                  loadMenu(
                    menus.data?.find(
                      (menu) => menu.menuId === Number(event.target.value),
                    ),
                  );
                }}
                className={`${field} mt-1`}
              >
                <option value="">새 메뉴 만들기</option>
                {menus.data?.map((menu) => (
                  <option key={menu.menuId} value={menu.menuId}>
                    {menu.name}
                  </option>
                ))}
              </select>
            </label>
            <button
              data-testid="menu-new"
              type="button"
              disabled={save.isPending}
              onClick={() => {
                save.reset();
                loadMenu();
              }}
              className="rounded-xl border border-[#bae6fd] px-4 py-2.5 text-sm font-medium text-[#0369a1]"
            >
              새 메뉴
            </button>
          </div>
          <div className="grid gap-3 sm:grid-cols-2">
            <label className="block text-sm font-medium text-[#334155]">
              메뉴명
              <input
                data-testid="menu-name"
                value={name}
                maxLength={100}
                required
                disabled={save.isPending}
                onChange={(event) => {
                  setName(event.target.value);
                  clearFeedback();
                }}
                className={`${field} mt-1`}
                placeholder="예: 배추전"
              />
            </label>
            <label className="block text-sm font-medium text-[#334155]">
              메뉴 판매 가격 (원)
              <input
                data-testid="menu-price"
                type="number"
                value={price}
                min="0"
                step="1"
                required
                disabled={save.isPending}
                onChange={(event) => {
                  setPrice(event.target.value);
                  clearFeedback();
                }}
                className={`${field} mt-1`}
                placeholder="예: 8000"
              />
            </label>
          </div>
          <fieldset disabled={save.isPending} className="space-y-3">
            <legend className="mb-2 text-sm font-semibold text-[#334155]">
              1인분에 필요한 재료
            </legend>
            {rows.map((row, index) => {
              const ingredient = ingredients.find(
                (item) => item.ingredientId === Number(row.ingredientId),
              );
              const units = ingredient ? allowedUnits(ingredient.baseUnit) : [];
              return (
                <div key={row.key} className="rounded-xl bg-[#F8FAFC] p-3">
                  <div className="grid gap-3 sm:grid-cols-[1.6fr_1fr_0.8fr_auto] sm:items-end">
                    <label className="block text-sm font-medium text-[#334155]">
                      재료 {index + 1}
                      <select
                        data-testid={`menu-recipe-ingredient-${index}`}
                        value={row.ingredientId}
                        required
                        onChange={(event) => {
                          const next = ingredients.find(
                            (item) =>
                              item.ingredientId === Number(event.target.value),
                          );
                          changeRow(row.key, {
                            ingredientId: event.target.value,
                            quantity: "",
                            unit: next?.baseUnit ?? "",
                          });
                        }}
                        className={`${field} mt-1`}
                      >
                        <option value="">재료 선택</option>
                        {ingredients.map((item) => (
                          <option
                            key={item.ingredientId}
                            value={item.ingredientId}
                          >
                            {item.ingredientName}
                          </option>
                        ))}
                      </select>
                    </label>
                    <label className="block text-sm font-medium text-[#334155]">
                      1인분 수량
                      <input
                        data-testid={`menu-recipe-quantity-${index}`}
                        type="number"
                        value={row.quantity}
                        min="0.001"
                        step="0.001"
                        required
                        onChange={(event) =>
                          changeRow(row.key, { quantity: event.target.value })
                        }
                        className={`${field} mt-1`}
                        placeholder="예: 200"
                      />
                    </label>
                    <label className="block text-sm font-medium text-[#334155]">
                      수량 단위
                      <select
                        data-testid={`menu-recipe-unit-${index}`}
                        value={row.unit}
                        required
                        onChange={(event) =>
                          changeRow(row.key, { unit: event.target.value })
                        }
                        className={`${field} mt-1`}
                      >
                        <option value="">단위 선택</option>
                        {row.unit && !units.includes(row.unit) && (
                          <option value={row.unit}>
                            {row.unit} (확인 필요)
                          </option>
                        )}
                        {units.map((unit) => (
                          <option key={unit} value={unit}>
                            {unit}
                          </option>
                        ))}
                      </select>
                    </label>
                    <button
                      type="button"
                      aria-label={`재료 ${index + 1} 삭제`}
                      disabled={rows.length === 1}
                      onClick={() => {
                        clearFeedback();
                        setRows((previous) =>
                          previous.filter((item) => item.key !== row.key),
                        );
                      }}
                      className="rounded-xl px-3 py-2.5 text-sm text-red-600 hover:bg-red-50 disabled:text-[#94a3b8]"
                    >
                      삭제
                    </button>
                  </div>
                  {ingredient && (
                    <p className="mt-2 text-xs text-[#64748b]">
                      재고 기준 단위: {ingredient.baseUnit} · 저장한 수량을 재고
                      단위로 환산해 차감합니다.
                    </p>
                  )}
                </div>
              );
            })}
            <button
              type="button"
              disabled={rows.length >= 100}
              onClick={() => {
                clearFeedback();
                setRows((previous) => [...previous, newRow()]);
              }}
              className="rounded-xl border border-[#bae6fd] px-4 py-2.5 text-sm font-medium text-[#0369a1]"
            >
              + 재료 추가
            </button>
          </fieldset>
          {(validationError || save.isError) && (
            <p
              role="alert"
              className="rounded-xl bg-red-50 p-3 text-sm text-red-700"
            >
              {validationError || errorMessage(save.error)}
            </p>
          )}
          {message && (
            <p
              role="status"
              className="rounded-xl bg-[#F0F9FF] p-3 text-sm text-[#0369a1]"
            >
              {message}
            </p>
          )}
          <button
            data-testid="menu-save"
            type="submit"
            disabled={save.isPending}
            className="rounded-xl bg-[#0EA5E9] px-4 py-2.5 text-sm font-semibold text-white hover:bg-[#0284c7] disabled:opacity-50"
          >
            {save.isPending
              ? "저장 중…"
              : editingId === null
                ? "메뉴 · 레시피 만들기"
                : "메뉴 · 레시피 저장"}
          </button>
        </form>
      )}
    </section>
  );
}

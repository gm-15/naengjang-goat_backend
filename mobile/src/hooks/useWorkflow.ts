import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { getClosingRecommendations, getDailyReport, getMenus, getPosMenuMappings, isBusinessDate, saveMenuRecipe, savePosMenuMapping, uploadPosSales } from "../api/workflow";
import type { MenuRecipePayload } from "../types/workflow";

export function useDailyReport(businessDate: string) {
  return useQuery({
    queryKey: ["daily-report", businessDate],
    queryFn: () => getDailyReport(businessDate),
    enabled: isBusinessDate(businessDate),
  });
}

export function useClosingRecommendations(businessDate: string) {
  return useQuery({
    queryKey: ["closing-recommendations", businessDate],
    queryFn: () => getClosingRecommendations(businessDate),
    enabled: isBusinessDate(businessDate),
  });
}

export function usePosMenuMappings() {
  return useQuery({ queryKey: ["pos-menu-mappings"], queryFn: getPosMenuMappings });
}

export function useMenus() {
  return useQuery({ queryKey: ["menus"], queryFn: getMenus });
}

export function useSaveMenuRecipe() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ menuId, payload }: { menuId: number | null; payload: MenuRecipePayload }) => saveMenuRecipe(menuId, payload),
    onSuccess: (menu) => {
      qc.setQueryData(["menus"], (old: Awaited<ReturnType<typeof getMenus>> | undefined) => {
        const existing = old ?? [];
        return existing.some((item) => item.menuId === menu.menuId)
          ? existing.map((item) => item.menuId === menu.menuId ? menu : item)
          : [...existing, menu];
      });
      void qc.invalidateQueries({ queryKey: ["menus"] });
    },
  });
}

export function useSavePosMenuMapping() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ posCode, menuId }: { posCode: string; menuId: number }) => savePosMenuMapping(posCode, menuId),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["pos-menu-mappings"] }),
  });
}

export function useUploadPosSales() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: uploadPosSales,
    onSuccess: async () => {
      await Promise.all([
        "daily-report", "closing-recommendations", "batches", "inventory-batches", "low-stock",
      ].map((key) => qc.invalidateQueries({ queryKey: [key] })));
    },
  });
}

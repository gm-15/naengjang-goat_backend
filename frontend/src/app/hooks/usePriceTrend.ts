import { useQuery } from "@tanstack/react-query";
import { getPriceTrend } from "../api/prices";

export function usePriceTrend(
  ingredientId: number | undefined,
  days = 30,
) {
  return useQuery({
    queryKey: ["price-trend", ingredientId, days],
    queryFn: () => getPriceTrend(ingredientId as number, days),
    enabled:
      typeof ingredientId === "number" && !Number.isNaN(ingredientId),
  });
}

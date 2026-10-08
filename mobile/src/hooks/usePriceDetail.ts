import { useQuery } from "@tanstack/react-query";
import { getPriceDetail } from "../api/prices";

export function usePriceDetail(id: number | undefined) {
  return useQuery({
    queryKey: ["price-detail", id],
    queryFn: () => getPriceDetail(id as number),
    enabled: typeof id === "number" && !Number.isNaN(id),
  });
}

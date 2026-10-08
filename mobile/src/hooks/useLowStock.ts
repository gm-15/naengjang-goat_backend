import { useQuery } from "@tanstack/react-query";
import { getLowStock } from "../api/orders";

export function useLowStock(limit = 5) {
  return useQuery({
    queryKey: ["low-stock", limit],
    queryFn: () => getLowStock(limit),
  });
}

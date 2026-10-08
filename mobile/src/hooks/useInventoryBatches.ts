import {
  useMutation,
  useQuery,
  useQueryClient,
} from "@tanstack/react-query";
import { createBatch, listBatches } from "../api/inventory";
import type { CreateBatchPayload } from "../types/inventory";

export function useBatches(ingredientId: number | undefined) {
  return useQuery({
    queryKey: ["batches", ingredientId],
    queryFn: () => listBatches(ingredientId as number),
    enabled: typeof ingredientId === "number" && !Number.isNaN(ingredientId),
  });
}

export function useCreateBatch() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (payload: CreateBatchPayload) => createBatch(payload),
    onSuccess: (_data, variables) => {
      // 해당 재료의 배치 목록만 invalidate
      qc.invalidateQueries({
        queryKey: ["batches", variables.ingredientId],
      });
      // 재고 부족도 영향받을 수 있어 같이
      qc.invalidateQueries({ queryKey: ["low-stock"] });
      qc.invalidateQueries({ queryKey: ["closing-recommendations"] });
      qc.invalidateQueries({ queryKey: ["daily-report"] });
    },
  });
}

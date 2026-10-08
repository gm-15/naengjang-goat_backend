import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { createBatch, listBatches } from "../api/inventory";
import type { CreateBatchPayload } from "../types/inventory";
import { invalidateOperations } from "../lib/workflow";

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
      invalidateOperations(qc);
    },
  });
}

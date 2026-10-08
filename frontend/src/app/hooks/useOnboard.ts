import { useMutation } from "@tanstack/react-query";
import { onboard } from "../api/users";
import type { OnboardPayload } from "../types/onboard";
import { useQueryClient } from "@tanstack/react-query";
import { invalidateOperations } from "../lib/workflow";

export function useOnboard() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (payload: OnboardPayload) => onboard(payload),
    onSuccess: () => {
      invalidateOperations(qc);
      qc.invalidateQueries({ queryKey: ["menus"] });
    },
  });
}

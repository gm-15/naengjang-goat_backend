import { useMutation } from "@tanstack/react-query";
import { onboard } from "../api/users";
import type { OnboardPayload } from "../types/onboard";

export function useOnboard() {
  return useMutation({
    mutationFn: (payload: OnboardPayload) => onboard(payload),
  });
}

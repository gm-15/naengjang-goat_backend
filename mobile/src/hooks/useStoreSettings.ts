import {
  useMutation,
  useQuery,
  useQueryClient,
} from "@tanstack/react-query";
import { getStoreSettings, updateStoreSettings } from "../api/settings";
import type { UpdateStoreSettingsPayload } from "../types/settings";

const SETTINGS_KEY = ["store-settings"] as const;

export function useStoreSettings() {
  return useQuery({
    queryKey: SETTINGS_KEY,
    queryFn: getStoreSettings,
  });
}

export function useUpdateStoreSettings() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (payload: UpdateStoreSettingsPayload) =>
      updateStoreSettings(payload),
    onSuccess: (data) => {
      qc.setQueryData(SETTINGS_KEY, data);
      // 재고 부족 계산이 store_settings 의존이라 같이 invalidate
      qc.invalidateQueries({ queryKey: ["low-stock"] });
      qc.invalidateQueries({ queryKey: ["closing-recommendations"] });
    },
  });
}

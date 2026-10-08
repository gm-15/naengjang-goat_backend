import { useQuery } from "@tanstack/react-query";
import { getClosingNotification } from "../api/closing";

export function useClosingNotification(id: number | undefined) {
  return useQuery({
    queryKey: ["closing-notification", id],
    queryFn: () => getClosingNotification(id as number),
    enabled: typeof id === "number" && !Number.isNaN(id),
  });
}

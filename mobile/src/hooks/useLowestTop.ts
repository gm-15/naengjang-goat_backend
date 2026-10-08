import { useQuery } from "@tanstack/react-query";
import { listLowestTop } from "../api/prices";

export function useLowestTop(limit = 5) {
  return useQuery({
    queryKey: ["lowest-top", limit],
    queryFn: () => listLowestTop(limit),
  });
}

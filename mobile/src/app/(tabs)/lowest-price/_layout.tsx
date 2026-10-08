import { Stack } from "expo-router";
import { C } from "../../../components/theme";

export default function LowestPriceStack() {
  return <Stack screenOptions={{ headerShown: false, contentStyle: { backgroundColor: C.bg } }} />;
}

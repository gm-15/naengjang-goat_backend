import { Redirect, Tabs } from "expo-router";
import { Ionicons } from "@expo/vector-icons";
import { useSafeAreaInsets } from "react-native-safe-area-context";
import { authStorage } from "../../lib/auth-storage";
import { C } from "../../components/theme";
import type { IconName } from "../../components/ui";

const TABS: Array<{ name: string; title: string; icon: IconName; iconOn: IconName }> = [
  { name: "main", title: "메인", icon: "home-outline", iconOn: "home" },
  { name: "lowest-price", title: "최저가", icon: "pricetag-outline", iconOn: "pricetag" },
  { name: "inventory", title: "재고", icon: "cube-outline", iconOn: "cube" },
  { name: "order", title: "발주", icon: "clipboard-outline", iconOn: "clipboard" },
  { name: "operations", title: "운영", icon: "bar-chart-outline", iconOn: "bar-chart" },
  { name: "settings", title: "설정", icon: "settings-outline", iconOn: "settings" },
];

export default function TabLayout() {
  const insets = useSafeAreaInsets();
  if (!authStorage.isAuthenticated()) return <Redirect href="/login" />;

  return (
    <Tabs
      screenOptions={{
        headerShown: false,
        tabBarActiveTintColor: C.primary,
        tabBarInactiveTintColor: C.textMute,
        tabBarLabelStyle: { fontSize: 11, fontWeight: "600" },
        tabBarStyle: {
          height: 64 + insets.bottom,
          paddingTop: 6,
          paddingBottom: insets.bottom + 8,
          borderTopColor: C.border,
          backgroundColor: "#fff",
        },
        sceneStyle: { backgroundColor: C.bg },
      }}
    >
      {TABS.map((t) => (
        <Tabs.Screen
          key={t.name}
          name={t.name}
          options={{
            title: t.title,
            tabBarIcon: ({ color, focused, size }) => (
              <Ionicons name={focused ? t.iconOn : t.icon} size={size - 2} color={color} />
            ),
          }}
        />
      ))}
    </Tabs>
  );
}

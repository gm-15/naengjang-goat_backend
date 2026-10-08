import { createBrowserRouter } from "react-router";
import MainPage from "./pages/MainPage";
import LowestPricePage from "./pages/LowestPricePage";
import LowestPriceDetailPage from "./pages/LowestPriceDetailPage";
import OrderPage from "./pages/OrderPage";
import LoginPage from "./pages/LoginPage";
import OnboardPage from "./pages/OnboardPage";
import InventoryPage from "./pages/InventoryPage";
import SettingsPage from "./pages/SettingsPage";
import OperationsPage from "./pages/OperationsPage";
import NotificationDetailPage from "./pages/NotificationDetailPage";
import { Navigate, Outlet } from "react-router";
import { authStorage } from "./lib/auth-storage";

function ProtectedRoutes() {
  return authStorage.isAuthenticated() ? (
    <Outlet />
  ) : (
    <Navigate to="/" replace />
  );
}

export const router = createBrowserRouter([
  {
    path: "/",
    Component: LoginPage,
  },
  {
    path: "/onboard",
    Component: OnboardPage,
  },
  {
    Component: ProtectedRoutes,
    children: [
      {
        path: "/main",
        Component: MainPage,
      },
      {
        path: "/lowest-price",
        Component: LowestPricePage,
      },
      {
        path: "/lowest-price/:id",
        Component: LowestPriceDetailPage,
      },
      {
        path: "/order",
        Component: OrderPage,
      },
      {
        path: "/inventory",
        Component: InventoryPage,
      },
      {
        path: "/settings",
        Component: SettingsPage,
      },
      { path: "/operations", Component: OperationsPage },
      { path: "/notifications/:id", Component: NotificationDetailPage },
    ],
  },
  { path: "*", element: <Navigate to="/main" replace /> },
]);

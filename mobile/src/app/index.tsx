import { Redirect } from "expo-router";
import { authStorage } from "../lib/auth-storage";

export default function Index() {
  return <Redirect href={authStorage.isAuthenticated() ? "/main" : "/login"} />;
}

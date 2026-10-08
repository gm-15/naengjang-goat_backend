import { defineConfig, loadEnv, type ProxyOptions } from "vite";
import path from "path";
import tailwindcss from "@tailwindcss/vite";
import react from "@vitejs/plugin-react";

function figmaAssetResolver() {
  return {
    name: "figma-asset-resolver",
    resolveId(id) {
      if (id.startsWith("figma:asset/")) {
        const filename = id.replace("figma:asset/", "");
        return path.resolve(__dirname, "src/assets", filename);
      }
    },
  };
}

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), "");
  const target = env.VITE_API_TARGET || "http://127.0.0.1:8080";
  const forwarding: ProxyOptions = {
    target,
    changeOrigin: true,
    configure(proxy) {
      // The browser calls this Vite origin; forwarding to Spring is a server request.
      proxy.on("proxyReq", (request) => request.removeHeader("origin"));
    },
  };
  const proxy: Record<string, ProxyOptions> = {
    "^/(api|ingredients|prices|purchase-orders|pos|reports|closing|menus)(/|\\?|$)": forwarding,
    "/inventory/": forwarding,
    "/settings": {
      ...forwarding,
      bypass(req) {
        if (req.headers.accept?.includes("text/html")) return req.url;
      },
    },
  };
  return {
    server: { proxy },
    preview: { proxy },
    plugins: [
      figmaAssetResolver(),
      // The React and Tailwind plugins are both required for Make, even if
      // Tailwind is not being actively used – do not remove them
      react(),
      tailwindcss(),
    ],
    resolve: {
      alias: {
        // Alias @ to the src directory
        "@": path.resolve(__dirname, "./src"),
      },
    },

    // File types to support raw imports. Never add .css, .tsx, or .ts files to this.
    assetsInclude: ["**/*.svg", "**/*.csv"],
  };
});

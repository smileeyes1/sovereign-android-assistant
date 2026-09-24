import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import { VitePWA } from "vite-plugin-pwa";

export default defineConfig({
  plugins: [
    react(),
    VitePWA({
      registerType: "autoUpdate",
      includeAssets: ["icon.svg"],
      manifest: {
        name: "حكيم | مصنع المعلم الفلسطيني",
        short_name: "حكيم",
        description: "مصنع محلي أولًا للمواد التعليمية الفلسطينية",
        lang: "ar",
        dir: "rtl",
        display: "standalone",
        start_url: "./",
        scope: "./",
        background_color: "#ffffff",
        theme_color: "#18352a",
        icons: [
          { src: "icon.svg", sizes: "any", type: "image/svg+xml", purpose: "any" },
          { src: "icon.svg", sizes: "any", type: "image/svg+xml", purpose: "maskable" }
        ]
      },
      workbox: {
        navigateFallback: "index.html",
        globPatterns: ["**/*.{js,css,html,svg,woff2}"]
      }
    })
  ],
  base: "./",
  build: {
    target: "es2022",
    sourcemap: false
  }
});
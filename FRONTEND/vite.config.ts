import { defineConfig, loadEnv } from 'vite';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';
import path from 'path';

export default defineConfig(({ mode }) => {
  // .env file load karo — sirf VITE_ prefixed variables.
  // NOTE: Pehle `loadEnv(mode, process.cwd(), '')` tha (empty prefix) — jo
  // non-VITE secrets ko bhi config me le aata tha. Ab empty prefix hataya gaya
  // hai taaki sirf client-safe VITE_* vars load hon.
  const env = loadEnv(mode, process.cwd(), 'VITE_');

  const backendUrl = env.VITE_BACKEND_URL || 'http://localhost:2911';

  return {
    plugins: [react(), tailwindcss()],

    server: {
      port: 1304,
      strictPort: true,

      // Dev proxy — CORS issues avoid honge.
      // NOTE: Ye tabhi use hota hai jab frontend RELATIVE base URL use kare
      // (VITE_API_BASE_URL=/api/v1). Absolute URL (http://localhost:2911/api/v1)
      // set karne par requests seedha backend jaati hain aur proxy bypass ho jata hai.
      proxy: {
        '/api': {
          target: backendUrl,
          changeOrigin: true,
          secure: false,
        },
        '/oauth2': {
          target: backendUrl,
          changeOrigin: true,
          secure: false,
        },
        '/login/oauth2': {
          target: backendUrl,
          changeOrigin: true,
          secure: false,
        },
      },
    },

    resolve: {
      alias: {
        '@': path.resolve(__dirname, './src'),
      },
    },

    build: {
      outDir: 'dist',
      sourcemap: false,
      chunkSizeWarningLimit: 1500,
      rollupOptions: {
        output: {
          manualChunks: {
            // React core alag chunk mein
            'react-vendor': ['react', 'react-dom', 'react-router-dom'],
            // Video player alag chunk mein (sabse bada)
            'video-vendor': ['video.js'],
            // UI libraries alag chunk mein
            'ui-vendor': ['lucide-react', 'react-toastify', 'flowbite-react'],
            // State management
            'state-vendor': ['zustand', 'axios'],
          },
        },
      },
    },
  };
});

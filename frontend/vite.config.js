import { defineConfig, loadEnv } from 'vite';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '');
  const backendPort = env.VITE_BACKEND_PORT || '8080';
  const backendTarget = `http://localhost:${backendPort}`;

  return {
    plugins: [react(), tailwindcss()],
    server: {
      port: 5173,
      proxy: {
        '/api': { target: backendTarget, changeOrigin: true },
        '/ws': { target: backendTarget, changeOrigin: true, ws: true },
      },
    },
  };
});
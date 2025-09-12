import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import { fileURLToPath, URL } from 'node:url';

declare const process: any;
const backendHost = process.env.BACKEND_HOST || 'localhost';
const backendPort = process.env.BACKEND_PORT || '8080';
const backend = `http://${backendHost}:${backendPort}`;

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) },
  },
  server: {
    proxy: { '/api': { target: backend, changeOrigin: true, secure: false } },
  },
  build: {
    sourcemap: true,   // ← добавил
  },
});

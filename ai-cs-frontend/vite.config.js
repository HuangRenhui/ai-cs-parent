import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      '/api/rag': { target: 'http://localhost:8080', changeOrigin: true },
      '/api/image': { target: 'http://localhost:8080', changeOrigin: true },
      '/api/audio': { target: 'http://localhost:8080', changeOrigin: true },
      '/api/prompt': { target: 'http://localhost:8080', changeOrigin: true },
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api/, '')
      },
      '/ws': {
        target: 'ws://localhost:8081',
        ws: true,
        changeOrigin: true
      }
    }
  }
})

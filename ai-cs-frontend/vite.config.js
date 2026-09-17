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
      // 以下知识服务接口本身带 /api 前缀，不能走下面的通配改写，否则会 404
      '/api/multimodal-knowledge': { target: 'http://localhost:8080', changeOrigin: true },
      '/api/multimodal': { target: 'http://localhost:8080', changeOrigin: true },
      '/api/knowledge-graph': { target: 'http://localhost:8080', changeOrigin: true },
      '/api/video': { target: 'http://localhost:8080', changeOrigin: true },
      '/api/document': { target: 'http://localhost:8080', changeOrigin: true },
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

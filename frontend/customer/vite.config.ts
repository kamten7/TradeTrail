import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: { port: 5173, proxy: { '/mock': 'http://127.0.0.1:8080', '/after-sales': 'http://127.0.0.1:8080', '/products': 'http://127.0.0.1:8080', '/cart': 'http://127.0.0.1:8080', '/orders': 'http://127.0.0.1:8080', '/session': 'http://127.0.0.1:8080', '/login': 'http://127.0.0.1:8080', '/logout': 'http://127.0.0.1:8080' } }
})

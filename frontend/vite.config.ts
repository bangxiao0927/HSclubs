import { fileURLToPath, URL } from 'node:url'

import { defineConfig, loadEnv, type Plugin } from 'vite'
import vue from '@vitejs/plugin-vue'
import vueDevTools from 'vite-plugin-vue-devtools'

const resolvedPort = Number(process.env.FRONTEND_PORT ?? process.env.PORT ?? '4173')
const devServerPort = Number.isFinite(resolvedPort) && resolvedPort > 0 ? resolvedPort : 4173

const escapeHtml = (value: string) =>
  value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#039;')

const schoolBrandTitlePlugin = (): Plugin => {
  let brandName = 'HS Clubs'

  return {
    name: 'school-brand-title',
    configResolved(config) {
      const env = loadEnv(config.mode, config.envDir, '')
      brandName = process.env.VITE_BRAND_NAME?.trim() || env.VITE_BRAND_NAME?.trim() || brandName
    },
    transformIndexHtml(html) {
      return html.replace('<title></title>', `<title>${escapeHtml(brandName)}</title>`)
    },
  }
}

// https://vite.dev/config/
export default defineConfig({
  plugins: [vue(), vueDevTools(), schoolBrandTitlePlugin()],
  server: {
    host: '0.0.0.0',
    port: devServerPort,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/oauth2': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
    allowedHosts: ['hsclubs.net'],
  },
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
})

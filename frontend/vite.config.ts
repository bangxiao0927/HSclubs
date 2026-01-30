import { fileURLToPath, URL } from 'node:url'

import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import vueDevTools from 'vite-plugin-vue-devtools'

const resolvedPort = Number(process.env.FRONTEND_PORT ?? process.env.PORT ?? '4173')
const devServerPort = Number.isFinite(resolvedPort) && resolvedPort > 0 ? resolvedPort : 4173

// https://vite.dev/config/
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')

  const envProxyTarget = env.VITE_API_BASE_URL ?? process.env.VITE_API_BASE_URL
  const rawProxyTarget = (envProxyTarget ?? 'http://localhost:8080').trim().replace(/\/$/, '')
  const proxySecure = rawProxyTarget.startsWith('https://') ? false : true

  const buildProxyConfig = () => ({
    target: rawProxyTarget,
    changeOrigin: true,
    secure: proxySecure,
  })

  return {
    plugins: [
      vue(),
      vueDevTools(),
    ],
    server: {
      host: '0.0.0.0',
      port: devServerPort,
      proxy: {
        '/api': buildProxyConfig(),
        '/oauth2': buildProxyConfig(),
      },
      allowedHosts: [
        'hsclubs.net',
      ],
    },
    resolve: {
      alias: {
        '@': fileURLToPath(new URL('./src', import.meta.url))
      },
    },
  }
})

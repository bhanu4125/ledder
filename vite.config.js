import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import fs from 'node:fs'
import path from 'node:path'

function netlifyRedirectsPlugin() {
  return {
    name: 'netlify-redirects',
    closeBundle() {
      const rawBackend = process.env.BACKEND_URL || 'https://ledgerline-backend.onrender.com'
      const backendUrl = rawBackend.replace(/\/+$/, '')
      const content = `/api/*  ${backendUrl}/api/:splat  200!\n/*      /index.html               200\n`
      try {
        fs.writeFileSync(path.resolve(__dirname, 'dist/_redirects'), content, 'utf-8')
      } catch (err) {
        console.warn('Could not write dist/_redirects:', err.message)
      }
    },
  }
}

export default defineConfig({
  plugins: [react(), netlifyRedirectsPlugin()],
  server: {
    proxy: {
      '/api': {
        target: process.env.API_PROXY_TARGET || 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
})

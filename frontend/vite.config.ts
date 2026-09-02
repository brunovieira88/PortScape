import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'
import tailwindcss from '@tailwindcss/vite'

// https://vite.dev/config/
export default defineConfig(({ mode }) => ({
  // A GitHub Pages project site is served from /<repo>/, not /. Every asset URL
  // Vite emits has to carry that prefix or the deployed page loads a blank screen
  // with 404s in the console -- this only applies to the `demo` build, never to a
  // real deployment behind its own domain.
  base: mode === 'demo' ? '/PortScape/' : '/',
  plugins: [react(), tailwindcss()],
  server: {
    proxy: {
      '/api': 'http://localhost:8080'
    }
  }
}))

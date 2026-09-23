import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  server: {
    // 5174 y no 5173: el 5173 lo usa el frontend del car-wash. Cada proyecto su carril.
    port: 5174,
    // Accesible desde la tablet y el celular por la red local. Es el punto entero del
    // sistema: se vende desde el pasillo y el ticket sale en el mostrador.
    host: true,
    proxy: {
      // Evita CORS en desarrollo: el navegador habla solo con Vite y Vite reenvía al backend.
      '/api': {
        target: 'http://localhost:8081',
        changeOrigin: true,
      },
    },
  },
})

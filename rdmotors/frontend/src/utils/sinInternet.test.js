import { test } from 'node:test'
import assert from 'node:assert/strict'
import { readdirSync, readFileSync, statSync } from 'node:fs'
import { join } from 'node:path'
import { fileURLToPath } from 'node:url'

import { loQuePideDeInternet } from './sinInternet.js'

const UTILS = fileURLToPath(new URL('.', import.meta.url))
const SRC = join(UTILS, '..')
const FRONTEND = join(SRC, '..')

function archivos(carpeta, extensiones) {
  const encontrados = []
  for (const entrada of readdirSync(carpeta)) {
    const ruta = join(carpeta, entrada)
    if (statSync(ruta).isDirectory()) {
      encontrados.push(...archivos(ruta, extensiones))
    } else if (extensiones.some((e) => entrada.endsWith(e))) {
      encontrados.push(ruta)
    }
  }
  return encontrados
}

test('reconoce una fuente de Google o un CDN metidos en el CSS', () => {
  assert.deepEqual(loQuePideDeInternet('body { color: red }'), [])
  assert.equal(loQuePideDeInternet("@import url('https://fonts.googleapis.com/css2?family=Inter');").length, 3)
  assert.equal(loQuePideDeInternet('<script src="https://cdn.jsdelivr.net/npm/chart.js"></script>').length, 2)
  // Un comentario que nombra la IP de la tienda NO es pedir nada de afuera.
  assert.deepEqual(loQuePideDeInternet('// la tablet entra por http://192.168.1.5:5174'), [])
})

test('RF-002: NADA de la pantalla se descarga de internet (ni fuentes, ni íconos, ni librerías)', () => {
  const revisados = [join(FRONTEND, 'index.html'), ...archivos(SRC, ['.css', '.jsx', '.js', '.html'])]
    // Las pruebas no se envían al navegador, y `sinInternet.js` lleva los patrones escritos: se encontraría a sí mismo.
    .filter((ruta) => !ruta.endsWith('.test.js') && !ruta.endsWith('sinInternet.js'))

  const problemas = revisados
    .map((ruta) => ({ ruta, pide: loQuePideDeInternet(readFileSync(ruta, 'utf8')) }))
    .filter(({ pide }) => pide.length > 0)
    .map(({ ruta, pide }) => `${ruta.replace(FRONTEND, '')}: ${pide.join(', ')}`)

  assert.deepEqual(problemas, [],
    'Sin internet, la tienda tiene que verse igual. Lo que se traiga de afuera se cae el día que se cae la red.')
  assert.ok(revisados.length > 20, 'la prueba tiene que estar mirando el proyecto de verdad')
})

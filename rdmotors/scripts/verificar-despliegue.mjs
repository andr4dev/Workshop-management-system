/**
 * Comprueba un despliegue de RD MOTORS de punta a punta (spec 0011).
 *
 * Se corre contra el contenedor local antes de desplegar, y contra el sitio de verdad después. Lo que revisa es
 * justo lo que se rompe al mudar un sistema de sitio: que la pantalla se sirva, que las direcciones de la API no
 * devuelvan la pantalla, que el reloj de afuera pueda entrar con su llave, y que la copia de la base se pueda
 * bajar entera.
 *
 *   # contra el contenedor local
 *   SITIO=http://localhost:8082 LLAVE_TAREAS=... USUARIO=ruben CLAVE=... node scripts/verificar-despliegue.mjs
 *
 *   # contra el sitio de verdad
 *   SITIO=https://rdmotors-xxxx.run.app LLAVE_TAREAS=... USUARIO=... CLAVE=... node scripts/verificar-despliegue.mjs
 *
 * NO deja el archivo del respaldo en el disco a menos que se le diga DESTINO=ruta: una copia de la base tirada en
 * una carpeta cualquiera es justo lo que este sistema trata de evitar.
 */
import { writeFileSync } from 'node:fs'

const H = (process.env.SITIO ?? 'http://localhost:8082').replace(/\/$/, '')
const LLAVE = process.env.LLAVE_TAREAS ?? ''
const USUARIO = process.env.USUARIO ?? 'ruben'
const CLAVE = process.env.CLAVE ?? ''

if (!CLAVE) {
  console.log('Falta la contraseña: USUARIO=... CLAVE=... node scripts/verificar-despliegue.mjs')
  process.exit(1)
}
console.log(`Comprobando ${H}\n`)

const resultados = []
const revisar = (fase, que, ok, detalle = '') => {
  resultados.push({ fase, que, ok, detalle })
  console.log(`${ok ? '  OK ' : '  NO '} [F${fase}] ${que}${detalle ? ' · ' + detalle : ''}`)
}

// Un navegador de mentira: guarda cookies y manda el token anti-CSRF.
const galletas = new Map()
const guardar = (r) => {
  for (const l of r.headers.getSetCookie?.() ?? []) {
    const [p] = l.split(';')
    const i = p.indexOf('=')
    galletas.set(p.slice(0, i).trim(), p.slice(i + 1).trim())
  }
}
const pedir = async (metodo, ruta, cuerpo, extra = {}) => {
  const h = { ...extra }
  if (cuerpo !== undefined) h['Content-Type'] = 'application/json'
  if (galletas.size) h.Cookie = [...galletas].map(([k, v]) => `${k}=${v}`).join('; ')
  if (metodo !== 'GET' && galletas.has('XSRF-TOKEN')) h['X-XSRF-TOKEN'] = galletas.get('XSRF-TOKEN')
  const r = await fetch(H + ruta, { method: metodo, headers: h, body: cuerpo ? JSON.stringify(cuerpo) : undefined })
  guardar(r)
  return r
}

// ── Fase 1: un solo servidor, con la pantalla adentro ────────────────────────
const raiz = await pedir('GET', '/')
const htmlRaiz = await raiz.text()
revisar(1, 'la raíz devuelve la pantalla', raiz.status === 200 && htmlRaiz.includes('<div id="root">'))

for (const ruta of ['/caja', '/reportes', '/inventario', '/respaldo']) {
  const r = await pedir('GET', ruta)
  const html = await r.text()
  revisar(1, `recargar en ${ruta} no da "no encontrado"`, r.status === 200 && html.includes('<div id="root">'))
}

const apiFantasma = await pedir('GET', '/api/instalacion/no-existe')
const cuerpoFantasma = await apiFantasma.text()
revisar(1, 'una dirección pública de la API que no existe NO devuelve la pantalla',
  apiFantasma.status === 404 && !cuerpoFantasma.includes('<div id="root">'))

const assets = await pedir('GET', '/assets/no-existe-abc.js')
await assets.text()
revisar(1, 'un archivo que no está es 404, no la pantalla', assets.status === 404)

// ── Fase 3: el reloj de afuera ───────────────────────────────────────────────
const salud = await pedir('GET', '/api/salud')
const cuerpoSalud = await salud.text()
revisar(3, 'la salud responde sin sesión y no cuenta nada del negocio',
  salud.status === 200 && cuerpoSalud === '{"ok":true}', cuerpoSalud)

if (!LLAVE) console.log('  (sin LLAVE_TAREAS solo se comprueba que sin llave no pasa nada)')
const sinLlave = await pedir('POST', '/api/tareas/correos')
const cuerpoSinLlave = await sinLlave.text()
revisar(3, 'disparar tareas sin llave: "no existe" y sin pistas',
  sinLlave.status === 404 && !/llave/i.test(cuerpoSinLlave))

const llaveMala = await pedir('POST', '/api/tareas/correos', undefined, { 'X-RDMOTORS-LLAVE': LLAVE + 'x' })
await llaveMala.text()
revisar(3, 'con la llave equivocada, tampoco', llaveMala.status === 404)

if (LLAVE) {
  const conLlave = await pedir('POST', '/api/tareas/correos', undefined, { 'X-RDMOTORS-LLAVE': LLAVE })
  const cuerpoConLlave = await conLlave.text()
  revisar(3, 'con la llave, la tarea corre', conLlave.status === 200 && cuerpoConLlave.includes('intentados'),
    cuerpoConLlave)
}

// ── Fase 2: entrar, y que un reinicio no saque a nadie ───────────────────────
const entrada = await pedir('POST', '/api/sesion', { usuario: USUARIO, contrasena: CLAVE })
const cuerpoEntrada = await entrada.text()
revisar(2, `entrar como ${USUARIO}`, entrada.status === 200, entrada.status === 200 ? '' : cuerpoEntrada)
if (entrada.status !== 200) {
  console.log('\nSin sesión no se puede seguir. ¿Otro usuario? USUARIO=... CLAVE=... node verificar-0011.mjs')
  process.exit(1)
}

const quienSoy = await pedir('GET', '/api/sesion')
const yo = JSON.parse(await quienSoy.text())
revisar(2, 'la sesión dice quién soy', quienSoy.status === 200, `${yo.nombre} · ${yo.rol}`)

// ── Fase 4: bajar la copia ───────────────────────────────────────────────────
const estadoAntes = JSON.parse(await (await pedir('GET', '/api/respaldos')).text())
revisar(4, 'el estado del respaldo ya no habla de carpetas ni de discos',
  !('carpeta' in estadoAntes) && !('segundaCarpeta' in estadoAntes) && 'diasSinBajar' in estadoAntes,
  `diasSinBajar=${estadoAntes.diasSinBajar} · avisa=${estadoAntes.hayQueAvisar}`)

const descarga = await pedir('GET', '/api/respaldos/archivo')
const bytes = Buffer.from(await descarga.arrayBuffer())
const comoSeLlama = descarga.headers.get('content-disposition') ?? ''
const nombre = comoSeLlama.match(/filename="([^"]+)"/)?.[1] ?? ''
revisar(4, 'la copia se baja con su nombre', descarga.status === 200 && /^rdmotors-.*\.dump$/.test(nombre), nombre)
revisar(4, 'el archivo llegó entero (un volcado custom empieza por PGDMP)',
  bytes.length > 1000 && bytes.subarray(0, 5).toString() === 'PGDMP', `${(bytes.length / 1024).toFixed(1)} KB`)

if (nombre && process.env.DESTINO) {
  writeFileSync(process.env.DESTINO, bytes)
  console.log(`  ·  guardado en ${process.env.DESTINO}`)
}

const estadoDespues = JSON.parse(await (await pedir('GET', '/api/respaldos')).text())
revisar(4, 'bajarla queda registrada y el aviso se apaga',
  estadoDespues.diasSinBajar === 0 && estadoDespues.hayQueAvisar === false,
  `copias registradas: ${estadoDespues.copias.length}`)

const dos = await pedir('GET', '/api/respaldos/archivo')
const bytesDos = Buffer.from(await dos.arrayBuffer())
const nombreDos = (dos.headers.get('content-disposition') ?? '').match(/filename="([^"]+)"/)?.[1] ?? ''
revisar(4, 'dos copias seguidas no se pisan el nombre', nombre !== nombreDos && bytesDos.length > 1000,
  `${nombre} / ${nombreDos}`)

// ── Resumen ──────────────────────────────────────────────────────────────────
const fallaron = resultados.filter((r) => !r.ok)
console.log(`\n${resultados.length - fallaron.length}/${resultados.length} comprobaciones bien`)
if (fallaron.length) {
  console.log('FALLARON:')
  for (const f of fallaron) console.log(`  [F${f.fase}] ${f.que} ${f.detalle}`)
  process.exit(1)
}

/**
 * La cartera en papel (spec 0008, H12): la misma lista que se está viendo, con su filtro, para imprimirla o guardarla
 * como PDF desde el diálogo de impresión.
 *
 * En hoja carta, no en 80 mm: esto no es un comprobante para el cliente, es una lista para el administrador.
 */
import { formatoCOP } from './formato.js'
import { esc } from './papel80mm.js'
import { desdeCuandoEnPalabras, fechaCorta, textoPendientes } from './cartera.js'

/** "Los que deben · fiado entre el 1 y el 21 de sept" — qué se está viendo, para que el papel no mienta. */
export function tituloDelFiltro({ vista, modoFecha, desde, hasta }) {
  const quienes = vista === 'HISTORIAL' ? 'Historial completo' : 'Los que deben'
  if (!desde || !hasta) return quienes
  const que = modoFecha === 'ABONO' ? 'abonaron' : 'se les fió'
  return `${quienes} · ${que} entre el ${fechaCorta(desde)} y el ${fechaCorta(hasta)}`
}

/**
 * @param lista  lo que devolvió `GET /api/cartera`
 * @param filtro lo que se está viendo
 * @param tienda los datos de la tienda
 */
export function htmlDeLaCartera(lista, filtro, tienda, hoy) {
  const filas = lista.clientes.map((c) => `
    <tr>
      <td>${esc(c.nombre)}${c.fiadoCerrado ? ' <span class="marca">fiado cerrado</span>' : ''}</td>
      <td>${esc([c.documento, c.celular].filter(Boolean).join(' · '))}</td>
      <td>${esc(c.debe > 0 ? [textoPendientes(c.pendientes), desdeCuandoEnPalabras(c.desde, hoy)].filter(Boolean).join(' · ') : 'Al día')}</td>
      <td class="cifra">${esc(formatoCOP(c.debe))}</td>
    </tr>`).join('')

  return `<!doctype html>
<html lang="es"><head><meta charset="utf-8"><title>Cartera</title>
<style>
  @page { size: letter; margin: 14mm; }
  body { font-family: Arial, Helvetica, sans-serif; color: #000; font-size: 12px; }
  h1 { margin: 0 0 2px; font-size: 18px; }
  .sub { margin: 0 0 14px; color: #444; }
  table { width: 100%; border-collapse: collapse; }
  th, td { padding: 5px 4px; border-bottom: 1px solid #ccc; text-align: left; vertical-align: top; }
  th { border-bottom: 1.5px solid #000; font-size: 11px; text-transform: uppercase; letter-spacing: .04em; }
  .cifra { text-align: right; font-variant-numeric: tabular-nums; white-space: nowrap; }
  tfoot td { border-bottom: none; border-top: 1.5px solid #000; font-weight: bold; }
  .marca { font-size: 10px; color: #a00; }
  .pie { margin-top: 14px; color: #666; font-size: 10.5px; }
</style></head>
<body>
  <h1>${esc(tienda.nombreComercial ?? 'RD MOTORS')} · Cartera</h1>
  <p class="sub">${esc(tituloDelFiltro(filtro))} · ${lista.deben} ${lista.deben === 1 ? 'cliente debe' : 'clientes deben'}</p>
  <table>
    <thead><tr><th>Cliente</th><th>Cédula y celular</th><th>Desde cuándo</th><th class="cifra">Debe</th></tr></thead>
    <tbody>${filas}</tbody>
    <tfoot><tr><td colspan="3">Por cobrar</td><td class="cifra">${esc(formatoCOP(lista.porCobrar))}</td></tr></tfoot>
  </table>
  <p class="pie">Impreso el ${esc(fechaCorta(hoy))} · Documento interno</p>
</body></html>`
}

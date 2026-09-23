import { test } from 'node:test'
import assert from 'node:assert/strict'
import {
  coincidenciasParaMostrar, consultaDeTotales, consultaDelHistorial, filtrosDesdeUrl, hayFiltros, lineaDeLaFactura,
  normalizarFiltros,
  muestraLaBusqueda, partesResaltadas, problemaDeFechas, queLePasoAlRenglon, resumenDeTotales,
  rutaDelDetalle, sinTildes, sumaDeRenglones, TODAS,
} from './historial.js'

test('filtrosDesdeUrl deja vacío lo que no viene y lee la página', () => {
  const filtros = filtrosDesdeUrl(new URLSearchParams('formaPago=TRANSFERENCIA&p=2'))
  assert.equal(filtros.formaPago, 'TRANSFERENCIA')
  assert.equal(filtros.proveedorId, '')
  assert.equal(filtros.factura, '')
  assert.equal(filtros.pagina, 2)
  assert.equal(filtros.estado, 'VIGENTE', 'por defecto, las vigentes')
})

test('el estado: vigentes por defecto, "todas" no manda estado', () => {
  const base = { proveedorId: '', desde: '', hasta: '', formaPago: '', cuentaId: '', factura: '', pagina: 0 }
  assert.equal(consultaDelHistorial({ ...base, estado: 'VIGENTE' }).estado, 'VIGENTE')
  assert.equal(consultaDelHistorial({ ...base, estado: 'ANULADA' }).estado, 'ANULADA')
  assert.equal('estado' in consultaDelHistorial({ ...base, estado: 'TODAS' }), false)
  assert.equal(hayFiltros({ ...base, estado: 'VIGENTE' }), false)
  assert.equal(hayFiltros({ ...base, estado: 'ANULADA' }), true)
})

test('RF-027: debajo de la factura, los tres primeros que coinciden con su cantidad, y cuántos más', () => {
  const r = (n, marca, cantidad) => ({ lineaId: `l${n}`, codigo: `C${n}`, nombre: 'FILTRO DE ACEITE', marca, aplicacion: null, cantidad })
  const cinco = [r(1, 'INOKI', 10), r(2, 'FACTORY', 5), r(3, 'GENERICO', 2), r(4, 'NGK', 1), r(5, 'CBI', 3)]

  const { visibles, restantes } = coincidenciasParaMostrar(cinco, 'aceite')
  assert.deepEqual(visibles.map((v) => `${v.descripcion} × ${v.cantidad}`),
    ['FILTRO DE ACEITE INOKI × 10', 'FILTRO DE ACEITE FACTORY × 5', 'FILTRO DE ACEITE GENERICO × 2'])
  assert.equal(restantes, 2)

  assert.equal(coincidenciasParaMostrar(cinco.slice(0, 2), 'aceite').restantes, 0)
  assert.deepEqual(coincidenciasParaMostrar(undefined, 'aceite'), { visibles: [], restantes: 0 })
})

test('RF-027: si lo buscado no se ve en nombre ni marca, dice por qué salió: el código o la moto', () => {
  const filtro = { lineaId: 'l1', codigo: '352B59K', nombre: 'FILTRO ACEITE', marca: 'INOKI', aplicacion: 'PULSAR NS 200/DUKE 200', cantidad: 10 }

  assert.equal(coincidenciasParaMostrar([filtro], 'inoki').visibles[0].porque, null)
  assert.equal(coincidenciasParaMostrar([filtro], 'filtró').visibles[0].porque, null, 'sin tildes, como el backend')
  assert.equal(coincidenciasParaMostrar([filtro], '352b').visibles[0].porque, '352B59K')
  assert.equal(coincidenciasParaMostrar([filtro], 'duke').visibles[0].porque, 'PULSAR NS 200/DUKE 200')
})

test('RF-028: sin buscar, los primeros renglones con su cantidad y cuántos más; sin resaltar', () => {
  const r = (n, nombre, marca, cantidad) => ({ lineaId: `l${n}`, codigo: `C${n}`, nombre, marca, aplicacion: null, cantidad })
  const factura = {
    renglones: 4,
    primeros: [r(1, 'FILTRO DE ACEITE', 'INOKI', 25), r(2, 'PASTILLAS FRENO', 'CBI', 4), r(3, 'BUJÍA', 'NGK', 10)],
    coinciden: [],
  }

  const linea = lineaDeLaFactura(factura, '')
  assert.deepEqual(linea.visibles.map((v) => `${v.descripcion} × ${v.cantidad}`),
    ['FILTRO DE ACEITE INOKI × 25', 'PASTILLAS FRENO CBI × 4', 'BUJÍA NGK × 10'])
  assert.equal(linea.restantes, 1)
  assert.equal(linea.resaltar, false)
  assert.equal(lineaDeLaFactura(factura, '   ').resaltar, false, 'espacios no son una búsqueda')

  const una = lineaDeLaFactura({ renglones: 1, primeros: [r(1, 'CADENA', 'DID', 1)], coinciden: [] }, null)
  assert.deepEqual(una.visibles.map((v) => v.descripcion), ['CADENA DID'])
  assert.equal(una.restantes, 0)
  assert.deepEqual(lineaDeLaFactura({}, ''), { visibles: [], restantes: 0, resaltar: false })
})

test('RF-028: buscando un repuesto, la línea es la de RF-027: lo que coincide, resaltado', () => {
  const filtro = { lineaId: 'l2', codigo: '352B59K', nombre: 'FILTRO ACEITE', marca: 'INOKI', aplicacion: null, cantidad: 10 }
  const factura = {
    renglones: 2,
    primeros: [{ lineaId: 'l1', codigo: 'C1', nombre: 'PASTILLAS FRENO', marca: 'CBI', aplicacion: null, cantidad: 4 }, filtro],
    coinciden: [filtro],
  }

  const linea = lineaDeLaFactura(factura, 'aceite')
  assert.deepEqual(linea.visibles.map((v) => v.lineaId), ['l2'])
  assert.equal(linea.restantes, 0)
  assert.equal(linea.resaltar, true)
})

test('filtrosDesdeUrl no revienta con una página basura', () => {
  assert.equal(filtrosDesdeUrl(new URLSearchParams('p=abc')).pagina, 0)
})

test('en efectivo no hay cuenta: el filtro de cuenta se descarta', () => {
  assert.equal(normalizarFiltros({ formaPago: 'EFECTIVO', cuentaId: 'c1' }).cuentaId, '')
  assert.equal(normalizarFiltros({ formaPago: 'TRANSFERENCIA', cuentaId: 'c1' }).cuentaId, 'c1')
  assert.equal(normalizarFiltros({ formaPago: '', cuentaId: 'c1' }).cuentaId, 'c1')
})

test('problemaDeFechas rechaza el rango al revés y acepta el mismo día', () => {
  assert.match(problemaDeFechas('2026-09-30', '2026-09-01'), /posterior/)
  assert.equal(problemaDeFechas('2026-09-01', '2026-09-01'), null)
  assert.equal(problemaDeFechas('2026-09-01', ''), null)
  assert.equal(problemaDeFechas('', ''), null)
})

test('problemaDeFechas compara fechas, no números de mes sueltos', () => {
  // Como texto ISO, "2026-10-01" > "2026-09-30": el mes con dos dígitos lo hace funcionar.
  assert.equal(problemaDeFechas('2026-09-30', '2026-10-01'), null)
})

test('hayFiltros ignora la página', () => {
  assert.equal(hayFiltros({ proveedorId: '', factura: '', pagina: 3 }), false)
  assert.equal(hayFiltros({ proveedorId: '', factura: 'FV' }), true)
})

test('consultaDelHistorial solo manda lo que tiene valor', () => {
  assert.deepEqual(
    consultaDelHistorial({ proveedorId: 'p1', desde: '', hasta: '', formaPago: '', cuentaId: '',
      factura: '  fv-99 ', pagina: 1 }),
    { proveedorId: 'p1', factura: 'fv-99', estado: 'VIGENTE', pagina: 1, tamano: 25 })
})

test('sumaDeRenglones suma lo pagado por renglón', () => {
  assert.equal(sumaDeRenglones([{ costoTotal: 200000 }, { costoTotal: 85200 }]), 285200)
  assert.equal(sumaDeRenglones([]), 0)
  assert.equal(sumaDeRenglones(undefined), 0)
})

// ── Totales (spec 0002, H6) ────────────────────────────────────────────────

test('totales: los mismos filtros de la lista, sin página', () => {
  const consulta = consultaDeTotales({ proveedorId: 'p1', desde: '2026-09-01', estado: 'VIGENTE', pagina: 3 })
  assert.deepEqual(consulta, { proveedorId: 'p1', desde: '2026-09-01', estado: 'VIGENTE' })
})

test('totales: ver todas pide sin estado; ver solo anuladas no pide nada', () => {
  assert.deepEqual(consultaDeTotales({ estado: TODAS }), {})
  assert.equal(consultaDeTotales({ estado: 'ANULADA' }), null)
})

test('totales: efectivo, transferencia y el desglose por cuenta de mayor a menor', () => {
  const r = resumenDeTotales({
    total: 100000,
    compras: 4,
    partes: [
      { formaPago: 'EFECTIVO', cuentaId: null, cuenta: null, compras: 2, total: 30000 },
      { formaPago: 'TRANSFERENCIA', cuentaId: 'n', cuenta: 'Nequi', compras: 1, total: 30000 },
      { formaPago: 'TRANSFERENCIA', cuentaId: 'b', cuenta: 'Bancolombia', compras: 1, total: 40000 },
    ],
  })
  assert.deepEqual(r.efectivo, { total: 30000, compras: 2 })
  assert.equal(r.transferencia.total, 70000)
  assert.equal(r.transferencia.compras, 2)
  assert.deepEqual(r.transferencia.cuentas.map((c) => c.nombre), ['Bancolombia', 'Nequi'])
  assert.equal(r.descuadre, 0)
})

test('totales: sin compras todo queda en cero, no en blanco', () => {
  const r = resumenDeTotales({ total: 0, compras: 0, partes: [] })
  assert.deepEqual(r.efectivo, { total: 0, compras: 0 })
  assert.deepEqual(r.transferencia.cuentas, [])
  assert.equal(r.descuadre, 0)
  assert.equal(resumenDeTotales(null), null)
})

test('totales: si las partes no suman el total, el descuadre lo dice', () => {
  const r = resumenDeTotales({
    total: 50000, compras: 1, partes: [{ formaPago: 'EFECTIVO', compras: 1, total: 49000 }],
  })
  assert.equal(r.descuadre, -1000)
})

// ── Renglones antes de corregir ────────────────────────────────────────────

test('qué le pasó a un renglón: cambiado si otra versión ocupa su lugar, quitado si no', () => {
  const detalle = {
    renglones: [{ lineaId: 'v2-filtro', posicion: 0 }, { lineaId: 'nuevo', posicion: 2 }],
    reemplazados: [
      { lineaId: 'v1-filtro', posicion: 0, reemplazadaEn: '2026-09-14T17:00:00Z' },
      { lineaId: 'pastillas', posicion: 1, reemplazadaEn: '2026-09-14T17:00:00Z' },
    ],
  }
  assert.equal(queLePasoAlRenglon(detalle.reemplazados[0], detalle), 'CAMBIADO')
  assert.equal(queLePasoAlRenglon(detalle.reemplazados[1], detalle), 'QUITADO')
})

test('en la cadena 12 → 17 → quitado, el de 12 se cambió y el de 17 se quitó', () => {
  const doce = { lineaId: 'doce', posicion: 0, reemplazadaEn: '2026-09-14T17:00:00Z' }
  const diecisiete = { lineaId: 'diecisiete', posicion: 0, reemplazadaEn: '2026-09-14T18:00:00Z' }
  const detalle = { renglones: [{ lineaId: 'otro', posicion: 1 }], reemplazados: [doce, diecisiete] }
  assert.equal(queLePasoAlRenglon(doce, detalle), 'CAMBIADO')
  assert.equal(queLePasoAlRenglon(diecisiete, detalle), 'QUITADO')
})

// ── Buscar por repuesto (spec 0002, H9) ────────────────────────────────────

test('buscar repuesto: vive en la URL y se manda al backend como los demás filtros', () => {
  const filtros = filtrosDesdeUrl(new URLSearchParams('repuesto=inoki'))
  assert.equal(filtros.repuesto, 'inoki')
  assert.equal(hayFiltros(filtros), true)
  assert.equal(consultaDelHistorial({ ...filtros, repuesto: '  inoki ' }).repuesto, 'inoki')
  assert.equal(consultaDeTotales(filtros).repuesto, 'inoki', 'los totales son los de la lista')
})

test('la dirección del detalle lleva la búsqueda, codificada, solo si la hay', () => {
  assert.equal(rutaDelDetalle('c1', ''), '/compras/historial/c1')
  assert.equal(rutaDelDetalle('c1', '   '), '/compras/historial/c1')
  assert.equal(rutaDelDetalle('c1', ' bujía ngk '), '/compras/historial/c1?repuesto=buj%C3%ADa+ngk')
})

test('partesResaltadas: sin distinguir mayúsculas, todas las veces, y el texto se reconstruye igual', () => {
  const partes = partesResaltadas('FILTRO INOKI · filtro', 'filtro')
  assert.deepEqual(partes, [
    { texto: 'FILTRO', resaltado: true },
    { texto: ' INOKI · ', resaltado: false },
    { texto: 'filtro', resaltado: true },
  ])
  assert.equal(partes.map((p) => p.texto).join(''), 'FILTRO INOKI · filtro')
  assert.deepEqual(partesResaltadas('BUJÍA ÑANDÚ', 'bujía ñandú'), [{ texto: 'BUJÍA ÑANDÚ', resaltado: true }])
})

test('partesResaltadas: lo escrito se busca tal cual, y sin búsqueda o sin texto no inventa', () => {
  assert.deepEqual(partesResaltadas('AS 200 (FI)', '(fi)'), [
    { texto: 'AS 200 ', resaltado: false }, { texto: '(FI)', resaltado: true },
  ])
  assert.equal(muestraLaBusqueda('352B59K', '5.K'), false, 'el punto no vale por cualquier letra')
  assert.deepEqual(partesResaltadas('INOKI', ''), [{ texto: 'INOKI', resaltado: false }])
  assert.deepEqual(partesResaltadas(null, 'inoki'), [])
  assert.equal(muestraLaBusqueda('PULSAR NS 200', 'pulsar'), true)
})

test('sinTildes: minúsculas sin tildes, del mismo largo que el original', () => {
  assert.equal(sinTildes('BUJÍA ÑANDÚ Pingüino'), 'bujia nandu pinguino')
  assert.equal(sinTildes('BUJÍA ÑANDÚ Pingüino').length, 'BUJÍA ÑANDÚ Pingüino'.length)
  assert.equal(sinTildes(null), '')
})

test('partesResaltadas ignora tildes y resalta el texto como está escrito', () => {
  assert.deepEqual(partesResaltadas('BUJÍA NGK', 'bujia'), [
    { texto: 'BUJÍA', resaltado: true }, { texto: ' NGK', resaltado: false },
  ])
  assert.deepEqual(partesResaltadas('CANCION', 'canción'), [{ texto: 'CANCION', resaltado: true }])
  assert.equal(muestraLaBusqueda('MOTO ÁGUILA', 'aguila'), true)
})

import { describe, test } from 'node:test'
import assert from 'node:assert/strict'
import {
  anterior, atajoDe, diasEntre, etiquetaDeFila, hoyEnColombia, periodoDelAtajo, periodoDesdeUrl, problemaDelPeriodo,
  siguiente, textoSinMovimientos, tituloDelPeriodo, urlDelPeriodo,
} from './periodo.js'

// Jueves 17 de septiembre de 2026.
const HOY = '2026-09-17'
const url = (texto) => new URLSearchParams(texto)

describe('los atajos (RF-001)', () => {
  test('Hoy, Ayer, Esta semana (lunes a hoy), Semana pasada, Este mes (del 1 a hoy) y Mes pasado', () => {
    assert.deepEqual(periodoDelAtajo('HOY', HOY), { tipo: 'DIA', desde: HOY, hasta: HOY })
    assert.deepEqual(periodoDelAtajo('AYER', HOY), { tipo: 'DIA', desde: '2026-09-16', hasta: '2026-09-16' })
    assert.deepEqual(periodoDelAtajo('ESTA_SEMANA', HOY), { tipo: 'SEMANA', desde: '2026-09-14', hasta: HOY })
    assert.deepEqual(periodoDelAtajo('SEMANA_PASADA', HOY), { tipo: 'SEMANA', desde: '2026-09-07', hasta: '2026-09-13' })
    assert.deepEqual(periodoDelAtajo('ESTE_MES', HOY), { tipo: 'MES', desde: '2026-09-01', hasta: HOY })
    assert.deepEqual(periodoDelAtajo('MES_PASADO', HOY), { tipo: 'MES', desde: '2026-08-01', hasta: '2026-08-31' })
  })

  test('un domingo, esta semana empieza el lunes anterior; el 1 de enero, el mes pasado es diciembre', () => {
    assert.deepEqual(periodoDelAtajo('ESTA_SEMANA', '2026-09-20'), { tipo: 'SEMANA', desde: '2026-09-14', hasta: '2026-09-20' })
    assert.deepEqual(periodoDelAtajo('MES_PASADO', '2027-01-01'), { tipo: 'MES', desde: '2026-12-01', hasta: '2026-12-31' })
  })

  test('el atajo se reconoce aunque se haya llegado con las flechas', () => {
    const ayer = anterior(periodoDelAtajo('HOY', HOY), HOY)
    assert.equal(atajoDe(ayer, HOY), 'AYER')
    assert.equal(atajoDe({ tipo: 'RANGO', desde: '2026-09-01', hasta: HOY }, HOY), null)
  })
})

describe('las flechas (RF-001, H3)', () => {
  test('este mes → agosto completo → julio; y de vuelta hasta hoy, no más allá', () => {
    const esteMes = periodoDelAtajo('ESTE_MES', HOY)
    const agosto = anterior(esteMes, HOY)
    assert.deepEqual(agosto, { tipo: 'MES', desde: '2026-08-01', hasta: '2026-08-31' })
    assert.deepEqual(anterior(agosto, HOY), { tipo: 'MES', desde: '2026-07-01', hasta: '2026-07-31' })
    assert.deepEqual(siguiente(agosto, HOY), esteMes)
    assert.equal(siguiente(esteMes, HOY), null)
  })

  test('la semana salta de lunes a lunes; el día, de a uno; hoy no tiene siguiente; un rango no tiene flechas', () => {
    assert.deepEqual(anterior(periodoDelAtajo('ESTA_SEMANA', HOY), HOY),
      { tipo: 'SEMANA', desde: '2026-09-07', hasta: '2026-09-13' })
    assert.deepEqual(siguiente(periodoDelAtajo('SEMANA_PASADA', HOY), HOY),
      { tipo: 'SEMANA', desde: '2026-09-14', hasta: HOY })
    assert.equal(siguiente(periodoDelAtajo('HOY', HOY), HOY), null)
    assert.equal(anterior({ tipo: 'RANGO', desde: '2026-09-01', hasta: HOY }, HOY), null)
  })
})

describe('la dirección', () => {
  test('sin nada, este mes y los gastos del mes repartidos', () => {
    assert.deepEqual(periodoDesdeUrl(url(''), HOY),
      { tipo: 'MES', desde: '2026-09-01', hasta: HOY, gastosDelMes: 'REPARTIDOS' })
  })

  test('ida y vuelta: una semana con cualquier fecha adentro, un rango con sus dos fechas, el modo', () => {
    const semana = { ...periodoDelAtajo('SEMANA_PASADA', HOY), gastosDelMes: 'SOLO_EN_EL_MES' }
    assert.deepEqual(periodoDesdeUrl(url(new URLSearchParams(urlDelPeriodo(semana)).toString()), HOY), semana)
    assert.deepEqual(periodoDesdeUrl(url('tipo=SEMANA&desde=2026-09-10'), HOY).desde, '2026-09-07')

    const rango = { tipo: 'RANGO', desde: '2026-08-03', hasta: '2026-09-12', gastosDelMes: 'REPARTIDOS' }
    assert.deepEqual(urlDelPeriodo(rango), { tipo: 'RANGO', desde: '2026-08-03', hasta: '2026-09-12' })
    assert.deepEqual(periodoDesdeUrl(url('tipo=RANGO&desde=2026-08-03&hasta=2026-09-12'), HOY), rango)
  })

  test('lo que no se entiende (tipo raro, fecha inválida o futura, modo raro) cae en este mes y repartidos', () => {
    for (const texto of ['tipo=AÑO&desde=2026-09-01', 'tipo=MES&desde=2026-02-31', 'tipo=DIA&desde=2026-09-18']) {
      assert.equal(periodoDesdeUrl(url(texto), HOY).desde, '2026-09-01', texto)
    }
    assert.equal(periodoDesdeUrl(url('gastosDelMes=OTRO'), HOY).gastosDelMes, 'REPARTIDOS')
  })
})

describe('un rango que no se consulta (RF-003, §6)', () => {
  test('al revés, más de 366 días, hasta mañana o incompleto dicen por qué; 366 días sí', () => {
    assert.equal(problemaDelPeriodo({ desde: '2026-09-20', hasta: '2026-09-14' }, HOY), 'La fecha inicial es posterior a la final')
    assert.equal(problemaDelPeriodo({ desde: '2025-09-16', hasta: HOY }, HOY), 'Un período no puede pasar de 366 días')
    assert.equal(problemaDelPeriodo({ desde: HOY, hasta: '2026-09-18' }, HOY), 'El período no puede terminar después de hoy')
    assert.equal(problemaDelPeriodo({ desde: '', hasta: HOY }, HOY), 'Elige la fecha inicial y la final')
    assert.equal(problemaDelPeriodo({ desde: '2025-09-17', hasta: HOY }, HOY), null)
    assert.equal(diasEntre('2025-09-17', HOY), 366)
  })
})

describe('los textos', () => {
  test('el título de cada tipo', () => {
    assert.equal(tituloDelPeriodo({ tipo: 'DIA', desde: HOY, hasta: HOY }), 'jueves 17 de septiembre')
    assert.equal(tituloDelPeriodo({ tipo: 'SEMANA', desde: '2026-09-14', hasta: '2026-09-20' }), 'semana del 14 al 20 de septiembre')
    assert.equal(tituloDelPeriodo({ tipo: 'SEMANA', desde: '2026-09-28', hasta: '2026-10-04' }),
      'semana del 28 de septiembre al 4 de octubre')
    assert.equal(tituloDelPeriodo({ tipo: 'MES', desde: '2026-08-01', hasta: '2026-08-31' }), 'agosto de 2026')
    assert.equal(tituloDelPeriodo({ tipo: 'MES', desde: '2026-09-01', hasta: HOY }), 'septiembre de 2026, del 1 al 17')
    assert.equal(tituloDelPeriodo({ tipo: 'RANGO', desde: '2025-12-20', hasta: '2026-01-10' }),
      'del 20 de diciembre de 2025 al 10 de enero de 2026')
  })

  test('sin movimientos, y la etiqueta de cada fila por día o por semana', () => {
    assert.equal(textoSinMovimientos({ desde: '2026-10-01', hasta: '2026-10-07' }), 'Sin movimientos entre el 1 y el 7 de octubre')
    assert.equal(textoSinMovimientos({ desde: HOY, hasta: HOY }), 'Sin movimientos el 17 de septiembre')
    assert.equal(etiquetaDeFila({ desde: '2026-09-14', hasta: '2026-09-14' }), 'lun 14')
    assert.equal(etiquetaDeFila({ desde: '2026-09-14', hasta: '2026-09-20' }), '14–20 sept')
    assert.equal(etiquetaDeFila({ desde: '2026-09-28', hasta: '2026-10-04' }), '28 sept–4 oct')
  })

  test('hoy es el día de Colombia: las 11 p. m. del 17 allá ya son el 18 en hora universal', () => {
    assert.equal(hoyEnColombia(new Date('2026-09-18T04:00:00Z')), '2026-09-17')
    assert.equal(hoyEnColombia(new Date('2026-09-18T05:00:00Z')), '2026-09-18')
  })
})

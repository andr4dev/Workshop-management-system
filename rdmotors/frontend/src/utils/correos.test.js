import { test } from 'node:test'
import assert from 'node:assert/strict'

import {
  avisoDeLosCorreos, destinatariosDesdeTexto, problemaDeLosDestinatarios, textoDelCorreo,
} from './correos.js'

const HOY = '2026-09-22'

const correo = (extra = {}) => ({
  id: 'c-1', tipo: 'CIERRE_DE_TURNO', estado: 'ENVIADO', intentos: 1, ultimoError: null,
  // 9 a. m. del 22 en Colombia: las horas de la madrugada UTC son del día anterior en la tienda.
  creadoEn: '2026-09-22T14:00:00Z', enviadoEn: '2026-09-22T14:03:00Z', ...extra,
})

test('los correos se leen uno por línea o separados por comas, limpios y sin repetir', () => {
  assert.deepEqual(destinatariosDesdeTexto(' Ruben@RDMotors.co \n socio@gmail.com, ruben@rdmotors.co '),
    ['ruben@rdmotors.co', 'socio@gmail.com'])
  assert.deepEqual(destinatariosDesdeTexto(''), [])
  assert.deepEqual(destinatariosDesdeTexto(null), [])
})

test('se dice cuál está mal escrito, antes de mandarlo al servidor', () => {
  assert.equal(problemaDeLosDestinatarios(['ruben@rdmotors.co']), null)
  assert.match(problemaDeLosDestinatarios(['ruben@gmail']), /«ruben@gmail» no parece un correo/)
  assert.match(problemaDeLosDestinatarios(['a@b.co', 'mal', 'peor']), /Estos no parecen correos: mal, peor/)
  assert.match(problemaDeLosDestinatarios(['a@x.co', 'b@x.co', 'c@x.co', 'd@x.co', 'e@x.co', 'f@x.co']), /5 correos/)
  assert.equal(problemaDeLosDestinatarios([]), null)
})

test('cada correo dice qué pasó: enviado, esperando internet, o por qué falló', () => {
  assert.equal(textoDelCorreo(correo(), HOY), 'Enviado hoy')
  assert.equal(textoDelCorreo(correo({ estado: 'FALLO', ultimoError: 'Brevo respondió 401' }), HOY),
    'Falló · Brevo respondió 401')
  assert.equal(textoDelCorreo(correo({ estado: 'POR_MANDAR', intentos: 0, ultimoError: null }), HOY), 'Por mandar')
  assert.match(textoDelCorreo(correo({ estado: 'POR_MANDAR', intentos: 2, ultimoError: 'sin internet' }), HOY),
    /Esperando para reintentar \(2 intentos\) · sin internet/)
  // Sin llave todavía no se ha intentado, pero sí hay que decir por qué espera.
  assert.equal(textoDelCorreo(correo({ estado: 'POR_MANDAR', intentos: 0, ultimoError: 'Falta la llave de Brevo' }), HOY),
    'Por mandar · Falta la llave de Brevo')
})

test('el aviso de arriba: sin destinatarios, sin llave, o con correos fallidos', () => {
  assert.equal(avisoDeLosCorreos({ destinatarios: ['a@x.co'], listoParaMandar: true, ultimos: [correo()] }), null)
  assert.match(avisoDeLosCorreos({ destinatarios: [], listoParaMandar: true, ultimos: [] }).texto,
    /Nadie recibe el resumen del cierre/)
  const sinLlave = avisoDeLosCorreos({
    destinatarios: ['a@x.co'], listoParaMandar: false, loQueFalta: 'Falta la llave de Brevo', ultimos: [],
  })
  assert.equal(sinLlave.tono, 'GRAVE')
  assert.match(sinLlave.texto, /se están acumulando sin salir: Falta la llave de Brevo/)
  const conFallidos = avisoDeLosCorreos({
    destinatarios: ['a@x.co'], listoParaMandar: true, ultimos: [correo({ estado: 'FALLO' })],
  })
  assert.match(conFallidos.texto, /1 correo falló/)
})

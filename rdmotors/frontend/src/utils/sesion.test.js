import { describe, test } from 'node:test'
import assert from 'node:assert/strict'
import {
  iniciales, leerCookie, pantallaDeSesion, problemasDeCambio, problemasDelAdministrador, problemasParaEntrar,
} from './sesion.js'

describe('qué pantalla toca (spec 0004)', () => {
  test('cargando, instalar, entrar, cambiar la contraseña y adentro', () => {
    assert.equal(pantallaDeSesion({ cargando: true }), 'CARGANDO')
    assert.equal(pantallaDeSesion({ cargando: false, usuario: null, faltaAdministrador: true }), 'INSTALAR')
    assert.equal(pantallaDeSesion({ cargando: false, usuario: null, faltaAdministrador: false }), 'ENTRAR')
    assert.equal(pantallaDeSesion({ cargando: false, usuario: { debeCambiarContrasena: true } }), 'CAMBIAR_CONTRASENA')
    assert.equal(pantallaDeSesion({ cargando: false, usuario: { debeCambiarContrasena: false } }), 'ADENTRO')
  })
})

describe('la cookie anti-CSRF', () => {
  test('se lee por nombre; las demás cookies no estorban; si no está, null', () => {
    const cookies = 'tema=dark; XSRF-TOKEN=abc%3D%3D; otra=x=y'
    assert.equal(leerCookie('XSRF-TOKEN', cookies), 'abc==')
    assert.equal(leerCookie('otra', cookies), 'x=y')
    assert.equal(leerCookie('nada', cookies), null)
    assert.equal(leerCookie('XSRF-TOKEN', ''), null)
  })
})

describe('los formularios', () => {
  test('entrar pide los dos campos', () => {
    assert.deepEqual(problemasParaEntrar({ usuario: ' ', contrasena: '' }),
      { usuario: 'Escribe tu usuario', contrasena: 'Escribe tu contraseña' })
    assert.deepEqual(problemasParaEntrar({ usuario: 'carolina', contrasena: 'x' }), {})
  })

  test('RF-018: el primer administrador, con las reglas del servidor', () => {
    assert.deepEqual(problemasDelAdministrador({ nombre: 'Rubén', usuario: 'ruben', contrasena: 'secreta1', confirmacion: 'secreta1' }), {})
    const mal = problemasDelAdministrador({ nombre: '', usuario: 'rubén díaz', contrasena: '123', confirmacion: '124' })
    assert.match(mal.nombre, /comprobante/)
    assert.match(mal.usuario, /sin espacios/)
    assert.equal(mal.contrasena, 'La contraseña tiene que tener al menos 6 caracteres')
    assert.equal(mal.confirmacion, 'No coincide con la contraseña')
    assert.equal(problemasDelAdministrador({ nombre: 'R', usuario: 'ruben', contrasena: 'x'.repeat(65), confirmacion: 'x'.repeat(65) }).contrasena,
      'La contraseña puede tener hasta 64 caracteres')
  })

  test('RF-017: cambiar la contraseña: la actual, una nueva distinta y que coincida', () => {
    assert.deepEqual(problemasDeCambio({ actual: 'vieja-1', nueva: 'nueva-1', confirmacion: 'nueva-1' }), {})
    assert.equal(problemasDeCambio({ actual: 'vieja-1', nueva: 'vieja-1', confirmacion: 'vieja-1' }).nueva,
      'Tiene que ser distinta de la actual')
    assert.equal(problemasDeCambio({ actual: '', nueva: 'nueva-1', confirmacion: 'otra' }).actual, 'Escribe tu contraseña actual')
    assert.equal(problemasDeCambio({ actual: 'v', nueva: 'nueva-1', confirmacion: 'otra' }).confirmacion,
      'No coincide con la contraseña nueva')
  })

  test('las iniciales del botón de la persona', () => {
    assert.equal(iniciales('Rubén Díaz'), 'RD')
    assert.equal(iniciales('  carolina  '), 'C')
    assert.equal(iniciales('Ana María de la Hoz'), 'AM')
    assert.equal(iniciales(''), '')
  })
})

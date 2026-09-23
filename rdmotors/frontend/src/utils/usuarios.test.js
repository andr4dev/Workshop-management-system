import { describe, test } from 'node:test'
import assert from 'node:assert/strict'
import {
  comandoDelUsuario, ordenarUsuarios, porQueNoCambiaAcajero, porQueNoSeDesactiva, problemasDelUsuario,
  sinProblemas, usuarioNuevo,
} from './usuarios.js'

const ruben = { id: 'u-ruben', nombre: 'Rubén', usuario: 'ruben', rol: 'ADMINISTRADOR', activo: true }
const ana = { id: 'u-ana', nombre: 'Ana', usuario: 'ana', rol: 'ADMINISTRADOR', activo: true }
const carolina = { id: 'u-carolina', nombre: 'Carolina', usuario: 'carolina', rol: 'CAJERO', activo: true }
const retirado = { id: 'u-beto', nombre: 'Beto', usuario: 'beto', rol: 'ADMINISTRADOR', activo: false }

describe('crear un usuario (RF-013)', () => {
  test('pide nombre, usuario, rol y una contraseña de al menos 6', () => {
    const problemas = problemasDelUsuario(usuarioNuevo())
    assert.equal(sinProblemas(problemas), false)
    assert.match(problemas.nombre, /el nombre/)
    assert.match(problemas.usuario, /Escribe el usuario/)
    assert.match(problemas.contrasena, /al menos 6/)
    assert.equal(problemas.rol, undefined)   // el formulario arranca en Cajero
  })

  test('el usuario no lleva espacios y va de 3 a 40', () => {
    assert.match(problemasDelUsuario({ nombre: 'Ana', usuario: 'an a', rol: 'CAJERO', contrasena: 'secreta' }).usuario,
      /sin espacios/)
    assert.match(problemasDelUsuario({ nombre: 'Ana', usuario: 'an', rol: 'CAJERO', contrasena: 'secreta' }).usuario,
      /De 3 a 40/)
    const bueno = { nombre: ' Ana María ', usuario: ' ana.maria ', rol: 'CAJERO', contrasena: 'temporal-1' }
    assert.equal(sinProblemas(problemasDelUsuario(bueno)), true)
    assert.deepEqual(comandoDelUsuario(bueno),
      { nombre: 'Ana María', usuario: 'ana.maria', rol: 'CAJERO', contrasena: 'temporal-1' })
  })
})

describe('siempre queda un administrador activo (RF-016)', () => {
  const dos = [ruben, ana, carolina, retirado]
  const uno = [ruben, carolina, retirado]

  test('con un solo administrador activo no se le quita el rol ni se le desactiva', () => {
    assert.match(porQueNoSeDesactiva(ruben, ana, uno), /al menos un administrador/)
    assert.match(porQueNoCambiaAcajero(ruben, uno), /al menos un administrador/)
    assert.equal(porQueNoSeDesactiva(carolina, ruben, uno), null)
    assert.equal(porQueNoCambiaAcajero(carolina, uno), null)
  })

  test('con dos administradores activos sí, y uno desactivado no cuenta', () => {
    assert.equal(porQueNoSeDesactiva(ruben, ana, dos), null)
    assert.equal(porQueNoCambiaAcajero(ruben, dos), null)
    assert.match(porQueNoSeDesactiva(ruben, ana, [ruben, carolina, retirado]), /al menos un administrador/)
  })

  test('nadie se desactiva a sí mismo, ni siquiera con otro administrador', () => {
    assert.equal(porQueNoSeDesactiva(ruben, ruben, dos), 'No puedes desactivarte a ti mismo')
  })

  test('a un desactivado no le aplica la regla del último administrador: se puede activar', () => {
    assert.equal(porQueNoCambiaAcajero(retirado, uno), null)
  })
})

describe('el orden de la lista', () => {
  test('los activos primero, por nombre; los desactivados al final', () => {
    assert.deepEqual(ordenarUsuarios([retirado, ruben, carolina, ana]).map((u) => u.nombre),
      ['Ana', 'Carolina', 'Rubén', 'Beto'])
  })
})

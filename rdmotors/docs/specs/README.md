# Specs — RD MOTORS

Índice de funcionalidades. Una línea por spec. **Una carpeta que no esté aquí no existe.**

El número es un identificador estable: se cita en commits (`implementa spec 0001`) y no se reusa
nunca, aunque la funcionalidad se descarte.

| # | Funcionalidad | Estado |
|---|---|---|
| [0001](0001-catalogo-y-compras-usables/spec.md) | Catálogo y compras usables | ✅ **cerrado** · [plan](0001-catalogo-y-compras-usables/plan.md) completo · verificado con factura real |
| [0002](0002-compras-fuente-de-pago-e-historial/spec.md) | Compras: fuente de pago, historial y corrección | ✅ **cerrado** · [plan](0002-compras-fuente-de-pago-e-historial/plan.md) · 7 de 7 fases · QA manual hecho · fase 8 ✅ (2026-09-15: la lista dice qué repuesto trae cada factura) · fase 9 ✅ (2026-09-19: sin buscar, cada factura dice qué trae) |
| [0003](0003-venta-de-mostrador/spec.md) | Venta de mostrador y comprobante | spec cerrado · [plan](0003-venta-de-mostrador/plan.md) · **implementado: 5 de 5 fases** (turno de caja; cobrar; pantalla de venta; comprobante, datos de la tienda y ventas del turno; anular) · falta probar la ticketera real y la verificación manual · RF-028 revisado el 2026-09-16 (la venta a medias se retoma sola), ✅ implementado el 2026-09-19 |
| [0004](0004-usuarios-y-roles/spec.md) | Usuarios, entrada y roles | spec cerrado · [plan](0004-usuarios-y-roles/plan.md) aprobado el 2026-09-19 · **las 5 fases hechas (2026-09-19 y 20)**, falta el QA del usuario · el cajero no ve costos; token firmado de 24 h en cookie; V16 a V19 |
| [0005](0005-catalogo-en-el-mostrador/spec.md) | Ver el catálogo en el mostrador | spec cerrado · [plan](0005-catalogo-en-el-mostrador/plan.md) aprobado el 2026-09-15 · **implementado: 3 de 3 fases** (categoría obligatoria; filtrar y contar por categoría; catálogo con F2) · falta la verificación manual |
| [0006](0006-cierre-de-caja/spec.md) | Cierre de caja, gastos y retiros | spec cerrado · [plan](0006-cierre-de-caja/plan.md) aprobado el 2026-09-16 · **implementado: 5 de 5 fases** (categorías y gastos; retiros, compras de caja y arqueo; cierre con lo contado y desglose en vivo; comprobante y turnos anteriores; billetes y fondo sugerido; la lista de gastos en Reportes) · falta el recorrido en el navegador y la verificación manual |
| [0007](0007-reportes-de-resultados/spec.md) | Reportes de resultados: ganancia, utilidad bruta y operativa | spec cerrado · [plan](0007-reportes-de-resultados/plan.md) aprobado el 2026-09-16 · **las 6 fases hechas el 2026-09-17** (la 6 limpió la base de QA, con respaldo) · falta la verificación manual del usuario |
| [0008](0008-fiado-a-clientes/spec.md) | Fiado a clientes y cartera | **implementado** el 2026-09-21 · [plan](0008-fiado-a-clientes/plan.md) · 5 fases en verde · sin cupo; fiar exige solo el nombre (decisión 2 cambiada el 2026-09-21: la cédula y el celular se piden, no se exigen); Cartera al estilo del car‑wash, por venta y con abono libre; V20 y V21 |
| [0009](0009-sin-internet-y-respaldo/spec.md) | Sin internet: bandeja hacia la nube y respaldo automático | **implementado** el 2026-09-21 · [plan](0009-sin-internet-y-respaldo/plan.md) · 3 fases en verde · vender sin internet ya funcionaba y quedó probado; respaldo automático con `pg_dump` (V23) y llave en compras (V22); la bandeja hacia la nube (H3–H5) va con la rebanada 5 |
| [0010](0010-correo-del-cierre/spec.md) | El correo del cierre de caja (Brevo) | **implementado** el 2026-09-22 · [plan](0010-correo-del-cierre/plan.md) · al cerrar, el resumen sale por correo al dueño; con cola y reintentos para que un corte de internet no lo pierda (V24). Falta que el dueño cree la cuenta de Brevo y ponga la llave |
| [0011](0011-la-tienda-en-la-nube/spec.md) | La tienda en la nube | spec cerrado · [plan](0011-la-tienda-en-la-nube/plan.md) · **fases 1 a 4 hechas el 2026-09-23** (la pantalla adentro del servidor + Dockerfile; secretos y clave obligatoria; el reloj de afuera con `/api/salud` y `/api/tareas`; el respaldo que se baja, V25) · **falta la fase 5**: crear las cuentas y desplegar, que es del usuario · le da la vuelta a la decisión más vieja del proyecto: la verdad se muda del computador del almacén a la nube · **después de esto, sin internet no se vende** |

## Documentos de contexto

Estos no son specs de funcionalidad; son el contexto del que todos parten:

- [`PLAN_DE_TRABAJO.md`](../PLAN_DE_TRABAJO.md) — **empieza aquí**: dónde estamos y qué sigue
- [`SPEC_Sistema_Ventas_Repuestos (3).md`](../) — alcance de negocio, del cliente
- [`SPEC_Modelo_Datos.md`](../SPEC_Modelo_Datos.md) — modelo de datos cerrado

## Deuda conocida

Lo que se construyó **antes** de adoptar este proceso, y por tanto no tiene spec propio:

- **Módulo de compras** (`RegistrarCompra`) — implementado directamente contra
  `SPEC_Modelo_Datos.md` §3.4. Funciona y tiene 28 pruebas, pero el *cómo* (orden de operaciones,
  diseño de clases, rechazo de variante repetida) solo vive en el javadoc del código.
  No se le escribe spec retroactivo: sería papeleo. Queda anotado para que se sepa por qué falta.

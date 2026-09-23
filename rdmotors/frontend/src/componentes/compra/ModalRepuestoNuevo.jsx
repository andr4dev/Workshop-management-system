import { useEffect, useRef, useState } from 'react'
import Modal from '../Modal'
import Boton from '../Boton'
import Campo from '../Campo'
import { catalogoApi, repuestosApi } from '../../api/cliente'
import { formatoCosto, GUION, soloDigitos } from '../../utils/formato'
import estilos from './ModalRepuestoNuevo.module.css'

/**
 * Un solo modal para tres gestos parecidos pero distintos:
 *
 *   CREAR              el código no existe; el repuesto nace con esta compra
 *   CORREGIR BORRADOR  uno que se creó hace un momento y aún no se ha guardado
 *   EDITAR EXISTENTE   uno que ya está en la base y quedó mal (RF-009)
 *
 * <p>Lo que cambia entre ellos no es cosmético. Al CREAR se ofrecen conceptos parecidos para
 * reutilizarlos (la decisión §4); al EDITAR eso no tiene sentido — sería ofrecerle reutilizar
 * el repuesto que está corrigiendo.
 *
 * <p><b>Este modal no sabe de precios.</b> Es identidad: qué repuesto es, cómo se llama, de qué
 * marca. El precio de venta es una decisión comercial y vive en el renglón de la compra, junto al
 * costo y al margen — un solo campo en todo el sistema, y por tanto una sola verdad.
 *
 * <p>Tenerlo en dos sitios ya costó un bug reportado: el renglón decía $190.000 y el modal
 * $180.000 a la vez.
 *
 * <p><b>La categoría es obligatoria</b> (spec 0005, RF-009 y RF-011): es como se encuentra el repuesto en
 * el catálogo del mostrador. El servidor también la exige.
 *
 * @param categoriaSugerida la del último repuesto nuevo de la misma compra (RF-010): en una factura de
 *                          veinte filtros no se escoge veinte veces. Solo se usa al CREAR.
 */
export default function ModalRepuestoNuevo({
  abierto, codigo, datosPrevios, existente, categoriaSugerida, onCerrar, onCrear, onActualizado,
}) {
  const editando = existente != null

  // El estado arranca YA con los datos correctos, en vez de arrancar vacío y corregirse con un
  // efecto. El padre monta este componente solo cuando se abre, así que cada apertura es una
  // instancia nueva y no hay nada que resetear. Es lo que React recomienda: derivar en vez de
  // sincronizar.
  const partida = existente ?? datosPrevios ?? null

  const [codigoEditable, setCodigoEditable] = useState(codigo)
  const [nombre, setNombre] = useState(
    existente?.nombre ?? datosPrevios?.nombreConcepto ?? '')
  const [conceptoElegido, setConceptoElegido] = useState(
    !existente && datosPrevios?.productoId
      ? { id: datosPrevios.productoId, nombre: datosPrevios.nombreConcepto }
      : null)
  const [sugerencias, setSugerencias] = useState([])
  const [categorias, setCategorias] = useState([])
  // Al editar arranca con la categoría QUE TIENE. Arrancaba vacía, y como el PUT reemplaza la
  // ficha entera, guardar sin tocar nada la borraba.
  const [categoriaId, setCategoriaId] = useState(
    partida?.categoriaId ?? (partida ? '' : categoriaSugerida) ?? '')
  const [aplicacion, setAplicacion] = useState(
    existente?.aplicacion ?? datosPrevios?.aplicacionOriginal ?? '')
  const [marca, setMarca] = useState(
    existente?.marca ?? datosPrevios?.marcaRepuesto ?? '')
  const [stockMinimo, setStockMinimo] = useState(String(partida?.stockMinimo ?? '5'))
  const [errores, setErrores] = useState({})
  const [guardando, setGuardando] = useState(false)

  const debounce = useRef(null)

  useEffect(() => {
    catalogoApi.categorias().then(setCategorias).catch(() => setCategorias([]))
  }, [])

  // Sugerir conceptos parecidos SOLO al crear. Al corregir uno que ya existe, ofrecerle
  // reutilizar otro no tiene sentido: no está eligiendo qué repuesto es, lo está arreglando.
  const buscando = !editando && !conceptoElegido && nombre.trim().length >= 3

  useEffect(() => {
    if (!buscando) return
    clearTimeout(debounce.current)
    debounce.current = setTimeout(() => {
      catalogoApi.buscarConceptos(nombre.trim()).then(setSugerencias).catch(() => setSugerencias([]))
    }, 250)
    return () => clearTimeout(debounce.current)
  }, [nombre, buscando])

  // Se DERIVA al pintar en vez de limpiarse con setState: si no toca buscar, no hay sugerencias.
  const sugerenciasVisibles = buscando ? sugerencias : []

  function validar() {
    const e = {}
    if (!codigoEditable.trim()) e.codigo = 'El código es obligatorio'
    if (!conceptoElegido && !nombre.trim()) e.nombre = 'Escribe qué repuesto es'
    if (!conceptoElegido && !categoriaId) e.categoria = 'Escoge la categoría: es como se encuentra en el catálogo del mostrador'
    if (!marca.trim()) e.marca = 'Falta la marca'
    setErrores(e)
    return Object.keys(e).length === 0
  }

  async function confirmar() {
    if (!validar()) return

    if (editando) {
      setGuardando(true)
      try {
        onActualizado(await repuestosApi.actualizar(existente.id, {
          nombreProducto: nombre.trim(),
          categoriaId: categoriaId || null,
          aplicacionOriginal: aplicacion.trim() || null,
          codigo: codigoEditable.trim().toUpperCase(),
          marcaRepuesto: marca.trim(),
          // El precio NO se edita aquí. Se reenvía el que ya tenía, intacto: vive en el
          // renglón de la compra, junto al costo y al margen.
          precio: existente.precio,
          stockMinimo: Number(soloDigitos(stockMinimo)) || 0,
        }))
      } catch (e) {
        setErrores({ general: e.message })
        setGuardando(false)
      }
      return
    }

    onCrear({
      productoId: conceptoElegido?.id ?? null,
      nombreProducto: conceptoElegido ? null : nombre.trim(),
      categoriaId: conceptoElegido ? null : (categoriaId || null),
      aplicacionOriginal: conceptoElegido ? null : (aplicacion.trim() || null),
      codigo: codigoEditable.trim().toUpperCase(),
      marcaRepuesto: marca.trim(),
      stockMinimo: Number(soloDigitos(stockMinimo)) || 0,
      nombreConcepto: conceptoElegido?.nombre ?? nombre.trim(),
    })
  }

  const titulo = editando
    ? `Corregir ${existente.codigo}`
    : datosPrevios ? `Corregir ${codigo}` : `Repuesto nuevo · ${codigo}`

  const textoBoton = guardando ? 'Guardando…'
    : editando ? 'Guardar corrección'
    : datosPrevios ? 'Guardar cambios'
    : 'Agregar a la compra'

  return (
    <Modal
      abierto={abierto}
      onCerrar={onCerrar}
      titulo={titulo}
      ancho={600}
      pie={
        <>
          <Boton variante="fantasma" onClick={onCerrar}>Cancelar</Boton>
          <Boton variante="primario" onClick={confirmar} disabled={guardando}>{textoBoton}</Boton>
        </>
      }
    >
      {!editando && !datosPrevios && (
        <p className={estilos.intro}>
          El código <strong className={estilos.codigo}>{codigo}</strong> no está en el sistema.
          Se crea aquí y entra con esta compra.
        </p>
      )}

      <Campo
        etiqueta="Código"
        requerido
        value={codigoEditable}
        onChange={(e) => setCodigoEditable(e.target.value.toUpperCase())}
        error={errores.codigo}
        autoComplete="off"
        spellCheck="false"
      />

      <div className={estilos.bloque}>
        {conceptoElegido ? (
          <div className={estilos.conceptoElegido}>
            <div>
              <span className={estilos.conceptoNombre}>{conceptoElegido.nombre}</span>
              <span className={estilos.reutilizando}>
                Se agrega como una marca más de este repuesto
              </span>
            </div>
            <Boton variante="fantasma" tamano="chico" onClick={() => setConceptoElegido(null)}>
              Cambiar
            </Boton>
          </div>
        ) : (
          <>
            <Campo
              etiqueta="Nombre del repuesto"
              requerido
              value={nombre}
              onChange={(e) => setNombre(e.target.value.toUpperCase())}
              placeholder="FILTRO ACEITE"
              error={errores.nombre}
              ayuda={editando ? 'Lo comparten todas las marcas de este repuesto' : undefined}
              autoComplete="off"
            />

            {sugerenciasVisibles.length > 0 && (
              <div className={estilos.sugerencias}>
                <p className={estilos.sugerenciasTitulo}>
                  Ya existe algo parecido. ¿Es una marca nueva de alguno de estos?
                </p>
                {sugerenciasVisibles.slice(0, 4).map((c) => (
                  <button
                    key={c.id}
                    type="button"
                    className={estilos.sugerencia}
                    onClick={() => { setConceptoElegido(c); setNombre(c.nombre) }}
                  >
                    <span className={estilos.sugerenciaNombre}>{c.nombre}</span>
                    {c.aplicacion && (
                      <span className={estilos.sugerenciaAplicacion}>{c.aplicacion}</span>
                    )}
                  </button>
                ))}
                <p className={estilos.sugerenciaPie}>
                  Si ninguno es, sigue llenando abajo y se crea uno nuevo.
                </p>
              </div>
            )}

            <div className={estilos.fila}>
              <Campo etiqueta="Categoría" requerido error={errores.categoria}>
                <select
                  className={estilos.select}
                  value={categoriaId}
                  onChange={(e) => { setCategoriaId(e.target.value); setErrores((x) => ({ ...x, categoria: undefined })) }}
                  aria-invalid={errores.categoria ? 'true' : undefined}
                >
                  <option value="" disabled>Escoge la categoría</option>
                  {categorias.map((c) => (
                    <option key={c.id} value={c.id}>{c.nombre}</option>
                  ))}
                </select>
              </Campo>

              <Campo
                etiqueta="Aplicación"
                ayuda="Cópiala tal cual de la factura"
                value={aplicacion}
                onChange={(e) => setAplicacion(e.target.value.toUpperCase())}
                placeholder="PULSAR NS 200/FI/AS 200-DUKE 200"
                autoComplete="off"
              />
            </div>
          </>
        )}
      </div>

      <div className={estilos.fila}>
        <Campo
          etiqueta="Marca"
          requerido
          value={marca}
          onChange={(e) => setMarca(e.target.value.toUpperCase())}
          placeholder="INOKI"
          error={errores.marca}
          autoComplete="off"
        />
        <Campo
          etiqueta="Avisar si quedan menos de"
          value={stockMinimo}
          onChange={(e) => setStockMinimo(soloDigitos(e.target.value))}
          inputMode="numeric"
        />
      </div>

      {/* Este modal es IDENTIDAD: qué repuesto es y cómo se llama. El precio es una decisión
          comercial y vive donde están el costo y el margen — en el renglón. Un solo campo en
          todo el sistema, y por tanto una sola verdad. */}
      <p className={estilos.pista}>
        El precio de venta se fija en el renglón de la compra, junto al costo: así el margen se
        decide con los dos números a la vista.
      </p>

      {/* El inventario en solo lectura. Mostrar el valor dice más que una alerta explicando que
          no se puede editar: lo ve, ve que no es un campo, y entiende. */}
      {editando && (
        <div className={estilos.inventario}>
          <div className={estilos.inventarioDato}>
            <span className={estilos.inventarioEtiqueta}>En inventario</span>
            <span className={estilos.inventarioValor}>{existente.stock} unidades</span>
          </div>
          <div className={estilos.inventarioDato}>
            <span className={estilos.inventarioEtiqueta}>Costo promedio</span>
            <span className={estilos.inventarioValor}>
              {existente.costoPromedio == null ? GUION : formatoCosto(existente.costoPromedio)}
            </span>
          </div>
          <p className={estilos.inventarioNota}>
            Salen del kardex. Se corrigen con un ajuste de inventario.
          </p>
        </div>
      )}

      {errores.general && (
        <p className={estilos.errorGeneral} role="alert">
          <span aria-hidden>⚠</span> {errores.general}
        </p>
      )}

    </Modal>
  )
}

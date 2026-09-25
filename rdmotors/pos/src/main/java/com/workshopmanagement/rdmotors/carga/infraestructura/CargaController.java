package com.workshopmanagement.rdmotors.carga.infraestructura;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.workshopmanagement.rdmotors.carga.aplicacion.ConfirmarCarga;
import com.workshopmanagement.rdmotors.carga.aplicacion.ConfirmarCarga.ResultadoConfirmacion;
import com.workshopmanagement.rdmotors.carga.aplicacion.ConsultarCarga;
import com.workshopmanagement.rdmotors.carga.aplicacion.DescartarCarga;
import com.workshopmanagement.rdmotors.carga.aplicacion.DetalleCarga;
import com.workshopmanagement.rdmotors.carga.aplicacion.EditarCarga;
import com.workshopmanagement.rdmotors.carga.aplicacion.SubirFactura;
import com.workshopmanagement.rdmotors.carga.dominio.CargaDeInventario;
import com.workshopmanagement.rdmotors.carga.dominio.EstadoCarga;
import com.workshopmanagement.rdmotors.carga.dominio.OrigenCarga;
import com.workshopmanagement.rdmotors.carga.dominio.Problema;
import com.workshopmanagement.rdmotors.carga.dominio.RenglonDeCarga;
import com.workshopmanagement.rdmotors.carga.dominio.ResumenCarga;
import com.workshopmanagement.rdmotors.carga.dominio.Revision;
import com.workshopmanagement.rdmotors.compartido.dominio.Actor;
import com.workshopmanagement.rdmotors.compartido.dominio.Dinero;
import com.workshopmanagement.rdmotors.compartido.dominio.FormaPago;
import com.workshopmanagement.rdmotors.compartido.infraestructura.seguridad.ActorActual;
import com.workshopmanagement.rdmotors.inventario.dominio.Variante;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;

/**
 * ADAPTADOR DE ENTRADA — la carga de inventario desde la factura (spec 0012).
 *
 * <p>Un endpoint por gesto de la pre-carga, y cada uno devuelve la carga entera revisada de nuevo: arreglar un
 * renglón puede quitarle el problema a otro, o habilitar la confirmación, y la pantalla lo ve sin tener que
 * adivinarlo.
 */
@RestController
@RequestMapping("/api/cargas")
@RequiredArgsConstructor
class CargaController {

    private final SubirFactura subirFactura;
    private final ConsultarCarga consultarCarga;
    private final EditarCarga editarCarga;
    private final DescartarCarga descartarCarga;
    private final ConfirmarCarga confirmarCarga;

    /** La factura: el PDF de Jotapartes tal como llegó, o la plantilla en Excel o CSV. El archivo no se guarda. */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    RespuestaCarga subir(@RequestParam("archivo") MultipartFile archivo, @ActorActual Actor actor)
            throws IOException {
        return RespuestaCarga.de(subirFactura.ejecutar(archivo.getOriginalFilename(), archivo.getBytes(), actor));
    }

    @GetMapping
    List<ResumenCarga> lista(@ActorActual Actor actor) {
        return consultarCarga.recientes(actor);
    }

    /**
     * La plantilla para lo que no llega en PDF de Jotapartes (spec 0012, H8): solo los títulos, para que ningún
     * renglón de ejemplo termine cargado por olvido. En CSV con punto y coma y la marca de UTF-8, que Excel en
     * español abre como hoja sin preguntar nada.
     */
    @GetMapping("/plantilla")
    ResponseEntity<byte[]> plantilla() {
        byte[] marca = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        byte[] titulos = (PlantillaDeCarga.titulosEnCsv() + "\r\n").getBytes(StandardCharsets.UTF_8);
        byte[] archivo = new byte[marca.length + titulos.length];
        System.arraycopy(marca, 0, archivo, 0, marca.length);
        System.arraycopy(titulos, 0, archivo, marca.length, titulos.length);
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("plantilla-carga-rdmotors.csv").build().toString())
                .body(archivo);
    }

    @GetMapping("/{id}")
    RespuestaCarga detalle(@PathVariable UUID id, @ActorActual Actor actor) {
        return consultarCarga.detalle(id, actor).map(RespuestaCarga::de)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Esa carga no existe"));
    }

    @PutMapping("/{id}/datos")
    RespuestaCarga datos(@PathVariable UUID id, @RequestBody PeticionDatos p, @ActorActual Actor actor) {
        return RespuestaCarga.de(editarCarga.cambiarDatos(id, p.proveedorId(), p.numeroFactura(), p.fechaFactura(),
                p.formaPago(), p.cuentaId(), actor));
    }

    @PutMapping("/{id}/subtotal")
    RespuestaCarga subtotal(@PathVariable UUID id, @RequestBody PeticionSubtotal p, @ActorActual Actor actor) {
        return RespuestaCarga.de(editarCarga.fijarSubtotal(id, p.subtotal() == null ? null : Dinero.de(p.subtotal()),
                actor));
    }

    @PutMapping("/{id}/regla")
    RespuestaCarga regla(@PathVariable UUID id, @Valid @RequestBody PeticionRegla p, @ActorActual Actor actor) {
        return RespuestaCarga.de(editarCarga.cambiarRegla(id, p.ivaPct(), p.gananciaPct(), p.redondeo(), actor));
    }

    @PutMapping("/{id}/renglones/{posicion}/precio")
    RespuestaCarga precio(@PathVariable UUID id, @PathVariable int posicion, @Valid @RequestBody PeticionPrecio p,
                          @ActorActual Actor actor) {
        return RespuestaCarga.de(editarCarga.ajustarPrecio(id, posicion, Dinero.de(p.precio()), actor));
    }

    @DeleteMapping("/{id}/renglones/{posicion}/precio")
    RespuestaCarga volverAlSugerido(@PathVariable UUID id, @PathVariable int posicion, @ActorActual Actor actor) {
        return RespuestaCarga.de(editarCarga.volverAlSugerido(id, posicion, actor));
    }

    @PutMapping("/{id}/renglones/{posicion}/lectura")
    RespuestaCarga lectura(@PathVariable UUID id, @PathVariable int posicion, @RequestBody PeticionLectura p,
                           @ActorActual Actor actor) {
        return RespuestaCarga.de(editarCarga.corregirLectura(id, posicion, p.codigo(), p.descripcion(), p.cantidad(),
                p.valorTotal() == null ? null : Dinero.de(p.valorTotal()),
                p.precioUnitario() == null ? null : Dinero.de(p.precioUnitario()), p.descuentoPct(), actor));
    }

    @PutMapping("/{id}/renglones/{posicion}/quitado")
    RespuestaCarga quitado(@PathVariable UUID id, @PathVariable int posicion, @RequestBody PeticionSiNo p,
                           @ActorActual Actor actor) {
        return RespuestaCarga.de(editarCarga.quitar(id, posicion, p.valor(), actor));
    }

    @PutMapping("/{id}/renglones/{posicion}/precio-nuevo")
    RespuestaCarga precioNuevo(@PathVariable UUID id, @PathVariable int posicion, @RequestBody PeticionSiNo p,
                               @ActorActual Actor actor) {
        return RespuestaCarga.de(editarCarga.aplicarPrecioNuevo(id, posicion, p.valor(), actor));
    }

    /** A los renglones elegidos, o con {@code losQueNoTienen} a todos los que no tienen marca. */
    @PutMapping("/{id}/marca")
    RespuestaCarga marca(@PathVariable UUID id, @RequestBody PeticionMarca p, @ActorActual Actor actor) {
        return RespuestaCarga.de(editarCarga.cambiarMarca(id, p.posiciones(), p.losQueNoTienen(), p.marca(), actor));
    }

    @PutMapping("/{id}/categoria")
    RespuestaCarga categoria(@PathVariable UUID id, @RequestBody PeticionCategoria p, @ActorActual Actor actor) {
        return RespuestaCarga.de(editarCarga.cambiarCategoria(id, p.posiciones(), p.losQueNoTienen(),
                p.categoriaId(), actor));
    }

    /**
     * Todo entra como una compra (RF-015). La pantalla manda cuántas reposiciones veía: si alguien creó uno de esos
     * códigos a mano mientras tanto, se avisa antes de registrar nada. Repetirlo devuelve la misma compra.
     */
    @PostMapping("/{id}/confirmacion")
    RespuestaConfirmacion confirmar(@PathVariable UUID id, @RequestBody PeticionConfirmacion p,
                                    @ActorActual Actor actor) {
        return RespuestaConfirmacion.de(confirmarCarga.ejecutar(id, p.reposicionesVistas(), actor));
    }

    /** {@code POST} y no {@code DELETE}: no se borra, queda descartada con quién y cuándo. */
    @PostMapping("/{id}/descarte")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void descartar(@PathVariable UUID id, @ActorActual Actor actor) {
        descartarCarga.ejecutar(id, actor);
    }

    // ── Peticiones ───────────────────────────────────────────────────────────

    record PeticionDatos(UUID proveedorId, String numeroFactura, LocalDate fechaFactura, FormaPago formaPago,
                         UUID cuentaId) {
    }

    record PeticionSubtotal(Long subtotal) {
    }

    record PeticionRegla(@NotNull BigDecimal ivaPct, @NotNull BigDecimal gananciaPct, int redondeo) {
    }

    record PeticionPrecio(@NotNull Long precio) {
    }

    record PeticionLectura(String codigo, String descripcion, Integer cantidad, Long valorTotal, Long precioUnitario,
                           BigDecimal descuentoPct) {
    }

    record PeticionSiNo(boolean valor) {
    }

    record PeticionConfirmacion(int reposicionesVistas) {
    }

    record PeticionMarca(List<Integer> posiciones, boolean losQueNoTienen, String marca) {
    }

    record PeticionCategoria(List<Integer> posiciones, boolean losQueNoTienen, UUID categoriaId) {
    }

    // ── Respuestas ───────────────────────────────────────────────────────────

    private static Long pesos(Dinero d) {
        return d == null ? null : d.valor().longValueExact();
    }

    record RespuestaCarga(UUID id, long version, EstadoCarga estado, OrigenCarga origen, String nombreArchivo,
                          UUID proveedorId, String proveedor, String nitProveedor, String numeroFactura,
                          LocalDate fechaFactura, FormaPago formaPago, UUID cuentaId, String cuenta,
                          Long subtotalFactura, boolean subtotalLeido, Long ivaImpreso, Long totalImpreso,
                          BigDecimal ivaPct, BigDecimal gananciaPct, int redondeo, Instant creadaEn,
                          Instant modificadaEn, Instant cerradaEn, UUID compraId, boolean sePuedeConfirmar,
                          List<RespuestaProblema> problemas, RespuestaTotales totales,
                          List<RespuestaRenglon> renglones) {

        static RespuestaCarga de(DetalleCarga d) {
            CargaDeInventario c = d.carga();
            Revision r = d.revision();
            return new RespuestaCarga(c.getId(), c.getVersion() == null ? 0 : c.getVersion(), c.getEstado(),
                    c.getOrigen(), c.getNombreArchivo(), c.getProveedorId(), d.proveedor(), c.getNitProveedor(),
                    c.getNumeroFactura(), c.getFechaFactura(), c.getFormaPago(), c.getCuentaId(), d.cuenta(),
                    pesos(c.getSubtotalFactura()), c.isSubtotalLeido(), pesos(c.getIvaImpreso()),
                    pesos(c.getTotalImpreso()), c.getIvaPct().stripTrailingZeros(),
                    c.getGananciaPct().stripTrailingZeros(), c.getRedondeo(), c.getCreadaEn(), c.getModificadaEn(),
                    c.getCerradaEn(), c.getCompraId(),
                    c.getEstado() == EstadoCarga.BORRADOR && r.sePuedeConfirmar(),
                    RespuestaProblema.de(r.problemas()), RespuestaTotales.de(r.totales()),
                    r.renglones().stream().map(rr -> RespuestaRenglon.de(rr, d)).toList());
        }
    }

    record RespuestaConfirmacion(UUID cargaId, UUID compraId, long total, int renglones, int unidades) {

        static RespuestaConfirmacion de(ResultadoConfirmacion r) {
            return new RespuestaConfirmacion(r.cargaId(), r.compraId(), pesos(r.total()), r.renglones(),
                    r.unidades());
        }
    }

    record RespuestaTotales(long sumaLeida, Long subtotalFactura, Long diferencia, long subtotalIncluido, long iva,
                            long totalConIva, int unidades, int incluidos, int conProblema,
                            int propuestasSinRevisar, int reposiciones, int ajustados, int quitados) {

        static RespuestaTotales de(Revision.Totales t) {
            return new RespuestaTotales(pesos(t.sumaLeida()), pesos(t.subtotalFactura()), pesos(t.diferencia()),
                    pesos(t.subtotalIncluido()), pesos(t.iva()), pesos(t.totalConIva()), t.unidades(),
                    t.incluidos(), t.conProblema(), t.propuestasSinRevisar(), t.reposiciones(), t.ajustados(),
                    t.quitados());
        }
    }

    /** Sin los campos vacíos: son 600 renglones en cada respuesta, y el celular los baja todos. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record RespuestaRenglon(int posicion, String ubicacion, String codigo, String descripcion, String nombre,
                            Integer cantidad, String unidad, Long precioUnitario, BigDecimal descuentoPct,
                            Long valorTotal, String marca, boolean marcaPropuesta, UUID categoriaId,
                            String categoria, boolean categoriaPropuesta, BigDecimal costoPorUnidad, Long sugerido,
                            Long precioFinal, boolean ajustadoAMano, boolean quitado, boolean aplicarPrecioNuevo,
                            RespuestaExistente existente, List<RespuestaProblema> problemas,
                            List<RespuestaProblema> avisos) {

        static RespuestaRenglon de(Revision.RenglonRevisado rr, DetalleCarga d) {
            RenglonDeCarga r = rr.renglon();
            return new RespuestaRenglon(r.getPosicion(), r.getUbicacion(), r.getCodigo(), r.getDescripcion(),
                    r.getNombre(), r.getCantidad(), r.getUnidad(), pesos(r.getPrecioUnitario()),
                    r.getDescuentoPct() == null ? null : r.getDescuentoPct().stripTrailingZeros(),
                    pesos(r.getValorTotal()), r.getMarca(), r.isMarcaPropuesta(), r.getCategoriaId(),
                    d.categorias().nombre(r.getCategoriaId()), r.isCategoriaPropuesta(), rr.costoPorUnidad(),
                    pesos(rr.sugerido()), pesos(r.getPrecioFinal()), r.isAjustadoAMano(), r.isQuitado(),
                    r.isAplicarPrecioNuevo(), RespuestaExistente.de(rr.existente()),
                    RespuestaProblema.de(rr.problemas()), RespuestaProblema.de(rr.avisos()));
        }
    }

    /** El repuesto que ya tiene ese código: lo que la pre-carga muestra de una reposición (RF-009). */
    record RespuestaExistente(UUID varianteId, String nombre, String marca, int stock, long precio) {

        static RespuestaExistente de(Variante v) {
            return v == null ? null : new RespuestaExistente(v.getId(), v.getProducto().getNombre(),
                    v.getMarcaRepuesto(), v.getStock(), pesos(v.getPrecio()));
        }
    }

    record RespuestaProblema(Problema.Tipo tipo, String mensaje) {

        static List<RespuestaProblema> de(List<Problema> problemas) {
            return problemas.stream().map(p -> new RespuestaProblema(p.tipo(), p.mensaje())).toList();
        }
    }
}

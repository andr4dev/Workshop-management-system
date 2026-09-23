package com.workshopmanagement.rdmotors.compras.infraestructura;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import com.workshopmanagement.rdmotors.compartido.aplicacion.CambioAuditado;
import com.workshopmanagement.rdmotors.compartido.dominio.Actor;
import com.workshopmanagement.rdmotors.compartido.dominio.Persona;
import com.workshopmanagement.rdmotors.compartido.infraestructura.seguridad.ActorActual;
import com.workshopmanagement.rdmotors.compartido.dominio.AccionAuditada;
import com.workshopmanagement.rdmotors.compartido.dominio.Dinero;
import com.workshopmanagement.rdmotors.compartido.dominio.EventoAuditoria;
import com.workshopmanagement.rdmotors.compartido.dominio.Pagina;
import com.workshopmanagement.rdmotors.compras.aplicacion.AnularCompra;
import com.workshopmanagement.rdmotors.compras.aplicacion.ComandoCorregirCompra;
import com.workshopmanagement.rdmotors.compras.aplicacion.ComandoRegistrarCompra;
import com.workshopmanagement.rdmotors.compras.aplicacion.ConsultarCompras;
import com.workshopmanagement.rdmotors.compras.aplicacion.CorregirCompra;
import com.workshopmanagement.rdmotors.compras.aplicacion.FilaHistorial;
import com.workshopmanagement.rdmotors.compras.aplicacion.DetalleCompra;
import com.workshopmanagement.rdmotors.compras.aplicacion.RegistrarCompra;
import com.workshopmanagement.rdmotors.compras.aplicacion.ResultadoCorreccion;
import com.workshopmanagement.rdmotors.compras.dominio.BusquedaDeRepuesto;
import com.workshopmanagement.rdmotors.compras.dominio.Compra;
import com.workshopmanagement.rdmotors.compras.dominio.EstadoCompra;
import com.workshopmanagement.rdmotors.compras.dominio.FiltroCompras;
import com.workshopmanagement.rdmotors.compartido.dominio.FormaPago;
import com.workshopmanagement.rdmotors.compras.dominio.LineaCompra;
import com.workshopmanagement.rdmotors.compras.dominio.ModoCaptura;
import com.workshopmanagement.rdmotors.compras.dominio.ResumenCompra;
import com.workshopmanagement.rdmotors.compras.dominio.TotalesCompras;
import com.workshopmanagement.rdmotors.inventario.aplicacion.ComandoCrearRepuesto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.RequiredArgsConstructor;

/**
 * ADAPTADOR DE ENTRADA — traduce HTTP al lenguaje del dominio.
 *
 * <p>Fijate en lo poco que hace: recibe JSON, arma un comando y llama al caso de uso. Ni una regla
 * de negocio vive aqui. Si manana la compra tambien se pudiera registrar desde la cola offline o
 * desde un importador de Excel, el caso de uso no cambiaria — solo se agregaria otro adaptador.
 */
@RestController
@RequestMapping("/api/compras")
@RequiredArgsConstructor
class CompraController {

    private final RegistrarCompra registrarCompra;
    private final CorregirCompra corregirCompra;
    private final AnularCompra anularCompra;
    private final ConsultarCompras consultarCompras;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    RespuestaCompra registrar(@Valid @RequestBody PeticionCompra peticion,
                              @ActorActual Actor actor) {

        Compra compra = registrarCompra.ejecutar(peticion.aComando(actor));
        return RespuestaCompra.de(compra);
    }

    /**
     * El historial (spec 0002, H2). Las fechas son de la factura, en formato {@code 2026-09-01}.
     * Un rango al revés lo rechaza el dominio con 422 y un mensaje legible.
     */
    @GetMapping
    RespuestaHistorial historial(
            @RequestParam(required = false) UUID proveedorId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) FormaPago formaPago,
            @RequestParam(required = false) UUID cuentaId,
            @RequestParam(required = false) String factura,
            @RequestParam(required = false) EstadoCompra estado,
            @RequestParam(required = false) String repuesto,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "25") int tamano,
            @ActorActual Actor actor) {
        var filtro = new FiltroCompras(proveedorId, desde, hasta, formaPago, cuentaId, factura, estado,
                BusquedaDeRepuesto.de(repuesto));
        return RespuestaHistorial.de(consultarCompras.historialConCoincidencias(filtro, pagina, tamano, actor));
    }

    /**
     * Lo pagado con esos filtros, por forma de pago y cuenta (spec 0002, H6). Mismos filtros que el
     * historial, sin paginar. Las anuladas no cuentan.
     */
    @GetMapping("/totales")
    RespuestaTotales totales(
            @RequestParam(required = false) UUID proveedorId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) FormaPago formaPago,
            @RequestParam(required = false) UUID cuentaId,
            @RequestParam(required = false) String factura,
            @RequestParam(required = false) EstadoCompra estado,
            @RequestParam(required = false) String repuesto,
            @ActorActual Actor actor) {
        var filtro = new FiltroCompras(proveedorId, desde, hasta, formaPago, cuentaId, factura, estado,
                BusquedaDeRepuesto.de(repuesto));
        return RespuestaTotales.de(consultarCompras.totales(filtro, actor));
    }

    /**
     * Una factura completa. 404 y no vacío: aquí se pide una concreta por su id.
     *
     * <p>Con {@code repuesto}, cada renglón dice si coincide (spec 0002, RF-026): la pantalla resalta
     * lo que decide el backend, no compara por su cuenta.
     */
    @GetMapping("/{id}")
    RespuestaDetalle detalle(@PathVariable UUID id, @RequestParam(required = false) String repuesto,
                             @ActorActual Actor actor) {
        return consultarCompras.detalle(id, BusquedaDeRepuesto.de(repuesto), actor)
                .map(RespuestaDetalle::de)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Esa compra no existe"));
    }

    /**
     * Corregir una compra (spec 0002, H3 y H4). {@code POST} y no {@code PUT}: una corrección es un
     * hecho nuevo que queda en la auditoría y puede mover inventario, no un reemplazo repetible.
     *
     * <p>Devuelve la compra ya corregida, leída de nuevo —con su versión nueva—, y los avisos.
     */
    @PostMapping("/{id}/correcciones")
    RespuestaCorreccion corregir(@PathVariable UUID id,
                                 @Valid @RequestBody PeticionCorreccion peticion,
                                 @ActorActual Actor actor) {
        ResultadoCorreccion resultado = corregirCompra.ejecutar(peticion.aComando(id, actor));
        return RespuestaCorreccion.de(detalleDe(id, actor), resultado.avisos());
    }

    /** Anular una compra que no debió registrarse (spec 0002, H5). */
    @PostMapping("/{id}/anulacion")
    RespuestaCorreccion anular(@PathVariable UUID id,
                               @Valid @RequestBody PeticionAnulacion peticion,
                               @ActorActual Actor actor) {
        ResultadoCorreccion resultado = anularCompra.ejecutar(id, peticion.version(),
                peticion.motivo(), actor);
        return RespuestaCorreccion.de(detalleDe(id, actor), resultado.avisos());
    }

    private DetalleCompra detalleDe(UUID id, Actor actor) {
        return consultarCompras.detalle(id, actor).orElseThrow();
    }

    // ── Lo que entra ─────────────────────────────────────────────────────────

    /**
     * {@code cuentaId} solo en transferencias. Que cuadre con la forma de pago no se valida aquí:
     * lo exige el dominio, con el mensaje que ve el administrador.
     */
    record PeticionCompra(
            /** La llave contra el doble registro (spec 0009, RF-009): la arma la pantalla y la repite al reintentar. */
            @NotNull UUID llave,
            @NotNull UUID proveedorId,
            @NotNull LocalDate fechaDocumento,
            String numeroFactura,
            @NotNull FormaPago formaPago,
            UUID cuentaId,
            @NotNull List<@Valid PeticionLinea> lineas,
            boolean pagadaDeCaja) {

        ComandoRegistrarCompra aComando(Actor actor) {
            return new ComandoRegistrarCompra(proveedorId, fechaDocumento, numeroFactura,
                    formaPago, cuentaId, actor,
                    lineas.stream().map(PeticionLinea::aLinea).toList(), pagadaDeCaja, llave);
        }
    }

    /**
     * Segun {@code modo} llega {@code costoTotal} o {@code costoUnitario}. Los montos viajan como
     * enteros en JSON y aqui se envuelven en {@link Dinero} — traducir del mundo exterior al
     * vocabulario del dominio es justo el trabajo de un adaptador.
     */
    record PeticionLinea(
            UUID varianteId,
            @Valid PeticionRepuestoNuevo repuestoNuevo,
            @Positive int cantidad,
            @NotNull ModoCaptura modo,
            Long costoTotal,
            BigDecimal costoUnitario,
            Long precioVenta) {

        ComandoRegistrarCompra.Linea aLinea() {
            if (repuestoNuevo != null) {
                var nuevo = repuestoNuevo.aComando();
                return switch (modo) {
                    case TOTAL -> ComandoRegistrarCompra.Linea.porTotalCreando(
                            nuevo, cantidad, Dinero.de(costoTotal));
                    case UNITARIO -> ComandoRegistrarCompra.Linea.porUnitarioCreando(
                            nuevo, cantidad, costoUnitario);
                };
            }
            Dinero precio = precioVenta == null ? null : Dinero.de(precioVenta);
            return switch (modo) {
                case TOTAL -> ComandoRegistrarCompra.Linea.porTotal(
                        varianteId, cantidad, Dinero.de(costoTotal), precio);
                case UNITARIO -> ComandoRegistrarCompra.Linea.porUnitario(
                        varianteId, cantidad, costoUnitario, precio);
            };
        }
    }

    /**
     * La compra completa como debe quedar. {@code lineas} {@code null} = no tocar renglones;
     * {@code pagadaDeCaja} {@code null} = no tocar si se pagó con plata del cajón.
     * {@code version} es la que la pantalla cargó: si ya no es la actual, responde 409.
     */
    record PeticionCorreccion(
            @NotNull Long version,
            String motivo,
            @NotNull UUID proveedorId,
            @NotNull LocalDate fechaDocumento,
            String numeroFactura,
            @NotNull FormaPago formaPago,
            UUID cuentaId,
            List<@Valid PeticionLineaCorregida> lineas,
            Boolean pagadaDeCaja) {

        ComandoCorregirCompra aComando(UUID compraId, Actor actor) {
            return new ComandoCorregirCompra(compraId, version, motivo, actor, proveedorId,
                    fechaDocumento, numeroFactura, formaPago, cuentaId,
                    lineas == null ? null : lineas.stream().map(PeticionLineaCorregida::aLinea).toList(),
                    pagadaDeCaja);
        }
    }

    /** Un renglón de la corrección: el mismo formato que al registrar, más el renglón que representa. */
    record PeticionLineaCorregida(
            UUID lineaId,
            UUID varianteId,
            @Valid PeticionRepuestoNuevo repuestoNuevo,
            @Positive int cantidad,
            @NotNull ModoCaptura modo,
            Long costoTotal,
            BigDecimal costoUnitario,
            Long precioVenta) {

        ComandoCorregirCompra.Linea aLinea() {
            return new ComandoCorregirCompra.Linea(lineaId, new PeticionLinea(varianteId, repuestoNuevo,
                    cantidad, modo, costoTotal, costoUnitario, precioVenta).aLinea());
        }
    }

    record PeticionAnulacion(@NotNull Long version, String motivo) {
    }

    /**
     * El repuesto que nace con esta compra. Su precio de venta va AQUI —no en el renglon— porque
     * es donde el administrador lo escribio: en el formulario de creacion. Dos fuentes para el
     * mismo dato es la receta para que diverjan, y el dominio rechaza que vengan las dos.
     */
    record PeticionRepuestoNuevo(
            UUID productoId,
            String nombreProducto,
            UUID categoriaId,
            String aplicacionOriginal,
            @NotBlank String codigo,
            @NotBlank String marcaRepuesto,
            @PositiveOrZero long precio,
            @PositiveOrZero int stockMinimo) {

        ComandoCrearRepuesto aComando() {
            return new ComandoCrearRepuesto(productoId, nombreProducto, categoriaId,
                    aplicacionOriginal, codigo, marcaRepuesto, Dinero.de(precio), stockMinimo);
        }
    }

    // ── Lo que sale ──────────────────────────────────────────────────────────

    record RespuestaHistorial(List<RespuestaResumen> elementos, long total, int numero, int tamano,
                              int totalPaginas) {

        static RespuestaHistorial de(Pagina<FilaHistorial> p) {
            return new RespuestaHistorial(p.elementos().stream().map(RespuestaResumen::de).toList(),
                    p.total(), p.numero(), p.tamano(), p.totalPaginas());
        }
    }

    /**
     * {@code cuenta} es el nombre, o {@code null} en efectivo. {@code primeros}: los primeros tres renglones
     * vigentes, en el orden de la factura (RF-028); cuántos tiene en total lo dice {@code renglones}.
     * {@code coinciden}: los renglones que coinciden con el repuesto buscado (RF-027); vacío si no se buscó.
     */
    record RespuestaResumen(UUID id, LocalDate fechaDocumento, Instant fechaRegistro,
                            String proveedor, String numeroFactura, FormaPago formaPago,
                            String cuenta, boolean pagadaDeCaja, EstadoCompra estado, long renglones, long total,
                            List<FilaHistorial.RenglonQueCoincide> primeros,
                            List<FilaHistorial.RenglonQueCoincide> coinciden) {

        static RespuestaResumen de(FilaHistorial fila) {
            ResumenCompra r = fila.resumen();
            return new RespuestaResumen(r.id(), r.fechaDocumento(), r.fechaRegistro(), r.proveedor(),
                    r.numeroFactura(), r.formaPago(), r.cuenta(), r.pagadaDeCaja(), r.estado(), r.renglones(),
                    r.total().valor().longValueExact(), fila.primeros(), fila.coinciden());
        }
    }

    record RespuestaDetalle(UUID id, long version, EstadoCompra estado, UUID proveedorId,
                            String proveedor, LocalDate fechaDocumento, Instant fechaRegistro,
                            String numeroFactura, FormaPago formaPago, UUID cuentaId, String cuenta,
                            boolean pagadaDeCaja, UUID turnoId, long total, Persona registradoPor, Instant modificadaEn,
                            Instant anuladaEn, Persona anuladaPor, String motivoAnulacion,
                            List<RespuestaRenglon> renglones, List<RespuestaRenglon> reemplazados,
                            List<RespuestaEvento> correcciones) {

        static RespuestaDetalle de(DetalleCompra d) {
            return new RespuestaDetalle(d.id(), d.version(), d.estado(), d.proveedorId(),
                    d.proveedor(), d.fechaDocumento(), d.fechaRegistro(), d.numeroFactura(),
                    d.formaPago(), d.cuentaId(), d.cuenta(), d.pagadaDeCaja(), d.turnoId(),
                    d.total().valor().longValueExact(),
                    d.registradoPor(), d.modificadaEn(), d.anuladaEn(), d.anuladaPor(),
                    d.motivoAnulacion(),
                    d.renglones().stream().map(RespuestaRenglon::de).toList(),
                    d.reemplazados().stream().map(RespuestaRenglon::de).toList(),
                    d.correcciones().stream().map(RespuestaEvento::de).toList());
        }
    }

    /** Una entrada del rastro, con el nombre de quién (spec 0004, RF-022). {@code antes} y {@code despues} son las fotos. */
    record RespuestaEvento(AccionAuditada accion, Instant ocurridoEn, Persona quien, String motivo,
                           Map<String, Object> antes, Map<String, Object> despues) {

        static RespuestaEvento de(CambioAuditado c) {
            return new RespuestaEvento(c.accion(), c.ocurridoEn(), c.quien(), c.motivo(), c.antes(), c.despues());
        }
    }

    /** {@code total} y {@code partes} vienen de consultas distintas: que cuadren se puede comprobar. */
    record RespuestaTotales(long total, long compras, List<RespuestaParte> partes) {

        static RespuestaTotales de(TotalesCompras t) {
            return new RespuestaTotales(t.total().valor().longValueExact(), t.compras(),
                    t.partes().stream().map(p -> new RespuestaParte(p.formaPago(), p.cuentaId(),
                            p.cuenta(), p.compras(), p.total().valor().longValueExact())).toList());
        }
    }

    record RespuestaParte(FormaPago formaPago, UUID cuentaId, String cuenta, long compras, long total) {
    }

    /** La compra como quedó, y lo que hay que avisar aunque todo haya salido bien. */
    record RespuestaCorreccion(RespuestaDetalle compra, List<String> avisos) {

        static RespuestaCorreccion de(DetalleCompra detalle, List<String> avisos) {
            return new RespuestaCorreccion(RespuestaDetalle.de(detalle), avisos);
        }
    }

    /**
     * Lo que se registró en esa compra, y cómo está el repuesto hoy ({@code precioActual},
     * {@code stockActual}, {@code costoPromedioActual}).
     *
     * <p>{@code precioVenta} es el precio que fijó esta compra, o {@code null} si no lo cambió.
     * {@code costoPromedioActual} es {@code null} si el costo no se conoce: nunca cero.
     */
    record RespuestaRenglon(UUID lineaId, int posicion, UUID varianteId, String codigo,
                            String nombre, String marca, String aplicacion, int cantidad,
                            ModoCaptura modoCaptura, long costoTotal, BigDecimal costoUnitario,
                            Long precioVenta, Instant reemplazadaEn, long precioActual,
                            int stockActual, BigDecimal costoPromedioActual, boolean coincide) {

        static RespuestaRenglon de(DetalleCompra.Renglon r) {
            var rep = r.repuesto();
            return new RespuestaRenglon(r.lineaId(), r.posicion(), rep.id(), rep.codigo(),
                    rep.nombre(), rep.marcaRepuesto(), rep.aplicacion(), r.cantidad(), r.modoCaptura(),
                    r.costoTotal().valor().longValueExact(), r.costoUnitario(),
                    r.precioVenta() == null ? null : r.precioVenta().valor().longValueExact(),
                    r.reemplazadaEn(), rep.precio().valor().longValueExact(), rep.stock(),
                    rep.costoPromedio(), r.coincide());
        }
    }

    /** {@code cuenta} es el nombre de la cuenta, o {@code null} si se pagó en efectivo. */
    record RespuestaCompra(UUID id, String proveedor, LocalDate fechaDocumento,
                           String numeroFactura, FormaPago formaPago, String cuenta, boolean pagadaDeCaja,
                           long total,
                           List<RespuestaLinea> lineas) {

        static RespuestaCompra de(Compra compra) {
            return new RespuestaCompra(
                    compra.getId(),
                    compra.getProveedor().getNombre(),
                    compra.getFechaDocumento(),
                    compra.getNumeroFactura(),
                    compra.getFormaPago(),
                    compra.getCuenta() == null ? null : compra.getCuenta().getNombre(),
                    compra.isPagadaDeCaja(),
                    compra.getTotal().valor().longValueExact(),
                    compra.lineasVigentes().stream().map(RespuestaLinea::de).toList());
        }
    }

    /**
     * Desglose completo del renglon: lo que se PAGO y lo que se VA A COBRAR, juntos.
     *
     * <p>Ver solo el costo deja al administrador sin saber si el precio que acaba de fijar tiene
     * sentido. Y ver solo el precio esconde si esa compra fue buena. Los dos lados, o ninguno.
     */
    record RespuestaLinea(String codigo, String nombre, String marca, int cantidad,
                          long costoTotal, BigDecimal costoUnitario,
                          long precioVenta, BigDecimal costoPromedio, int stockResultante) {

        static RespuestaLinea de(LineaCompra linea) {
            var variante = linea.getVariante();
            return new RespuestaLinea(
                    variante.getCodigo(),
                    variante.getProducto().getNombre(),
                    variante.getMarcaRepuesto(),
                    linea.getCantidad(),
                    linea.getCostoTotal().valor().longValueExact(),
                    // Se devuelve el unitario CON decimales: es lo que el usuario quiere ver
                    // ("me sale a $13.333,33 c/u"), y ocultarlo esconderia de donde salio.
                    linea.getCostoUnitario(),
                    // El precio de venta YA actualizado si este renglon lo cambio.
                    variante.getPrecio().valor().longValueExact(),
                    // Promedio ponderado tras la compra: puede diferir del costo de este renglon
                    // si el repuesto ya tenia existencias a otro precio.
                    variante.getCostoPromedio(),
                    variante.getStock());
        }
    }
}

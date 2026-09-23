package com.workshopmanagement.rdmotors.inventario.infraestructura;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
import com.workshopmanagement.rdmotors.compartido.dominio.puerto.RepositorioAuditoria;
import com.workshopmanagement.rdmotors.compras.infraestructura.DocumentosDeCompra;
import com.workshopmanagement.rdmotors.compras.infraestructura.DocumentosDeCompra.Documento;
import com.workshopmanagement.rdmotors.compras.infraestructura.DocumentosDeCompra.Descripcion;
import com.workshopmanagement.rdmotors.inventario.aplicacion.ActualizarRepuesto;
import com.workshopmanagement.rdmotors.inventario.aplicacion.ActualizarRepuesto.ComandoActualizarRepuesto;
import com.workshopmanagement.rdmotors.inventario.aplicacion.BuscarRepuestos;
import com.workshopmanagement.rdmotors.inventario.aplicacion.ComandoCrearRepuesto;
import com.workshopmanagement.rdmotors.inventario.aplicacion.CrearRepuesto;
import com.workshopmanagement.rdmotors.inventario.aplicacion.RepuestoEncontrado;
import com.workshopmanagement.rdmotors.inventario.dominio.MovimientoKardex;
import com.workshopmanagement.rdmotors.inventario.dominio.OrigenMovimiento;
import com.workshopmanagement.rdmotors.inventario.dominio.TipoMovimiento;
import com.workshopmanagement.rdmotors.inventario.dominio.Variante;
import com.workshopmanagement.rdmotors.inventario.dominio.puerto.RepositorioKardex;
import com.workshopmanagement.rdmotors.usuarios.dominio.puerto.RepositorioUsuarios;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.RequiredArgsConstructor;

/**
 * ADAPTADOR DE ENTRADA — repuestos: crear, buscar y ver su historial.
 *
 * <p>La busqueda acepta {@code ?codigo=} o {@code ?q=} y devuelve <b>una lista en los dos casos</b>,
 * aunque por codigo traiga cero o uno. Es a proposito: el frontend tiene un solo campo (RF-007b) y
 * una sola forma de respuesta le evita dos caminos de pintado.
 */
@RestController
@RequestMapping("/api/repuestos")
@RequiredArgsConstructor
class RepuestoController {

    private final CrearRepuesto crearRepuesto;
    private final ActualizarRepuesto actualizarRepuesto;
    private final BuscarRepuestos buscarRepuestos;
    private final RepositorioKardex kardex;
    private final DocumentosDeCompra documentosDeCompra;
    private final RepositorioAuditoria auditoria;
    private final RepositorioUsuarios usuarios;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    RespuestaRepuesto crear(@Valid @RequestBody PeticionRepuesto peticion, @ActorActual Actor actor) {
        Variante creada = crearRepuesto.ejecutar(peticion.aComando(), actor);
        return RespuestaRepuesto.de(creada);
    }

    /**
     * Corregir un repuesto mal creado (RF-009).
     *
     * <p>PUT y no PATCH a proposito: REEMPLAZA la ficha entera. Un endpoint que reemplaza es
     * idempotente por construccion — repetirlo deja el mismo estado — y eso importa con una cola
     * offline que reenvia. Ver la regla SUMA vs REEMPLAZO en la skill {@code backend}.
     */
    @PutMapping("/{id}")
    RespuestaRepuesto actualizar(@PathVariable UUID id,
                                 @Valid @RequestBody PeticionActualizar peticion,
                                 @ActorActual Actor actor) {
        return RespuestaRepuesto.de(actualizarRepuesto.ejecutar(id, peticion.aComando(actor)));
    }

    /**
     * Las correcciones de la ficha, de la más antigua a la más reciente (spec 0002, H7). Lee el
     * puerto directo: no hay nada que mapear ni proteger.
     */
    @GetMapping("/{id}/correcciones")
    List<RespuestaCorreccion> correcciones(@PathVariable UUID id) {
        List<EventoAuditoria> historial = auditoria.historialDe(ActualizarRepuesto.TIPO_AUDITORIA, id);
        // Los nombres de quienes la corrigieron, en una sola consulta (spec 0004, RF-022).
        Map<UUID, String> nombres = usuarios.nombresDe(historial.stream().map(EventoAuditoria::usuarioId).toList());
        return historial.stream().map(e -> RespuestaCorreccion.de(CambioAuditado.de(e, nombres))).toList();
    }

    /**
     * Un solo endpoint para los dos gestos. Si vienen los dos parametros manda el codigo: es el
     * mas especifico, y es el que el administrador copia de la factura.
     */
    @GetMapping
    List<Object> buscar(@RequestParam(required = false) String codigo,
                        @RequestParam(required = false) String q,
                        @ActorActual Actor actor) {
        if (codigo != null && !codigo.isBlank()) {
            return buscarRepuestos.porCodigoExacto(codigo)
                    .map(r -> RespuestaRepuesto.de(r).para(actor))
                    .map(List::of)
                    .orElseGet(List::of);
        }
        return buscarRepuestos.porTexto(q).stream().map(r -> RespuestaRepuesto.de(r).para(actor)).toList();
    }

    /** La ficha del repuesto. 404 y no lista vacia: aqui se pide uno concreto por su id. */
    @GetMapping("/{id}")
    Object ficha(@PathVariable UUID id, @ActorActual Actor actor) {
        return buscarRepuestos.porId(id)
                .map(r -> RespuestaRepuesto.de(r).para(actor))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Ese repuesto no existe"));
    }

    /**
     * El kardex es cronologico por naturaleza; la pantalla lo muestra al reves (RF-021).
     *
     * <p>Cada movimiento de compra sale con su proveedor y su factura, y cada entrada con el precio
     * que fijó su renglón. Se busca todo en dos consultas, no una por movimiento.
     */
    @GetMapping("/{id}/kardex")
    List<?> historial(@PathVariable UUID id, @ActorActual Actor actor) {
        List<MovimientoKardex> historial = kardex.historialDe(id);
        if (!actor.rol().veCostos()) {
            // Sin costos ni documentos de compra: las compras son del administrador (spec 0004, RF-011).
            return historial.reversed().stream().map(RespuestaMovimientoSinCostos::de).toList();
        }
        List<UUID> compras = historial.stream()
                .filter(m -> m.getOrigenTipo() == OrigenMovimiento.COMPRA && m.getOrigenId() != null)
                .map(MovimientoKardex::getOrigenId)
                .distinct()
                .toList();
        List<UUID> entradas = historial.stream()
                .filter(m -> m.getTipo() == TipoMovimiento.COMPRA)
                .map(MovimientoKardex::getId)
                .toList();
        Descripcion descripcion = documentosDeCompra.describir(compras, entradas);

        List<RespuestaMovimiento> movimientos = new ArrayList<>(historial.stream()
                .map(m -> RespuestaMovimiento.de(m,
                        m.getOrigenTipo() == OrigenMovimiento.COMPRA
                                ? descripcion.compras().get(m.getOrigenId())
                                : null,
                        descripcion.precioPorEntrada().get(m.getId())))
                .toList());
        Collections.reverse(movimientos);
        return movimientos;
    }

    // ── Lo que entra ─────────────────────────────────────────────────────────

    /**
     * O {@code productoId} (marca nueva de algo que ya se vende) o {@code nombreProducto} (algo
     * que nunca se ha vendido). Nunca los dos: el comando del dominio lo rechaza.
     */
    record PeticionRepuesto(
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

    /**
     * Lo que se puede corregir. <b>El stock y el costo promedio NO estan aqui y no es un olvido:</b>
     * son resultado del kardex y se corrigen con un ajuste de inventario, que deja su movimiento
     * y su motivo. Escribirlos a mano dejaria un saldo que ningun movimiento explica.
     */
    record PeticionActualizar(
            @NotBlank String nombreProducto,
            UUID categoriaId,
            String aplicacionOriginal,
            @NotBlank String codigo,
            @NotBlank String marcaRepuesto,
            @PositiveOrZero long precio,
            @PositiveOrZero int stockMinimo) {

        ComandoActualizarRepuesto aComando(Actor actor) {
            return new ComandoActualizarRepuesto(nombreProducto, categoriaId, aplicacionOriginal,
                    codigo, marcaRepuesto, Dinero.de(precio), stockMinimo, actor);
        }
    }

    /**
     * Una corrección de la ficha, con el nombre de quién la hizo (spec 0004, RF-022). {@code antes} y
     * {@code despues} son las fotos de la ficha.
     */
    record RespuestaCorreccion(AccionAuditada accion, java.time.Instant ocurridoEn, Persona quien,
                               String motivo, Map<String, Object> antes, Map<String, Object> despues) {

        static RespuestaCorreccion de(CambioAuditado c) {
            return new RespuestaCorreccion(c.accion(), c.ocurridoEn(), c.quien(), c.motivo(), c.antes(), c.despues());
        }
    }

    // ── Lo que sale ──────────────────────────────────────────────────────────

    /**
     * {@code costoPromedio} y {@code valor} viajan como {@code null} cuando el repuesto nunca se ha
     * comprado, y el frontend pinta "—". <b>Nunca se manda cero</b>: un cero inventado se lee como
     * un hecho y haria reportar 100% de margen (RF-013).
     *
     * <p>{@code categoriaId} y {@code stockMinimo} no son adorno: el formulario de corregir ficha
     * arranca con ellos y los reenvia. Sin ellos, guardar sin tocar nada borraba la categoria y
     * dejaba el minimo en 5.
     */
    public record RespuestaRepuesto(UUID id, String codigo, String nombre, String marca,
                                    String aplicacion, UUID categoriaId, String categoria,
                                    long precio, int stock, int stockMinimo,
                                    BigDecimal costoPromedio, boolean costoDesconocido,
                                    boolean stockBajo, Long valor) {

        static RespuestaRepuesto de(RepuestoEncontrado r) {
            return new RespuestaRepuesto(r.id(), r.codigo(), r.nombre(), r.marcaRepuesto(),
                    r.aplicacion(), r.categoriaId(), r.categoria(),
                    r.precio().valor().longValueExact(), r.stock(), r.stockMinimo(),
                    r.costoPromedio(), r.costoDesconocido(), r.stockBajo(),
                    // Se redondea a pesos al SALIR: 15 x 13.333,3333 son $200.000, no $199.999
                    r.valor() == null ? null : Dinero.de(r.valor()).valor().longValueExact());
        }

        static RespuestaRepuesto de(Variante v) {
            return de(RepuestoEncontrado.de(v));
        }

        /** Tal cual al administrador; al cajero, sin costo ni valor (spec 0004, decisión 1). */
        Object para(Actor actor) {
            return actor.rol().veCostos() ? this : new RespuestaRepuestoSinCostos(id, codigo, nombre, marca,
                    aplicacion, categoriaId, categoria, precio, stock, stockMinimo, stockBajo);
        }
    }

    /**
     * El repuesto como lo ve el cajero: precio y existencias, sin costo, sin valor y sin decir si el costo se
     * desconoce. No son los mismos campos en {@code null}: los campos no están (spec 0004, RF-011).
     */
    public record RespuestaRepuestoSinCostos(UUID id, String codigo, String nombre, String marca,
                                             String aplicacion, UUID categoriaId, String categoria,
                                             long precio, int stock, int stockMinimo, boolean stockBajo) {
    }

    /**
     * {@code proveedor}, {@code factura}, {@code fechaDocumento}, {@code compraId} y
     * {@code compraAnulada} solo vienen en los movimientos de compra y sus reversiones.
     * {@code precioVenta} solo en las entradas cuyo renglón cambió el precio.
     * {@code motivo} en las correcciones, anulaciones y entradas corregidas.
     */
    record RespuestaMovimiento(TipoMovimiento tipo, Instant cuando, int cantidad,
                               BigDecimal costoUnitario, Long costoTotal,
                               int saldoDespues, BigDecimal costoPromedioDespues,
                               String proveedor, String factura, LocalDate fechaDocumento,
                               UUID compraId, boolean compraAnulada, Long precioVenta,
                               String motivo) {

        static RespuestaMovimiento de(MovimientoKardex m, Documento documento, Long precioVenta) {
            return new RespuestaMovimiento(
                    m.getTipo(), m.getCreadoEn(), m.getCantidadDelta(), m.getCostoUnitario(),
                    m.getCostoTotal() == null ? null : m.getCostoTotal().valor().longValueExact(),
                    m.getSaldoDespues(), m.getCostoPromedioDespues(),
                    documento == null ? null : documento.proveedor(),
                    documento == null ? null : documento.factura(),
                    documento == null ? null : documento.fechaDocumento(),
                    documento == null ? null : m.getOrigenId(),
                    documento != null && documento.anulada(),
                    precioVenta,
                    m.getMotivo());
        }
    }

    /** El kardex como lo ve el cajero: qué entró y salió y cuánto quedó, sin costos ni compras. */
    record RespuestaMovimientoSinCostos(TipoMovimiento tipo, Instant cuando, int cantidad, int saldoDespues,
                                        String motivo) {

        static RespuestaMovimientoSinCostos de(MovimientoKardex m) {
            return new RespuestaMovimientoSinCostos(m.getTipo(), m.getCreadoEn(), m.getCantidadDelta(),
                    m.getSaldoDespues(), m.getMotivo());
        }
    }
}

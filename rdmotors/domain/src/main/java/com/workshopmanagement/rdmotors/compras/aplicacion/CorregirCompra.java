package com.workshopmanagement.rdmotors.compras.aplicacion;

import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.springframework.transaction.annotation.Transactional;

import com.workshopmanagement.rdmotors.caja.dominio.TurnoCaja;
import com.workshopmanagement.rdmotors.caja.dominio.puerto.RepositorioTurnos;
import com.workshopmanagement.rdmotors.compartido.dominio.AccionAuditada;
import com.workshopmanagement.rdmotors.compartido.dominio.Dinero;
import com.workshopmanagement.rdmotors.compartido.dominio.EventoAuditoria;
import com.workshopmanagement.rdmotors.compartido.dominio.ReglaDeNegocioException;
import com.workshopmanagement.rdmotors.compartido.dominio.puerto.Reloj;
import com.workshopmanagement.rdmotors.compartido.dominio.puerto.RepositorioAuditoria;
import com.workshopmanagement.rdmotors.compras.dominio.Compra;
import com.workshopmanagement.rdmotors.compras.dominio.CuentaPago;
import com.workshopmanagement.rdmotors.compras.dominio.LineaCompra;
import com.workshopmanagement.rdmotors.compras.dominio.Proveedor;
import com.workshopmanagement.rdmotors.compras.dominio.RenglonesBloqueadosException;
import com.workshopmanagement.rdmotors.compras.dominio.puerto.RepositorioCompras;
import com.workshopmanagement.rdmotors.compras.dominio.puerto.RepositorioCuentas;
import com.workshopmanagement.rdmotors.compras.dominio.puerto.RepositorioProveedores;
import com.workshopmanagement.rdmotors.inventario.aplicacion.CrearRepuesto;
import com.workshopmanagement.rdmotors.inventario.dominio.TipoMovimiento;
import com.workshopmanagement.rdmotors.inventario.dominio.Variante;
import com.workshopmanagement.rdmotors.inventario.dominio.puerto.RepositorioKardex;
import com.workshopmanagement.rdmotors.inventario.dominio.puerto.RepositorioVariantes;

/**
 * CASO DE USO — corregir una compra ya registrada (spec 0002, H3 y H4).
 *
 * <h2>Corregir por renglón (decisión 1 del spec)</h2>
 *
 * Cada renglón se clasifica contra lo guardado:
 *
 * <ul>
 *   <li><b>sin cambio</b> — no se toca, y su repuesto no recibe movimientos;
 *   <li><b>solo precio</b> — no mueve inventario ni se bloquea por ventas: el precio no cambia stock
 *       ni costo;
 *   <li><b>cambia la entrada</b> (cantidad, costo o repuesto) — su entrada se revierte y entra la
 *       corregida;
 *   <li><b>quitado</b> — su entrada se revierte;
 *   <li><b>nuevo</b> — entra como al registrar.
 * </ul>
 *
 * <p>Antes de mover nada se juntan <b>todos</b> los renglones bloqueados por ventas o ajustes
 * posteriores. Si hay uno solo, no se aplica nada. Y todo va bajo un commit: jamás queda una entrada
 * revertida sin la corregida.
 *
 * <p>Las salidas se hacen antes que las entradas. Así intercambiar dos repuestos entre renglones
 * funciona: primero salen los dos, después entra cada uno en su nuevo lugar.
 */
@Transactional
public class CorregirCompra {

    private final RepositorioCompras compras;
    private final RepositorioProveedores proveedores;
    private final RepositorioCuentas cuentas;
    private final RepositorioTurnos turnos;
    private final RepositorioVariantes variantes;
    private final RepositorioAuditoria auditoria;
    private final InventarioDeCompra inventario;
    private final Reloj reloj;

    public CorregirCompra(RepositorioCompras compras, RepositorioProveedores proveedores,
                          RepositorioCuentas cuentas, RepositorioTurnos turnos, RepositorioVariantes variantes,
                          RepositorioKardex kardex, CrearRepuesto crearRepuesto,
                          RepositorioAuditoria auditoria, Reloj reloj) {
        this.compras = compras;
        this.proveedores = proveedores;
        this.cuentas = cuentas;
        this.turnos = turnos;
        this.variantes = variantes;
        this.auditoria = auditoria;
        this.inventario = new InventarioDeCompra(variantes, kardex, compras, crearRepuesto);
        this.reloj = reloj;
    }

    public ResultadoCorreccion ejecutar(ComandoCorregirCompra comando) {
        comando.actor().exigirAdministrador();
        Compra compra = compras.buscarParaModificar(comando.compraId())
                .orElseThrow(() -> new ReglaDeNegocioException("La compra no existe"));
        compra.exigirVersion(comando.versionEsperada());
        compra.exigirVigente();

        // Con plata del cajón (spec 0006): el turno se pide antes que los repuestos, para que el cierre no
        // se calcule en medio. Va después de la compra y no antes: el cierre nunca bloquea compras, así que
        // este orden no puede trabarse con él, y leer la compra sin bloqueo primero la dejaría vieja en memoria.
        boolean deCaja = comando.pagadaDeCaja() == null ? compra.isPagadaDeCaja() : comando.pagadaDeCaja();
        UUID turnoAbiertoId = compra.isPagadaDeCaja() || deCaja
                ? turnos.abiertoParaMover().map(TurnoCaja::getId).orElse(null)
                : null;

        Map<String, Object> antes = compra.fotografia();

        Proveedor proveedor = proveedores.buscar(comando.proveedorId())
                .orElseThrow(() -> new ReglaDeNegocioException("El proveedor no existe"));
        CuentaPago cuenta = comando.cuentaId() == null ? null
                : cuentas.buscar(comando.cuentaId())
                        .orElseThrow(() -> new ReglaDeNegocioException("La cuenta no existe"));

        Instant ahora = reloj.ahora();
        List<String> avisos = new ArrayList<>();

        boolean cambiaronDatos = compra.corregirDatos(proveedor, comando.fechaDocumento(),
                comando.numeroFactura(), comando.formaPago(), cuenta);
        cambiaronDatos |= compra.corregirPagoDeCaja(deCaja, turnoAbiertoId);
        boolean cambiaronRenglones = comando.lineas() != null
                && corregirRenglones(compra, comando, ahora, avisos);

        if (!cambiaronDatos && !cambiaronRenglones) {
            throw new ReglaDeNegocioException("No hay cambios que guardar");
        }

        compra.cerrarCorreccion(ahora);
        auditoria.registrar(EventoAuditoria.nuevo(ahora, comando.actor().id(),
                AccionAuditada.CORREGIR_COMPRA, Compra.TIPO_AUDITORIA, compra.getId(),
                antes, compra.fotografia(), comando.motivo()));

        return new ResultadoCorreccion(compras.guardar(compra), avisos);
    }

    /** @return {@code true} si algún renglón cambió */
    private boolean corregirRenglones(Compra compra, ComandoCorregirCompra comando, Instant ahora,
                                      List<String> avisos) {
        Map<UUID, LineaCompra> vigentes = new LinkedHashMap<>();
        compra.lineasVigentes().forEach(linea -> vigentes.put(linea.getId(), linea));

        Set<UUID> referidas = new HashSet<>();
        for (ComandoCorregirCompra.Linea linea : comando.lineas()) {
            if (linea.lineaId() == null) continue;
            if (!vigentes.containsKey(linea.lineaId())) {
                throw new ReglaDeNegocioException(
                        "Uno de los renglones no es de esta compra o ya fue reemplazado. Vuelve a abrirla.");
            }
            if (!referidas.add(linea.lineaId())) {
                throw new ReglaDeNegocioException("El mismo renglón viene dos veces en la corrección");
            }
        }
        InventarioDeCompra.exigirRepuestosDistintos(
                comando.lineas().stream().map(ComandoCorregirCompra.Linea::datos).toList());

        // ── Clasificar ───────────────────────────────────────────────────────
        List<LineaCompra> quitadas = vigentes.values().stream()
                .filter(linea -> !referidas.contains(linea.getId()))
                .toList();
        List<LineaCompra> aRevertir = new ArrayList<>(quitadas);
        boolean hayCambio = !quitadas.isEmpty();
        for (ComandoCorregirCompra.Linea linea : comando.lineas()) {
            if (linea.lineaId() == null) {
                hayCambio = true;
                continue;
            }
            LineaCompra vieja = vigentes.get(linea.lineaId());
            if (!mismaEntrada(vieja, linea.datos())) {
                aRevertir.add(vieja);
                hayCambio = true;
            } else if (!Objects.equals(vieja.getPrecioVenta(), linea.datos().precioVenta())) {
                hayCambio = true;
            }
        }
        if (!hayCambio) {
            return false;
        }
        aRevertir.sort(Comparator.comparingInt(LineaCompra::getPosicion));

        // ── Revisar todos antes de mover nada ────────────────────────────────
        List<String> bloqueados = inventario.bloqueados(aRevertir);
        if (!bloqueados.isEmpty()) {
            throw new RenglonesBloqueadosException(bloqueados);
        }

        // ── 1. Salidas ───────────────────────────────────────────────────────
        for (LineaCompra vieja : aRevertir) {
            avisos.addAll(inventario.revertirEntrada(vieja, TipoMovimiento.CORRECCION_COMPRA,
                    compra.getId(), comando.motivo(), comando.actor().id(), ahora));
        }

        // ── 2. Entradas y precios ────────────────────────────────────────────
        for (ComandoCorregirCompra.Linea linea : comando.lineas()) {
            ComandoRegistrarCompra.Linea datos = linea.datos();
            if (linea.lineaId() == null) {
                LineaCompra nueva = datos.aLineaDe(inventario.resolverRepuesto(datos, comando.actor()));
                compra.agregarLinea(nueva);
                inventario.darEntrada(nueva, compra.getId(), comando.motivo(), comando.actor().id(), ahora);
                continue;
            }
            LineaCompra vieja = vigentes.get(linea.lineaId());
            if (!mismaEntrada(vieja, datos)) {
                LineaCompra nueva = datos.aLineaDe(inventario.resolverRepuesto(datos, comando.actor()));
                compra.reemplazarLinea(vieja, nueva, ahora);
                inventario.darEntrada(nueva, compra.getId(), comando.motivo(), comando.actor().id(), ahora);
            } else if (!Objects.equals(vieja.getPrecioVenta(), datos.precioVenta())) {
                Variante variante = variantes.buscarParaModificar(vieja.getVariante().getId())
                        .orElseThrow(() -> new ReglaDeNegocioException("El repuesto no existe"));
                LineaCompra nueva = vieja.conOtroPrecio(datos.precioVenta(), variante.getPrecio());
                avisos.addAll(inventario.cambiarPrecio(vieja, nueva, variante));
                compra.reemplazarLinea(vieja, nueva, ahora);
            }
        }

        // ── 3. Lo que ya no está en la factura ───────────────────────────────
        for (LineaCompra vieja : quitadas) {
            compra.quitarLinea(vieja, ahora);
        }
        return true;
    }

    /**
     * ¿El renglón mete lo mismo al inventario? Mismo repuesto, misma cantidad y el mismo costo en el
     * mismo modo. El precio no cuenta: cambiarlo no mueve inventario.
     */
    private static boolean mismaEntrada(LineaCompra vieja, ComandoRegistrarCompra.Linea datos) {
        if (datos.creaRepuesto() || !vieja.getVariante().getId().equals(datos.varianteId())) {
            return false;
        }
        if (vieja.getCantidad() != datos.cantidad() || vieja.getModoCaptura() != datos.modo()) {
            return false;
        }
        return switch (datos.modo()) {
            case TOTAL -> vieja.getCostoTotal().equals(datos.costoTotal());
            case UNITARIO -> datos.costoUnitario() != null && vieja.getCostoUnitario().compareTo(
                    datos.costoUnitario().setScale(Dinero.ESCALA_UNITARIA, RoundingMode.HALF_UP)) == 0;
        };
    }
}

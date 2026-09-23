package com.workshopmanagement.rdmotors.compras.aplicacion;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.workshopmanagement.rdmotors.compartido.dominio.Actor;
import com.workshopmanagement.rdmotors.compartido.dominio.Dinero;
import com.workshopmanagement.rdmotors.compartido.dominio.ReglaDeNegocioException;
import com.workshopmanagement.rdmotors.compras.dominio.LineaCompra;
import com.workshopmanagement.rdmotors.compras.dominio.puerto.RepositorioCompras;
import com.workshopmanagement.rdmotors.inventario.aplicacion.CrearRepuesto;
import com.workshopmanagement.rdmotors.inventario.dominio.MovimientoKardex;
import com.workshopmanagement.rdmotors.inventario.dominio.TipoMovimiento;
import com.workshopmanagement.rdmotors.inventario.dominio.Variante;
import com.workshopmanagement.rdmotors.inventario.dominio.puerto.RepositorioKardex;
import com.workshopmanagement.rdmotors.inventario.dominio.puerto.RepositorioVariantes;

/**
 * Lo que una compra le hace al inventario, y cómo se deshace (spec 0002, decisión 7 del plan).
 *
 * <p>La usan registrar, corregir y anular. Existe para que la regla del costo promedio, la del
 * precio anterior y la escritura del kardex vivan <b>en un solo sitio</b>: el mismo cálculo copiado
 * en tres casos de uso es el que termina corregido en dos y olvidado en el tercero.
 *
 * <p>No es un caso de uso ni abre transacción: trabaja dentro de la del caso de uso que la llama.
 * Por eso es de paquete y se construye adentro de cada uno, sin cableado aparte.
 */
final class InventarioDeCompra {

    private final RepositorioVariantes variantes;
    private final RepositorioKardex kardex;
    private final RepositorioCompras compras;
    private final CrearRepuesto crearRepuesto;

    InventarioDeCompra(RepositorioVariantes variantes, RepositorioKardex kardex,
                       RepositorioCompras compras, CrearRepuesto crearRepuesto) {
        this.variantes = variantes;
        this.kardex = kardex;
        this.compras = compras;
        this.crearRepuesto = crearRepuesto;
    }

    // ── Entrar ───────────────────────────────────────────────────────────────

    /**
     * El repuesto de un renglón: o ya estaba, o nace con esta compra.
     *
     * <p><b>Crear se delega a {@link CrearRepuesto}, no se reimplementa aqui.</b> Ese caso de uso
     * es el dueno de la regla del codigo unico y de la decision de reutilizar el concepto en vez
     * de duplicarlo; copiarla seria condenarlas a divergir. Como las dos son transaccionales con
     * propagacion por defecto, la creacion entra en el MISMO commit que la compra: si la compra
     * falla despues, el repuesto tampoco queda.
     *
     * <p>Un repuesto recien creado no necesita bloqueo de fila: nadie mas puede verlo todavia.
     */
    Variante resolverRepuesto(ComandoRegistrarCompra.Linea linea, Actor actor) {
        if (linea.creaRepuesto()) {
            return crearRepuesto.ejecutar(linea.repuestoNuevo(), actor);
        }
        // Con INTENCION DE MODIFICARLO. El adaptador toma bloqueo de fila: sin eso, dos registros
        // simultaneos leen el mismo saldo y uno pisa al otro.
        return variantes.buscarParaModificar(linea.varianteId())
                .orElseThrow(() -> new ReglaDeNegocioException(
                        "El repuesto no existe: " + linea.varianteId()));
    }

    /**
     * El mismo repuesto dos veces en una compra se rechaza. Permitirlo daria dos instancias del
     * mismo registro moviendose por separado, y el segundo movimiento de kardex naceria con un
     * saldo que ya quedo obsoleto. Si de verdad llegaron dos renglones del mismo item, se suman.
     *
     * <p>Se comprueban las dos formas de nombrar un repuesto: por id los que ya existen, y por
     * codigo los que se crean. Sin lo segundo, dos renglones creando el mismo codigo pasarian el
     * filtro y reventarian mas adelante con un mensaje peor.
     */
    static void exigirRepuestosDistintos(Collection<ComandoRegistrarCompra.Linea> lineas) {
        Set<UUID> idsVistos = new HashSet<>();
        Set<String> codigosVistos = new HashSet<>();
        for (ComandoRegistrarCompra.Linea linea : lineas) {
            boolean repetido = linea.creaRepuesto()
                    ? !codigosVistos.add(linea.repuestoNuevo().codigo().trim().toUpperCase())
                    : !idsVistos.add(linea.varianteId());
            if (repetido) {
                throw new ReglaDeNegocioException(
                        "El mismo repuesto aparece dos veces en la compra. Sumalos en un solo renglon.");
            }
        }
    }

    /**
     * Mete al inventario lo de un renglón: stock, costo promedio, precio si lo trae, y su movimiento
     * de kardex. Deja anotado en el renglón con qué movimiento entró y qué precio había antes, que es
     * justo lo que hace falta para poder deshacerlo.
     *
     * <p>La variante del renglón ya tiene que venir bloqueada ({@link #resolverRepuesto}).
     *
     * @param motivo {@code null} al registrar; el de la corrección si es un renglón corregido
     */
    MovimientoKardex darEntrada(LineaCompra linea, UUID compraId, String motivo, UUID usuarioId,
                                Instant ahora) {
        Variante variante = linea.getVariante();

        // La regla del promedio ponderado vive DENTRO de la variante, no aqui. Esto coordina; no
        // calcula. Si manana otro flujo repone stock, la regla viaja con el dato.
        variante.reponerPorCompra(linea.getCantidad(), linea.getCostoUnitario());

        // Precio de venta vacio = no tocar el precio. Reponer stock no debe reescribir en silencio
        // un precio que nadie quiso cambiar. El de antes se guarda para poder devolverlo.
        Dinero precioAntes = variante.getPrecio();
        if (linea.getPrecioVenta() != null) {
            variante.fijarPrecio(linea.getPrecioVenta());
        }

        // El movimiento se construye DESPUES de mover: toma la foto del saldo y del promedio ya
        // actualizados. Al reves, el kardex guardaria el estado anterior y mentiria.
        MovimientoKardex movimiento = MovimientoKardex.porCompra(variante, linea.getCantidad(),
                linea.getCostoUnitario(), linea.getCostoTotal(), compraId, motivo, usuarioId, ahora);
        linea.anotarEntrada(movimiento.getId(), precioAntes);

        variantes.guardar(variante);
        kardex.agregar(movimiento);
        return movimiento;
    }

    // ── Salir ────────────────────────────────────────────────────────────────

    /**
     * Los códigos de los renglones que <b>no</b> se pueden revertir (spec 0002, RF-019).
     *
     * <p>Se revisan todos antes de mover nada, para decir de una vez cuáles están bloqueados en vez
     * de fallar a mitad de camino. Un renglón no se puede revertir si su repuesto tuvo ventas o
     * ajustes de salida después de la compra: con costo promedio no se sabe si esas unidades eran de
     * esta compra, y la cuenta de revertir el promedio deja de ser exacta.
     */
    List<String> bloqueados(Collection<LineaCompra> lineas) {
        List<String> codigos = new ArrayList<>();
        for (LineaCompra linea : lineas) {
            Variante variante = linea.getVariante();
            Optional<MovimientoKardex> entrada = linea.getMovimientoEntradaId() == null
                    ? Optional.empty()
                    : kardex.buscar(linea.getMovimientoEntradaId());
            boolean bloqueado = entrada.isEmpty()
                    || entrada.get().getSecuencia() == null
                    || kardex.huboSalidasDespuesDe(variante.getId(), entrada.get().getSecuencia())
                    || variante.getStock() < linea.getCantidad();
            if (bloqueado && !codigos.contains(variante.getCodigo())) {
                codigos.add(variante.getCodigo());
            }
        }
        return codigos;
    }

    /**
     * Saca del inventario lo que metió un renglón: stock, su costo del promedio, y el precio si lo
     * había cambiado. Escribe la salida en el kardex apuntando a la entrada que deshace.
     *
     * <p><b>Revisar {@link #bloqueados} antes.</b> Aquí se vuelve a exigir lo imposible (sacar más de
     * lo que hay), pero la regla de las salidas posteriores es de quien llama, que la revisa para
     * todos los renglones a la vez.
     *
     * @param tipo {@link TipoMovimiento#CORRECCION_COMPRA} o {@link TipoMovimiento#ANULACION_COMPRA}
     * @return avisos para el administrador, por ejemplo si el precio no pudo volver a su valor
     */
    List<String> revertirEntrada(LineaCompra linea, TipoMovimiento tipo, UUID compraId,
                                 String motivo, UUID usuarioId, Instant ahora) {
        MovimientoKardex entrada = kardex.buscar(linea.getMovimientoEntradaId())
                .orElseThrow(() -> new ReglaDeNegocioException("El renglón de "
                        + linea.getVariante().getCodigo() + " no tiene su movimiento de entrada"));

        Variante variante = variantes.buscarParaModificar(linea.getVariante().getId())
                .orElseThrow(() -> new ReglaDeNegocioException("El repuesto no existe"));

        var promedioAntes = kardex.ultimoAntesDe(variante.getId(), entrada.getSecuencia())
                .map(MovimientoKardex::getCostoPromedioDespues)
                .orElse(null);
        variante.revertirEntradaDeCompra(linea.getCantidad(), linea.getCostoUnitario(), promedioAntes);

        List<String> avisos = new ArrayList<>(devolverPrecio(linea, variante, entrada.getSecuencia()));

        kardex.agregar(MovimientoKardex.porReversionDeCompra(variante, tipo, linea.getCantidad(),
                linea.getCostoUnitario(), linea.getCostoTotal(), compraId, entrada.getId(), motivo,
                usuarioId, ahora));
        variantes.guardar(variante);
        return avisos;
    }

    /**
     * Un renglón que solo cambia de precio: cantidad y costo quedan, así que <b>no hay kardex</b> ni
     * se bloquea por ventas (decisión 2 del plan). Si el nuevo renglón ya no fija precio, se intenta
     * devolver el que había antes.
     */
    List<String> cambiarPrecio(LineaCompra vieja, LineaCompra nueva, Variante variante) {
        if (nueva.getPrecioVenta() != null) {
            variante.fijarPrecio(nueva.getPrecioVenta());
            variantes.guardar(variante);
            return List.of();
        }
        Long secuencia = kardex.buscar(vieja.getMovimientoEntradaId())
                .map(MovimientoKardex::getSecuencia)
                .orElse(null);
        List<String> avisos = devolverPrecio(vieja, variante, secuencia);
        variantes.guardar(variante);
        return avisos;
    }

    /**
     * RF-017: el precio vuelve al que tenía antes del renglón solo si se cumplen las tres cosas —
     * el renglón lo había cambiado y se sabe cuál era, el precio de hoy sigue siendo el que puso ese
     * renglón, y ninguna compra posterior lo fijó. Si no, se deja el vigente y se avisa.
     */
    private List<String> devolverPrecio(LineaCompra linea, Variante variante, Long secuenciaEntrada) {
        if (linea.getPrecioVenta() == null) {
            return List.of();
        }
        String codigo = variante.getCodigo();
        if (linea.getPrecioAnterior() == null) {
            return List.of("El precio de " + codigo + " se deja en " + variante.getPrecio()
                    + ": esa compra es de antes de que se guardara el precio anterior.");
        }
        if (!variante.getPrecio().equals(linea.getPrecioVenta()) || otraCompraLoFijoDespues(linea, secuenciaEntrada)) {
            return List.of("El precio de " + codigo + " se deja en " + variante.getPrecio()
                    + ": cambió después de esa compra.");
        }
        variante.fijarPrecio(linea.getPrecioAnterior());
        return List.of();
    }

    private boolean otraCompraLoFijoDespues(LineaCompra linea, Long secuenciaEntrada) {
        if (secuenciaEntrada == null) {
            return true;   // sin orden conocido no se puede afirmar que nadie lo cambió después
        }
        return compras.lineasQueFijaronPrecio(linea.getVariante().getId()).stream()
                .filter(otra -> !otra.getId().equals(linea.getId()))
                .filter(otra -> otra.getMovimientoEntradaId() != null
                        && !otra.getMovimientoEntradaId().equals(linea.getMovimientoEntradaId()))
                .map(otra -> kardex.buscar(otra.getMovimientoEntradaId())
                        .map(MovimientoKardex::getSecuencia).orElse(null))
                .anyMatch(secuencia -> secuencia != null && secuencia > secuenciaEntrada);
    }
}

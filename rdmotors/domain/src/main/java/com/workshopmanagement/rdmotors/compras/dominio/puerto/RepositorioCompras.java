package com.workshopmanagement.rdmotors.compras.dominio.puerto;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.workshopmanagement.rdmotors.compartido.dominio.Pagina;
import com.workshopmanagement.rdmotors.compras.dominio.Compra;
import com.workshopmanagement.rdmotors.compras.dominio.FiltroCompras;
import com.workshopmanagement.rdmotors.compras.dominio.LineaCompra;
import com.workshopmanagement.rdmotors.compras.dominio.ResumenCompra;
import com.workshopmanagement.rdmotors.compras.dominio.TotalesCompras;

/** PUERTO — persistencia de compras. Habla el idioma del negocio, no el de SQL. */
public interface RepositorioCompras {

    Compra guardar(Compra compra);

    Optional<Compra> buscar(UUID id);

    /**
     * La compra que se registró con esa llave, si ya existe (spec 0009, RF-009). Es lo que hace que reintentar
     * una compra sea seguro: la misma llave devuelve la misma compra en vez de entrar la mercancía dos veces.
     */
    Optional<Compra> buscarPorLlave(UUID llave);

    /**
     * La compra con intención de corregirla o anularla. El adaptador toma bloqueo de fila: dos
     * correcciones a la vez se ponen en fila, y la segunda ve la versión que dejó la primera.
     */
    Optional<Compra> buscarParaModificar(UUID id);

    /**
     * Los renglones vigentes, de compras vigentes, que le fijaron precio a este repuesto. Sirve para
     * saber si, al deshacer un renglón, otra compra posterior ya cambió el precio (spec 0002, RF-017).
     */
    List<LineaCompra> lineasQueFijaronPrecio(UUID varianteId);

    /**
     * Los renglones <b>vigentes</b> de esas compras, con su repuesto y concepto traídos en la misma
     * consulta, en el orden de cada factura. Es lo que la lista del historial necesita para decir qué
     * renglones coinciden con lo buscado (spec 0002, RF-027) sin una consulta por factura.
     */
    List<LineaCompra> lineasVigentesDe(Collection<UUID> compraIds);

    /**
     * La compra con todo lo que su detalle muestra —proveedor, cuenta, renglones y el repuesto de
     * cada renglón— traído en la misma consulta. Sin eso, una factura de 30 renglones son 30
     * consultas más.
     */
    Optional<Compra> buscarConLineas(UUID id);

    /**
     * Las pagadas con plata del cajón en ese turno, anuladas incluidas, con su proveedor, en el orden en que
     * se registraron (spec 0006, RF-011).
     */
    List<Compra> deCajaEnTurno(UUID turnoId);

    /**
     * El historial, de la factura más reciente a la más antigua.
     *
     * <p>Todo filtro {@code null} de {@link FiltroCompras} significa "sin filtro", y la integración
     * prueba cada uno contra Postgres, incluido el caso sin ninguno.
     *
     * @param pagina empieza en 0
     */
    Pagina<ResumenCompra> historial(FiltroCompras filtro, int pagina, int tamano);

    /**
     * Lo pagado con esos filtros, por forma de pago y cuenta (spec 0002, RF-010). El total y las
     * partes se calculan en la base con consultas separadas: ver {@link TotalesCompras}.
     */
    TotalesCompras totales(FiltroCompras filtro);
}

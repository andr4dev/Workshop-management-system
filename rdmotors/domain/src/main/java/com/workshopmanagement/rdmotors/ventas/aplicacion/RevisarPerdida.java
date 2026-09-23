package com.workshopmanagement.rdmotors.ventas.aplicacion;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.transaction.annotation.Transactional;

import com.workshopmanagement.rdmotors.compartido.dominio.Dinero;
import com.workshopmanagement.rdmotors.compartido.dominio.ReglaDeNegocioException;
import com.workshopmanagement.rdmotors.inventario.dominio.Variante;
import com.workshopmanagement.rdmotors.inventario.dominio.puerto.RepositorioVariantes;
import com.workshopmanagement.rdmotors.ventas.dominio.AvisoDePerdida;

/**
 * CASO DE USO — ¿la venta que se está armando queda a pérdida? (spec 0004, RF-011). Lo pregunta la pantalla de
 * Vender cada vez que cambian los renglones o el descuento, antes de cobrar.
 *
 * <p>Con el precio y el costo que tiene cada repuesto <b>ahora</b>: es lo que se cobraría. Un renglón que no se
 * podría cobrar (el repuesto no existe, cantidad en cero) no entra; eso lo dice el cobro, no este aviso. Un
 * descuento que todavía no es válido (sin motivo, más grande que el total) tampoco revienta: no hay aviso.
 */
@Transactional(readOnly = true)
public class RevisarPerdida {

    private final RepositorioVariantes variantes;

    public RevisarPerdida(RepositorioVariantes variantes) {
        this.variantes = variantes;
    }

    public Optional<AvisoDePerdida> ejecutar(List<ComandoCobrarVenta.Renglon> renglones,
                                             ComandoCobrarVenta.ComandoDescuento descuento) {
        List<AvisoDePerdida.Renglon> conPrecio = new ArrayList<>();
        Dinero subtotal = Dinero.CERO;
        for (ComandoCobrarVenta.Renglon r : renglones == null ? List.<ComandoCobrarVenta.Renglon>of() : renglones) {
            Variante v = r.varianteId() == null || r.cantidad() <= 0 ? null
                    : variantes.buscar(r.varianteId()).orElse(null);
            if (v != null) {
                conPrecio.add(new AvisoDePerdida.Renglon(v.getPrecio(), r.cantidad(), v.getCostoPromedio()));
                subtotal = subtotal.mas(v.getPrecio().por(r.cantidad()));
            }
        }
        Dinero montoDescuento;
        try {
            montoDescuento = descuento == null ? Dinero.CERO : descuento.montoSobre(subtotal);
        } catch (ReglaDeNegocioException e) {
            return Optional.empty();
        }
        if (montoDescuento.valor().compareTo(subtotal.valor()) > 0) {
            return Optional.empty();
        }
        return AvisoDePerdida.calcular(conPrecio, montoDescuento);
    }
}

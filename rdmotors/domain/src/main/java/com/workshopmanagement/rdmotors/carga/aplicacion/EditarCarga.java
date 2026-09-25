package com.workshopmanagement.rdmotors.carga.aplicacion;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import org.springframework.transaction.annotation.Transactional;

import com.workshopmanagement.rdmotors.carga.dominio.CargaDeInventario;
import com.workshopmanagement.rdmotors.carga.dominio.ReglaDePrecio;
import com.workshopmanagement.rdmotors.carga.dominio.puerto.RepositorioCargas;
import com.workshopmanagement.rdmotors.compartido.dominio.Actor;
import com.workshopmanagement.rdmotors.compartido.dominio.Dinero;
import com.workshopmanagement.rdmotors.compartido.dominio.FormaPago;
import com.workshopmanagement.rdmotors.compartido.dominio.ReglaDeNegocioException;
import com.workshopmanagement.rdmotors.compartido.dominio.puerto.Reloj;
import com.workshopmanagement.rdmotors.compras.dominio.CuentaPago;
import com.workshopmanagement.rdmotors.compras.dominio.Proveedor;
import com.workshopmanagement.rdmotors.compras.dominio.puerto.RepositorioCuentas;
import com.workshopmanagement.rdmotors.compras.dominio.puerto.RepositorioProveedores;
import com.workshopmanagement.rdmotors.inventario.dominio.Categoria;
import com.workshopmanagement.rdmotors.inventario.dominio.puerto.RepositorioCategorias;
import com.workshopmanagement.rdmotors.inventario.dominio.puerto.RepositorioVariantes;

/**
 * CASO DE USO — cada gesto sobre la pre-carga (spec 0012, RF-005 a RF-014): un precio, los porcentajes, una marca,
 * quitar un renglón. <b>Cada uno se guarda en el momento</b>, y devuelve la carga revisada de nuevo: arreglar un
 * renglón puede quitarle el problema a otro (el código repetido) o habilitar la confirmación.
 *
 * <p>La carga se bloquea mientras se edita: el socio en el celular y el dueño en el computador guardan uno detrás del
 * otro, sin pisarse.
 */
@Transactional
public class EditarCarga {

    private final RepositorioCargas cargas;
    private final RepositorioProveedores proveedores;
    private final RepositorioCuentas cuentas;
    private final RepositorioCategorias categorias;
    private final ArmadoDelDetalle detalle;
    private final Reloj reloj;

    public EditarCarga(RepositorioCargas cargas, RepositorioVariantes variantes, RepositorioCategorias categorias,
                       RepositorioProveedores proveedores, RepositorioCuentas cuentas, Reloj reloj) {
        this.cargas = cargas;
        this.proveedores = proveedores;
        this.cuentas = cuentas;
        this.categorias = categorias;
        this.detalle = new ArmadoDelDetalle(variantes, categorias, proveedores, cuentas);
        this.reloj = reloj;
    }

    /** Proveedor, número, fecha, forma de pago y cuenta: los de una compra a mano (RF-015). */
    public DetalleCarga cambiarDatos(UUID id, UUID proveedorId, String numeroFactura, LocalDate fecha,
                                     FormaPago formaPago, UUID cuentaId, Actor actor) {
        return editar(id, actor, carga -> {
            if (proveedorId != null) {
                Proveedor proveedor = proveedores.buscar(proveedorId)
                        .orElseThrow(() -> new ReglaDeNegocioException("El proveedor no existe"));
                if (!proveedor.isActivo() && !proveedorId.equals(carga.getProveedorId())) {
                    throw new ReglaDeNegocioException("El proveedor " + proveedor.getNombre()
                            + " está desactivado: elige otro");
                }
            }
            if (cuentaId != null) {
                CuentaPago cuenta = cuentas.buscar(cuentaId)
                        .orElseThrow(() -> new ReglaDeNegocioException("La cuenta no existe"));
                if (!cuenta.isActiva()) {
                    throw new ReglaDeNegocioException("La cuenta " + cuenta.getNombre() + " está desactivada: "
                            + "elige otra");
                }
            }
            carga.cambiarDatos(proveedorId, numeroFactura, fecha, formaPago, cuentaId);
        });
    }

    public DetalleCarga fijarSubtotal(UUID id, Dinero subtotal, Actor actor) {
        return editar(id, actor, carga -> carga.fijarSubtotal(subtotal));
    }

    public DetalleCarga cambiarRegla(UUID id, BigDecimal ivaPct, BigDecimal gananciaPct, int redondeo, Actor actor) {
        return editar(id, actor, carga -> carga.cambiarRegla(new ReglaDePrecio(ivaPct, gananciaPct, redondeo)));
    }

    public DetalleCarga ajustarPrecio(UUID id, int posicion, Dinero precio, Actor actor) {
        return editar(id, actor, carga -> carga.ajustarPrecio(posicion, precio));
    }

    public DetalleCarga volverAlSugerido(UUID id, int posicion, Actor actor) {
        return editar(id, actor, carga -> carga.volverAlSugerido(posicion));
    }

    /** A los renglones elegidos, o —con {@code posiciones} vacío y {@code losQueNoTienen}— a todos los que no tienen. */
    public DetalleCarga cambiarMarca(UUID id, List<Integer> posiciones, boolean losQueNoTienen, String marca,
                                     Actor actor) {
        return editar(id, actor, carga -> {
            if (losQueNoTienen) {
                carga.cambiarMarcaDeLosQueNoTienen(marca);
            } else {
                carga.cambiarMarca(posiciones, marca);
            }
        });
    }

    public DetalleCarga cambiarCategoria(UUID id, List<Integer> posiciones, boolean losQueNoTienen, UUID categoriaId,
                                         Actor actor) {
        return editar(id, actor, carga -> {
            Categoria categoria = categoriaId == null ? null : categorias.buscar(categoriaId)
                    .orElseThrow(() -> new ReglaDeNegocioException("La categoría no existe"));
            if (categoria != null && !categoria.isActiva()) {
                throw new ReglaDeNegocioException("La categoría " + categoria.getNombre() + " está desactivada");
            }
            if (losQueNoTienen) {
                carga.cambiarCategoriaDeLosQueNoTienen(categoriaId);
            } else {
                carga.cambiarCategoria(posiciones, categoriaId);
            }
        });
    }

    /** Lo que se leyó mal, escrito mirando el papel (§6: "se corrige a mano mirando el papel"). */
    public DetalleCarga corregirLectura(UUID id, int posicion, String codigo, String descripcion, Integer cantidad,
                                        Dinero valorTotal, Dinero precioUnitario, BigDecimal descuentoPct,
                                        Actor actor) {
        return editar(id, actor, carga -> carga.corregirLectura(posicion, codigo, descripcion, cantidad, valorTotal,
                precioUnitario, descuentoPct));
    }

    public DetalleCarga quitar(UUID id, int posicion, boolean quitado, Actor actor) {
        return editar(id, actor, carga -> {
            if (quitado) {
                carga.quitar(posicion);
            } else {
                carga.restaurar(posicion);
            }
        });
    }

    public DetalleCarga aplicarPrecioNuevo(UUID id, int posicion, boolean aplicar, Actor actor) {
        return editar(id, actor, carga -> carga.aplicarPrecioNuevo(posicion, aplicar));
    }

    private DetalleCarga editar(UUID id, Actor actor, Consumer<CargaDeInventario> gesto) {
        actor.exigirAdministrador();
        CargaDeInventario carga = cargas.buscarParaModificar(id)
                .orElseThrow(() -> new ReglaDeNegocioException("Esa carga no existe"));
        gesto.accept(carga);
        carga.registrarCambio(reloj.ahora());
        return detalle.armar(cargas.guardar(carga));
    }
}

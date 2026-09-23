package com.workshopmanagement.rdmotors.inventario.aplicacion;

import org.springframework.transaction.annotation.Transactional;

import com.workshopmanagement.rdmotors.compartido.dominio.Actor;
import com.workshopmanagement.rdmotors.compartido.dominio.ReglaDeNegocioException;
import com.workshopmanagement.rdmotors.inventario.dominio.Categoria;
import com.workshopmanagement.rdmotors.inventario.dominio.CodigoDuplicadoException;
import com.workshopmanagement.rdmotors.inventario.dominio.Producto;
import com.workshopmanagement.rdmotors.inventario.dominio.Variante;
import com.workshopmanagement.rdmotors.inventario.dominio.puerto.RepositorioCategorias;
import com.workshopmanagement.rdmotors.inventario.dominio.puerto.RepositorioProductos;
import com.workshopmanagement.rdmotors.inventario.dominio.puerto.RepositorioVariantes;

/**
 * CASO DE USO — dar de alta un repuesto vendible.
 *
 * <p>Es la puerta de entrada al catálogo. Hasta que existiera, el sistema no se podía encender: el
 * registro de compras exigía repuestos que nadie podía crear.
 *
 * <p><b>Aquí vive la decisión §4 del spec 0001:</b> un repuesto nuevo o cuelga de un concepto que
 * ya existe, o crea el suyo. Nunca se duplica el concepto en silencio.
 *
 * <p>El repuesto nace <b>sin stock y sin costo</b>. Los dos los pone la primera compra — que es lo
 * coherente con que el cliente fije precios a mano, uno a la vez, cuando la mercancía llega.
 */
@Transactional
public class CrearRepuesto {

    private final RepositorioVariantes variantes;
    private final RepositorioProductos productos;
    private final RepositorioCategorias categorias;

    public CrearRepuesto(RepositorioVariantes variantes, RepositorioProductos productos,
                         RepositorioCategorias categorias) {
        this.variantes = variantes;
        this.productos = productos;
        this.categorias = categorias;
    }

    public Variante ejecutar(ComandoCrearRepuesto comando, Actor actor) {
        actor.exigirAdministrador();
        String codigo = comando.codigo().trim().toUpperCase();

        // El código es único en todo el sistema. Se avisa CUÁL repuesto lo tiene: sin eso el
        // administrador no sabe si se equivocó tecleando o si ya lo había creado antes.
        variantes.buscarPorCodigo(codigo).ifPresent(existente -> {
            throw new CodigoDuplicadoException(codigo, existente.getProducto().getNombre());
        });

        Producto concepto = comando.reutilizaConcepto()
                ? conceptoExistente(comando)
                : conceptoNuevo(comando);

        Variante variante = Variante.nueva(concepto, codigo, comando.marcaRepuesto(),
                comando.precio(), comando.stockMinimo());

        return variantes.guardar(variante);
    }

    private Producto conceptoExistente(ComandoCrearRepuesto comando) {
        return productos.buscar(comando.productoId())
                .orElseThrow(() -> new ReglaDeNegocioException(
                        "El repuesto al que lo quieres asociar no existe"));
    }

    private Producto conceptoNuevo(ComandoCrearRepuesto comando) {
        Categoria categoria = comando.categoriaId() == null ? null
                : categorias.buscar(comando.categoriaId())
                        .orElseThrow(() -> new ReglaDeNegocioException("La categoría no existe"));

        // La aplicación vehicular se guarda TAL CUAL la trae el proveedor, sin interpretarla.
        // Es la fuente para normalizar los modelos después: en el catálogo de Jotapartes se ve
        // como "PULSAR NS 200/FI/AS 200-DUKE 200".
        Producto concepto = Producto.nuevo(comando.nombreProducto(), categoria,
                comando.aplicacionOriginal());
        return productos.guardar(concepto);
    }
}

package com.workshopmanagement.rdmotors.inventario.infraestructura;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.workshopmanagement.rdmotors.inventario.dominio.Categoria;
import com.workshopmanagement.rdmotors.inventario.dominio.Producto;
import com.workshopmanagement.rdmotors.inventario.dominio.puerto.RepositorioCategorias;
import com.workshopmanagement.rdmotors.inventario.dominio.puerto.RepositorioProductos;

import lombok.RequiredArgsConstructor;

/**
 * ADAPTADOR DE ENTRADA — categorias y conceptos de repuesto.
 *
 * <p>Solo lecturas, y ninguna tiene regla que proteger: van por el puerto directo.
 *
 * <p><b>{@code GET /api/productos?q=} es la pieza clave de la decision §4 del spec 0001.</b> Es lo
 * que le muestra al administrador los conceptos que YA existen antes de que cree uno nuevo. Si
 * esta busqueda no encuentra lo que hay, el catalogo se duplica igual — y con el, la compatibilidad
 * vehicular escrita dos veces.
 */
@RestController
@RequiredArgsConstructor
class CatalogoController {

    private final RepositorioCategorias categorias;
    private final RepositorioProductos productos;

    @GetMapping("/api/categorias")
    List<RespuestaCategoria> categoriasActivas() {
        return categorias.activas().stream().map(RespuestaCategoria::de).toList();
    }

    @GetMapping("/api/productos")
    List<RespuestaProducto> buscarConceptos(@RequestParam("q") String texto) {
        if (texto == null || texto.isBlank()) {
            return List.of();
        }
        return productos.buscarPorNombre(texto.trim()).stream().map(RespuestaProducto::de).toList();
    }

    record RespuestaCategoria(UUID id, String nombre, int orden) {
        static RespuestaCategoria de(Categoria c) {
            return new RespuestaCategoria(c.getId(), c.getNombre(), c.getOrden());
        }
    }

    record RespuestaProducto(UUID id, String nombre, String categoria, String aplicacion) {
        static RespuestaProducto de(Producto p) {
            return new RespuestaProducto(p.getId(), p.getNombre(),
                    p.getCategoria() == null ? null : p.getCategoria().getNombre(),
                    p.getAplicacionOriginal());
        }
    }
}

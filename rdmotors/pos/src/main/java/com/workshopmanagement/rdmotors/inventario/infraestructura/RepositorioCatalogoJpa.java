package com.workshopmanagement.rdmotors.inventario.infraestructura;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.workshopmanagement.rdmotors.compartido.dominio.TextoDeBusqueda;
import com.workshopmanagement.rdmotors.inventario.dominio.Categoria;
import com.workshopmanagement.rdmotors.inventario.dominio.Producto;
import com.workshopmanagement.rdmotors.inventario.dominio.puerto.RepositorioCategorias;
import com.workshopmanagement.rdmotors.inventario.dominio.puerto.RepositorioProductos;

import lombok.RequiredArgsConstructor;

/** ADAPTADOR — el CONCEPTO de repuesto sobre Postgres. */
@Repository
@RequiredArgsConstructor
class RepositorioProductosJpa implements RepositorioProductos {

    private final ProductosSpringData jpa;

    @Override
    public Optional<Producto> buscar(UUID id) {
        return jpa.findById(id);
    }

    @Override
    public List<Producto> buscarPorNombre(String texto) {
        return jpa.buscarPorNombre(TextoDeBusqueda.normalizar(texto));
    }

    @Override
    public Producto guardar(Producto producto) {
        return jpa.save(producto);
    }
}

/** ADAPTADOR — las categorias sobre Postgres. */
@Repository
@RequiredArgsConstructor
class RepositorioCategoriasJpa implements RepositorioCategorias {

    private final CategoriasSpringData jpa;

    @Override
    public Optional<Categoria> buscar(UUID id) {
        return jpa.findById(id);
    }

    @Override
    public List<Categoria> activas() {
        return jpa.findByActivaTrueOrderByOrdenAsc();
    }

    @Override
    public Categoria guardar(Categoria categoria) {
        return jpa.save(categoria);
    }
}

interface ProductosSpringData extends JpaRepository<Producto, UUID> {

    /**
     * Coincidencia parcial sin distinguir mayusculas ni tildes. Es lo que le ofrece al administrador
     * los conceptos que ya existen antes de que cree uno nuevo — la decision §4 del spec 0001 depende
     * de que esto funcione bien: si no encuentra lo que ya hay, el catalogo se duplica igual. Por eso
     * "BUJIA" tiene que ofrecer "BUJÍA". {@code texto} llega normalizado ({@code TextoDeBusqueda}).
     */
    @Query("""
            select p from Producto p
            where p.activo
              and sin_tildes(p.nombre) like concat('%', :texto, '%')
            order by p.nombre
            """)
    List<Producto> buscarPorNombre(@Param("texto") String texto);
}

interface CategoriasSpringData extends JpaRepository<Categoria, UUID> {
    List<Categoria> findByActivaTrueOrderByOrdenAsc();
}

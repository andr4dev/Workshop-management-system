package com.workshopmanagement.rdmotors.carga.aplicacion;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import com.workshopmanagement.rdmotors.carga.dominio.CargaDeInventario;
import com.workshopmanagement.rdmotors.carga.dominio.CategoriasConocidas;
import com.workshopmanagement.rdmotors.compras.dominio.CuentaPago;
import com.workshopmanagement.rdmotors.compras.dominio.Proveedor;
import com.workshopmanagement.rdmotors.compras.dominio.puerto.RepositorioCuentas;
import com.workshopmanagement.rdmotors.compras.dominio.puerto.RepositorioProveedores;
import com.workshopmanagement.rdmotors.inventario.dominio.Categoria;
import com.workshopmanagement.rdmotors.inventario.dominio.Variante;
import com.workshopmanagement.rdmotors.inventario.dominio.puerto.RepositorioCategorias;
import com.workshopmanagement.rdmotors.inventario.dominio.puerto.RepositorioVariantes;

/**
 * Lo que comparten consultar y editar: revisar la carga contra el inventario de ahora. Los códigos que ya existen se
 * buscan <b>en una sola consulta</b>; de a uno serían 600 viajes a la base cada vez que se guarda un precio.
 */
final class ArmadoDelDetalle {

    private final RepositorioVariantes variantes;
    private final RepositorioCategorias categorias;
    private final RepositorioProveedores proveedores;
    private final RepositorioCuentas cuentas;

    ArmadoDelDetalle(RepositorioVariantes variantes, RepositorioCategorias categorias,
                     RepositorioProveedores proveedores, RepositorioCuentas cuentas) {
        this.variantes = variantes;
        this.categorias = categorias;
        this.proveedores = proveedores;
        this.cuentas = cuentas;
    }

    DetalleCarga armar(CargaDeInventario carga) {
        Map<String, Variante> existentes = new LinkedHashMap<>();
        for (Variante v : variantes.buscarPorCodigos(carga.codigos())) {
            existentes.put(v.getCodigo(), v);
        }
        CategoriasConocidas conocidas = categoriasConocidas();
        return new DetalleCarga(carga, carga.revisar(existentes, conocidas), nombreDelProveedor(carga.getProveedorId()),
                nombreDeLaCuenta(carga.getCuentaId()), conocidas);
    }

    CategoriasConocidas categoriasConocidas() {
        Map<UUID, String> activas = new LinkedHashMap<>();
        for (Categoria c : categorias.activas()) {
            activas.put(c.getId(), c.getNombre());
        }
        return new CategoriasConocidas(activas);
    }

    private String nombreDelProveedor(UUID id) {
        return id == null ? null : proveedores.buscar(id).map(Proveedor::getNombre).orElse(null);
    }

    private String nombreDeLaCuenta(UUID id) {
        return id == null ? null : cuentas.buscar(id).map(CuentaPago::getNombre).orElse(null);
    }
}

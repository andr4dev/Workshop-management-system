package com.workshopmanagement.rdmotors.carga.dominio.puerto;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.workshopmanagement.rdmotors.carga.dominio.CargaDeInventario;
import com.workshopmanagement.rdmotors.carga.dominio.ResumenCarga;

/** PUERTO — dónde esperan las cargas mientras se revisan (spec 0012, decisión 4). */
public interface RepositorioCargas {

    /** Con sus renglones: una carga no se mira sin ellos. */
    Optional<CargaDeInventario> buscar(UUID id);

    /**
     * La carga con intención de editarla o confirmarla. El adaptador toma bloqueo de fila: el socio en el celular y
     * el dueño en el computador guardan uno detrás del otro, y confirmar dos veces a la vez se pone en fila.
     */
    Optional<CargaDeInventario> buscarParaModificar(UUID id);

    /**
     * La carga de esa misma factura que no se descartó, si hay: subir dos veces la MAG477 y confirmar las dos
     * entraría la mercancía dos veces.
     */
    Optional<CargaDeInventario> deLaFactura(String nitProveedor, String numeroFactura);

    /** Las que están en borrador, y las últimas cerradas: la lista de la pantalla de cargas. */
    List<ResumenCarga> recientes(int cerradasQueSeMuestran);

    CargaDeInventario guardar(CargaDeInventario carga);
}

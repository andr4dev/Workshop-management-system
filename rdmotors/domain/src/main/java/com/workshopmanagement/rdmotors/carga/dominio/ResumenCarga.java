package com.workshopmanagement.rdmotors.carga.dominio;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Una carga en la lista, sin sus renglones. */
public record ResumenCarga(UUID id, EstadoCarga estado, OrigenCarga origen, String nombreArchivo,
                           String numeroFactura, LocalDate fechaFactura, UUID proveedorId, long renglones,
                           Instant creadaEn, Instant modificadaEn, Instant cerradaEn, UUID compraId) {
}

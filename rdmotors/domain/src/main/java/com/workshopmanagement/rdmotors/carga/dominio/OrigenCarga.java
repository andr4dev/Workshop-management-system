package com.workshopmanagement.rdmotors.carga.dominio;

/** De qué clase de archivo salió una carga (spec 0012, decisión 3). */
public enum OrigenCarga {

    /** La factura de Importadora Jotapartes, tal como llega por correo. Sus renglones se comprueban solos. */
    PDF_JOTAPARTES,

    /** La plantilla, para otros proveedores o mercancía sin factura. */
    EXCEL,

    /** Lo mismo que el Excel, guardado como texto separado. */
    CSV
}

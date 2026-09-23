package com.workshopmanagement.rdmotors.compras.aplicacion;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.workshopmanagement.rdmotors.compartido.dominio.Actor;
import com.workshopmanagement.rdmotors.compartido.dominio.Motivo;
import com.workshopmanagement.rdmotors.compartido.dominio.ReglaDeNegocioException;
import com.workshopmanagement.rdmotors.compras.dominio.Compra;
import com.workshopmanagement.rdmotors.compartido.dominio.FormaPago;

/**
 * Cómo debe quedar una compra ya registrada (spec 0002, H3 y H4).
 *
 * <p>Viaja la compra <b>completa</b> como debe quedar, no los cambios sueltos: la pantalla manda lo
 * que muestra y el caso de uso compara contra lo guardado. Así no hay forma de mandar "cambia la
 * cantidad" sobre un renglón que otro ya había modificado —para eso está {@code versionEsperada}.
 *
 * @param versionEsperada la versión que la pantalla cargó
 * @param motivo          obligatorio: queda en la auditoría
 * @param lineas          {@code null} = los renglones no se tocan; si viene, es la lista completa de
 *                        cómo deben quedar
 * @param pagadaDeCaja    si se pagó con plata del cajón (spec 0006); {@code null} = como estaba
 */
public record ComandoCorregirCompra(
        UUID compraId,
        long versionEsperada,
        String motivo,
        Actor actor,
        UUID proveedorId,
        LocalDate fechaDocumento,
        String numeroFactura,
        FormaPago formaPago,
        UUID cuentaId,
        List<Linea> lineas,
        Boolean pagadaDeCaja) {

    public ComandoCorregirCompra {
        if (compraId == null) throw new ReglaDeNegocioException("Falta la compra a corregir");
        if (actor == null) throw new ReglaDeNegocioException("Falta el usuario que corrige");
        motivo = Motivo.exigir(motivo);
        lineas = lineas == null ? null : List.copyOf(lineas);
    }

    /** Sin tocar si se pagó con plata del cajón. */
    public ComandoCorregirCompra(UUID compraId, long versionEsperada, String motivo, Actor actor,
                                 UUID proveedorId, LocalDate fechaDocumento, String numeroFactura,
                                 FormaPago formaPago, UUID cuentaId, List<Linea> lineas) {
        this(compraId, versionEsperada, motivo, actor, proveedorId, fechaDocumento, numeroFactura, formaPago,
                cuentaId, lineas, null);
    }

    /**
     * Un renglón de la compra corregida.
     *
     * @param lineaId el renglón vigente que representa, o {@code null} si es uno que no estaba
     * @param datos   cómo debe quedar: mismo formato y mismas reglas que al registrar
     */
    public record Linea(UUID lineaId, ComandoRegistrarCompra.Linea datos) {

        public Linea {
            if (datos == null) throw new ReglaDeNegocioException("El renglón no trae sus datos");
        }
    }
}

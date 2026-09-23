package com.workshopmanagement.rdmotors.clientes.infraestructura;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.workshopmanagement.rdmotors.clientes.aplicacion.ConsultarCartera;
import com.workshopmanagement.rdmotors.clientes.dominio.FiltroCartera;
import com.workshopmanagement.rdmotors.clientes.dominio.ResumenDeCliente;
import com.workshopmanagement.rdmotors.compartido.dominio.Dinero;

import lombok.RequiredArgsConstructor;

/**
 * ADAPTADOR DE ENTRADA — la lista de la Cartera (spec 0008, H3 y H5). Solo lee; la ven los dos roles.
 *
 * <p>{@code vista=DEBEN} (sin decirlo): los que deben hoy, del que más al que menos. {@code vista=HISTORIAL}: todos los
 * que alguna vez tuvieron fiado, del último movimiento al más viejo.
 */
@RestController
@RequestMapping("/api/cartera")
@RequiredArgsConstructor
class CarteraController {

    private final ConsultarCartera consultarCartera;

    @GetMapping
    RespuestaCartera lista(@RequestParam(defaultValue = "DEBEN") FiltroCartera.Vista vista,
                           @RequestParam(defaultValue = "") String q,
                           @RequestParam(defaultValue = "VENTA") FiltroCartera.ModoFecha modoFecha,
                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        ConsultarCartera.Cartera cartera = consultarCartera.lista(new FiltroCartera(vista, q, modoFecha, desde, hasta));
        return new RespuestaCartera(cartera.clientes().stream().map(RespuestaFila::de).toList(), cartera.deben(),
                pesos(cartera.porCobrar()));
    }

    /** @param porCobrar lo que deben entre todos los de la lista */
    record RespuestaCartera(List<RespuestaFila> clientes, int deben, long porCobrar) {
    }

    /** @param desde el día de la deuda pendiente más vieja; {@code null} si está al día */
    record RespuestaFila(UUID id, String nombre, String documento, String celular, boolean fiadoCerrado, long debe,
                         long aFavor, int pendientes, LocalDate desde, long fiadoTotal, long pagadoTotal,
                         Instant ultimoMovimiento) {

        static RespuestaFila de(ResumenDeCliente r) {
            return new RespuestaFila(r.clienteId(), r.nombre(), r.documento(), r.celular(), r.fiadoCerrado(),
                    pesos(r.debe()), pesos(r.aFavor()), r.pendientes(), r.desde(), pesos(r.fiadoTotal()),
                    pesos(r.pagadoTotal()), r.ultimoMovimiento());
        }
    }

    private static long pesos(Dinero dinero) {
        return dinero.valor().longValueExact();
    }
}

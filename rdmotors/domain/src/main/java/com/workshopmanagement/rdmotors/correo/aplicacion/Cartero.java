package com.workshopmanagement.rdmotors.correo.aplicacion;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;

import com.workshopmanagement.rdmotors.caja.aplicacion.DetalleTurno;
import com.workshopmanagement.rdmotors.compartido.dominio.puerto.Reloj;
import com.workshopmanagement.rdmotors.correo.dominio.Correo;
import com.workshopmanagement.rdmotors.correo.dominio.CorreoArmado;
import com.workshopmanagement.rdmotors.correo.dominio.EnvioFallidoException;
import com.workshopmanagement.rdmotors.correo.dominio.PoliticaDeReintentos;
import com.workshopmanagement.rdmotors.correo.dominio.TipoCorreo;
import com.workshopmanagement.rdmotors.correo.dominio.puerto.EnviadorDeCorreos;

/**
 * Arma un correo, lo manda y anota cómo le fue (spec 0010). Lo usan la tarea de cada minuto y el botón de prueba.
 *
 * <p><b>Nunca lanza.</b> Todo lo que falle queda escrito en el correo —con si se arregla solo o no—, porque una
 * excepción aquí mataría la tarea y los demás correos se quedarían esperando.
 *
 * <p>Recibe el detalle del turno y el nombre de la tienda como funciones, no como casos de uso: así el correo no
 * depende de cómo la caja arma su detalle, solo de que se lo den.
 */
public class Cartero {

    /** Sin llave de Brevo no se insiste cada minuto: se vuelve a mirar cada tanto, por si ya la pusieron. */
    static final Duration MIRAR_OTRA_VEZ_SI_FALTA_CONFIGURAR = Duration.ofMinutes(10);

    private final EnviadorDeCorreos enviador;
    private final Function<UUID, Optional<DetalleTurno>> detalleDelTurno;
    private final Supplier<String> nombreDeLaTienda;
    private final PoliticaDeReintentos politica;
    private final Reloj reloj;

    public Cartero(EnviadorDeCorreos enviador, Function<UUID, Optional<DetalleTurno>> detalleDelTurno,
                   Supplier<String> nombreDeLaTienda, PoliticaDeReintentos politica, Reloj reloj) {
        this.enviador = enviador;
        this.detalleDelTurno = detalleDelTurno;
        this.nombreDeLaTienda = nombreDeLaTienda;
        this.politica = politica;
        this.reloj = reloj;
    }

    public void intentar(Correo correo) {
        Instant ahora = reloj.ahora();
        if (!enviador.estaConfigurado()) {
            correo.esperaConfiguracion(enviador.loQueFalta(), ahora, MIRAR_OTRA_VEZ_SI_FALTA_CONFIGURAR);
            return;
        }
        CorreoArmado armado;
        try {
            armado = armar(correo);
        } catch (RuntimeException e) {
            // Un turno que no existe no va a aparecer esperando: no se reintenta solo.
            correo.noSalio("No se pudo armar el correo: " + e.getMessage(), false, politica, ahora);
            return;
        }
        try {
            String id = enviador.enviar(correo.para(), armado);
            correo.seEnvio(id, reloj.ahora());
        } catch (EnvioFallidoException e) {
            correo.noSalio(e.getMessage(), e.seArreglaSola(), politica, reloj.ahora());
        } catch (RuntimeException e) {
            // Lo que no se esperaba se trata como pasajero: mejor un reintento de más que un correo perdido.
            correo.noSalio(e.getClass().getSimpleName() + ": " + e.getMessage(), true, politica, reloj.ahora());
        }
    }

    private CorreoArmado armar(Correo correo) {
        String tienda = nombreDeLaTienda.get();
        if (correo.getTipo() == TipoCorreo.PRUEBA) {
            return CorreoDePrueba.de(tienda, enviador.remitente(), reloj.ahora());
        }
        DetalleTurno turno = detalleDelTurno.apply(correo.getTurnoId())
                .orElseThrow(() -> new IllegalStateException("el turno " + correo.getTurnoId() + " no existe"));
        return CorreoDelCierre.de(turno, tienda);
    }
}

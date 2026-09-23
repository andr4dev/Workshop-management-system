package com.workshopmanagement.rdmotors.correo.dominio;

/**
 * El correo no salió (spec 0010, RF-003 y RF-004).
 *
 * <p>La pregunta que importa es si <b>se arregla sola</b>: sin internet, o Brevo caído, basta con esperar y volver a
 * intentar. Una llave inválida o un remitente sin verificar, no: reintentar cada hora sería gastar intentos contra
 * una pared, así que queda fallido y a la vista hasta que alguien lo arregle.
 */
public class EnvioFallidoException extends RuntimeException {

    private final boolean seArreglaSola;

    public EnvioFallidoException(String mensaje, boolean seArreglaSola) {
        super(mensaje);
        this.seArreglaSola = seArreglaSola;
    }

    public EnvioFallidoException(String mensaje, boolean seArreglaSola, Throwable causa) {
        super(mensaje, causa);
        this.seArreglaSola = seArreglaSola;
    }

    public boolean seArreglaSola() {
        return seArreglaSola;
    }
}

package com.workshopmanagement.rdmotors.usuarios.dominio;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

/**
 * Un intento de entrar (spec 0004, RF-025): quién lo intentó, si entró, cuándo y desde qué equipo. Solo lo ve el
 * administrador.
 *
 * <p>Se guarda <b>siempre</b>, entre o no: cinco intentos fallidos seguidos de madrugada son justo lo que hay que
 * poder ver. Nunca guarda la contraseña que se escribió, ni siquiera cuando falló.
 *
 * @param usuarioEscrito lo que se escribió en la casilla, tal cual; {@code usuarioId} va vacío si no existe nadie así
 */
@Entity
@Table(name = "entrada")
@Getter
public class Entrada {

    /** Lo que se escribe en la casilla cabe de sobra; más que esto es basura, y se corta. */
    static final int LARGO_MAXIMO_USUARIO = 60;
    static final int LARGO_MAXIMO_NAVEGADOR = 200;
    static final int LARGO_MAXIMO_IP = 45;

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "usuario_id")
    private UUID usuarioId;

    @Column(name = "usuario_escrito", nullable = false, length = LARGO_MAXIMO_USUARIO)
    private String usuarioEscrito;

    @Column(name = "exito", nullable = false)
    private boolean exito;

    @Column(name = "momento", nullable = false, updatable = false)
    private Instant momento;

    @Column(name = "ip", length = LARGO_MAXIMO_IP)
    private String ip;

    @Column(name = "navegador", length = LARGO_MAXIMO_NAVEGADOR)
    private String navegador;

    protected Entrada() {
        // JPA
    }

    private Entrada(UUID usuarioId, String usuarioEscrito, boolean exito, Instant momento, DatosDelEquipo equipo) {
        this.id = UUID.randomUUID();
        this.usuarioId = usuarioId;
        this.usuarioEscrito = recortar(usuarioEscrito, LARGO_MAXIMO_USUARIO);
        this.exito = exito;
        this.momento = momento;
        this.ip = equipo == null ? null : recortar(equipo.ip(), LARGO_MAXIMO_IP);
        this.navegador = equipo == null ? null : recortar(equipo.navegador(), LARGO_MAXIMO_NAVEGADOR);
    }

    /** Entró. {@code usuarioId} es quien entró. */
    public static Entrada exitosa(UUID usuarioId, String usuarioEscrito, Instant momento, DatosDelEquipo equipo) {
        return new Entrada(usuarioId, usuarioEscrito, true, momento, equipo);
    }

    /** No entró: contraseña mal, usuario que no existe, desactivado o bloqueado por intentos. */
    public static Entrada fallida(UUID usuarioId, String usuarioEscrito, Instant momento, DatosDelEquipo equipo) {
        return new Entrada(usuarioId, usuarioEscrito, false, momento, equipo);
    }

    private static String recortar(String texto, int largo) {
        if (texto == null) {
            return null;
        }
        String limpio = texto.strip();
        return limpio.length() <= largo ? limpio : limpio.substring(0, largo);
    }
}

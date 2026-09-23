package com.workshopmanagement.rdmotors.correo.dominio;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

/**
 * A quién le llega el resumen del cierre (spec 0010, RF-007). Una sola fila: es de la tienda, no de una persona.
 *
 * <p>La llave de Brevo <b>no está aquí</b>: es un secreto y vive en la configuración del servidor (RF-008). Aquí solo
 * lo que el administrador puede ver y cambiar desde la pantalla.
 */
@Entity
@Table(name = "ajustes_correo")
@Getter
public class AjustesDeCorreo {

    public static final short ID = 1;

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private short id;

    /** Separados por comas; vacío apaga el correo del cierre. */
    @Column(name = "destinatarios", nullable = false, length = 700)
    private String destinatarios;

    @Column(name = "actualizado_en")
    private Instant actualizadoEn;

    @Column(name = "actualizado_por_id")
    private UUID actualizadoPorId;

    protected AjustesDeCorreo() {
        // JPA
    }

    /** Los de una base recién creada: nadie recibe nada hasta que el administrador lo configure. */
    public static AjustesDeCorreo iniciales() {
        AjustesDeCorreo ajustes = new AjustesDeCorreo();
        ajustes.id = ID;
        ajustes.destinatarios = "";
        return ajustes;
    }

    public Destinatarios destinatarios() {
        return Destinatarios.desdeTexto(destinatarios);
    }

    public void cambiarDestinatarios(Destinatarios nuevos, UUID quien, Instant cuando) {
        this.destinatarios = nuevos.comoTexto();
        this.actualizadoPorId = quien;
        this.actualizadoEn = cuando;
    }
}

package com.workshopmanagement.rdmotors.compartido.dominio;

import jakarta.persistence.*;
import lombok.Getter;

/**
 * Los datos de la tienda que encabezan el comprobante (spec 0003, RF-033): nombre comercial, NIT,
 * dirección, teléfono y el mensaje al pie.
 *
 * <p><b>Es una sola fila.</b> La tienda es una: no hay "crear" ni "borrar", solo reemplazar. La base lo
 * exige con {@code CHECK (id = 1)} y la siembra con "RD MOTORS", así el comprobante tiene encabezado
 * desde la primera venta aunque el cliente todavía no haya dado el NIT.
 *
 * <p><b>Un dato opcional vacío se guarda como nada</b>, no como texto vacío: el comprobante no imprime
 * una línea "NIT" sin número.
 *
 * <p>El comprobante toma estos datos al imprimirse, no los copia en cada venta: si cambia el teléfono,
 * la reimpresión de una venta vieja sale con el nuevo. Las cifras sí son las de la venta.
 */
@Entity
@Table(name = "datos_tienda")
@Getter
public class DatosTienda {

    /** La única fila. */
    public static final short ID = 1;

    /** Lo que se siembra mientras el cliente no dé sus datos. */
    public static final String NOMBRE_INICIAL = "RD MOTORS";

    static final int NOMBRE_MAXIMO = 80;
    static final int NIT_MAXIMO = 30;
    static final int DIRECCION_MAXIMO = 120;
    static final int TELEFONO_MAXIMO = 40;
    static final int MENSAJE_MAXIMO = 160;

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private short id;

    @Column(name = "nombre_comercial", nullable = false, length = NOMBRE_MAXIMO)
    private String nombreComercial;

    @Column(name = "nit", length = NIT_MAXIMO)
    private String nit;

    @Column(name = "direccion", length = DIRECCION_MAXIMO)
    private String direccion;

    @Column(name = "telefono", length = TELEFONO_MAXIMO)
    private String telefono;

    @Column(name = "mensaje_pie", length = MENSAJE_MAXIMO)
    private String mensajePie;

    protected DatosTienda() {
        // JPA
    }

    /** Como los siembra la migración. Para las pruebas y dobles: en la base la fila ya existe. */
    public static DatosTienda iniciales() {
        DatosTienda datos = new DatosTienda();
        datos.id = ID;
        datos.nombreComercial = NOMBRE_INICIAL;
        return datos;
    }

    /**
     * Reemplaza todos los datos. Primero valida todo y después asigna: si el NIT es muy largo, el
     * nombre tampoco cambia.
     */
    public void actualizar(String nombreComercial, String nit, String direccion, String telefono,
                           String mensajePie) {
        String nombre = limpio(nombreComercial);
        if (nombre == null) {
            throw new ReglaDeNegocioException(
                    "El nombre comercial es obligatorio: es lo primero que sale en el comprobante");
        }
        exigirLargo("El nombre comercial", nombre, NOMBRE_MAXIMO);
        String nitLimpio = exigirLargo("El NIT", limpio(nit), NIT_MAXIMO);
        String direccionLimpia = exigirLargo("La dirección", limpio(direccion), DIRECCION_MAXIMO);
        String telefonoLimpio = exigirLargo("El teléfono", limpio(telefono), TELEFONO_MAXIMO);
        String mensajeLimpio = exigirLargo("El mensaje al pie", limpio(mensajePie), MENSAJE_MAXIMO);

        this.nombreComercial = nombre;
        this.nit = nitLimpio;
        this.direccion = direccionLimpia;
        this.telefono = telefonoLimpio;
        this.mensajePie = mensajeLimpio;
    }

    /** Sin espacios en las puntas ni repetidos entre palabras. Vacío es {@code null}. */
    private static String limpio(String texto) {
        if (texto == null) {
            return null;
        }
        String limpio = texto.trim().replaceAll("\\s+", " ");
        return limpio.isEmpty() ? null : limpio;
    }

    private static String exigirLargo(String campo, String valor, int maximo) {
        if (valor != null && valor.length() > maximo) {
            throw new ReglaDeNegocioException(campo + " es muy largo: máximo " + maximo + " caracteres");
        }
        return valor;
    }
}

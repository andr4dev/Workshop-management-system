package com.workshopmanagement.rdmotors.compartido.infraestructura.seguridad;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Una sola cosa, y es la que costó encontrar (spec 0011, RF-010).
 *
 * <p>Cuando el sistema entrega un archivo <b>mientras lo lee</b> —bajar la copia de la base—, el servidor atiende
 * esa petición en dos tramos: el que decide qué responder y el que va mandando los bytes. Este filtro tiene que
 * correr en los dos, porque el guardia de permisos de Spring corre en los dos: si en el segundo no hay sesión, se
 * niega el acceso a mitad de la descarga.
 *
 * <p>Cuando pasó, el archivo igual llegó completo —la respuesta ya iba en camino— y lo único que quedó fue un error
 * en el registro. Pero eso depende de si alcanzó a salir la primera parte: con una copia pequeña, o con otro ritmo
 * de red, al dueño le habría llegado un "no permitido" en vez de su respaldo. De los errores que aparecen un día de
 * cada veinte y no se pueden reproducir.
 */
class FiltroDeSesionTest {

    @Test
    @DisplayName("LA SESIÓN TAMBIÉN SE LEE EN EL SEGUNDO TRAMO de una respuesta que se manda por partes")
    void tambienEnElSegundoTramo() {
        FiltroDeSesion filtro = new FiltroDeSesion(null, null, null);

        assertThat(filtro.shouldNotFilterAsyncDispatch())
                .as("si esto vuelve a ser true, bajar el respaldo se niega a sí mismo a media descarga")
                .isFalse();
    }
}

package com.workshopmanagement.rdmotors.compartido.infraestructura;

import java.io.IOException;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

/**
 * El servidor sirve la pantalla (spec 0011, RF-002).
 *
 * <h2>Por qué hace falta esto</h2>
 *
 * En desarrollo son dos cosas corriendo: el servidor en un puerto y la pantalla en otro, servida por su propia
 * herramienta. En la nube hay <b>una sola dirección</b>, así que la pantalla va empacada adentro del servidor
 * ({@code classpath:/static/}, que mete el {@code Dockerfile} al construir la imagen).
 *
 * <h2>El problema que resuelve el resolutor</h2>
 *
 * La pantalla tiene sus propias rutas —{@code /caja}, {@code /reportes}, {@code /inventario}— que existen solo
 * dentro del navegador. El servidor no tiene un archivo llamado {@code reportes}, así que si alguien <b>recarga</b>
 * estando ahí, o entra directo por esa dirección, el servidor respondería "no encontrado" y la persona vería una
 * página en blanco. La regla es: si lo que piden no es un archivo que existe, se manda {@code index.html} y la
 * pantalla se encarga.
 *
 * <h2>Y la trampa que hay que esquivar</h2>
 *
 * Esa regla, escrita de más, se traga <b>las direcciones de la API</b>: una petición a {@code /api/algo-que-no-existe}
 * devolvería la pantalla con estado 200, y el navegador se quedaría esperando un JSON que nunca llega. El error no
 * aparece donde se cometió, sino meses después y en cualquier pantalla. Por eso {@code /api/**} nunca cae en el
 * {@code index.html}, y hay una prueba que lo vigila.
 *
 * <p>Lo mismo con lo que <b>parece un archivo</b> ({@code /assets/main-a1b2.js}): si no está, la respuesta correcta
 * es "no encontrado". Devolverle la página a una etiqueta {@code <script>} solo esconde el problema.
 */
@Configuration
class ConfiguracionDeLaPantalla implements WebMvcConfigurer {

    private static final String PANTALLA = "classpath:/static/";
    private static final String INDICE = "index.html";

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registro) {
        registro.addResourceHandler("/**")
                .addResourceLocations(PANTALLA)
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String rutaPedida, Resource ubicacion) throws IOException {
                        // super hace las comprobaciones de siempre, incluida la de no salirse de la carpeta.
                        Resource archivo = super.getResource(rutaPedida, ubicacion);
                        if (archivo != null) {
                            return archivo;
                        }
                        if (esDeLaApi(rutaPedida) || pareceArchivo(rutaPedida)) {
                            return null;
                        }
                        return super.getResource(INDICE, ubicacion);
                    }
                });
    }

    /** Spring entrega la ruta sin la barra del principio: {@code api/ventas}, no {@code /api/ventas}. */
    private static boolean esDeLaApi(String ruta) {
        return ruta.equals("api") || ruta.startsWith("api/");
    }

    /** {@code assets/main-a1b2.js} sí; {@code reportes} no. */
    private static boolean pareceArchivo(String ruta) {
        int ultimaBarra = ruta.lastIndexOf('/');
        return ruta.indexOf('.', ultimaBarra + 1) >= 0;
    }
}

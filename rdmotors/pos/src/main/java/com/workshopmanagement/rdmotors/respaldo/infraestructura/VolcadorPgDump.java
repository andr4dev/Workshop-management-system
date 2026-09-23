package com.workshopmanagement.rdmotors.respaldo.infraestructura;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Component;

import com.workshopmanagement.rdmotors.respaldo.dominio.RespaldoFallidoException;
import com.workshopmanagement.rdmotors.respaldo.dominio.puerto.Volcador;

/**
 * ADAPTADOR — la copia la saca {@code pg_dump}, la herramienta del propio Postgres.
 *
 * <p><b>Por qué no se hace en Java.</b> Un volcado escrito a mano leyendo tablas se desincroniza con el esquema en
 * la primera migración que alguien olvide replicar, y lo descubre el día que hay que restaurar. {@code pg_dump}
 * viene con el motor, sabe del esquema lo mismo que él, y su archivo lo restaura {@code pg_restore} de una.
 *
 * <p>Formato {@code custom} ({@code -Fc}): pesa menos que el SQL plano, se restaura en paralelo y no invita a
 * editarlo a mano.
 *
 * <p>La contraseña va por la variable de entorno {@code PGPASSWORD} del proceso hijo, no en la línea de comandos:
 * ahí la vería cualquiera que liste los procesos del equipo.
 */
@Component
class VolcadorPgDump implements Volcador {

    /** Una base de tienda se vuelca en segundos; diez minutos es un cuelgue, no una demora. */
    private static final int MINUTOS_MAXIMO = 10;
    private static final int LARGO_DEL_ERROR = 600;

    private final ConfiguracionDeRespaldo configuracion;
    private final DatosDeConexion conexion;

    VolcadorPgDump(ConfiguracionDeRespaldo configuracion, DatosDeConexion conexion) {
        this.configuracion = configuracion;
        this.conexion = conexion;
    }

    @Override
    public long volcar(Path destino) {
        Path programa = Path.of(configuracion.getPgDump());
        if (programa.isAbsolute() && !Files.isExecutable(programa)) {
            throw new RespaldoFallidoException("No encontré pg_dump en " + programa + ". Revisa "
                    + "rdmotors.respaldo.pg-dump en la configuración.");
        }
        List<String> orden = new ArrayList<>(List.of(
                programa.toString(),
                "--host=" + conexion.host(),
                "--port=" + conexion.puerto(),
                "--username=" + conexion.usuario(),
                "--dbname=" + conexion.base(),
                "--format=custom",
                "--no-password",
                "--file=" + destino));
        try {
            ProcessBuilder constructor = new ProcessBuilder(orden);
            constructor.environment().put("PGPASSWORD", conexion.contrasena());
            constructor.redirectErrorStream(true);
            Process proceso = constructor.start();
            String salida = leer(proceso.getInputStream());
            if (!proceso.waitFor(MINUTOS_MAXIMO, TimeUnit.MINUTES)) {
                proceso.destroyForcibly();
                throw new RespaldoFallidoException("pg_dump se quedó colgado más de " + MINUTOS_MAXIMO + " minutos");
            }
            if (proceso.exitValue() != 0) {
                throw new RespaldoFallidoException("pg_dump terminó con error " + proceso.exitValue() + ": "
                        + recortar(salida));
            }
            if (!Files.exists(destino)) {
                throw new RespaldoFallidoException("pg_dump dijo que todo salió bien pero no dejó el archivo "
                        + destino);
            }
            return Files.size(destino);
        } catch (IOException e) {
            throw new RespaldoFallidoException("No se pudo correr pg_dump: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RespaldoFallidoException("El respaldo se interrumpió", e);
        }
    }

    private static String leer(InputStream entrada) throws IOException {
        try (InputStream in = entrada) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String recortar(String texto) {
        String limpio = texto == null ? "" : texto.strip();
        return limpio.length() <= LARGO_DEL_ERROR ? limpio : limpio.substring(0, LARGO_DEL_ERROR);
    }
}

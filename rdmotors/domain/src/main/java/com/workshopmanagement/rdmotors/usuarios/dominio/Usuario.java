package com.workshopmanagement.rdmotors.usuarios.dominio;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import com.workshopmanagement.rdmotors.compartido.dominio.Actor;
import com.workshopmanagement.rdmotors.compartido.dominio.ReglaDeNegocioException;
import com.workshopmanagement.rdmotors.compartido.dominio.Rol;
import com.workshopmanagement.rdmotors.compartido.dominio.TextoDeBusqueda;

import jakarta.persistence.*;
import lombok.Getter;

/**
 * Una persona que entra al sistema (spec 0004): usuario y contraseña propios, y un rol.
 *
 * <p><b>La contraseña nunca se guarda</b>: solo su hash, que hace el puerto
 * {@link com.workshopmanagement.rdmotors.usuarios.dominio.puerto.Contrasenas}. Este objeto no sabe calcularlo; solo
 * guarda el que le dan.
 *
 * <p><b>No se borra</b>: se desactiva (spec 0004, RF-015). Sus ventas, gastos y cierres lo siguen nombrando.
 *
 * <p>{@code versionSesion} es lo que permite sacar a alguien en el acto aunque su token siga vigente: el token
 * lleva la versión con que se emitió, y cambiar la contraseña, restablecerla o desactivar a la persona la sube.
 */
@Entity
@Table(name = "usuario")
@Getter
public class Usuario {

    /** Con este tipo quedan en la auditoría: crear, cambiar el rol, desactivar, activar y restablecer (RF-020). */
    public static final String TIPO_AUDITORIA = "USUARIO";
    public static final int LARGO_MINIMO_CONTRASENA = 6;
    /** BCrypt solo mira los primeros 72 bytes: más largo engañaría a quien la escribe. */
    public static final int LARGO_MAXIMO_CONTRASENA = 64;
    public static final int INTENTOS_PERMITIDOS = 5;
    public static final Duration ESPERA_TRAS_INTENTOS = Duration.ofMinutes(5);
    static final int LARGO_MAXIMO_NOMBRE = 80;

    /** Letras, números, punto, guion y guion bajo; de 3 a 40. Sin espacios: se escribe a diario en el mostrador. */
    private static final Pattern USUARIO_VALIDO = Pattern.compile("[\\p{L}\\p{N}._-]{3,40}");

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /** Como lo escribió quien lo creó. */
    @Column(name = "usuario", nullable = false, length = 40)
    private String usuario;

    /** Sin mayúsculas ni tildes: "Carolina" y "carolina" son el mismo usuario (RF-001). Lo hace único la base. */
    @Column(name = "usuario_normalizado", nullable = false, length = 40)
    private String usuarioNormalizado;

    @Column(name = "nombre", nullable = false, length = LARGO_MAXIMO_NOMBRE)
    private String nombre;

    @Column(name = "hash", nullable = false, length = 200)
    private String hash;

    @Enumerated(EnumType.STRING)
    @Column(name = "rol", nullable = false, length = 15)
    private Rol rol;

    @Column(name = "activo", nullable = false)
    private boolean activo;

    /** Una contraseña inicial o restablecida se cambia al entrar, antes de cualquier otra cosa (RF-014). */
    @Column(name = "debe_cambiar_contrasena", nullable = false)
    private boolean debeCambiarContrasena;

    @Column(name = "version_sesion", nullable = false)
    private long versionSesion;

    @Column(name = "intentos_fallidos", nullable = false)
    private int intentosFallidos;

    @Column(name = "bloqueado_hasta")
    private Instant bloqueadoHasta;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn;

    @Column(name = "ultima_entrada")
    private Instant ultimaEntrada;

    protected Usuario() {
        // JPA
    }

    private Usuario(String usuario, String nombre, Rol rol, String hash, boolean debeCambiarContrasena,
                    Instant cuando) {
        this.id = UUID.randomUUID();
        this.usuario = usuario;
        this.usuarioNormalizado = normalizar(usuario);
        this.nombre = nombre;
        this.rol = rol;
        this.hash = hash;
        this.activo = true;
        this.debeCambiarContrasena = debeCambiarContrasena;
        this.versionSesion = 1;
        this.intentosFallidos = 0;
        this.creadoEn = cuando;
    }

    /**
     * @param hash el de la contraseña, ya calculado; la contraseña se valida antes con {@link #exigirContrasenaValida}
     * @param debeCambiarContrasena {@code true} si la eligió otro (el administrador): la cambia al entrar
     */
    public static Usuario nuevo(String usuario, String nombre, Rol rol, String hash, boolean debeCambiarContrasena,
                                Instant cuando) {
        String limpio = usuario == null ? "" : usuario.strip();
        if (!USUARIO_VALIDO.matcher(limpio).matches()) {
            throw new ReglaDeNegocioException(
                    "El usuario tiene de 3 a 40 letras o números, sin espacios (puede llevar punto, guion o guion bajo)");
        }
        String nombreLimpio = nombre == null ? "" : nombre.strip();
        if (nombreLimpio.isEmpty()) {
            throw new ReglaDeNegocioException("Escribe el nombre: es el que sale en el comprobante");
        }
        if (nombreLimpio.length() > LARGO_MAXIMO_NOMBRE) {
            throw new ReglaDeNegocioException("El nombre puede tener hasta " + LARGO_MAXIMO_NOMBRE + " letras");
        }
        if (rol == null) {
            throw new ReglaDeNegocioException("Elige el rol");
        }
        return new Usuario(limpio, nombreLimpio, rol, hash, debeCambiarContrasena, cuando);
    }

    /** Como se compara un usuario: sin mayúsculas, sin tildes y sin espacios alrededor. */
    public static String normalizar(String usuario) {
        return usuario == null ? null : TextoDeBusqueda.normalizar(usuario.strip());
    }

    /** Antes de calcular el hash: la contraseña en claro no llega más lejos que esto. */
    public static void exigirContrasenaValida(String contrasena) {
        if (contrasena == null || contrasena.length() < LARGO_MINIMO_CONTRASENA) {
            throw new ReglaDeNegocioException(
                    "La contraseña tiene que tener al menos " + LARGO_MINIMO_CONTRASENA + " caracteres");
        }
        if (contrasena.length() > LARGO_MAXIMO_CONTRASENA) {
            throw new ReglaDeNegocioException(
                    "La contraseña puede tener hasta " + LARGO_MAXIMO_CONTRASENA + " caracteres");
        }
    }

    // ── Entrar ───────────────────────────────────────────────────────────────

    /** Tras {@value #INTENTOS_PERMITIDOS} intentos fallidos seguidos, espera 5 minutos (RF-003). */
    public boolean estaBloqueado(Instant ahora) {
        return bloqueadoHasta != null && ahora.isBefore(bloqueadoHasta);
    }

    /** Un intento fallido más. Al quinto, queda esperando; al terminar la espera, vuelve a tener cinco. */
    public void registrarIntentoFallido(Instant ahora) {
        intentosFallidos++;
        if (intentosFallidos >= INTENTOS_PERMITIDOS) {
            bloqueadoHasta = ahora.plus(ESPERA_TRAS_INTENTOS);
            intentosFallidos = 0;
        }
    }

    /** Entró: se olvidan los intentos fallidos y queda la última entrada. */
    public void entroBien(Instant ahora) {
        intentosFallidos = 0;
        bloqueadoHasta = null;
        ultimaEntrada = ahora;
    }

    /**
     * La cambia la misma persona (RF-017). Sube la versión: los tokens que tenía dejan de valer, y quien la cambió
     * recibe uno nuevo.
     */
    public void cambiarContrasena(String hashNuevo) {
        this.hash = hashNuevo;
        this.debeCambiarContrasena = false;
        this.versionSesion++;
    }

    // ── Administrar (spec 0004, fase 4) ──────────────────────────────────────

    public boolean esAdministradorActivo() {
        return activo && rol == Rol.ADMINISTRADOR;
    }

    /**
     * No se borra: se desactiva (RF-015). No puede entrar, y su sesión abierta se cae en su próxima acción: sube la
     * versión. Lo que hizo lo sigue nombrando.
     *
     * @return si cambió algo: desactivar a uno ya desactivado no es un error, y no queda en la auditoría
     */
    public boolean desactivar() {
        if (!activo) {
            return false;
        }
        activo = false;
        versionSesion++;
        return true;
    }

    /** Vuelve a poder entrar, con su misma contraseña, y sin la espera de intentos fallidos que tuviera. */
    public boolean activar() {
        if (activo) {
            return false;
        }
        activo = true;
        intentosFallidos = 0;
        bloqueadoHasta = null;
        return true;
    }

    /**
     * Sube la versión: su sesión se cae y, al entrar de nuevo, ve las pantallas de su rol nuevo. El servidor ya lo
     * trataba con el rol nuevo desde la siguiente petición; esto es para que la pantalla no se quede con el viejo.
     */
    public boolean cambiarRol(Rol nuevo) {
        if (nuevo == null) {
            throw new ReglaDeNegocioException("Elige el rol");
        }
        if (nuevo == rol) {
            return false;
        }
        rol = nuevo;
        versionSesion++;
        return true;
    }

    /**
     * El administrador le pone una contraseña temporal (RF-017): queda como inicial, la cambia al entrar, y las
     * sesiones que tenía abiertas dejan de valer. Se olvida la espera por intentos fallidos: justo la pudo haber
     * provocado olvidarla.
     */
    public void restablecerContrasena(String hashTemporal) {
        this.hash = hashTemporal;
        this.debeCambiarContrasena = true;
        this.versionSesion++;
        this.intentosFallidos = 0;
        this.bloqueadoHasta = null;
    }

    /** Quién es, para los casos de uso: id, nombre y rol. */
    public Actor actor() {
        return new Actor(id, nombre, rol);
    }

    /** Lo que queda en la auditoría: nunca el hash. */
    public Map<String, Object> fotografia() {
        return Map.of("usuario", usuario, "nombre", nombre, "rol", rol.name(), "activo", activo);
    }
}

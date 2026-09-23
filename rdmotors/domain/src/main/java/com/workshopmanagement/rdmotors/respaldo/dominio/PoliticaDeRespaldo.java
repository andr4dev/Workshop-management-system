package com.workshopmanagement.rdmotors.respaldo.dominio;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;

import com.workshopmanagement.rdmotors.compartido.dominio.ReglaDeNegocioException;

/**
 * Las reglas del respaldo: cómo se llama cada copia y cuándo hay que avisarle al administrador.
 *
 * <h2>Lo que cambió con el spec 0011</h2>
 *
 * Antes el respaldo lo hacía el sistema solo, de madrugada, y lo dejaba en una carpeta: las reglas eran cuántos días
 * de copias guardar y cuáles podar. Ahora <b>la copia la baja el administrador</b> y el archivo se va con él, así
 * que no hay nada que podar y no hay copia de hoy que esperar.
 *
 * <p>Queda una sola regla, y es la que de verdad protege al negocio: <b>hace cuántos días que nadie baja una
 * copia</b>. El sistema no puede saber si ese archivo todavía existe en el computador del dueño —eso no se puede
 * saber desde aquí— pero sí puede saber cuándo fue la última vez que se lo llevó, y recordárselo.
 *
 * @param diasSinBajarParaAvisar a los cuántos días sin bajar una copia se enciende el aviso (propuesta: 7)
 * @param zona                   la zona de la tienda: el nombre del archivo dice la hora de Colombia, no la UTC
 */
public record PoliticaDeRespaldo(int diasSinBajarParaAvisar, ZoneId zona) {

    /**
     * Con segundos, no solo con la hora y el minuto: dos copias seguidas —dos clics— caían en el mismo nombre y la
     * segunda <b>pisaba</b> el archivo de la primera. Lo encontró el dueño usándolo.
     */
    private static final DateTimeFormatter NOMBRE = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss");

    public PoliticaDeRespaldo {
        if (diasSinBajarParaAvisar < 1) {
            throw new ReglaDeNegocioException("El aviso del respaldo tiene que esperar al menos un día");
        }
        if (zona == null) {
            throw new ReglaDeNegocioException("A la política de respaldo le falta la zona de la tienda");
        }
    }

    /** {@code rdmotors-2026-09-21-020013.dump}: se ordena solo por nombre y se lee de un vistazo. */
    public String nombreDeArchivo(Instant cuando) {
        return "rdmotors-" + NOMBRE.format(cuando.atZone(zona)) + ".dump";
    }

    /** El día de la tienda en que se bajó una copia. */
    public LocalDate diaDe(Instant cuando) {
        return cuando.atZone(zona).toLocalDate();
    }

    /** La última copia que salió bien, o {@code null} si nunca salió bien ninguna. */
    public Respaldo ultimaBuena(List<Respaldo> copias) {
        return copias.stream()
                .filter(Respaldo::salioBien)
                .max(Comparator.comparing(Respaldo::getHechoEn))
                .orElse(null);
    }

    /**
     * Hace cuántos días se bajó la última copia buena, en días de la tienda. {@code null} si nunca se bajó ninguna
     * — que no es "hace muchos días", es algo distinto y peor, y la pantalla lo dice distinto.
     */
    public Integer diasSinBajar(List<Respaldo> copias, Instant ahora) {
        Respaldo ultimaBuena = ultimaBuena(copias);
        if (ultimaBuena == null) {
            return null;
        }
        return (int) ChronoUnit.DAYS.between(diaDe(ultimaBuena.getHechoEn()), diaDe(ahora));
    }

    /**
     * Si hay que avisarle al administrador. Tres motivos, y los tres importan:
     *
     * <ol>
     *   <li><b>Nunca se ha bajado ninguna</b>: lo más urgente de todo.</li>
     *   <li><b>El último intento falló</b>: el dueño tiene que saber que lo que creyó que se bajó, no se bajó.</li>
     *   <li><b>Hace más de {@link #diasSinBajarParaAvisar} días</b> que no se baja una.</li>
     * </ol>
     */
    public boolean hayQueAvisar(List<Respaldo> copias, Instant ahora) {
        Respaldo ultimoIntento = copias.stream().max(Comparator.comparing(Respaldo::getHechoEn)).orElse(null);
        if (ultimoIntento != null && !ultimoIntento.salioBien()) {
            return true;
        }
        Integer dias = diasSinBajar(copias, ahora);
        return dias == null || dias > diasSinBajarParaAvisar;
    }
}

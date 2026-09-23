package com.workshopmanagement.rdmotors.usuarios.aplicacion;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.springframework.transaction.annotation.Transactional;

import com.workshopmanagement.rdmotors.compartido.dominio.Actor;
import com.workshopmanagement.rdmotors.compartido.dominio.Pagina;
import com.workshopmanagement.rdmotors.compartido.dominio.Persona;
import com.workshopmanagement.rdmotors.usuarios.dominio.Entrada;
import com.workshopmanagement.rdmotors.usuarios.dominio.puerto.RepositorioEntradas;
import com.workshopmanagement.rdmotors.usuarios.dominio.puerto.RepositorioUsuarios;

/**
 * CASO DE USO — el registro de entradas (spec 0004, RF-025): quién entró, cuándo, desde qué equipo, y los intentos
 * que fallaron. Del administrador.
 *
 * <p>Sale el <b>usuario escrito</b> tal cual, aunque no exista: un montón de intentos con "admin" a las 3 de la
 * mañana es justo lo que hay que poder ver.
 */
@Transactional(readOnly = true)
public class ConsultarEntradas {

    public static final int TAMANO_MAXIMO_PAGINA = 100;

    private final RepositorioEntradas entradas;
    private final RepositorioUsuarios usuarios;

    public ConsultarEntradas(RepositorioEntradas entradas, RepositorioUsuarios usuarios) {
        this.entradas = entradas;
        this.usuarios = usuarios;
    }

    public Pagina<FichaEntrada> ultimas(int pagina, int tamano, Actor actor) {
        actor.exigirAdministrador();
        Pagina<Entrada> encontradas = entradas.ultimas(Math.max(pagina, 0), Math.clamp(tamano, 1, TAMANO_MAXIMO_PAGINA));
        Map<UUID, String> nombres = usuarios.nombresDe(
                encontradas.elementos().stream().map(Entrada::getUsuarioId).filter(java.util.Objects::nonNull).toList());
        return encontradas.mapear(e -> FichaEntrada.de(e, nombres));
    }

    /**
     * Un intento, como se muestra.
     *
     * @param quien   {@code null} si nadie se llama así: el registro no inventa una persona que no existe
     * @param exito   si entró
     */
    public record FichaEntrada(String usuarioEscrito, Persona quien, boolean exito, Instant momento, String ip,
                               String navegador) {

        static FichaEntrada de(Entrada e, Map<UUID, String> nombres) {
            return new FichaEntrada(e.getUsuarioEscrito(), Persona.de(e.getUsuarioId(), nombres), e.isExito(),
                    e.getMomento(), e.getIp(), e.getNavegador());
        }
    }
}

package com.workshopmanagement.rdmotors.usuarios.infraestructura;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.stereotype.Repository;

import com.workshopmanagement.rdmotors.compartido.dominio.ReglaDeNegocioException;
import com.workshopmanagement.rdmotors.compartido.dominio.Rol;
import com.workshopmanagement.rdmotors.usuarios.dominio.Usuario;
import com.workshopmanagement.rdmotors.usuarios.dominio.puerto.RepositorioUsuarios;

import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;

/** ADAPTADOR — las personas que entran al sistema, sobre Postgres (spec 0004). */
@Repository
@RequiredArgsConstructor
class RepositorioUsuariosJpa implements RepositorioUsuarios {

    private static final String INDICE_USUARIO = "ux_usuario_normalizado";

    /**
     * El candado del primer administrador. Un candado de transacción de Postgres y no una fila: cuando no hay
     * usuarios no hay ninguna fila que bloquear. Se suelta solo al terminar la transacción.
     */
    private static final long CANDADO_PRIMER_ADMINISTRADOR = 4_004L;

    /** El de los cambios que pueden quitar un administrador: otro número, para no frenar el alta del primero. */
    private static final long CANDADO_ADMINISTRADORES = 4_005L;

    private final UsuariosSpringData jpa;
    private final JdbcTemplate jdbc;

    @Override
    public Optional<Usuario> buscar(UUID id) {
        return jpa.findById(id);
    }

    @Override
    public Optional<Usuario> buscarPorUsuario(String usuario) {
        return jpa.buscarPorNormalizado(Usuario.normalizar(usuario));
    }

    @Override
    public Optional<Usuario> buscarPorUsuarioParaModificar(String usuario) {
        return jpa.bloquearPorNormalizado(Usuario.normalizar(usuario));
    }

    @Override
    public Optional<Usuario> buscarParaModificar(UUID id) {
        return jpa.bloquearPorId(id);
    }

    @Override
    public boolean hayUsuarios() {
        return jpa.count() > 0;
    }

    @Override
    public void bloquearAltaDelPrimero() {
        jdbc.query("select pg_advisory_xact_lock(?)", (ResultSetExtractor<Void>) rs -> null,
                CANDADO_PRIMER_ADMINISTRADOR);
    }

    @Override
    public void bloquearCambiosDeAdministradores() {
        jdbc.query("select pg_advisory_xact_lock(?)", (ResultSetExtractor<Void>) rs -> null, CANDADO_ADMINISTRADORES);
    }

    @Override
    public long administradoresActivos() {
        return jpa.contarActivos(Rol.ADMINISTRADOR);
    }

    @Override
    public List<Usuario> todos() {
        return jpa.findAll(Sort.by("nombre", "usuario"));
    }

    /** Con {@code saveAndFlush} para traducir aquí el choque de dos altas con el mismo usuario a la vez. */
    @Override
    public Usuario guardar(Usuario usuario) {
        try {
            return jpa.saveAndFlush(usuario);
        } catch (DataIntegrityViolationException e) {
            if (String.valueOf(e.getMostSpecificCause().getMessage()).contains(INDICE_USUARIO)) {
                throw new ReglaDeNegocioException("Ya existe el usuario «" + usuario.getUsuario() + "»");
            }
            throw e;
        }
    }

    @Override
    public Map<UUID, String> nombresDe(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return jpa.nombresDe(ids.stream().distinct().toList()).stream()
                .collect(Collectors.toMap(fila -> (UUID) fila[0], fila -> (String) fila[1]));
    }
}

interface UsuariosSpringData extends JpaRepository<Usuario, UUID> {

    @Query("select u from Usuario u where u.usuarioNormalizado = :normalizado")
    Optional<Usuario> buscarPorNormalizado(@Param("normalizado") String normalizado);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from Usuario u where u.usuarioNormalizado = :normalizado")
    Optional<Usuario> bloquearPorNormalizado(@Param("normalizado") String normalizado);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from Usuario u where u.id = :id")
    Optional<Usuario> bloquearPorId(@Param("id") UUID id);

    @Query("select count(u) from Usuario u where u.rol = :rol and u.activo = true")
    long contarActivos(@Param("rol") Rol rol);

    /** Cada fila: el id y el nombre. */
    @Query("select u.id, u.nombre from Usuario u where u.id in :ids")
    List<Object[]> nombresDe(@Param("ids") List<UUID> ids);
}

package com.workshopmanagement.rdmotors.inventario.aplicacion;

import java.util.Map;
import java.util.UUID;

import org.springframework.transaction.annotation.Transactional;

import com.workshopmanagement.rdmotors.compartido.dominio.AccionAuditada;
import com.workshopmanagement.rdmotors.compartido.dominio.Actor;
import com.workshopmanagement.rdmotors.compartido.dominio.Dinero;
import com.workshopmanagement.rdmotors.compartido.dominio.EventoAuditoria;
import com.workshopmanagement.rdmotors.compartido.dominio.ReglaDeNegocioException;
import com.workshopmanagement.rdmotors.compartido.dominio.puerto.Reloj;
import com.workshopmanagement.rdmotors.compartido.dominio.puerto.RepositorioAuditoria;
import com.workshopmanagement.rdmotors.inventario.dominio.Categoria;
import com.workshopmanagement.rdmotors.inventario.dominio.CodigoDuplicadoException;
import com.workshopmanagement.rdmotors.inventario.dominio.Variante;
import com.workshopmanagement.rdmotors.inventario.dominio.puerto.RepositorioCategorias;
import com.workshopmanagement.rdmotors.inventario.dominio.puerto.RepositorioVariantes;

/**
 * CASO DE USO — corregir un repuesto mal creado (RF-009).
 *
 * <p>Sin esto, un repuesto que nacio con el nombre equivocado, la categoria que no era o un
 * codigo mal tecleado queda inservible para siempre: el administrador no puede arreglarlo y
 * termina creando un duplicado, que es justo lo que el modelo existe para evitar.
 *
 * <h2>La frontera: que se corrige y que no</h2>
 *
 * Se corrige la <b>ficha</b>: nombre, categoria, aplicacion, marca, precio, stock minimo y codigo.
 *
 * <p><b>NO se tocan el stock ni el costo promedio.</b> Esos dos son el resultado del kardex.
 * Escribirlos aqui dejaria un saldo que ningun movimiento explica, y el kardex dejaria de ser
 * auditable — que es toda su razon de existir. Se corrigen con un ajuste de inventario, que deja
 * su movimiento y su motivo.
 *
 * <p>Fijate que este caso de uso ni siquiera <i>puede</i> tocarlos: {@code Variante} no expone un
 * metodo para hacerlo. La frontera esta en el dominio, no en un comentario.
 *
 * <h2>Deja auditoría (spec 0001 H5, saldado en spec 0002 H7)</h2>
 *
 * Si algo cambió, queda un evento con la ficha antes y después. Si se guardó sin tocar nada, no se
 * registra nada: un rastro lleno de "no cambió nada" esconde los cambios de verdad.
 */
@Transactional
public class ActualizarRepuesto {

    public static final String TIPO_AUDITORIA = "REPUESTO";

    private final RepositorioVariantes variantes;
    private final RepositorioCategorias categorias;
    private final RepositorioAuditoria auditoria;
    private final Reloj reloj;

    public ActualizarRepuesto(RepositorioVariantes variantes, RepositorioCategorias categorias,
                              RepositorioAuditoria auditoria, Reloj reloj) {
        this.variantes = variantes;
        this.categorias = categorias;
        this.auditoria = auditoria;
        this.reloj = reloj;
    }

    public Variante ejecutar(UUID varianteId, ComandoActualizarRepuesto comando) {
        comando.actor().exigirAdministrador();
        Variante variante = variantes.buscarParaModificar(varianteId)
                .orElseThrow(() -> new ReglaDeNegocioException("El repuesto no existe"));
        Map<String, Object> antes = variante.fotografiaDeFicha();

        String codigo = comando.codigo().trim().toUpperCase();

        // Solo se valida si de verdad cambio: sin esto, guardar sin tocar el codigo chocaria
        // consigo mismo y el administrador veria "ya lo usa FILTRO ACEITE" señalando al propio
        // repuesto que esta editando.
        if (!codigo.equals(variante.getCodigo())) {
            variantes.buscarPorCodigo(codigo).ifPresent(otro -> {
                throw new CodigoDuplicadoException(codigo, otro.getProducto().getNombre());
            });
        }

        // El concepto es COMPARTIDO: corregir su nombre cambia todas las marcas que cuelgan de el.
        // Es lo correcto —es el mismo repuesto— y el borde tiene que avisarlo.
        Categoria categoria = comando.categoriaId() == null ? null
                : categorias.buscar(comando.categoriaId())
                        .orElseThrow(() -> new ReglaDeNegocioException("La categoria no existe"));

        variante.getProducto().corregir(
                comando.nombreProducto(), categoria, comando.aplicacionOriginal());

        variante.corregirFicha(codigo, comando.marcaRepuesto(), comando.precio(),
                comando.stockMinimo());

        Map<String, Object> despues = variante.fotografiaDeFicha();
        if (!antes.equals(despues)) {
            auditoria.registrar(EventoAuditoria.nuevo(reloj.ahora(), comando.actor().id(),
                    AccionAuditada.CORREGIR_REPUESTO, TIPO_AUDITORIA, variante.getId(),
                    antes, despues, null));
        }
        return variantes.guardar(variante);
    }

    /**
     * Lo que se puede corregir. Que el stock y el costo promedio NO esten aqui es la decision,
     * no un olvido.
     */
    public record ComandoActualizarRepuesto(
            String nombreProducto,
            UUID categoriaId,
            String aplicacionOriginal,
            String codigo,
            String marcaRepuesto,
            Dinero precio,
            int stockMinimo,
            Actor actor) {

        public ComandoActualizarRepuesto {
            if (actor == null) {
                throw new ReglaDeNegocioException("Falta el usuario que corrige");
            }
            if (codigo == null || codigo.isBlank()) {
                throw new ReglaDeNegocioException("El codigo del repuesto es obligatorio");
            }
            if (precio == null) {
                throw new ReglaDeNegocioException("El precio de venta es obligatorio");
            }
        }
    }
}

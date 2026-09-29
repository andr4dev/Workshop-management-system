package com.workshopmanagement.rdmotors.caja.aplicacion;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import com.workshopmanagement.rdmotors.caja.dominio.CategoriaGasto;
import com.workshopmanagement.rdmotors.caja.dominio.Gasto;
import com.workshopmanagement.rdmotors.caja.dominio.puerto.RepositorioCategoriasGasto;
import com.workshopmanagement.rdmotors.compartido.dominio.Actor;
import com.workshopmanagement.rdmotors.compartido.dominio.Dinero;
import com.workshopmanagement.rdmotors.compartido.dominio.ReglaDeNegocioException;
import com.workshopmanagement.rdmotors.usuarios.dominio.Usuario;
import com.workshopmanagement.rdmotors.usuarios.dominio.puerto.RepositorioUsuarios;

/**
 * La comisión de un cambio de aceite, pagada del cajón al cobrar (spec 0015, decisión 5): un gasto del cajón en la
 * categoría del sistema {@value #CATEGORIA}, de naturaleza costo, a nombre de quien hizo el cambio.
 *
 * <p>Siendo un gasto del cajón, el cierre lo descuenta, los reportes lo restan de la utilidad bruta y <i>Ver
 * cálculo</i> lo lista con su enlace, sin nada nuevo. No hay un paso de "pagar": sale al cobrar.
 *
 * <p><b>No abre transacción</b>, como {@code FiarVenta}: lo llama {@code CobrarVenta} dentro de la suya. Si la venta no
 * se guarda, la comisión tampoco. La llave sale del renglón: reintentar el mismo cobro no paga dos veces.
 */
public class PagarComisionDeCambio {

    /** La categoría del sistema: la siembra la V30. */
    public static final String CATEGORIA = "Comisión cambio de aceite";

    static final String NO_SE_TOCA = "«" + CATEGORIA + "» es la que usa el cobro para pagar los cambios de aceite: "
            + "no se renombra ni se desactiva";

    /**
     * El cobro la busca por su nombre: renombrarla o desactivarla dejaría sin cobrar toda venta de un aceite que se
     * cambia aquí, con un mensaje que el cajero no puede resolver. Por eso la cuidan quienes la editan.
     */
    static boolean esLaDeComisiones(CategoriaGasto categoria) {
        return CATEGORIA.equals(categoria.getNombre());
    }

    private final RegistrarGasto registrarGasto;
    private final RepositorioCategoriasGasto categorias;
    private final RepositorioUsuarios usuarios;

    public PagarComisionDeCambio(RegistrarGasto registrarGasto, RepositorioCategoriasGasto categorias,
                                 RepositorioUsuarios usuarios) {
        this.registrarGasto = registrarGasto;
        this.categorias = categorias;
        this.usuarios = usuarios;
    }

    /**
     * @param lineaId     el renglón que se cambió: de él sale la llave del gasto
     * @param descripcion la venta y el repuesto: "venta N.º 12 · MOTUL 7100 10W30"; aquí se le suma la persona
     * @return el gasto, para que el renglón lo guarde
     * @throws ReglaDeNegocioException si quien hizo el cambio no es un usuario activo, o falta la categoría
     */
    public Gasto ejecutar(UUID lineaId, UUID personaId, Dinero comision, String descripcion, Actor actor) {
        Usuario persona = usuarios.buscar(personaId).filter(Usuario::isActivo)
                .orElseThrow(() -> new ReglaDeNegocioException(
                        "Quien hizo el cambio de aceite no es alguien activo de la tienda: escoge a otra persona"));
        CategoriaGasto categoria = categorias.buscarPorNombre(CATEGORIA)
                .orElseThrow(() -> new ReglaDeNegocioException(
                        "Falta la categoría de gasto «" + CATEGORIA + "»: créala como costo en Categorías de gasto"));
        UUID llave = UUID.nameUUIDFromBytes(("comision-de-cambio:" + lineaId).getBytes(StandardCharsets.UTF_8));
        // Confirmado de entrada: la venta ya se cobró, y la comisión sale aunque pase de lo que hay en el cajón (RF-007).
        return registrarGasto.ejecutar(ComandoRegistrarGasto.delCajon(llave, categoria.getId(), comision,
                descripcion + " · " + persona.getNombre(), actor).conConfirmacion());
    }
}

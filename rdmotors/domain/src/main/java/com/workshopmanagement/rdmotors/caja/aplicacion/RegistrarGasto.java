package com.workshopmanagement.rdmotors.caja.aplicacion;

import java.time.Instant;
import java.util.Optional;

import org.springframework.transaction.annotation.Transactional;

import com.workshopmanagement.rdmotors.caja.dominio.CategoriaGasto;
import com.workshopmanagement.rdmotors.caja.dominio.Gasto;
import com.workshopmanagement.rdmotors.caja.dominio.SinTurnoAbiertoException;
import com.workshopmanagement.rdmotors.caja.dominio.TurnoCaja;
import com.workshopmanagement.rdmotors.caja.dominio.puerto.RepositorioCategoriasGasto;
import com.workshopmanagement.rdmotors.caja.dominio.puerto.RepositorioGastos;
import com.workshopmanagement.rdmotors.caja.dominio.puerto.RepositorioTurnos;
import com.workshopmanagement.rdmotors.compartido.dominio.ReglaDeNegocioException;
import com.workshopmanagement.rdmotors.compartido.dominio.puerto.Reloj;
import com.workshopmanagement.rdmotors.compras.dominio.CuentaPago;
import com.workshopmanagement.rdmotors.compras.dominio.puerto.RepositorioCuentas;

/**
 * CASO DE USO — registrar un gasto (spec 0006, H1 y H10).
 *
 * <h2>Uno del cajón, en orden</h2>
 * <ol>
 *   <li><b>La llave.</b> Si ya se registró con esta llave (doble clic, reintento tras un corte), se devuelve
 *       ese gasto.</li>
 *   <li><b>El turno, bloqueado antes que nada</b>, como todo lo que mueve plata del turno: el cierre no se
 *       puede calcular en medio (RF-017).</li>
 *   <li><b>La llave otra vez</b>: si el mismo gasto llegó dos veces a la vez, este esperó al otro y ahora ya
 *       lo ve.</li>
 *   <li><b>Si es más de lo que debería haber, pedir confirmar</b> (decisión 3), sin decir la cifra.</li>
 * </ol>
 *
 * <p>Uno <b>por fuera</b> no toca ningún turno: sin turno abierto se registra igual (decisión 5).
 */
@Transactional
public class RegistrarGasto {

    private final RepositorioGastos gastos;
    private final RepositorioCategoriasGasto categorias;
    private final RepositorioCuentas cuentas;
    private final RepositorioTurnos turnos;
    private final CalcularArqueo arqueo;
    private final Reloj reloj;

    public RegistrarGasto(RepositorioGastos gastos, RepositorioCategoriasGasto categorias, RepositorioCuentas cuentas,
                          RepositorioTurnos turnos, CalcularArqueo arqueo, Reloj reloj) {
        this.gastos = gastos;
        this.categorias = categorias;
        this.cuentas = cuentas;
        this.turnos = turnos;
        this.arqueo = arqueo;
        this.reloj = reloj;
    }

    public Gasto ejecutar(ComandoRegistrarGasto comando) {
        if (!comando.delCajon()) {
            // Por fuera del cajón (el arriendo por Nequi) es del administrador (spec 0004, §5).
            comando.actor().exigirAdministrador();
        }
        Optional<Gasto> yaRegistrado = gastos.buscarPorLlave(comando.llave());
        if (yaRegistrado.isPresent()) {
            return yaRegistrado.get();
        }

        TurnoCaja turno = null;
        if (comando.delCajon()) {
            turno = turnos.abiertoParaMover().orElseThrow(() -> new SinTurnoAbiertoException(
                    "No hay un turno abierto. Ábrelo en Vender, o registra el gasto como pagado por fuera del cajón."));
            turno.exigirQuePuedaOperar(comando.actor());
            yaRegistrado = gastos.buscarPorLlave(comando.llave());
            if (yaRegistrado.isPresent()) {
                return yaRegistrado.get();
            }
        }

        CategoriaGasto categoria = comando.categoriaId() == null ? null
                : categorias.buscar(comando.categoriaId())
                        .orElseThrow(() -> new ReglaDeNegocioException("La categoría no existe"));
        Instant ahora = reloj.ahora();

        if (turno == null) {
            CuentaPago cuenta = comando.cuentaId() == null ? null
                    : cuentas.buscar(comando.cuentaId())
                            .orElseThrow(() -> new ReglaDeNegocioException("La cuenta no existe"));
            return gastos.guardar(Gasto.porFuera(categoria, comando.monto(), comando.descripcion(), comando.delMes(),
                    comando.formaPago(), cuenta, comando.fecha(), reloj.hoy(), comando.actor().id(), comando.llave(),
                    ahora));
        }

        Gasto gasto = Gasto.delCajon(categoria, comando.monto(), comando.descripcion(), comando.delMes(),
                turno.getId(), reloj.hoy(), comando.actor().id(), comando.llave(), ahora);
        arqueo.exigirConfirmacionSiSupera(turno, gasto.getMonto(), comando.confirmado());
        return gastos.guardar(gasto);
    }
}

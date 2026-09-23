package com.workshopmanagement.rdmotors.compras.aplicacion;

import static com.workshopmanagement.rdmotors.compras.aplicacion.EscenarioCompras.unidades;
import static com.workshopmanagement.rdmotors.compras.aplicacion.EscenarioCompras.unidadesConPrecio;
import static com.workshopmanagement.rdmotors.compras.aplicacion.EscenarioCompras.version;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.workshopmanagement.rdmotors.compartido.dominio.AccionAuditada;
import com.workshopmanagement.rdmotors.compartido.dominio.Dinero;
import com.workshopmanagement.rdmotors.compartido.dominio.ReglaDeNegocioException;
import com.workshopmanagement.rdmotors.compras.dominio.Compra;
import com.workshopmanagement.rdmotors.compras.dominio.EstadoCompra;
import com.workshopmanagement.rdmotors.compras.dominio.FiltroCompras;
import com.workshopmanagement.rdmotors.compras.dominio.RenglonesBloqueadosException;
import com.workshopmanagement.rdmotors.inventario.dominio.TipoMovimiento;
import com.workshopmanagement.rdmotors.inventario.dominio.Variante;

/** Anular una compra que no debió registrarse (spec 0002, H5). */
class AnularCompraTest {

    private EscenarioCompras t;
    private Variante filtro;
    private Variante pastillas;

    @BeforeEach
    void preparar() {
        t = new EscenarioCompras();
        filtro = t.repuesto("352B59K", 6_000);
        pastillas = t.repuesto("152RTX2B", 24_000);
    }

    @Test
    @DisplayName("una factura registrada dos veces se anula una vez y el inventario queda como con una sola")
    void duplicada() {
        Variante gemelo = t.repuesto("GEMELO", 6_000);
        t.comprar(unidadesConPrecio(filtro, 10, 2_000, 9_000), unidadesConPrecio(gemelo, 10, 2_000, 9_000));
        Compra repetida = t.comprar(unidadesConPrecio(filtro, 10, 2_000, 9_000));

        var resultado = t.anular.ejecutar(repetida.getId(), version(repetida),
                "se registró dos veces", t.usuario);

        assertThat(filtro.getStock()).isEqualTo(gemelo.getStock()).isEqualTo(10);
        assertThat(filtro.getCostoPromedio()).isEqualByComparingTo(gemelo.getCostoPromedio());
        assertThat(filtro.getPrecio()).isEqualTo(Dinero.de(9_000));
        assertThat(resultado.avisos()).as("la primera compra fijó el mismo precio antes: no hay que avisar")
                .isEmpty();
        assertThat(t.kardex.historialDe(filtro.getId()).getLast().getTipo())
                .isEqualTo(TipoMovimiento.ANULACION_COMPRA);
    }

    @Test
    @DisplayName("la anulada sigue en el historial, marcada con quién, cuándo y por qué")
    void quedaEnElHistorial() {
        Compra compra = t.comprar(unidades(filtro, 10, 2_000));

        t.anular.ejecutar(compra.getId(), version(compra), "no era de esta tienda", t.usuario);

        assertThat(compra.getEstado()).isEqualTo(EstadoCompra.ANULADA);
        assertThat(compra.getMotivoAnulacion()).isEqualTo("no era de esta tienda");
        assertThat(compra.getAnuladaPorId()).isEqualTo(t.usuario.id());
        assertThat(compra.getAnuladaEn()).isEqualTo(t.reloj.ahora());
        assertThat(compra.getLineas()).as("los renglones no se borran").hasSize(1);

        var historial = t.compras.historial(FiltroCompras.sinFiltros(), 0, 25);
        assertThat(historial.elementos()).extracting(r -> r.estado()).containsExactly(EstadoCompra.ANULADA);

        var evento = t.auditoria.eventos.getLast();
        assertThat(evento.accion()).isEqualTo(AccionAuditada.ANULAR_COMPRA);
        assertThat(evento.antes()).containsEntry("estado", "VIGENTE");
        assertThat(evento.despues()).containsEntry("estado", "ANULADA");
    }

    @Test
    @DisplayName("si un solo renglón está bloqueado no se anula nada, y se dice cuál")
    void bloqueadaNoAnulaNada() {
        Compra compra = t.comprar(unidades(filtro, 10, 2_000), unidades(pastillas, 6, 14_200));
        t.vender(pastillas, 1);

        assertThatThrownBy(() -> t.anular.ejecutar(compra.getId(), version(compra), "motivo", t.usuario))
                .isInstanceOfSatisfying(RenglonesBloqueadosException.class,
                        e -> assertThat(e.getCodigos()).containsExactly("152RTX2B"));

        assertThat(compra.getEstado()).isEqualTo(EstadoCompra.VIGENTE);
        assertThat(filtro.getStock()).as("el renglón no bloqueado tampoco se revirtió").isEqualTo(10);
        assertThat(t.auditoria.eventos).isEmpty();
    }

    @Test
    @DisplayName("una compra ya anulada no se anula otra vez")
    void yaAnulada() {
        Compra compra = t.comprar(unidades(filtro, 10, 2_000));
        t.anular.ejecutar(compra.getId(), version(compra), "duplicada", t.usuario);

        assertThatThrownBy(() -> t.anular.ejecutar(compra.getId(), version(compra), "otra vez", t.usuario))
                .isInstanceOf(ReglaDeNegocioException.class)
                .hasMessageContaining("ya fue anulada");
        assertThat(filtro.getStock()).as("no se revirtió dos veces").isZero();
    }

    @Test
    @DisplayName("sin motivo no se anula")
    void sinMotivo() {
        Compra compra = t.comprar(unidades(filtro, 10, 2_000));

        assertThatThrownBy(() -> t.anular.ejecutar(compra.getId(), version(compra), "", t.usuario))
                .isInstanceOf(ReglaDeNegocioException.class)
                .hasMessageContaining("motivo");
        assertThat(filtro.getStock()).isEqualTo(10);
    }
}

package com.workshopmanagement.rdmotors.compras.aplicacion;

import static com.workshopmanagement.rdmotors.compras.aplicacion.EscenarioCompras.en;
import static com.workshopmanagement.rdmotors.compras.aplicacion.EscenarioCompras.igual;
import static com.workshopmanagement.rdmotors.compras.aplicacion.EscenarioCompras.nuevo;
import static com.workshopmanagement.rdmotors.compras.aplicacion.EscenarioCompras.unidades;
import static com.workshopmanagement.rdmotors.compras.aplicacion.EscenarioCompras.unidadesConPrecio;
import static com.workshopmanagement.rdmotors.compras.aplicacion.EscenarioCompras.version;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.workshopmanagement.rdmotors.compartido.dominio.AccionAuditada;
import com.workshopmanagement.rdmotors.compartido.dominio.Dinero;
import com.workshopmanagement.rdmotors.compartido.dominio.ReglaDeNegocioException;
import com.workshopmanagement.rdmotors.compras.dominio.Compra;
import com.workshopmanagement.rdmotors.compras.dominio.CompraModificadaException;
import com.workshopmanagement.rdmotors.compras.dominio.CuentaPago;
import com.workshopmanagement.rdmotors.compartido.dominio.FormaPago;
import com.workshopmanagement.rdmotors.compras.dominio.LineaCompra;
import com.workshopmanagement.rdmotors.compras.dominio.RenglonesBloqueadosException;
import com.workshopmanagement.rdmotors.inventario.aplicacion.ComandoCrearRepuesto;
import com.workshopmanagement.rdmotors.inventario.dominio.TipoMovimiento;
import com.workshopmanagement.rdmotors.inventario.dominio.Variante;

/**
 * Corregir una compra (spec 0002, H3 y H4). La promesa que se prueba: <b>stock y costo quedan como
 * si la factura se hubiera registrado bien desde el principio</b>, y nada se toca que no haya
 * cambiado.
 */
class CorregirCompraTest {

    private EscenarioCompras t;
    private Variante filtro;
    private Variante pastillas;

    @BeforeEach
    void preparar() {
        t = new EscenarioCompras();
        filtro = t.repuesto("352B59K", 6_000);
        pastillas = t.repuesto("152RTX2B", 24_000);
    }

    private ComandoCorregirCompra cabecera(Compra compra, FormaPago forma, UUID cuentaId, String motivo) {
        return new ComandoCorregirCompra(compra.getId(), version(compra), motivo, t.usuario,
                compra.getProveedor().getId(), compra.getFechaDocumento(), compra.getNumeroFactura(),
                forma, cuentaId, null);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Datos de la factura (H3)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("pasar de efectivo a transferencia deja el rastro con antes y después, y no toca el kardex")
    void corregirFormaDePago() {
        Compra compra = t.comprar(unidades(filtro, 10, 2_000));
        int movimientos = t.movimientosDe(filtro);

        var resultado = t.corregir.ejecutar(cabecera(compra, FormaPago.TRANSFERENCIA,
                t.nequi.getId(), "se pagó por Nequi, no en efectivo"));

        assertThat(resultado.compra().getFormaPago()).isEqualTo(FormaPago.TRANSFERENCIA);
        assertThat(resultado.compra().getCuenta()).isSameAs(t.nequi);
        assertThat(t.movimientosDe(filtro)).as("no mueve inventario").isEqualTo(movimientos);
        assertThat(filtro.getStock()).isEqualTo(10);

        var evento = t.auditoria.eventos.getLast();
        assertThat(evento.accion()).isEqualTo(AccionAuditada.CORREGIR_COMPRA);
        assertThat(evento.motivo()).isEqualTo("se pagó por Nequi, no en efectivo");
        assertThat(evento.usuarioId()).isEqualTo(t.usuario.id());
        assertThat(evento.antes()).containsEntry("formaPago", "EFECTIVO").containsEntry("cuenta", null);
        assertThat(evento.despues()).containsEntry("formaPago", "TRANSFERENCIA")
                .containsEntry("cuenta", "Nequi del dueño");
    }

    @Test
    @DisplayName("sin motivo no se corrige: queda en la auditoría")
    void sinMotivo() {
        Compra compra = t.comprar(unidades(filtro, 10, 2_000));

        assertThatThrownBy(() -> cabecera(compra, FormaPago.TRANSFERENCIA, t.nequi.getId(), "   "))
                .isInstanceOf(ReglaDeNegocioException.class)
                .hasMessageContaining("motivo");
    }

    @Test
    @DisplayName("sin cambios no se registra nada")
    void sinCambios() {
        Compra compra = t.comprar(unidades(filtro, 10, 2_000));

        assertThatThrownBy(() -> t.corregir.ejecutar(
                t.correccion(compra, List.of(igual(t.renglonDe(compra, filtro))))))
                .isInstanceOf(ReglaDeNegocioException.class)
                .hasMessageContaining("No hay cambios");
        assertThat(t.auditoria.eventos).isEmpty();
    }

    @Test
    @DisplayName("con una versión vieja se rechaza: otra corrección se guardó en el medio")
    void versionVieja() {
        Compra compra = t.comprar(unidades(filtro, 10, 2_000));

        var desactualizada = new ComandoCorregirCompra(compra.getId(), 7, "motivo", t.usuario,
                compra.getProveedor().getId(), compra.getFechaDocumento(), "FV-OTRA",
                FormaPago.EFECTIVO, null, null);

        assertThatThrownBy(() -> t.corregir.ejecutar(desactualizada))
                .isInstanceOf(CompraModificadaException.class);
        assertThat(t.compras.vecesBuscadaParaModificar).as("se bloqueó antes de comparar").isEqualTo(1);
    }

    @Test
    @DisplayName("una transferencia sin cuenta se rechaza también al corregir")
    void transferenciaSinCuenta() {
        Compra compra = t.comprar(unidades(filtro, 10, 2_000));

        assertThatThrownBy(() -> t.corregir.ejecutar(cabecera(compra, FormaPago.TRANSFERENCIA, null, "motivo")))
                .isInstanceOf(ReglaDeNegocioException.class)
                .hasMessageContaining("cuenta");
    }

    @Test
    @DisplayName("una cuenta desactivada nueva se rechaza, pero la que ya tenía la compra se conserva")
    void cuentaDesactivada() {
        CuentaPago vieja = t.cuentas.sembrar(CuentaPago.nueva("Bancolombia ···4521"));
        Compra compra = t.registrar.ejecutar(new ComandoRegistrarCompra(t.jotapartes.getId(),
                LocalDate.of(2026, 9, 1), "FV-1", FormaPago.TRANSFERENCIA, vieja.getId(), t.usuario,
                List.of(unidades(filtro, 10, 2_000))));
        vieja.desactivar();

        // Corregir el número no obliga a cambiar una cuenta que se dio de baja después.
        var soloNumero = new ComandoCorregirCompra(compra.getId(), version(compra), "número mal",
                t.usuario, t.jotapartes.getId(), compra.getFechaDocumento(), "FV-1-BIS",
                FormaPago.TRANSFERENCIA, vieja.getId(), null);
        assertThat(t.corregir.ejecutar(soloNumero).compra().getNumeroFactura()).isEqualTo("FV-1-BIS");

        CuentaPago otraDesactivada = t.cuentas.sembrar(CuentaPago.nueva("Daviplata"));
        otraDesactivada.desactivar();
        assertThatThrownBy(() -> t.corregir.ejecutar(cabecera(compra, FormaPago.TRANSFERENCIA,
                otraDesactivada.getId(), "motivo")))
                .isInstanceOf(ReglaDeNegocioException.class)
                .hasMessageContaining("desactivada");
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Renglones (H4)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("EL GEMELO: 100 unidades corregidas a 10 quedan idénticas a registrar 10 desde el principio")
    void gemelo() {
        Variante gemelo = t.repuesto("GEMELO", 6_000);
        // La misma historia para los dos: una compra previa, y luego la factura.
        t.comprar(unidades(filtro, 5, 1_000), unidades(gemelo, 5, 1_000));
        Compra mal = t.comprar(unidades(filtro, 100, 2_000));
        t.comprar(unidades(gemelo, 10, 2_000));

        t.corregir.ejecutar(t.correccion(mal, List.of(
                en(t.renglonDe(mal, filtro), unidades(filtro, 10, 2_000)))));

        assertThat(filtro.getStock()).isEqualTo(gemelo.getStock()).isEqualTo(15);
        // No idéntico al diezmilésimo: deshacer un promedio guardado con 4 decimales deja un residuo
        // ($1.666,6670 contra $1.666,6667 aquí). La tolerancia declarada en el plan es el VALOR del
        // repuesto: menos de un peso de diferencia.
        assertThat(valor(filtro).subtract(valor(gemelo)).abs()).isLessThan(java.math.BigDecimal.ONE);
    }

    private static java.math.BigDecimal valor(Variante v) {
        return v.getCostoPromedio().multiply(java.math.BigDecimal.valueOf(v.getStock()));
    }

    @Test
    @DisplayName("el kardex muestra la entrada original, su reversión y la corregida; el otro renglón no se toca")
    void soloElRenglonQueCambia() {
        Compra compra = t.comprar(unidades(filtro, 100, 2_000), unidades(pastillas, 6, 14_200));
        int movimientosPastillas = t.movimientosDe(pastillas);

        t.corregir.ejecutar(t.correccion(compra, List.of(
                en(t.renglonDe(compra, filtro), unidades(filtro, 10, 2_000)),
                igual(t.renglonDe(compra, pastillas)))));

        assertThat(t.kardex.historialDe(filtro.getId()))
                .extracting(m -> m.getTipo(), m -> m.getCantidadDelta())
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(TipoMovimiento.COMPRA, 100),
                        org.assertj.core.groups.Tuple.tuple(TipoMovimiento.CORRECCION_COMPRA, -100),
                        org.assertj.core.groups.Tuple.tuple(TipoMovimiento.COMPRA, 10));
        assertThat(t.movimientosDe(pastillas)).as("pastillas no cambió").isEqualTo(movimientosPastillas);
    }

    @Test
    @DisplayName("el renglón viejo no se borra: queda dado de baja, y el total se suma de los vigentes")
    void renglonViejoQuedaComoHistoria() {
        Compra compra = t.comprar(unidades(filtro, 100, 2_000), unidades(pastillas, 6, 14_200));
        LineaCompra vieja = t.renglonDe(compra, filtro);

        var resultado = t.corregir.ejecutar(t.correccion(compra, List.of(
                en(vieja, unidades(filtro, 10, 2_000)), igual(t.renglonDe(compra, pastillas)))));

        assertThat(vieja.isVigente()).isFalse();
        assertThat(vieja.getReemplazadaEn()).isNotNull();
        assertThat(resultado.compra().getLineas()).hasSize(3);
        assertThat(resultado.compra().lineasVigentes()).hasSize(2);
        assertThat(resultado.compra().lineasVigentes().get(0).getPosicion())
                .as("el corregido ocupa el lugar del que reemplaza").isEqualTo(vieja.getPosicion());
        assertThat(resultado.compra().getTotal()).isEqualTo(Dinero.de(20_000 + 6 * 14_200));
    }

    @Test
    @DisplayName("corregir solo el costo deja el promedio en el valor correcto")
    void soloCosto() {
        t.comprar(unidades(filtro, 10, 1_000));
        Compra compra = t.comprar(unidades(filtro, 10, 5_000));    // era $3.000

        t.corregir.ejecutar(t.correccion(compra, List.of(
                en(t.renglonDe(compra, filtro), unidades(filtro, 10, 3_000)))));

        assertThat(filtro.getCostoPromedio()).isEqualByComparingTo("2000.0000");
    }

    @Test
    @DisplayName("cambiar solo el precio no mueve inventario ni se bloquea aunque ya se haya vendido")
    void soloPrecio() {
        Compra compra = t.comprar(unidadesConPrecio(filtro, 10, 2_000, 9_000));
        t.vender(filtro, 3);
        int movimientos = t.movimientosDe(filtro);

        t.corregir.ejecutar(t.correccion(compra, List.of(
                en(t.renglonDe(compra, filtro), unidadesConPrecio(filtro, 10, 2_000, 12_000)))));

        assertThat(filtro.getPrecio()).isEqualTo(Dinero.de(12_000));
        assertThat(t.movimientosDe(filtro)).isEqualTo(movimientos);
        assertThat(filtro.getStock()).isEqualTo(7);
    }

    @Test
    @DisplayName("quitar el cambio de precio de un renglón devuelve el precio de antes de esa compra")
    void quitarCambioDePrecio() {
        Compra compra = t.comprar(unidadesConPrecio(filtro, 10, 2_000, 9_000));
        // Otra corrección de precio antes: la base tiene que seguir siendo la de antes de la compra.
        t.corregir.ejecutar(t.correccion(compra, List.of(
                en(t.renglonDe(compra, filtro), unidadesConPrecio(filtro, 10, 2_000, 12_000)))));

        t.corregir.ejecutar(t.correccion(compra, List.of(
                en(t.renglonDe(compra, filtro), unidades(filtro, 10, 2_000)))));

        assertThat(filtro.getPrecio()).isEqualTo(Dinero.de(6_000));
    }

    @Test
    @DisplayName("quitar un renglón que había cambiado el precio devuelve el precio anterior")
    void quitarRenglonConPrecio() {
        Compra compra = t.comprar(unidadesConPrecio(filtro, 10, 2_000, 9_000), unidades(pastillas, 6, 14_200));

        t.corregir.ejecutar(t.correccion(compra, List.of(igual(t.renglonDe(compra, pastillas)))));

        assertThat(filtro.getStock()).isZero();
        assertThat(filtro.getPrecio()).isEqualTo(Dinero.de(6_000));
        assertThat(compra.lineasVigentes()).hasSize(1);
    }

    @Test
    @DisplayName("un renglón que faltaba se agrega, aunque cree un repuesto que no existía")
    void agregarRenglonCreandoRepuesto() {
        Compra compra = t.comprar(unidades(filtro, 10, 2_000));
        var electrico = t.categorias.sembrar(com.workshopmanagement.rdmotors.inventario.dominio.Categoria.nueva("ELECTRICO", 4));
        var nuevoRepuesto = ComandoCrearRepuesto.conConceptoNuevo("BUJIA", electrico.getId(), null,
                "CR7HSA", "NGK", Dinero.de(12_000), 5);

        var resultado = t.corregir.ejecutar(t.correccion(compra, List.of(
                igual(t.renglonDe(compra, filtro)),
                nuevo(ComandoRegistrarCompra.Linea.porTotalCreando(nuevoRepuesto, 4, Dinero.de(28_000))))));

        Variante bujia = t.variantes.buscarPorCodigo("CR7HSA").orElseThrow();
        assertThat(bujia.getStock()).isEqualTo(4);
        assertThat(resultado.compra().lineasVigentes()).hasSize(2);
        assertThat(resultado.compra().lineasVigentes().get(1).getPosicion()).isEqualTo(1);
        assertThat(resultado.compra().getTotal()).isEqualTo(Dinero.de(48_000));
    }

    @Test
    @DisplayName("un renglón con el repuesto equivocado se cambia por el correcto")
    void cambiarRepuesto() {
        Compra compra = t.comprar(unidades(filtro, 10, 2_000));

        t.corregir.ejecutar(t.correccion(compra, List.of(
                en(t.renglonDe(compra, filtro), unidades(pastillas, 10, 2_000)))));

        assertThat(filtro.getStock()).isZero();
        assertThat(filtro.getCostoPromedio()).isNull();
        assertThat(pastillas.getStock()).isEqualTo(10);
    }

    @Test
    @DisplayName("intercambiar los repuestos de dos renglones funciona: primero salen, después entran")
    void intercambiarRepuestos() {
        Compra compra = t.comprar(unidades(filtro, 10, 2_000), unidades(pastillas, 6, 14_200));

        t.corregir.ejecutar(t.correccion(compra, List.of(
                en(t.renglonDe(compra, filtro), unidades(pastillas, 10, 2_000)),
                en(t.renglonDe(compra, pastillas), unidades(filtro, 6, 14_200)))));

        assertThat(filtro.getStock()).isEqualTo(6);
        assertThat(filtro.getCostoPromedio()).isEqualByComparingTo("14200.0000");
        assertThat(pastillas.getStock()).isEqualTo(10);
        assertThat(pastillas.getCostoPromedio()).isEqualByComparingTo("2000.0000");
    }

    @Test
    @DisplayName("corregir dos veces el mismo renglón deja el resultado de la última")
    void corregirDosVeces() {
        t.comprar(unidades(filtro, 5, 1_000));
        Compra compra = t.comprar(unidades(filtro, 10, 3_000));

        t.corregir.ejecutar(t.correccion(compra, List.of(
                en(t.renglonDe(compra, filtro), unidades(filtro, 10, 2_000)))));
        t.corregir.ejecutar(t.correccion(compra, List.of(
                en(t.renglonDe(compra, filtro), unidades(filtro, 10, 2_500)))));

        assertThat(filtro.getStock()).isEqualTo(15);
        // (5 x 1.000 + 10 x 2.500) / 15 = $2.000, con la tolerancia de redondeo del plan
        assertThat(valor(filtro).subtract(new java.math.BigDecimal("30000")).abs())
                .isLessThan(java.math.BigDecimal.ONE);
        assertThat(t.auditoria.eventos).hasSize(2);
    }

    @Test
    @DisplayName("si un renglón está bloqueado, no se aplica NADA y se dicen todos los bloqueados")
    void bloqueoNoAplicaNada() {
        Compra compra = t.comprar(unidades(filtro, 10, 2_000), unidades(pastillas, 6, 14_200));
        t.vender(filtro, 1);
        t.vender(pastillas, 1);
        int movimientos = t.kardex.movimientos.size();

        assertThatThrownBy(() -> t.corregir.ejecutar(t.correccion(compra, List.of(
                en(t.renglonDe(compra, filtro), unidades(filtro, 8, 2_000)),
                en(t.renglonDe(compra, pastillas), unidades(pastillas, 5, 14_200))))))
                .isInstanceOfSatisfying(RenglonesBloqueadosException.class,
                        e -> assertThat(e.getCodigos()).containsExactly("352B59K", "152RTX2B"));

        assertThat(t.kardex.movimientos).hasSize(movimientos);
        assertThat(filtro.getStock()).isEqualTo(9);
        assertThat(compra.lineasVigentes()).hasSize(2);
        assertThat(t.auditoria.eventos).isEmpty();
    }

    @Test
    @DisplayName("una corrección no puede dejar la compra sin renglones: para eso está anular")
    void sinRenglones() {
        Compra compra = t.comprar(unidades(filtro, 10, 2_000));

        assertThatThrownBy(() -> t.corregir.ejecutar(t.correccion(compra, List.of())))
                .isInstanceOf(ReglaDeNegocioException.class)
                .hasMessageContaining("anúlala");
    }

    @Test
    @DisplayName("el mismo repuesto en dos renglones se rechaza, igual que al registrar")
    void repuestoRepetido() {
        Compra compra = t.comprar(unidades(filtro, 10, 2_000));

        assertThatThrownBy(() -> t.corregir.ejecutar(t.correccion(compra, List.of(
                igual(t.renglonDe(compra, filtro)), nuevo(unidades(filtro, 3, 2_000))))))
                .isInstanceOf(ReglaDeNegocioException.class)
                .hasMessageContaining("dos veces");
    }

    @Test
    @DisplayName("un renglón de otra compra se rechaza")
    void renglonDeOtraCompra() {
        Compra compra = t.comprar(unidades(filtro, 10, 2_000));
        Compra otra = t.comprar(unidades(pastillas, 6, 14_200));

        assertThatThrownBy(() -> t.corregir.ejecutar(t.correccion(compra, List.of(
                igual(t.renglonDe(otra, pastillas))))))
                .isInstanceOf(ReglaDeNegocioException.class)
                .hasMessageContaining("no es de esta compra");
    }

    @Test
    @DisplayName("una compra anulada no se corrige")
    void anuladaNoSeCorrige() {
        Compra compra = t.comprar(unidades(filtro, 10, 2_000));
        t.anular.ejecutar(compra.getId(), version(compra), "duplicada", t.usuario);

        assertThatThrownBy(() -> t.corregir.ejecutar(cabecera(compra, FormaPago.TRANSFERENCIA,
                t.nequi.getId(), "motivo")))
                .isInstanceOf(ReglaDeNegocioException.class)
                .hasMessageContaining("anulada");
    }

    @Test
    @DisplayName("el evento guarda los renglones antes y después de la corrección")
    void eventoConRenglones() {
        Compra compra = t.comprar(unidades(filtro, 100, 2_000));

        t.corregir.ejecutar(t.correccion(compra, List.of(
                en(t.renglonDe(compra, filtro), unidades(filtro, 10, 2_000)))));

        var evento = t.auditoria.eventos.getLast();
        @SuppressWarnings("unchecked")
        var antes = (List<Map<String, Object>>) evento.antes().get("renglones");
        @SuppressWarnings("unchecked")
        var despues = (List<Map<String, Object>>) evento.despues().get("renglones");
        assertThat(antes.get(0)).containsEntry("cantidad", 100).containsEntry("codigo", "352B59K");
        assertThat(despues.get(0)).containsEntry("cantidad", 10);
        assertThat(evento.antes()).containsEntry("total", 200_000L);
        assertThat(evento.despues()).containsEntry("total", 20_000L);
    }
}

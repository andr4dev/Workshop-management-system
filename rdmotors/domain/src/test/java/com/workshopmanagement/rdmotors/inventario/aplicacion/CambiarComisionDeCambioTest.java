package com.workshopmanagement.rdmotors.inventario.aplicacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.workshopmanagement.rdmotors.compartido.ActoresDePrueba;
import com.workshopmanagement.rdmotors.compartido.Falsos;
import com.workshopmanagement.rdmotors.compartido.dominio.AccionAuditada;
import com.workshopmanagement.rdmotors.compartido.dominio.Dinero;
import com.workshopmanagement.rdmotors.compartido.dominio.NoPermitidoException;
import com.workshopmanagement.rdmotors.compartido.dominio.ReglaDeNegocioException;
import com.workshopmanagement.rdmotors.inventario.dominio.Categoria;
import com.workshopmanagement.rdmotors.inventario.dominio.Producto;
import com.workshopmanagement.rdmotors.inventario.dominio.Variante;

/** Qué repuestos pagan comisión por cambio de aceite, y cuánto (spec 0015, fase 1, RF-001). */
class CambiarComisionDeCambioTest {

    private Falsos.VariantesEnMemoria variantes;
    private Falsos.AuditoriaEnMemoria auditoria;
    private CambiarComisionDeCambio cambiar;
    private Variante motul;
    private Variante lubricanteDeCadena;

    @BeforeEach
    void preparar() {
        variantes = new Falsos.VariantesEnMemoria();
        auditoria = new Falsos.AuditoriaEnMemoria();
        cambiar = new CambiarComisionDeCambio(variantes, auditoria, new Falsos.RelojFijo("2026-09-29T15:00:00Z"));
        Categoria motor = Categoria.nueva("MOTOR", 1);
        motul = variantes.sembrar(Variante.nueva(Producto.nuevo("MOTUL 7100 10W30", motor, null), "104089", "MOTUL",
                Dinero.de(65_000), 3));
        lubricanteDeCadena = variantes.sembrar(Variante.nueva(Producto.nuevo("LUBRICA CADENA C4 MOTUL", motor, null),
                "111658", "MOTUL", Dinero.de(55_000), 3));
    }

    @Test
    @DisplayName("el administrador marca el aceite con $3.000; el lubricante de cadena, sin marcar, no paga")
    void marcarElAceite() {
        Variante marcado = cambiar.ejecutar(motul.getId(), Dinero.de(3_000), ActoresDePrueba.administrador());

        assertThat(marcado.pagaComisionDeCambio()).isTrue();
        assertThat(marcado.getComisionCambio()).isEqualTo(Dinero.de(3_000));
        assertThat(lubricanteDeCadena.pagaComisionDeCambio()).isFalse();
        assertThat(auditoria.eventos).singleElement().satisfies(e -> {
            assertThat(e.accion()).isEqualTo(AccionAuditada.CORREGIR_REPUESTO);
            assertThat(e.antes()).containsEntry("comisionCambio", null);
            assertThat(e.despues()).containsEntry("comisionCambio", 3_000L);
        });
    }

    @Test
    @DisplayName("quitarla lo deja sin pagar; guardar lo mismo no deja rastro")
    void quitarYRepetir() {
        cambiar.ejecutar(motul.getId(), Dinero.de(3_000), ActoresDePrueba.administrador());
        cambiar.ejecutar(motul.getId(), Dinero.de(3_000), ActoresDePrueba.administrador());
        assertThat(auditoria.eventos).as("repetir no es cambiar").hasSize(1);

        cambiar.ejecutar(motul.getId(), null, ActoresDePrueba.administrador());
        assertThat(motul.pagaComisionDeCambio()).isFalse();
        assertThat(auditoria.eventos).hasSize(2);
    }

    @Test
    @DisplayName("el cajero no la cambia, y $0 o menos no es una comisión")
    void loQueNoSePuede() {
        assertThatThrownBy(() -> cambiar.ejecutar(motul.getId(), Dinero.de(3_000), ActoresDePrueba.cajero()))
                .isInstanceOf(NoPermitidoException.class);
        assertThatThrownBy(() -> cambiar.ejecutar(motul.getId(), Dinero.CERO, ActoresDePrueba.administrador()))
                .isInstanceOf(ReglaDeNegocioException.class).hasMessageContaining("mayor a $0");
        assertThatThrownBy(() -> cambiar.ejecutar(motul.getId(), Dinero.de(-3_000), ActoresDePrueba.administrador()))
                .isInstanceOf(ReglaDeNegocioException.class);
        assertThat(motul.pagaComisionDeCambio()).isFalse();
        assertThat(auditoria.eventos).isEmpty();
    }
}

package com.workshopmanagement.rdmotors.compras.aplicacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.workshopmanagement.rdmotors.compartido.ActoresDePrueba;
import com.workshopmanagement.rdmotors.compartido.Falsos;
import com.workshopmanagement.rdmotors.compartido.dominio.Actor;
import com.workshopmanagement.rdmotors.compartido.dominio.ReglaDeNegocioException;
import com.workshopmanagement.rdmotors.compras.dominio.CuentaPago;

/** Dar de baja una cuenta (spec 0002, RF-004): deja de ofrecerse, pero no desaparece. */
class DesactivarCuentaTest {

    private final Actor admin = ActoresDePrueba.administrador();

    private Falsos.CuentasEnMemoria cuentas;
    private DesactivarCuenta desactivar;
    private CuentaPago nequi;

    @BeforeEach
    void preparar() {
        cuentas = new Falsos.CuentasEnMemoria();
        desactivar = new DesactivarCuenta(cuentas);
        nequi = cuentas.sembrar(CuentaPago.nueva("Nequi del dueño"));
    }

    @Test
    @DisplayName("una cuenta desactivada deja de ofrecerse, pero sigue existiendo para sus compras")
    void dejaDeOfrecerse() {
        desactivar.ejecutar(nequi.getId(), admin);

        assertThat(cuentas.activas()).isEmpty();
        assertThat(cuentas.buscar(nequi.getId())).get().extracting(CuentaPago::isActiva).isEqualTo(false);
    }

    @Test
    @DisplayName("desactivarla otra vez no es un error: un doble clic no debe asustar")
    void repetirNoFalla() {
        desactivar.ejecutar(nequi.getId(), admin);

        assertThat(desactivar.ejecutar(nequi.getId(), admin).isActiva()).isFalse();
    }

    @Test
    @DisplayName("una cuenta que no existe se rechaza")
    void inexistente() {
        assertThatThrownBy(() -> desactivar.ejecutar(UUID.randomUUID(), admin))
                .isInstanceOf(ReglaDeNegocioException.class)
                .hasMessageContaining("no existe");
    }
}

package com.workshopmanagement.rdmotors.compras.aplicacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.workshopmanagement.rdmotors.compartido.ActoresDePrueba;
import com.workshopmanagement.rdmotors.compartido.Falsos;
import com.workshopmanagement.rdmotors.compartido.dominio.Actor;
import com.workshopmanagement.rdmotors.compartido.dominio.ReglaDeNegocioException;
import com.workshopmanagement.rdmotors.compras.dominio.CuentaPago;

/**
 * Las cuentas son una lista para que los totales por cuenta cuadren. Lo que se prueba aquí es
 * justo lo que rompería eso: nombres vacíos y la misma cuenta escrita de dos formas.
 */
class RegistrarCuentaTest {

    private final Actor admin = ActoresDePrueba.administrador();

    private Falsos.CuentasEnMemoria cuentas;
    private RegistrarCuenta registrarCuenta;

    @BeforeEach
    void preparar() {
        cuentas = new Falsos.CuentasEnMemoria();
        registrarCuenta = new RegistrarCuenta(cuentas);
    }

    @Test
    @DisplayName("crea la cuenta activa, con los espacios de más quitados")
    void creaActiva() {
        CuentaPago creada = registrarCuenta.ejecutar("  Nequi   del dueño ", admin);

        assertThat(creada.getNombre()).isEqualTo("Nequi del dueño");
        assertThat(creada.isActiva()).isTrue();
        assertThat(cuentas.activas()).containsExactly(creada);
    }

    @Test
    @DisplayName("sin nombre no se crea")
    void sinNombre() {
        assertThatThrownBy(() -> registrarCuenta.ejecutar("   ", admin))
                .isInstanceOf(ReglaDeNegocioException.class)
                .hasMessageContaining("obligatorio");
        assertThat(cuentas.activas()).isEmpty();
    }

    @Test
    @DisplayName("la misma cuenta con otras mayúsculas o espacios se rechaza y dice cuál existe")
    void duplicadaIgnorandoMayusculasYEspacios() {
        registrarCuenta.ejecutar("Nequi del dueño", admin);

        // Si esto pasara, el total de "Nequi del dueño" quedaría partido en dos filas.
        assertThatThrownBy(() -> registrarCuenta.ejecutar("NEQUI  del dueño", admin))
                .isInstanceOf(ReglaDeNegocioException.class)
                .hasMessageContaining("«Nequi del dueño»");
        assertThat(cuentas.activas()).hasSize(1);
    }

    @Test
    @DisplayName("un nombre de más de 80 caracteres se rechaza en vez de truncarse")
    void nombreMuyLargo() {
        assertThatThrownBy(() -> registrarCuenta.ejecutar("x".repeat(81), admin))
                .isInstanceOf(ReglaDeNegocioException.class)
                .hasMessageContaining("muy largo");
    }
}

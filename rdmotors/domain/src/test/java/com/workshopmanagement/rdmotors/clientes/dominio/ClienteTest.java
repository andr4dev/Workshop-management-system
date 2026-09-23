package com.workshopmanagement.rdmotors.clientes.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.workshopmanagement.rdmotors.compartido.dominio.ReglaDeNegocioException;

/** Los datos de un cliente y lo que hace falta para fiarle (spec 0008, RF-001, RF-002, RF-004 y RF-005). */
class ClienteTest {

    private static final Instant AHORA = Instant.parse("2026-09-21T15:00:00Z");
    private final UUID cajero = UUID.randomUUID();

    private Cliente juan() {
        return Cliente.nuevo(new DatosCliente("  Juan   Pérez ", "1.234.567-8", "300 123 4567", " Calle 5 ", null),
                cajero, AHORA);
    }

    @Test
    @DisplayName("los datos quedan limpios, y la cédula y el celular se comparan sin puntos, guiones ni espacios")
    void datosLimpios() {
        Cliente juan = juan();

        assertThat(juan.getNombre()).isEqualTo("Juan Pérez");
        assertThat(juan.getNombreNormalizado()).isEqualTo("juan perez");
        assertThat(juan.getDocumento()).isEqualTo("1.234.567-8");
        assertThat(juan.getDocumentoNormalizado()).isEqualTo("12345678");
        assertThat(juan.getCelular()).isEqualTo("300 123 4567");
        assertThat(juan.getCelularNormalizado()).isEqualTo("3001234567");
        assertThat(juan.getDireccion()).isEqualTo("Calle 5");
        assertThat(juan.getNota()).isNull();
        assertThat(juan.getCreadoPorId()).isEqualTo(cajero);
        assertThat(Cliente.normalizarDocumento("900.123.456-7")).isEqualTo("9001234567");
        assertThat(Cliente.normalizarDocumento("  - . ")).isNull();
    }

    @Test
    @DisplayName("SE LE FÍA CON SOLO EL NOMBRE: la cédula y el celular se avisan, no se exigen (decisión 2, cambiada el 2026-09-21)")
    void seLeFiaConSoloElNombre() {
        Cliente soloNombre = Cliente.nuevo(new DatosCliente("Juan Pérez", null, "  ", null, null), cajero, AHORA);

        // Con el cliente enfrente, frenar la venta por un dato que no trae encima es peor que fiar con lo que hay.
        assertThat(soloNombre.sePuedeFiar()).isTrue();
        assertThatCode(soloNombre::exigirQueSePuedaFiar).doesNotThrowAnyException();

        // Pero queda dicho qué falta, para completarlo cuando se pueda.
        assertThat(soloNombre.datosQueFaltan()).containsExactly("la cédula", "el celular");

        Cliente sinCelular = Cliente.nuevo(new DatosCliente("Juan Pérez", "12345678", null, null, null), cajero, AHORA);
        assertThat(sinCelular.datosQueFaltan()).containsExactly("el celular");
        assertThat(juan().datosQueFaltan()).isEmpty();
    }

    @Test
    @DisplayName("sin nombre no hay cliente; una cédula o un celular que no son números no se aceptan")
    void datosQueNoSirven() {
        assertThatThrownBy(() -> Cliente.nuevo(new DatosCliente("   ", null, null, null, null), cajero, AHORA))
                .hasMessage("Escribe el nombre del cliente");
        assertThatThrownBy(() -> Cliente.nuevo(new DatosCliente("Juan", "12-3", null, null, null), cajero, AHORA))
                .hasMessageContaining("cédula o NIT no parece válida");
        assertThatThrownBy(() -> Cliente.nuevo(new DatosCliente("Juan", "12345678", "12 34", null, null), cajero, AHORA))
                .hasMessageContaining("de 7 a 15 dígitos");
        assertThatThrownBy(() -> Cliente.nuevo(new DatosCliente("Juan", "12345678", "tres cero cero", null, null),
                cajero, AHORA))
                .hasMessageContaining("de 7 a 15 dígitos");
    }

    @Test
    @DisplayName("completar lo que faltaba no es corregir; cambiar o borrar lo escrito, sí")
    void completarContraCorregir() {
        Cliente soloNombre = Cliente.nuevo(new DatosCliente("Juan Pérez", null, null, null, null), cajero, AHORA);

        assertThat(soloNombre.cambiaLoEscrito(new DatosCliente("Juan Pérez", "1.234.567", "3001234567", "Calle 5", null)))
                .isFalse();

        Cliente juan = juan();
        // Otra forma de escribir la misma cédula y el mismo celular: no cambia nada.
        assertThat(juan.cambiaLoEscrito(new DatosCliente("Juan Pérez", "12345678", "3001234567", "Calle 5", "Taller")))
                .isFalse();
        assertThat(juan.cambiaLoEscrito(new DatosCliente("Juan Perez", "12345678", "3001234567", "Calle 5", null)))
                .as("otro nombre").isTrue();
        assertThat(juan.cambiaLoEscrito(new DatosCliente("Juan Pérez", "87654321", "3001234567", "Calle 5", null)))
                .as("otra cédula").isTrue();
        assertThat(juan.cambiaLoEscrito(new DatosCliente("Juan Pérez", "12345678", null, "Calle 5", null)))
                .as("borrar el celular").isTrue();
    }

    @Test
    @DisplayName("cerrarle el fiado exige motivo y no deja fiar; abrirlo lo deja como estaba")
    void cerrarYAbrirElFiado() {
        Cliente juan = juan();

        assertThatThrownBy(() -> juan.cerrarFiado("  ")).isInstanceOf(ReglaDeNegocioException.class);
        juan.cerrarFiado("No paga desde julio");

        assertThat(juan.isFiadoCerrado()).isTrue();
        assertThat(juan.getMotivoFiadoCerrado()).isEqualTo("No paga desde julio");
        assertThat(juan.sePuedeFiar()).isFalse();
        assertThatThrownBy(juan::exigirQueSePuedaFiar)
                .hasMessage("A Juan Pérez no se le fía: lo cerró el administrador. Puede pagar de contado");
        assertThatThrownBy(() -> juan.cerrarFiado("otra vez")).hasMessageContaining("ya se le había cerrado");
        assertThat(juan.fotografia()).containsEntry("fiadoCerrado", true)
                .containsEntry("motivoFiadoCerrado", "No paga desde julio");

        juan.abrirFiado();
        assertThat(juan.sePuedeFiar()).isTrue();
        assertThat(juan.getMotivoFiadoCerrado()).isNull();
        assertThatThrownBy(juan::abrirFiado).hasMessageContaining("no está cerrado");
    }
}

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
    @DisplayName("SIN FRENOS: la cédula y el celular se guardan como los escriban; solo se exige el nombre (2026-09-28)")
    void loQueEscribanSeGuarda() {
        assertThatThrownBy(() -> Cliente.nuevo(new DatosCliente("   ", null, null, null, null), cajero, AHORA))
                .hasMessage("Escribe el nombre del cliente");

        // Antes cada uno de estos frenaba el fiado con "no parece válida" o "de 7 a 15 dígitos".
        Cliente corto = Cliente.nuevo(new DatosCliente("Juan", "12-3", "12 34", null, null), cajero, AHORA);
        assertThat(corto.getDocumento()).isEqualTo("12-3");
        assertThat(corto.getDocumentoNormalizado()).isEqualTo("123");
        assertThat(corto.getCelular()).isEqualTo("12 34");
        assertThat(corto.getCelularNormalizado()).isEqualTo("1234");
        assertThat(corto.datosQueFaltan()).isEmpty();

        Cliente dosNumeros = Cliente.nuevo(
                new DatosCliente("Juan", "C.C. 1.234.567 de Pasto", "3001234567 - 3109876543 ext12", null, null),
                cajero, AHORA);
        assertThat(dosNumeros.getDocumento()).isEqualTo("C.C. 1.234.567 de Pasto");
        // 22 dígitos: antes de la V27 la base guardaba 20.
        assertThat(dosNumeros.getCelularNormalizado()).isEqualTo("3001234567310987654312");

        // Lo que no trae ni un número (el celular) ni una letra o número (la cédula) no se puede buscar: queda vacío.
        Cliente nada = Cliente.nuevo(new DatosCliente("Juan", " - . ", "no tiene", null, null), cajero, AHORA);
        assertThat(nada.getDocumento()).isNull();
        assertThat(nada.getCelular()).isNull();
        assertThat(nada.datosQueFaltan()).containsExactly("la cédula", "el celular");

        // Lo único que se sigue revisando es el largo: es lo que cabe en la base.
        assertThatThrownBy(() -> Cliente.nuevo(new DatosCliente("Juan", "1".repeat(31), null, null, null), cajero, AHORA))
                .hasMessageContaining("máximo 30");
        assertThatThrownBy(() -> Cliente.nuevo(new DatosCliente("Juan", null, "3".repeat(31), null, null), cajero, AHORA))
                .hasMessageContaining("máximo 30");
    }

    @Test
    @DisplayName("solo la cédula que parece un documento dice quién es el cliente: \"no tiene\" o \"123\" no")
    void documentoQueIdentifica() {
        assertThat(Cliente.documentoQueIdentifica("1.234.567-8")).isEqualTo("12345678");
        assertThat(Cliente.documentoQueIdentifica("900.123.456-7")).isEqualTo("9001234567");
        assertThat(Cliente.documentoQueIdentifica("pa 12345")).as("un pasaporte").isEqualTo("PA12345");
        assertThat(Cliente.documentoQueIdentifica("12345")).as("el más corto que cuenta").isEqualTo("12345");
        assertThat(Cliente.documentoQueIdentifica("1234")).isNull();
        assertThat(Cliente.documentoQueIdentifica("123")).isNull();
        assertThat(Cliente.documentoQueIdentifica("no tiene")).as("sin un solo número").isNull();
        assertThat(Cliente.documentoQueIdentifica("N/A")).isNull();
        assertThat(Cliente.documentoQueIdentifica("1234567890123456")).as("más de 15").isNull();
        assertThat(Cliente.documentoQueIdentifica("NIÑO123")).as("letras que no son de la A a la Z").isNull();
        assertThat(Cliente.documentoQueIdentifica(null)).isNull();
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

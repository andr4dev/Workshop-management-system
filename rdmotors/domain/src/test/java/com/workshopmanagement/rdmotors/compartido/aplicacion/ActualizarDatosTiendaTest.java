package com.workshopmanagement.rdmotors.compartido.aplicacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.function.Function;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.workshopmanagement.rdmotors.compartido.ActoresDePrueba;
import com.workshopmanagement.rdmotors.compartido.Falsos;
import com.workshopmanagement.rdmotors.compartido.aplicacion.ActualizarDatosTienda.ComandoDatosTienda;
import com.workshopmanagement.rdmotors.compartido.dominio.Actor;
import com.workshopmanagement.rdmotors.compartido.dominio.DatosTienda;
import com.workshopmanagement.rdmotors.compartido.dominio.NoPermitidoException;
import com.workshopmanagement.rdmotors.compartido.dominio.ReglaDeNegocioException;

/** Los datos de la tienda que encabezan el comprobante (spec 0003, RF-033). */
class ActualizarDatosTiendaTest {

    private final Actor admin = ActoresDePrueba.administrador();

    private Falsos.TiendaEnMemoria tienda;
    private ActualizarDatosTienda actualizar;

    @BeforeEach
    void preparar() {
        tienda = new Falsos.TiendaEnMemoria();
        actualizar = new ActualizarDatosTienda(tienda);
    }

    private static ComandoDatosTienda completos() {
        return new ComandoDatosTienda("RD Motors Almacén", "900.123.456-7", "Calle 10 # 5-20, Sincelejo",
                "300 123 4567", "Gracias por su compra");
    }

    @Test
    @DisplayName("arranca con \"RD MOTORS\" y nada más, como la siembra de V10")
    void iniciales() {
        DatosTienda datos = tienda.actuales();

        assertThat(datos.getNombreComercial()).isEqualTo("RD MOTORS");
        assertThat(datos.getNit()).isNull();
        assertThat(datos.getDireccion()).isNull();
        assertThat(datos.getTelefono()).isNull();
        assertThat(datos.getMensajePie()).isNull();
    }

    @Test
    @DisplayName("reemplaza todos los datos y los guarda")
    void reemplaza() {
        DatosTienda datos = actualizar.ejecutar(completos(), admin);

        assertThat(datos.getNombreComercial()).isEqualTo("RD Motors Almacén");
        assertThat(datos.getNit()).isEqualTo("900.123.456-7");
        assertThat(datos.getDireccion()).isEqualTo("Calle 10 # 5-20, Sincelejo");
        assertThat(datos.getTelefono()).isEqualTo("300 123 4567");
        assertThat(datos.getMensajePie()).isEqualTo("Gracias por su compra");
        assertThat(tienda.vecesGuardada).isEqualTo(1);
    }

    @Test
    @DisplayName("quita los espacios de las puntas y los repetidos entre palabras")
    void limpiaEspacios() {
        DatosTienda datos = actualizar.ejecutar(new ComandoDatosTienda("  RD   Motors ", " 900 123 ", null, null,
                "  Gracias   por su compra  "), admin);

        assertThat(datos.getNombreComercial()).isEqualTo("RD Motors");
        assertThat(datos.getNit()).isEqualTo("900 123");
        assertThat(datos.getMensajePie()).isEqualTo("Gracias por su compra");
    }

    @Test
    @DisplayName("un opcional vacío o de solo espacios queda sin valor: el ticket no imprime una línea vacía")
    void opcionalesVaciosSonNada() {
        actualizar.ejecutar(completos(), admin);

        DatosTienda datos = actualizar.ejecutar(new ComandoDatosTienda("RD MOTORS", "", "   ", null, "\t"), admin);

        assertThat(datos.getNit()).isNull();
        assertThat(datos.getDireccion()).isNull();
        assertThat(datos.getTelefono()).isNull();
        assertThat(datos.getMensajePie()).isNull();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    @DisplayName("sin nombre comercial no se guarda")
    void nombreObligatorio(String nombre) {
        assertThatThrownBy(() -> actualizar.ejecutar(new ComandoDatosTienda(nombre, null, null, null, null), admin))
                .isInstanceOf(ReglaDeNegocioException.class)
                .hasMessageContaining("nombre comercial es obligatorio");
        assertThat(tienda.vecesGuardada).isZero();
    }

    static Stream<Arguments> largos() {
        Function<String, ComandoDatosTienda> nombre = t -> new ComandoDatosTienda(t, null, null, null, null);
        Function<String, ComandoDatosTienda> nit = t -> new ComandoDatosTienda("RD", t, null, null, null);
        Function<String, ComandoDatosTienda> direccion = t -> new ComandoDatosTienda("RD", null, t, null, null);
        Function<String, ComandoDatosTienda> telefono = t -> new ComandoDatosTienda("RD", null, null, t, null);
        Function<String, ComandoDatosTienda> mensaje = t -> new ComandoDatosTienda("RD", null, null, null, t);
        return Stream.of(
                Arguments.of("El nombre comercial", 80, nombre),
                Arguments.of("El NIT", 30, nit),
                Arguments.of("La dirección", 120, direccion),
                Arguments.of("El teléfono", 40, telefono),
                Arguments.of("El mensaje al pie", 160, mensaje));
    }

    @ParameterizedTest(name = "{0}: hasta {1} caracteres")
    @MethodSource("largos")
    @DisplayName("cada campo tiene su largo máximo, y justo en el máximo pasa")
    void largoMaximo(String campo, int maximo, Function<String, ComandoDatosTienda> con) {
        assertThat(actualizar.ejecutar(con.apply("x".repeat(maximo)), admin)).isNotNull();

        assertThatThrownBy(() -> actualizar.ejecutar(con.apply("x".repeat(maximo + 1)), admin))
                .isInstanceOf(ReglaDeNegocioException.class)
                .hasMessage(campo + " es muy largo: máximo " + maximo + " caracteres");
    }

    @Test
    @DisplayName("si un campo no pasa, no cambia ninguno: ni el nombre que venía bien")
    void todoONada() {
        actualizar.ejecutar(completos(), admin);

        assertThatThrownBy(() -> actualizar.ejecutar(
                new ComandoDatosTienda("Otro nombre", "9".repeat(31), null, null, null), admin))
                .isInstanceOf(ReglaDeNegocioException.class);

        assertThat(tienda.actuales().getNombreComercial()).isEqualTo("RD Motors Almacén");
        assertThat(tienda.actuales().getDireccion()).isEqualTo("Calle 10 # 5-20, Sincelejo");
    }

    @Test
    @DisplayName("mandar dos veces lo mismo deja el mismo estado")
    void repetirEsIdempotente() {
        DatosTienda primera = actualizar.ejecutar(completos(), admin);
        String nombre = primera.getNombreComercial();
        String nit = primera.getNit();
        String pie = primera.getMensajePie();

        DatosTienda segunda = actualizar.ejecutar(completos(), admin);

        assertThat(segunda.getNombreComercial()).isEqualTo(nombre);
        assertThat(segunda.getNit()).isEqualTo(nit);
        assertThat(segunda.getMensajePie()).isEqualTo(pie);
        assertThat(segunda.getId()).isEqualTo(DatosTienda.ID);
    }

    @Test
    @DisplayName("los datos de la tienda los cambia el administrador: el cajero no, y nada cambia (spec 0004)")
    void elCajeroNo() {
        assertThatThrownBy(() -> actualizar.ejecutar(completos(), ActoresDePrueba.cajero()))
                .isInstanceOf(NoPermitidoException.class);
        assertThat(tienda.vecesGuardada).isZero();
    }
}

package com.workshopmanagement.rdmotors.compras.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.workshopmanagement.rdmotors.compartido.dominio.Dinero;
import com.workshopmanagement.rdmotors.compartido.dominio.ReglaDeNegocioException;
import com.workshopmanagement.rdmotors.inventario.dominio.Categoria;
import com.workshopmanagement.rdmotors.inventario.dominio.Producto;
import com.workshopmanagement.rdmotors.inventario.dominio.Variante;

/** La regla de "este repuesto coincide con lo buscado" (spec 0002, RF-025 y RF-026). */
class BusquedaDeRepuestoTest {

    private final Variante inoki = Variante.nueva(
            Producto.nuevo("FILTRO ACEITE", Categoria.nueva("PRUEBAS", 99), "PULSAR NS 200/FI/AS 200"),
            "352B59K", "INOKI", Dinero.de(6_000), 5);

    private boolean coincide(String texto) {
        return BusquedaDeRepuesto.de(texto).coincideCon(inoki);
    }

    @Test
    @DisplayName("coincide por código, nombre, marca o aplicación, con parte del texto")
    void porCadaCampo() {
        assertThat(coincide("59K")).as("código").isTrue();
        assertThat(coincide("aceite")).as("nombre").isTrue();
        assertThat(coincide("inok")).as("marca").isTrue();
        assertThat(coincide("pulsar")).as("aplicación").isTrue();
        assertThat(coincide("pastillas")).isFalse();
    }

    @Test
    @DisplayName("sin distinguir mayúsculas ni tildes, en los dos sentidos")
    void sinMayusculasNiTildes() {
        Variante bujia = Variante.nueva(Producto.nuevo("BUJÍA ÑANDÚ", Categoria.nueva("PRUEBAS", 99), null),
                "B-1", "NGK", Dinero.de(9_000), 3);

        assertThat(coincide("FiLtRo AcEiTe")).isTrue();
        assertThat(BusquedaDeRepuesto.de("bujía ñandú").coincideCon(bujia)).isTrue();
        assertThat(BusquedaDeRepuesto.de("bujia nandu").coincideCon(bujia)).as("sin tildes encuentra con tildes").isTrue();
        assertThat(coincide("ACÉITE")).as("con tilde encuentra sin tilde").isTrue();
        assertThat(BusquedaDeRepuesto.de("  Bujía ").normalizado()).isEqualTo("bujia");
    }

    @Test
    @DisplayName("un repuesto sin aplicación no revienta al comparar")
    void sinAplicacion() {
        Variante sinAplicacion = Variante.nueva(Producto.nuevo("CADENA", Categoria.nueva("PRUEBAS", 99), null),
                "C-1", "DID", Dinero.de(50_000), 2);

        assertThat(BusquedaDeRepuesto.de("pulsar").coincideCon(sinAplicacion)).isFalse();
    }

    @Test
    @DisplayName("vacío o solo espacios es sin búsqueda; lo demás se recorta")
    void vacioEsSinBusqueda() {
        assertThat(BusquedaDeRepuesto.de(null)).isNull();
        assertThat(BusquedaDeRepuesto.de("   ")).isNull();
        assertThat(BusquedaDeRepuesto.de("  inoki ").texto()).isEqualTo("inoki");
    }

    @Test
    @DisplayName("una búsqueda de más de 80 caracteres se rechaza")
    void topeDeLargo() {
        assertThat(BusquedaDeRepuesto.de("x".repeat(80)).texto()).hasSize(80);
        assertThatThrownBy(() -> BusquedaDeRepuesto.de("x".repeat(81)))
                .isInstanceOf(ReglaDeNegocioException.class)
                .hasMessageContaining("80");
    }
}

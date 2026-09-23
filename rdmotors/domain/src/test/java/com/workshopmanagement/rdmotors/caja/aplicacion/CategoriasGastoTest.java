package com.workshopmanagement.rdmotors.caja.aplicacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.workshopmanagement.rdmotors.caja.dominio.CategoriaGasto;
import com.workshopmanagement.rdmotors.caja.dominio.NaturalezaGasto;
import com.workshopmanagement.rdmotors.compartido.ActoresDePrueba;
import com.workshopmanagement.rdmotors.compartido.Falsos;
import com.workshopmanagement.rdmotors.compartido.dominio.Actor;
import com.workshopmanagement.rdmotors.compartido.dominio.ReglaDeNegocioException;

/** Categorías de gasto propias (spec 0006, H11 y RF-002a). */
class CategoriasGastoTest {

    private final Actor admin = ActoresDePrueba.administrador();

    private Falsos.CategoriasGastoEnMemoria categorias;
    private RegistrarCategoriaGasto registrar;
    private ActualizarCategoriaGasto actualizar;
    private DesactivarCategoriaGasto desactivar;

    @BeforeEach
    void preparar() {
        categorias = new Falsos.CategoriasGastoEnMemoria();
        registrar = new RegistrarCategoriaGasto(categorias);
        actualizar = new ActualizarCategoriaGasto(categorias);
        desactivar = new DesactivarCategoriaGasto(categorias);
        categorias.sembrar(CategoriaGasto.nueva("Papelería", NaturalezaGasto.GASTO));
    }

    @Test
    @DisplayName("se crea Publicidad como gasto, activa, con los espacios de sobra limpios")
    void crea() {
        CategoriaGasto publicidad = registrar.ejecutar("  Publicidad   en  redes ", NaturalezaGasto.GASTO, admin);

        assertThat(publicidad.getNombre()).isEqualTo("Publicidad en redes");
        assertThat(publicidad.getNaturaleza()).isEqualTo(NaturalezaGasto.GASTO);
        assertThat(publicidad.isActiva()).isTrue();
        assertThat(categorias.todas()).extracting(CategoriaGasto::getNombre).contains("Publicidad en redes");
    }

    @Test
    @DisplayName("sin nombre o sin decir si es costo o gasto no se crea")
    void obligatorios() {
        assertThatThrownBy(() -> registrar.ejecutar("  ", NaturalezaGasto.COSTO, admin))
                .hasMessage("El nombre de la categoría es obligatorio");
        assertThatThrownBy(() -> registrar.ejecutar("Fletes de importación", null, admin))
                .hasMessage("Di si la categoría es un costo o un gasto");
    }

    @Test
    @DisplayName("no hay dos con el mismo nombre, sin distinguir mayúsculas ni tildes")
    void nombreRepetido() {
        assertThatThrownBy(() -> registrar.ejecutar("PAPELERIA", NaturalezaGasto.GASTO, admin))
                .isInstanceOf(ReglaDeNegocioException.class)
                .hasMessage("Ya existe la categoría «Papelería»");
        assertThat(categorias.todas()).hasSize(1);
    }

    @Test
    @DisplayName("se renombra; a un nombre que ya tiene otra no; a sí misma con otras mayúsculas sí")
    void renombra() {
        CategoriaGasto aseo = registrar.ejecutar("Aseo", NaturalezaGasto.GASTO, admin);

        assertThat(actualizar.ejecutar(aseo.getId(), "Aseo y cafetería", false, admin).getNombre()).isEqualTo("Aseo y cafetería");
        assertThatThrownBy(() -> actualizar.ejecutar(aseo.getId(), "papeleria", false, admin))
                .hasMessage("Ya existe la categoría «Papelería»");
        assertThat(actualizar.ejecutar(aseo.getId(), "ASEO Y CAFETERÍA", false, admin).getNombre()).isEqualTo("ASEO Y CAFETERÍA");
        assertThat(aseo.getNaturaleza()).isEqualTo(NaturalezaGasto.GASTO);
    }

    @Test
    @DisplayName("SPEC 0007: una categoría dice si se paga cada mes, al crearla y después")
    void mensual() {
        CategoriaGasto arriendo = registrar.ejecutar("Arriendo de bodega", NaturalezaGasto.GASTO, true, admin);
        CategoriaGasto volantes = registrar.ejecutar("Volantes", NaturalezaGasto.GASTO, admin);
        assertThat(arriendo.isMensual()).isTrue();
        assertThat(volantes.isMensual()).isFalse();

        actualizar.ejecutar(arriendo.getId(), "Arriendo de bodega", false, admin);
        actualizar.ejecutar(volantes.getId(), "Volantes del mes", true, admin);

        assertThat(arriendo.isMensual()).isFalse();
        assertThat(volantes.isMensual()).isTrue();
        assertThat(volantes.getNombre()).isEqualTo("Volantes del mes");
    }

    @Test
    @DisplayName("se desactiva y no se borra; desactivarla dos veces no es un error")
    void desactiva() {
        CategoriaGasto flete = registrar.ejecutar("Flete de importación", NaturalezaGasto.COSTO, admin);

        desactivar.ejecutar(flete.getId(), admin);
        desactivar.ejecutar(flete.getId(), admin);

        assertThat(flete.isActiva()).isFalse();
        assertThat(categorias.buscar(flete.getId())).isPresent();
        assertThatThrownBy(() -> registrar.ejecutar("flete de importacion", NaturalezaGasto.COSTO, admin))
                .hasMessage("Ya existe la categoría «Flete de importación», desactivada");
    }
}

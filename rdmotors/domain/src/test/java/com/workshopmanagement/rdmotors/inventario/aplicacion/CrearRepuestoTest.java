package com.workshopmanagement.rdmotors.inventario.aplicacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.workshopmanagement.rdmotors.compartido.ActoresDePrueba;
import com.workshopmanagement.rdmotors.compartido.Falsos;
import com.workshopmanagement.rdmotors.compartido.dominio.Actor;
import com.workshopmanagement.rdmotors.compartido.dominio.Dinero;
import com.workshopmanagement.rdmotors.compartido.dominio.ReglaDeNegocioException;
import com.workshopmanagement.rdmotors.inventario.dominio.Categoria;
import com.workshopmanagement.rdmotors.inventario.dominio.CodigoDuplicadoException;
import com.workshopmanagement.rdmotors.inventario.dominio.Producto;
import com.workshopmanagement.rdmotors.inventario.dominio.Variante;

/**
 * El alta de repuestos, sin base de datos.
 *
 * <p>El caso central es {@link #marcaNuevaReutilizaElConcepto()}: es la decisión §4 del spec 0001
 * y lo único que impide que el catálogo se llene de conceptos duplicados.
 */
class CrearRepuestoTest {

    private final Actor admin = ActoresDePrueba.administrador();

    private Falsos.VariantesEnMemoria variantes;
    private Falsos.ProductosEnMemoria productos;
    private Falsos.CategoriasEnMemoria categorias;
    private CrearRepuesto crearRepuesto;

    private Categoria filtros;

    @BeforeEach
    void preparar() {
        variantes = new Falsos.VariantesEnMemoria();
        productos = new Falsos.ProductosEnMemoria();
        categorias = new Falsos.CategoriasEnMemoria();
        crearRepuesto = new CrearRepuesto(variantes, productos, categorias);

        filtros = categorias.sembrar(Categoria.nueva("FILTROS", 11));
    }

    // ── Concepto nuevo ───────────────────────────────────────────────────────

    @Test
    @DisplayName("un repuesto que no se parece a nada crea su propio concepto")
    void conceptoNuevo() {
        Variante creada = crearRepuesto.ejecutar(ComandoCrearRepuesto.conConceptoNuevo(
                "FILTRO ACEITE", filtros.getId(), "PULSAR NS 200/FI/AS 200-DUKE 200",
                "352B59K", "INOKI", Dinero.de(6_000), 5), admin);

        assertThat(creada.getCodigo()).isEqualTo("352B59K");
        assertThat(creada.getMarcaRepuesto()).isEqualTo("INOKI");
        assertThat(creada.getProducto().getNombre()).isEqualTo("FILTRO ACEITE");
        assertThat(creada.getProducto().getCategoria()).isEqualTo(filtros);
        // El texto del proveedor se guarda verbatim: es la fuente para normalizar modelos después.
        assertThat(creada.getProducto().getAplicacionOriginal())
                .isEqualTo("PULSAR NS 200/FI/AS 200-DUKE 200");
    }

    @Test
    @DisplayName("nace sin stock y SIN costo — null, nunca cero")
    void naceSinStockNiCosto() {
        Variante creada = crearRepuesto.ejecutar(ComandoCrearRepuesto.conConceptoNuevo(
                "FILTRO ACEITE", filtros.getId(), null, "352B59K", "INOKI", Dinero.de(6_000), 5), admin);

        assertThat(creada.getStock()).isZero();
        assertThat(creada.getCostoPromedio()).isNull();
    }

    @Test
    @DisplayName("el código se normaliza a mayúsculas y sin espacios sobrantes")
    void normalizaElCodigo() {
        Variante creada = crearRepuesto.ejecutar(ComandoCrearRepuesto.conConceptoNuevo(
                "FILTRO ACEITE", filtros.getId(), null, "  352b59k  ", "INOKI",
                Dinero.de(6_000), 5), admin);

        assertThat(creada.getCodigo()).isEqualTo("352B59K");
    }

    // ── LA DECISIÓN §4: reutilizar el concepto ───────────────────────────────

    @Test
    @DisplayName("una marca nueva REUTILIZA el concepto en vez de duplicarlo")
    void marcaNuevaReutilizaElConcepto() {
        Variante inoki = crearRepuesto.ejecutar(ComandoCrearRepuesto.conConceptoNuevo(
                "FILTRO ACEITE", filtros.getId(), "PULSAR NS 200", "352B59K", "INOKI",
                Dinero.de(6_000), 5), admin);

        UUID conceptoId = inoki.getProducto().getId();

        Variante factory = crearRepuesto.ejecutar(ComandoCrearRepuesto.sobreConceptoExistente(
                conceptoId, "370P2NN", "FACTORY", Dinero.de(12_000), 5), admin);

        // Mismo concepto, dos marcas. Si esto falla, el catálogo se llena de conceptos repetidos:
        // la compatibilidad vehicular se escribe dos veces y diverge, y el cajero que busca
        // "filtro pulsar" recibe dos resultados sueltos en vez de uno con dos precios.
        assertThat(factory.getProducto().getId()).isEqualTo(conceptoId);
        assertThat(productos.buscarPorNombre("FILTRO ACEITE")).hasSize(1);

        // Y son dos repuestos vendibles distintos, con precios distintos
        assertThat(factory.getId()).isNotEqualTo(inoki.getId());
        assertThat(factory.getPrecio()).isEqualTo(Dinero.de(12_000));
        assertThat(inoki.getPrecio()).isEqualTo(Dinero.de(6_000));
    }

    @Test
    @DisplayName("apuntar a un concepto que no existe se rechaza")
    void conceptoInexistente() {
        assertThatThrownBy(() -> crearRepuesto.ejecutar(
                ComandoCrearRepuesto.sobreConceptoExistente(
                        UUID.randomUUID(), "370P2NN", "FACTORY", Dinero.de(12_000), 5), admin))
                .isInstanceOf(ReglaDeNegocioException.class)
                .hasMessageContaining("no existe");
    }

    // ── Código único ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("código repetido se rechaza diciendo CUÁL repuesto lo tiene")
    void codigoDuplicado() {
        crearRepuesto.ejecutar(ComandoCrearRepuesto.conConceptoNuevo(
                "FILTRO ACEITE", filtros.getId(), null, "352B59K", "INOKI", Dinero.de(6_000), 5), admin);

        assertThatThrownBy(() -> crearRepuesto.ejecutar(ComandoCrearRepuesto.conConceptoNuevo(
                "PASTILLAS FRENO", filtros.getId(), null, "352B59K", "CBI", Dinero.de(24_000), 4), admin))
                .isInstanceOf(CodigoDuplicadoException.class)
                // Sin el nombre, el administrador no sabe si tecleó mal o si ya lo había creado.
                .hasMessageContaining("FILTRO ACEITE")
                .hasMessageContaining("352B59K");
    }

    @Test
    @DisplayName("el duplicado se detecta aunque venga en minúsculas")
    void codigoDuplicadoIgnorandoMayusculas() {
        crearRepuesto.ejecutar(ComandoCrearRepuesto.conConceptoNuevo(
                "FILTRO ACEITE", filtros.getId(), null, "352B59K", "INOKI", Dinero.de(6_000), 5), admin);

        assertThatThrownBy(() -> crearRepuesto.ejecutar(ComandoCrearRepuesto.conConceptoNuevo(
                "OTRA COSA", filtros.getId(), null, "352b59k", "CBI", Dinero.de(24_000), 4), admin))
                .isInstanceOf(CodigoDuplicadoException.class);
    }

    @Test
    @DisplayName("nada se guarda cuando el código está repetido")
    void duplicadoNoDejaBasura() {
        crearRepuesto.ejecutar(ComandoCrearRepuesto.conConceptoNuevo(
                "FILTRO ACEITE", filtros.getId(), null, "352B59K", "INOKI", Dinero.de(6_000), 5), admin);

        try {
            crearRepuesto.ejecutar(ComandoCrearRepuesto.conConceptoNuevo(
                    "PASTILLAS FRENO", filtros.getId(), null, "352B59K", "CBI",
                    Dinero.de(24_000), 4), admin);
        } catch (CodigoDuplicadoException esperada) {
            // El código se valida ANTES de crear el concepto: si no, quedaría un
            // "PASTILLAS FRENO" huérfano sin ningún repuesto colgando.
        }

        assertThat(productos.buscarPorNombre("PASTILLAS")).isEmpty();
    }

    // ── El comando se defiende solo ──────────────────────────────────────────

    @Test
    @DisplayName("mandar concepto existente Y nuevo a la vez se rechaza")
    void conceptoAmbiguo() {
        assertThatThrownBy(() -> new ComandoCrearRepuesto(
                UUID.randomUUID(), "FILTRO ACEITE", filtros.getId(), null,
                "352B59K", "INOKI", Dinero.de(6_000), 5))
                .isInstanceOf(ReglaDeNegocioException.class)
                .hasMessageContaining("uno u otro");
    }

    @Test
    @DisplayName("no mandar ningún concepto se rechaza")
    void sinConcepto() {
        assertThatThrownBy(() -> new ComandoCrearRepuesto(
                null, null, filtros.getId(), null, "352B59K", "INOKI", Dinero.de(6_000), 5))
                .isInstanceOf(ReglaDeNegocioException.class)
                .hasMessageContaining("Falta el concepto");
    }

    @Test
    @DisplayName("sin código no se crea nada")
    void sinCodigo() {
        assertThatThrownBy(() -> ComandoCrearRepuesto.conConceptoNuevo(
                "FILTRO ACEITE", filtros.getId(), null, "  ", "INOKI", Dinero.de(6_000), 5))
                .isInstanceOf(ReglaDeNegocioException.class)
                .hasMessageContaining("código");
    }

    @Test
    @DisplayName("una categoría inexistente se rechaza")
    void categoriaInexistente() {
        assertThatThrownBy(() -> crearRepuesto.ejecutar(ComandoCrearRepuesto.conConceptoNuevo(
                "FILTRO ACEITE", UUID.randomUUID(), null, "352B59K", "INOKI",
                Dinero.de(6_000), 5), admin))
                .isInstanceOf(ReglaDeNegocioException.class)
                .hasMessageContaining("categoría");
    }

    @Test
    @DisplayName("spec 0005, RF-009: un concepto nuevo sin categoría no se crea, y no queda nada guardado")
    void categoriaObligatoria() {
        assertThatThrownBy(() -> crearRepuesto.ejecutar(ComandoCrearRepuesto.conConceptoNuevo(
                "ALGO RARO", null, null, "XYZ-1", "IMPORTADO", Dinero.de(5_000), 3), admin))
                .isInstanceOf(ReglaDeNegocioException.class)
                .hasMessage(Producto.SIN_CATEGORIA);

        assertThat(variantes.buscarPorCodigo("XYZ-1")).isEmpty();
        assertThat(productos.buscarPorNombre("ALGO RARO")).isEmpty();
    }

    @Test
    @DisplayName("spec 0005, RF-009: una marca nueva de un concepto existente no pide categoría: hereda la del concepto")
    void marcaNuevaHeredaLaCategoria() {
        Variante inoki = crearRepuesto.ejecutar(ComandoCrearRepuesto.conConceptoNuevo(
                "FILTRO ACEITE", filtros.getId(), null, "352B59K", "INOKI", Dinero.de(6_000), 5), admin);

        Variante factory = crearRepuesto.ejecutar(ComandoCrearRepuesto.sobreConceptoExistente(
                inoki.getProducto().getId(), "370P2NN", "FACTORY", Dinero.de(12_000), 5), admin);

        assertThat(factory.getProducto().getCategoria()).isEqualTo(filtros);
    }

    @Test
    @DisplayName("el repuesto creado queda buscable por su código")
    void quedaBuscable() {
        crearRepuesto.ejecutar(ComandoCrearRepuesto.conConceptoNuevo(
                "FILTRO ACEITE", filtros.getId(), null, "352B59K", "INOKI", Dinero.de(6_000), 5), admin);

        assertThat(variantes.buscarPorCodigo("352B59K")).isPresent();
    }

    @Test
    @DisplayName("un concepto sembrado antes se puede reutilizar")
    void reutilizaConceptoSembrado() {
        Producto concepto = productos.sembrar(
                Producto.nuevo("FILTRO AIRE ELEMENTO", filtros, "CBF125"));

        Variante creada = crearRepuesto.ejecutar(ComandoCrearRepuesto.sobreConceptoExistente(
                concepto.getId(), "351CBF5C", "IMPORTADO", Dinero.de(7_500), 5), admin);

        assertThat(creada.getProducto().getId()).isEqualTo(concepto.getId());
        assertThat(productos.buscarPorNombre("FILTRO AIRE")).hasSize(1);
    }
}

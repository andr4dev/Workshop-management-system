package com.workshopmanagement.rdmotors.inventario.aplicacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.workshopmanagement.rdmotors.compartido.Falsos;
import com.workshopmanagement.rdmotors.compartido.dominio.Dinero;
import com.workshopmanagement.rdmotors.compartido.dominio.ReglaDeNegocioException;
import com.workshopmanagement.rdmotors.inventario.dominio.ConsultaInventario;
import com.workshopmanagement.rdmotors.inventario.dominio.ConteoCategoria;
import com.workshopmanagement.rdmotors.inventario.dominio.FiltroCategoria;
import com.workshopmanagement.rdmotors.inventario.dominio.Categoria;
import com.workshopmanagement.rdmotors.inventario.dominio.Producto;
import com.workshopmanagement.rdmotors.inventario.dominio.Variante;

/**
 * El buscador. Lo usa el administrador al capturar una factura, y en la rebanada 2 lo usará el
 * cajero con un cliente enfrente — así que lo que se pruebe aquí protege las dos cosas.
 */
class BuscarRepuestosTest {

    private Falsos.VariantesEnMemoria variantes;
    private BuscarRepuestos buscar;

    private Variante filtroInoki;
    private Categoria filtros;

    @BeforeEach
    void preparar() {
        variantes = new Falsos.VariantesEnMemoria();
        buscar = new BuscarRepuestos(variantes);

        filtros = Categoria.nueva("FILTROS", 1);
        Producto filtroAceite = Producto.nuevo("FILTRO ACEITE", filtros,
                "PULSAR NS 200/FI/AS 200-DUKE 200");
        filtroInoki = variantes.sembrar(
                Variante.nueva(filtroAceite, "352B59K", "INOKI", Dinero.de(6_000), 5));
        variantes.sembrar(
                Variante.nueva(filtroAceite, "370P2NN", "FACTORY", Dinero.de(12_000), 5));

        Producto pastillas = Producto.nuevo("PASTILLAS FRENO DELANTERA", Categoria.nueva("FRENOS", 8),
                "AK150 RTX UNISHOCK");
        variantes.sembrar(
                Variante.nueva(pastillas, "152RTX2B", "CBI", Dinero.de(24_000), 4));
    }

    // ── Por código: el campo que hace de buscador (RF-007b) ──────────────────

    @Test
    @DisplayName("código exacto encuentra el repuesto")
    void porCodigoExacto() {
        var encontrado = buscar.porCodigoExacto("352B59K");

        assertThat(encontrado).isPresent();
        assertThat(encontrado.get().nombre()).isEqualTo("FILTRO ACEITE");
        assertThat(encontrado.get().marcaRepuesto()).isEqualTo("INOKI");
        assertThat(encontrado.get().precio()).isEqualTo(Dinero.de(6_000));
    }

    @Test
    @DisplayName("código exacto tolera minúsculas y espacios: se copia de una factura")
    void porCodigoNormaliza() {
        assertThat(buscar.porCodigoExacto("  352b59k ")).isPresent();
    }

    @Test
    @DisplayName("código que no existe devuelve vacío — y eso habilita crear con ese código")
    void porCodigoInexistente() {
        // No es un error: es la señal de que el borde debe ofrecer crear el repuesto
        // con el código ya puesto, para no teclearlo dos veces (RF-007b).
        assertThat(buscar.porCodigoExacto("NO-EXISTE")).isEmpty();
    }

    @Test
    @DisplayName("código vacío no revienta")
    void porCodigoVacio() {
        assertThat(buscar.porCodigoExacto(null)).isEmpty();
        assertThat(buscar.porCodigoExacto("   ")).isEmpty();
    }

    // ── Por texto ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("busca por nombre del repuesto")
    void porNombre() {
        assertThat(buscar.porTexto("filtro"))
                .hasSize(2)
                .allMatch(r -> r.nombre().equals("FILTRO ACEITE"));
    }

    @Test
    @DisplayName("busca por marca del repuesto")
    void porMarca() {
        assertThat(buscar.porTexto("inoki"))
                .singleElement()
                .satisfies(r -> assertThat(r.codigo()).isEqualTo("352B59K"));
    }

    @Test
    @DisplayName("busca por el texto de aplicación: así llega el cliente, diciendo su moto")
    void porAplicacion() {
        assertThat(buscar.porTexto("pulsar")).hasSize(2);
        assertThat(buscar.porTexto("AK150"))
                .singleElement()
                .satisfies(r -> assertThat(r.nombre()).isEqualTo("PASTILLAS FRENO DELANTERA"));
    }

    @Test
    @DisplayName("respeta el límite: teclear dos letras no puede traer miles de filas")
    void respetaElLimite() {
        assertThat(buscar.porTexto("filtro", 1)).hasSize(1);
    }

    @Test
    @DisplayName("texto vacío devuelve lista vacía, no todo el catálogo")
    void textoVacio() {
        assertThat(buscar.porTexto("")).isEmpty();
        assertThat(buscar.porTexto(null)).isEmpty();
    }

    @Test
    @DisplayName("sin coincidencias devuelve lista vacía")
    void sinCoincidencias() {
        assertThat(buscar.porTexto("bujia")).isEmpty();
    }

    // ── RF-013: null no es cero ──────────────────────────────────────────────

    @Test
    @DisplayName("un repuesto sin comprar reporta costo DESCONOCIDO, no cero")
    void costoDesconocidoNoEsCero() {
        var encontrado = buscar.porCodigoExacto("352B59K").orElseThrow();

        // Un cero inventado se lee como un hecho y haría reportar 100% de margen.
        assertThat(encontrado.costoPromedio()).isNull();
        assertThat(encontrado.costoDesconocido()).isTrue();
    }

    @Test
    @DisplayName("tras comprarlo, el costo aparece con sus decimales")
    void costoConocidoTrasComprar() {
        filtroInoki.reponerPorCompra(15, Dinero.de(200_000).dividirEntre(15));

        var encontrado = buscar.porCodigoExacto("352B59K").orElseThrow();

        assertThat(encontrado.costoDesconocido()).isFalse();
        assertThat(encontrado.costoPromedio()).isEqualByComparingTo("13333.3333");
        assertThat(encontrado.stock()).isEqualTo(15);
    }

    @Test
    @DisplayName("marca el stock bajo para la alerta")
    void marcaStockBajo() {
        assertThat(buscar.porCodigoExacto("352B59K").orElseThrow().stockBajo()).isTrue();

        filtroInoki.reponerPorCompra(20, new BigDecimal("2977"));

        assertThat(buscar.porCodigoExacto("352B59K").orElseThrow().stockBajo()).isFalse();
    }

    // ── La ficha: lo que necesita el formulario de corregir ──────────────────

    @Test
    @DisplayName("trae la categoría y el stock mínimo: sin ellos, corregir la ficha los borraba")
    void traeLoQueNecesitaCorregirLaFicha() {
        filtroInoki.corregirFicha("352B59K", "INOKI", Dinero.de(6_000), 12);

        var encontrado = buscar.porId(filtroInoki.getId()).orElseThrow();

        // El formulario arranca con estos valores y los reenvía al guardar. Si no llegaran, arrancaría
        // con "Sin clasificar" y 5, y guardar sin tocar nada borraría los datos reales.
        assertThat(encontrado.categoriaId()).isEqualTo(filtros.getId());
        assertThat(encontrado.categoria()).isEqualTo("FILTROS");
        assertThat(encontrado.stockMinimo()).isEqualTo(12);
    }

    @Test
    @DisplayName("por id: uno que no existe devuelve vacío")
    void porIdInexistente() {
        assertThat(buscar.porId(UUID.randomUUID())).isEmpty();
    }

    // ── Inventario: todo, por páginas ────────────────────────────────────────

    @Test
    @DisplayName("sin texto trae TODO el inventario — al revés que el buscador de la compra")
    void inventarioSinTextoTraeTodo() {
        var pagina = buscar.inventario("", false, 0, 25);

        assertThat(pagina.total()).isEqualTo(3);
        assertThat(pagina.elementos()).hasSize(3);
        // Y el buscador de la compra sigue sin traer nada con texto vacío: son dos gestos distintos
        assertThat(buscar.porTexto("")).isEmpty();
    }

    @Test
    @DisplayName("ordena por nombre y luego por marca: las marcas de un mismo repuesto quedan juntas")
    void inventarioOrdenado() {
        assertThat(buscar.inventario(null, false, 0, 25).elementos())
                .extracting(RepuestoEncontrado::codigo)
                .containsExactly("370P2NN", "352B59K", "152RTX2B");
    }

    @Test
    @DisplayName("en el inventario el texto también encuentra por código parcial")
    void inventarioPorCodigoParcial() {
        assertThat(buscar.inventario("rtx2", false, 0, 25).elementos())
                .singleElement()
                .satisfies(r -> assertThat(r.codigo()).isEqualTo("152RTX2B"));
    }

    @Test
    @DisplayName("pagina y cuenta el total de todas las páginas, no solo el de esta")
    void inventarioPagina() {
        var primera = buscar.inventario("", false, 0, 2);
        var segunda = buscar.inventario("", false, 1, 2);

        assertThat(primera.elementos()).hasSize(2);
        assertThat(segunda.elementos()).hasSize(1);
        assertThat(primera.total()).isEqualTo(3);
        assertThat(primera.totalPaginas()).isEqualTo(2);
    }

    @Test
    @DisplayName("solo stock bajo deja fuera los que tienen suficiente")
    void inventarioSoloStockBajo() {
        filtroInoki.reponerPorCompra(20, new BigDecimal("2977"));

        assertThat(buscar.inventario("", true, 0, 25).elementos())
                .extracting(RepuestoEncontrado::codigo)
                .doesNotContain("352B59K")
                .hasSize(2);
    }

    @Test
    @DisplayName("parámetros absurdos de la URL se sanean en vez de reventar")
    void inventarioSaneaParametros() {
        var pagina = buscar.inventario("  ", false, -3, 100_000);

        assertThat(pagina.numero()).isZero();
        assertThat(pagina.tamano()).isEqualTo(BuscarRepuestos.TAMANO_MAXIMO_PAGINA);
        assertThat(pagina.total()).isEqualTo(3);

        assertThat(buscar.inventario("", false, 0, 0).tamano()).isEqualTo(1);
    }

    // ── Por categoría (spec 0005) ────────────────────────────────────────────

    private static ConsultaInventario porCategoria(String texto, FiltroCategoria categoria, boolean stockPrimero) {
        return new ConsultaInventario(texto, false, categoria, stockPrimero);
    }

    /** Un repuesto creado antes de que la categoría fuera obligatoria: así llega de la base. */
    private Variante sinCategoria(String nombre, String codigo) throws Exception {
        Producto concepto = Producto.nuevo(nombre, Categoria.nueva("TEMPORAL", 50), null);
        var campo = Producto.class.getDeclaredField("categoria");
        campo.setAccessible(true);
        campo.set(concepto, null);
        return variantes.sembrar(Variante.nueva(concepto, codigo, "GENERICO", Dinero.de(3_000), 1));
    }

    @Test
    @DisplayName("una categoría deja solo sus repuestos; sin categoría, solo los que no tienen")
    void filtraPorCategoria() throws Exception {
        sinCategoria("TORNILLO VIEJO", "TOR-1");

        assertThat(buscar.inventario(porCategoria("", FiltroCategoria.de(filtros.getId()), false), 0, 25).elementos())
                .extracting(RepuestoEncontrado::codigo).containsExactly("370P2NN", "352B59K");
        assertThat(buscar.inventario(porCategoria("", FiltroCategoria.sinCategoria(), false), 0, 25).elementos())
                .extracting(RepuestoEncontrado::codigo).containsExactly("TOR-1");
        assertThat(buscar.inventario(porCategoria("", FiltroCategoria.todas(), false), 0, 25).total()).isEqualTo(4);
    }

    @Test
    @DisplayName("con stock primero, los agotados van al final; entre los que hay, por nombre y marca")
    void stockPrimero() {
        filtroInoki.reponerPorCompra(3, new BigDecimal("2977"));   // el INOKI tiene; FACTORY y pastillas no

        assertThat(buscar.inventario(porCategoria("", FiltroCategoria.todas(), true), 0, 25).elementos())
                .extracting(RepuestoEncontrado::codigo)
                .containsExactly("352B59K", "370P2NN", "152RTX2B");
        // Sin pedirlo, el orden de siempre: por nombre y marca, con stock o sin él.
        assertThat(buscar.inventario("", false, 0, 25).elementos())
                .extracting(RepuestoEncontrado::codigo)
                .containsExactly("370P2NN", "352B59K", "152RTX2B");
    }

    @Test
    @DisplayName("RF-002: el conteo por categoría es de lo buscado, sin tildes, en orden, sin las vacías, y suma el total")
    void conteoDeLoBuscado() throws Exception {
        sinCategoria("FILTRÓ RARO", "RAR-1");

        var conteo = buscar.conteoPorCategoria("filtro");

        assertThat(conteo).extracting(ConteoCategoria::nombre, ConteoCategoria::repuestos)
                .as("FRENOS no tiene filtros: no sale; el sin categoría va al final")
                .containsExactly(org.assertj.core.groups.Tuple.tuple("FILTROS", 2L),
                        org.assertj.core.groups.Tuple.tuple(null, 1L));
        assertThat(conteo.stream().mapToLong(ConteoCategoria::repuestos).sum())
                .isEqualTo(buscar.inventario("filtro", false, 0, 25).total());
        assertThat(buscar.conteoPorCategoria("")).extracting(ConteoCategoria::nombre)
                .containsExactly("FILTROS", "FRENOS", null);
    }

    @Test
    @DisplayName("pedir una categoría y los sin categoría a la vez se rechaza")
    void categoriaYSinCategoriaNo() {
        assertThatThrownBy(() -> FiltroCategoria.desde(filtros.getId(), true))
                .isInstanceOf(ReglaDeNegocioException.class);
        assertThat(FiltroCategoria.desde(null, false)).isEqualTo(FiltroCategoria.todas());
        assertThat(FiltroCategoria.desde(null, true)).isEqualTo(FiltroCategoria.sinCategoria());
    }

    @Test
    @DisplayName("el valor de un repuesto sin costo es DESCONOCIDO, no cero")
    void valorDesconocidoNoEsCero() {
        filtroInoki.reponerPorCompra(15, Dinero.de(200_000).dividirEntre(15));

        var inoki = buscar.porCodigoExacto("352B59K").orElseThrow();
        var factory = buscar.porCodigoExacto("370P2NN").orElseThrow();

        assertThat(inoki.valor()).isEqualByComparingTo("199999.9995");
        assertThat(factory.valor()).isNull();
    }
}

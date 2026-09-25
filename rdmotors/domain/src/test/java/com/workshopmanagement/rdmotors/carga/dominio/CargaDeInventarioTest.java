package com.workshopmanagement.rdmotors.carga.dominio;

import static com.workshopmanagement.rdmotors.carga.dominio.FacturasDePrueba.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.workshopmanagement.rdmotors.carga.dominio.Problema.Tipo;
import com.workshopmanagement.rdmotors.carga.dominio.Revision.RenglonRevisado;
import com.workshopmanagement.rdmotors.compartido.dominio.Dinero;
import com.workshopmanagement.rdmotors.compartido.dominio.FormaPago;
import com.workshopmanagement.rdmotors.compartido.dominio.ReglaDeNegocioException;
import com.workshopmanagement.rdmotors.inventario.dominio.Categoria;
import com.workshopmanagement.rdmotors.inventario.dominio.Producto;
import com.workshopmanagement.rdmotors.inventario.dominio.Variante;

/** El borrador de una carga y sus reglas (spec 0012, fase 3). */
class CargaDeInventarioTest {

    private static final UUID PROVEEDOR = UUID.randomUUID();
    private static final UUID USUARIO = UUID.randomUUID();
    private static final Instant AHORA = Instant.parse("2026-09-25T15:00:00Z");

    private static CargaDeInventario carga(FacturaLeida factura) {
        return CargaDeInventario.desde(factura, "factura.pdf", PROVEEDOR, marcas(), categorias(), USUARIO, AHORA);
    }

    /** Con los datos de la compra completos: lo que queda por revisar son los renglones y la suma. */
    private static CargaDeInventario lista(FacturaLeida factura) {
        CargaDeInventario carga = carga(factura);
        carga.cambiarDatos(PROVEEDOR, "MAG477", LocalDate.of(2026, 8, 31), FormaPago.EFECTIVO, null);
        return carga;
    }

    private static Revision revisar(CargaDeInventario carga) {
        return carga.revisar(Map.of(), categorias());
    }

    private static RenglonRevisado del(Revision revision, String codigo) {
        return revision.renglones().stream().filter(r -> codigo.equals(r.renglon().getCodigo())).findFirst()
                .orElseThrow();
    }

    private static int posicionDe(CargaDeInventario carga, String codigo) {
        return carga.getRenglones().stream().filter(r -> codigo.equals(r.getCodigo())).findFirst().orElseThrow()
                .getPosicion();
    }

    private static List<Tipo> tipos(List<Problema> problemas) {
        return problemas.stream().map(Problema::tipo).toList();
    }

    private static Variante existente(String codigo, long precio) {
        Producto producto = Producto.nuevo("KIT EMPAQUES YA CREADO", Categoria.nueva("EMPAQUES Y SELLOS", 2), null);
        return Variante.nueva(producto, codigo, "INOKI", Dinero.de(precio), 2);
    }

    // ── La plata de cada renglón ─────────────────────────────────────────────

    @Test
    @DisplayName("LA BUJÍA: $308.274 son las 8; cada una cuesta $45.855,7575 con IVA y se sugiere a $66.500")
    void laBujia() {
        RenglonRevisado bujia = del(revisar(carga(jotapartes(bujia()))), "524XRE3IJ");

        assertThat(bujia.costoPorUnidad()).isEqualByComparingTo("45855.7575");
        assertThat(bujia.sugerido()).isEqualTo(Dinero.de(66_500));
        assertThat(bujia.renglon().getPrecioFinal()).isEqualTo(Dinero.de(66_500));
        assertThat(bujia.renglon().isAjustadoAMano()).isFalse();
    }

    @Test
    @DisplayName("LA MARCA Y LA CATEGORÍA SE PROPONEN desde la descripción, y quedan marcadas como propuestas")
    void propuestas() {
        Revision revision = revisar(carga(jotapartes(bujia(), kitEmpaques())));

        RenglonDeCarga bujia = del(revision, "524XRE3IJ").renglon();
        assertThat(bujia.getMarca()).isEqualTo("NGK");
        assertThat(bujia.isMarcaPropuesta()).isTrue();
        assertThat(bujia.getNombre()).isEqualTo("BUJIA IRIDIUM CR7HIX");
        assertThat(bujia.getCategoriaId()).isEqualTo(ELECTRICO);
        assertThat(bujia.isCategoriaPropuesta()).isTrue();

        RenglonRevisado kit = del(revision, "093AKTCLKI");
        assertThat(kit.renglon().getMarca()).isNull();
        assertThat(kit.renglon().getCategoriaId()).isEqualTo(EMPAQUES);
        assertThat(tipos(kit.problemas())).containsExactly(Tipo.SIN_MARCA);
    }

    @Test
    @DisplayName("del Excel, la marca y la categoría escritas se toman tal cual; una categoría que no existe queda vacía")
    void delExcel() {
        CargaDeInventario carga = carga(excel(
                deExcel("fila 2", "a1", "Bujía iridium", 2, 20_000L, "ngk", "Eléctrico"),
                deExcel("fila 3", "A2", "Filtro de aire", 1, 10_000L, "INOKI", "INVENTADA")));

        RenglonDeCarga primero = carga.getRenglones().get(0);
        assertThat(primero.getCodigo()).isEqualTo("A1");
        assertThat(primero.getMarca()).isEqualTo("NGK");
        assertThat(primero.isMarcaPropuesta()).isFalse();
        assertThat(primero.getCategoriaId()).isEqualTo(ELECTRICO);
        assertThat(primero.isCategoriaPropuesta()).isFalse();
        assertThat(carga.getRenglones().get(1).getCategoriaId()).isNull();
        assertThat(carga.getOrigen()).isEqualTo(OrigenCarga.EXCEL);
    }

    // ── Los precios ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("CAMBIAR LA GANANCIA mueve los sugeridos y RESPETA LOS AJUSTADOS A MANO (RF-007)")
    void cambiarLaGanancia() {
        CargaDeInventario carga = carga(jotapartes(bujia(), tensor()));
        carga.ajustarPrecio(posicionDe(carga, "524XRE3IJ"), Dinero.de(68_500));

        carga.cambiarRegla(new ReglaDePrecio(new BigDecimal("19"), new BigDecimal("50"), 100));

        Revision revision = revisar(carga);
        assertThat(del(revision, "524XRE3IJ").renglon().getPrecioFinal()).isEqualTo(Dinero.de(68_500));
        assertThat(del(revision, "524XRE3IJ").renglon().isAjustadoAMano()).isTrue();
        // $39.950 × 1,19 ÷ 5 = $9.508,10; con 45% daba $13.800 y con 50% da $14.262,15 → $14.300.
        assertThat(del(revision, "082T3S").renglon().getPrecioFinal()).isEqualTo(Dinero.de(14_300));
        assertThat(carga.regla().gananciaPct()).isEqualByComparingTo("50");
    }

    @Test
    @DisplayName("un precio ajustado vuelve al sugerido cuando se pide, y no se ajusta a cero")
    void volverAlSugerido() {
        CargaDeInventario carga = carga(jotapartes(bujia()));
        carga.ajustarPrecio(0, Dinero.de(70_000));

        carga.volverAlSugerido(0);

        assertThat(carga.getRenglones().getFirst().getPrecioFinal()).isEqualTo(Dinero.de(66_500));
        assertThat(carga.getRenglones().getFirst().isAjustadoAMano()).isFalse();
        assertThatThrownBy(() -> carga.ajustarPrecio(0, Dinero.CERO)).isInstanceOf(ReglaDeNegocioException.class);
        assertThatThrownBy(() -> carga.ajustarPrecio(0, null)).isInstanceOf(ReglaDeNegocioException.class);
    }

    @Test
    @DisplayName("VENDER A PÉRDIDA se avisa con cuánto, y no impide confirmar")
    void aPerdida() {
        CargaDeInventario carga = lista(jotapartes(bujia()));
        carga.ajustarPrecio(0, Dinero.de(40_000));

        Revision revision = revisar(carga);

        RenglonRevisado bujia = del(revision, "524XRE3IJ");
        assertThat(bujia.avisos()).extracting(Problema::mensaje)
                .containsExactly("Vendes a pérdida: $5.856 por unidad");
        assertThat(bujia.problemas()).isEmpty();
        assertThat(revision.sePuedeConfirmar()).isTrue();
    }

    // ── Lo que no deja confirmar ─────────────────────────────────────────────

    @Test
    @DisplayName("UN RENGLÓN MALO NO DEJA CONFIRMAR, y quitarlo lo desbloquea")
    void renglonMalo() {
        RenglonLeido sinCantidad = renglon("pág. 9", "111X", "TENSOR CADENILLA AK125 INOKI", null, "UND", 10_000,
                "0", 10_000);
        CargaDeInventario carga = lista(jotapartes(bujia(), sinCantidad));

        Revision antes = revisar(carga);
        // Sin cantidad tampoco hay costo por unidad, ni precio que sugerir.
        assertThat(tipos(del(antes, "111X").problemas())).containsExactly(Tipo.CANTIDAD_INVALIDA, Tipo.SIN_PRECIO);
        assertThat(tipos(antes.problemas())).containsExactly(Tipo.RENGLONES_CON_PROBLEMA);
        assertThat(antes.problemas().getFirst().mensaje()).isEqualTo("1 renglón tiene problemas");
        assertThat(antes.sePuedeConfirmar()).isFalse();

        carga.quitar(posicionDe(carga, "111X"));

        Revision despues = revisar(carga);
        assertThat(despues.sePuedeConfirmar()).isTrue();
        assertThat(despues.totales().quitados()).isEqualTo(1);
        assertThat(despues.totales().incluidos()).isEqualTo(1);
    }

    @Test
    @DisplayName("LA SUMA QUE NO CUADRA no deja confirmar, y dice cuánto falta o cuánto sobra")
    void sumaQueNoCuadra() {
        // Los tres suman $354.412; la factura dice $12.943 más: se perdió un renglón.
        Revision faltan = revisar(lista(jotapartes(354_412 + 12_943, bujia(), kitEmpaques(), tensor())));
        Revision sobran = revisar(lista(jotapartes(354_412 - 6_188, bujia(), kitEmpaques(), tensor())));

        assertThat(faltan.problemas()).extracting(Problema::mensaje)
                .contains("Faltan $12.943: el archivo suma $354.412 y la factura dice $367.355");
        assertThat(faltan.totales().diferencia()).isEqualTo(Dinero.de(12_943));
        assertThat(faltan.sePuedeConfirmar()).isFalse();
        assertThat(sobran.problemas()).extracting(Problema::mensaje)
                .contains("Sobran $6.188: el archivo suma $354.412 y la factura dice $348.224");
    }

    @Test
    @DisplayName("QUITAR UN RENGLÓN NO ARREGLA LA SUMA: lo leído se compara entero, y la compra lleva solo lo que entra")
    void quitarNoArreglaLaSuma() {
        CargaDeInventario carga = lista(jotapartes(bujia(), kitEmpaques(), tensor()));

        carga.cambiarMarca(List.of(posicionDe(carga, "093AKTCLKI")), "NACIONAL");
        carga.quitar(posicionDe(carga, "082T3S"));

        Revision.Totales totales = revisar(carga).totales();
        assertThat(totales.sumaLeida()).isEqualTo(Dinero.de(354_412));
        assertThat(totales.diferencia()).isEqualTo(Dinero.CERO);
        assertThat(totales.subtotalIncluido()).isEqualTo(Dinero.de(314_462));
        // $314.462 × 19% = $59.747,78 → $59.748.
        assertThat(totales.iva()).isEqualTo(Dinero.de(59_748));
        assertThat(totales.totalConIva()).isEqualTo(Dinero.de(374_210));
        assertThat(revisar(carga).sePuedeConfirmar()).isTrue();
    }

    @Test
    @DisplayName("LOS TOTALES de tres renglones de la MAG477: sub-total, IVA y lo que se pagó, al peso")
    void totales() {
        Revision.Totales totales = revisar(lista(jotapartes(bujia(), kitEmpaques(), tensor()))).totales();

        assertThat(totales.subtotalIncluido()).isEqualTo(Dinero.de(354_412));
        assertThat(totales.iva()).isEqualTo(Dinero.de(67_338));
        assertThat(totales.totalConIva()).isEqualTo(Dinero.de(421_750));
        assertThat(totales.unidades()).isEqualTo(14);
        assertThat(totales.incluidos()).isEqualTo(3);
        assertThat(totales.propuestasSinRevisar()).isEqualTo(3);
    }

    @Test
    @DisplayName("EL MISMO CÓDIGO DOS VECES se marca en los dos, dice dónde está el otro, y no se suman solos")
    void codigoRepetido() {
        RenglonLeido otraBujia = renglon("pág. 27", "524XRE3IJ", "BUJIA IRIDIUM CR7HIX NGK", 8, "UND", 46_993, "18",
                308_274);
        CargaDeInventario carga = lista(jotapartes(bujia(), otraBujia));

        Revision revision = revisar(carga);
        assertThat(revision.renglones()).allSatisfy(r -> assertThat(tipos(r.problemas()))
                .containsExactly(Tipo.CODIGO_REPETIDO));
        assertThat(revision.renglones().getFirst().problemas().getFirst().mensaje()).contains("también en pág. 27");

        carga.quitar(1);

        assertThat(revisar(carga).renglones().getFirst().problemas()).isEmpty();
    }

    @Test
    @DisplayName("UNA CUENTA QUE NO CUADRA dice los números, para corregirla mirando el papel")
    void noCuadra() {
        RenglonLeido malLeida = renglon("pág. 26", "524XRE3IJ", "BUJIA IRIDIUM CR7HIX NGK", 8, "UND", 46_993, "18",
                380_274);

        RenglonRevisado bujia = del(revisar(carga(jotapartes(malLeida))), "524XRE3IJ");

        assertThat(bujia.problemas()).extracting(Problema::mensaje)
                .containsExactly("8 × $46.993 − 18% da $308.274, pero la factura dice $380.274");
    }

    @Test
    @DisplayName("un descuento de 0,18 —una celda de porcentaje de Excel— también cuadra")
    void descuentoEnFraccion() {
        RenglonLeido conFraccion = renglon("fila 2", "524XRE3IJ", "BUJIA IRIDIUM CR7HIX NGK", 8, "UND", 46_993,
                "0.18", 308_274);

        assertThat(del(revisar(carga(jotapartes(conFraccion))), "524XRE3IJ").problemas()).isEmpty();
    }

    @Test
    @DisplayName("CORREGIR LA LECTURA vuelve a sacar el costo y el sugerido")
    void corregirLectura() {
        RenglonLeido cantidadMalLeida = renglon("pág. 26", "524XRE3IJ", "BUJIA IRIDIUM CR7HIX NGK", 80, "UND",
                46_993, "18", 308_274);
        CargaDeInventario carga = carga(jotapartes(cantidadMalLeida));
        assertThat(tipos(revisar(carga).renglones().getFirst().problemas())).contains(Tipo.NO_CUADRA);

        carga.corregirLectura(0, "524xre3ij", "BUJIA IRIDIUM CR7HIX NGK", 8, Dinero.de(308_274), Dinero.de(46_993),
                new BigDecimal("18"));

        RenglonRevisado bujia = revisar(carga).renglones().getFirst();
        assertThat(bujia.problemas()).isEmpty();
        assertThat(bujia.renglon().getCodigo()).isEqualTo("524XRE3IJ");
        assertThat(bujia.renglon().getPrecioFinal()).isEqualTo(Dinero.de(66_500));
    }

    @Test
    @DisplayName("LOS DATOS DE LA COMPRA: sin proveedor, fecha o forma de pago no se confirma; la transferencia pide cuenta")
    void datosDeLaCompra() {
        CargaDeInventario carga = CargaDeInventario.desde(excel(deExcel("fila 2", "A1", "FILTRO AIRE", 1, 10_000L,
                "INOKI", "MOTOR")), "carga.xlsx", null, marcas(), categorias(), USUARIO, AHORA);
        carga.fijarSubtotal(Dinero.de(10_000));

        assertThat(tipos(revisar(carga).problemas()))
                .containsExactly(Tipo.SIN_PROVEEDOR, Tipo.SIN_FECHA, Tipo.SIN_FORMA_PAGO);

        carga.cambiarDatos(PROVEEDOR, "FV-1", LocalDate.of(2026, 9, 20), FormaPago.TRANSFERENCIA, null);
        assertThat(tipos(revisar(carga).problemas())).containsExactly(Tipo.SIN_CUENTA);

        carga.cambiarDatos(PROVEEDOR, "FV-1", LocalDate.of(2026, 9, 20), FormaPago.TRANSFERENCIA, UUID.randomUUID());
        assertThat(revisar(carga).sePuedeConfirmar()).isTrue();

        assertThatThrownBy(() -> carga.cambiarDatos(PROVEEDOR, "FV-1", LocalDate.of(2026, 9, 20), FormaPago.EFECTIVO,
                UUID.randomUUID())).isInstanceOf(ReglaDeNegocioException.class).hasMessageContaining("no lleva cuenta");
    }

    @Test
    @DisplayName("DEL EXCEL EL SUB-TOTAL SE ESCRIBE (sin él no se confirma); el que se leyó del PDF no se toca")
    void subtotal() {
        CargaDeInventario deExcel = lista(excel(deExcel("fila 2", "A1", "FILTRO AIRE", 1, 10_000L, "INOKI",
                "MOTOR")));
        assertThat(tipos(revisar(deExcel).problemas())).containsExactly(Tipo.SIN_SUBTOTAL);

        deExcel.fijarSubtotal(Dinero.de(10_000));
        assertThat(revisar(deExcel).sePuedeConfirmar()).isTrue();

        CargaDeInventario delPdf = lista(jotapartes(bujia()));
        assertThat(delPdf.isSubtotalLeido()).isTrue();
        assertThatThrownBy(() -> delPdf.fijarSubtotal(Dinero.de(1))).isInstanceOf(ReglaDeNegocioException.class)
                .hasMessageContaining("se leyó de la factura");
    }

    // ── Marca y categoría, de a varios ───────────────────────────────────────

    @Test
    @DisplayName("PONERLE MARCA A TODOS LOS QUE NO TIENEN —en la MAG477 son 176— sin tocar los que sí")
    void marcaALosQueNoTienen() {
        RenglonLeido otroSinMarca = renglon("pág. 4", "094CBFTA", "EMPAQUE CULATA LAMINA ACERADA CB110", 2, "UND",
                5_000, "0", 10_000);
        CargaDeInventario carga = carga(jotapartes(kitEmpaques(), tensor(), otroSinMarca));

        int cuantos = carga.cambiarMarcaDeLosQueNoTienen("nacional");

        assertThat(cuantos).isEqualTo(2);
        assertThat(carga.getRenglones()).extracting(RenglonDeCarga::getMarca)
                .containsExactly("NACIONAL", "INOKI", "NACIONAL");
        assertThat(carga.getRenglones().get(0).isMarcaPropuesta()).isFalse();
        assertThat(carga.getRenglones().get(1).isMarcaPropuesta()).isTrue();
    }

    @Test
    @DisplayName("cambiar la marca rehace el nombre; aceptar la propuesta solo le quita el sello de propuesta")
    void cambiarLaMarca() {
        CargaDeInventario carga = carga(jotapartes(tensor()));
        assertThat(carga.getRenglones().getFirst().getNombre()).isEqualTo("TENSOR CADENILLA CB110");

        carga.cambiarMarca(List.of(0), "INOKI");
        assertThat(carga.getRenglones().getFirst().isMarcaPropuesta()).isFalse();
        assertThat(carga.getRenglones().getFirst().getNombre()).isEqualTo("TENSOR CADENILLA CB110");

        carga.cambiarMarca(List.of(0), "KOYO");
        assertThat(carga.getRenglones().getFirst().getNombre()).isEqualTo("TENSOR CADENILLA CB110 INOKI");
    }

    @Test
    @DisplayName("la categoría se cambia a varios, o a todos los que no tienen; una desactivada vuelve a pedirse")
    void categoria() {
        RenglonLeido sinCategoria = renglon("pág. 5", "777Q", "COSA RARA INOKI", 1, "UND", 1_000, "0", 1_000);
        CargaDeInventario carga = lista(jotapartes(bujia(), sinCategoria));

        assertThat(carga.cambiarCategoriaDeLosQueNoTienen(FRENOS)).isEqualTo(1);
        carga.cambiarCategoria(List.of(0), MOTOR);

        assertThat(carga.getRenglones()).extracting(RenglonDeCarga::getCategoriaId).containsExactly(MOTOR, FRENOS);
        assertThat(carga.getRenglones()).noneMatch(RenglonDeCarga::isCategoriaPropuesta);

        CategoriasConocidas sinFrenos = new CategoriasConocidas(Map.of(MOTOR, "MOTOR"));
        assertThat(carga.revisar(Map.of(), sinFrenos).renglones().get(1).problemas()).extracting(Problema::mensaje)
                .containsExactly("Esa categoría ya no existe o se desactivó: elige otra");
    }

    // ── Reposiciones ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("UNA REPOSICIÓN no pide marca ni categoría, y avisa si con el costo nuevo su precio deja pérdida")
    void reposicion() {
        CargaDeInventario carga = lista(jotapartes(kitEmpaques()));
        Variante yaEsta = existente("093AKTCLKI", 7_000);

        Revision revision = carga.revisar(Map.of("093AKTCLKI", yaEsta), categorias());

        RenglonRevisado kit = revision.renglones().getFirst();
        assertThat(kit.esReposicion()).isTrue();
        assertThat(kit.problemas()).isEmpty();
        // $6.188 × 1,19 = $7.363,72 por unidad, y el repuesto se vende a $7.000.
        assertThat(kit.avisos()).extracting(Problema::mensaje)
                .containsExactly("Con este costo, el precio que ya tiene ($7.000) deja pérdida: $364 por unidad");
        assertThat(revision.totales().reposiciones()).isEqualTo(1);
        assertThat(revision.totales().propuestasSinRevisar()).isZero();

        carga.aplicarPrecioNuevo(0, true);
        assertThat(carga.revisar(Map.of("093AKTCLKI", yaEsta), categorias()).renglones().getFirst().avisos())
                .isEmpty();
    }

    // ── Estado ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("LO DESCARTADO NO SE EDITA")
    void descartada() {
        CargaDeInventario carga = carga(jotapartes(bujia()));

        carga.descartar(USUARIO, AHORA);

        assertThat(carga.getEstado()).isEqualTo(EstadoCarga.DESCARTADA);
        assertThat(carga.getCerradaEn()).isEqualTo(AHORA);
        assertThatThrownBy(() -> carga.ajustarPrecio(0, Dinero.de(70_000)))
                .isInstanceOf(ReglaDeNegocioException.class).hasMessageContaining("se descartó");
        assertThatThrownBy(() -> carga.cambiarRegla(ReglaDePrecio.porDefecto()))
                .isInstanceOf(ReglaDeNegocioException.class);
        assertThatThrownBy(() -> carga.descartar(USUARIO, AHORA)).isInstanceOf(ReglaDeNegocioException.class);
    }

    @Test
    @DisplayName("un archivo sin renglones, o con más de 3.000, no es una factura")
    void tamanos() {
        List<RenglonLeido> muchos = new ArrayList<>(Collections.nCopies(CargaDeInventario.MAXIMO_RENGLONES + 1,
                bujia()));

        assertThatThrownBy(() -> carga(excel())).isInstanceOf(ReglaDeNegocioException.class)
                .hasMessageContaining("ningún renglón");
        assertThatThrownBy(() -> carga(excel(muchos.toArray(RenglonLeido[]::new))))
                .isInstanceOf(ReglaDeNegocioException.class).hasMessageContaining("Pártelo en dos");
    }

    @Test
    @DisplayName("un renglón que no existe no se edita")
    void renglonQueNoExiste() {
        CargaDeInventario carga = carga(jotapartes(bujia()));

        assertThatThrownBy(() -> carga.ajustarPrecio(7, Dinero.de(1_000))).isInstanceOf(ReglaDeNegocioException.class)
                .hasMessageContaining("no está en la carga");
        assertThatThrownBy(() -> carga.cambiarMarca(List.of(), "NGK")).isInstanceOf(ReglaDeNegocioException.class);
    }
}

package com.workshopmanagement.rdmotors.carga.aplicacion;

import static com.workshopmanagement.rdmotors.carga.dominio.FacturasDePrueba.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.workshopmanagement.rdmotors.carga.dominio.EstadoCarga;
import com.workshopmanagement.rdmotors.carga.dominio.FacturaLeida;
import com.workshopmanagement.rdmotors.carga.dominio.FacturaNoReconocidaException;
import com.workshopmanagement.rdmotors.carga.dominio.FacturaYaCargadaException;
import com.workshopmanagement.rdmotors.carga.dominio.OrigenCarga;
import com.workshopmanagement.rdmotors.carga.dominio.Revision.RenglonRevisado;
import com.workshopmanagement.rdmotors.compartido.Falsos;
import com.workshopmanagement.rdmotors.compartido.dominio.Dinero;
import com.workshopmanagement.rdmotors.compartido.dominio.NoPermitidoException;
import com.workshopmanagement.rdmotors.inventario.dominio.Variante;

/** Subir una factura y armar su pre-carga (spec 0012, H1 y H5). */
class SubirFacturaTest {

    private final EscenarioCargas tienda = new EscenarioCargas();

    private static RenglonRevisado del(DetalleCarga detalle, String codigo) {
        return detalle.revision().renglones().stream().filter(r -> codigo.equals(r.renglon().getCodigo()))
                .findFirst().orElseThrow();
    }

    @Test
    @DisplayName("SUBIR NO TOCA EL INVENTARIO: la pre-carga queda guardada y los repuestos, como estaban")
    void noTocaElInventario() {
        Variante kit = tienda.yaExiste("093AKTCLKI", 9_000, 4);

        DetalleCarga detalle = tienda.subirLaMag();

        assertThat(tienda.cargas.cuantas()).isEqualTo(1);
        assertThat(detalle.carga().getEstado()).isEqualTo(EstadoCarga.BORRADOR);
        assertThat(detalle.carga().getRenglones()).hasSize(3);
        assertThat(kit.getStock()).isEqualTo(4);
        assertThat(kit.getPrecio()).isEqualTo(Dinero.de(9_000));
        assertThat(tienda.variantes.buscarPorCodigo("524XRE3IJ")).isEmpty();
        assertThat(tienda.compras.buscarPorLlave(detalle.carga().getId())).isEmpty();
    }

    @Test
    @DisplayName("EL PROVEEDOR SE ENCUENTRA POR EL NIT IMPRESO, aunque en su ficha tenga puntos y dígito de verificación")
    void proveedorPorNit() {
        DetalleCarga detalle = tienda.subirLaMag();

        assertThat(detalle.carga().getProveedorId()).isEqualTo(tienda.jotapartes.getId());
        assertThat(detalle.proveedor()).isEqualTo("Importadora Jotapartes");
        assertThat(detalle.carga().getNumeroFactura()).isEqualTo("MAG477");
        assertThat(detalle.carga().getSubtotalFactura()).isEqualTo(Dinero.de(354_412));
        assertThat(detalle.carga().isSubtotalLeido()).isTrue();
        assertThat(detalle.carga().getOrigen()).isEqualTo(OrigenCarga.PDF_JOTAPARTES);
        assertThat(detalle.carga().getNombreArchivo()).isEqualTo("fv09005765280152600000477.pdf");
    }

    @Test
    @DisplayName("UN CÓDIGO QUE YA EXISTE es reposición, con su stock y su precio; y se miran todos en UNA consulta")
    void reposicion() {
        tienda.yaExiste("093AKTCLKI", 9_000, 4);

        DetalleCarga detalle = tienda.subirLaMag();

        RenglonRevisado kit = del(detalle, "093AKTCLKI");
        assertThat(kit.esReposicion()).isTrue();
        assertThat(kit.existente().getStock()).isEqualTo(4);
        assertThat(kit.existente().getPrecio()).isEqualTo(Dinero.de(9_000));
        assertThat(del(detalle, "524XRE3IJ").esReposicion()).isFalse();
        assertThat(tienda.variantes.vecesBuscadaPorCodigos).isEqualTo(1);
    }

    @Test
    @DisplayName("la marca y la categoría se proponen con las categorías de la tienda, y con las marcas que ya se usan")
    void propuestas() {
        Variante conMarcaPropia = tienda.yaExiste("ZZ1", 1_000, 0);
        conMarcaPropia.corregirFicha("ZZ1", "MARCA RARA", Dinero.de(1_000), 2);

        DetalleCarga detalle = tienda.subirPdf(jotapartes(bujia(), renglon("pág. 1", "555R", "RETEN CIGUEÑAL "
                + "CB110 MARCA RARA", 1, "UND", 2_000, "0", 2_000)));

        assertThat(del(detalle, "524XRE3IJ").renglon().getCategoriaId()).isEqualTo(tienda.electrico.getId());
        assertThat(del(detalle, "555R").renglon().getMarca()).isEqualTo("MARCA RARA");
        assertThat(del(detalle, "555R").renglon().getCategoriaId()).isEqualTo(tienda.empaques.getId());
    }

    @Test
    @DisplayName("EL CAJERO NO PUEDE: la pre-carga está llena de costos (RF-019)")
    void cajero() {
        tienda.leyendo(jotapartes(bujia()));

        assertThatThrownBy(() -> tienda.subir().ejecutar("f.pdf", new byte[] {1}, tienda.cajero))
                .isInstanceOf(NoPermitidoException.class);
        assertThat(tienda.cargas.cuantas()).isZero();
    }

    @Test
    @DisplayName("UN ARCHIVO QUE NINGÚN LECTOR RECONOCE se rechaza diciendo qué sí se puede subir; el .xls viejo, cómo pasarlo")
    void noReconocido() {
        tienda.leyendo(jotapartes(bujia()));

        assertThatThrownBy(() -> tienda.subir().ejecutar("foto.jpg", new byte[] {1}, tienda.dueno))
                .isInstanceOf(FacturaNoReconocidaException.class)
                .hasMessage("Ese archivo no se puede leer. Sube la factura en PDF, un Excel (.xlsx) o un .csv.");
        assertThatThrownBy(() -> tienda.subir().ejecutar("inventario.xls", new byte[] {1}, tienda.dueno))
                .isInstanceOf(FacturaNoReconocidaException.class).hasMessageContaining("guárdalo como .xlsx");
    }

    @Test
    @DisplayName("un archivo vacío o de más de 5 MB no se lee")
    void tamano() {
        tienda.leyendo(jotapartes(bujia()));

        assertThatThrownBy(() -> tienda.subir().ejecutar("f.pdf", new byte[0], tienda.dueno))
                .isInstanceOf(FacturaNoReconocidaException.class).hasMessageContaining("vacío");
        assertThatThrownBy(() -> tienda.subir().ejecutar("f.pdf", new byte[SubirFactura.TAMANO_MAXIMO + 1],
                tienda.dueno)).isInstanceOf(FacturaNoReconocidaException.class).hasMessageContaining("5 MB");
    }

    @Test
    @DisplayName("LA MISMA FACTURA DOS VECES no: la segunda dice dónde está la primera; descartada, sí se vuelve a subir")
    void mismaFactura() {
        DetalleCarga primera = tienda.subirLaMag();

        assertThatThrownBy(() -> tienda.subirLaMag())
                .isInstanceOf(FacturaYaCargadaException.class)
                .hasMessageContaining("Ya hay una pre-carga de la factura MAG477")
                .satisfies(e -> assertThat(((FacturaYaCargadaException) e).getCargaId())
                        .isEqualTo(primera.carga().getId()));

        tienda.descartar.ejecutar(primera.carga().getId(), tienda.dueno);

        assertThat(tienda.subirLaMag().carga().getId()).isNotEqualTo(primera.carga().getId());
    }

    @Test
    @DisplayName("un Excel no tiene número de factura: se puede subir las veces que haga falta")
    void excelSinNumero() {
        FacturaLeida excel = excel(deExcel("fila 2", "A1", "FILTRO AIRE", 1, 10_000L, "INOKI", "MOTOR"));
        tienda.lectores.add(new Falsos.LectorFalso(".xlsx", excel));

        tienda.subir().ejecutar("a.xlsx", new byte[] {1}, tienda.dueno);
        DetalleCarga segunda = tienda.subir().ejecutar("a.xlsx", new byte[] {1}, tienda.dueno);

        assertThat(tienda.cargas.cuantas()).isEqualTo(2);
        assertThat(segunda.carga().getProveedorId()).isNull();
        assertThat(segunda.carga().getSubtotalFactura()).isNull();
    }

    @Test
    @DisplayName("si nadie tiene ese NIT, el proveedor queda por elegir")
    void sinProveedor() {
        DetalleCarga detalle = tienda.subirPdf(new FacturaLeida(OrigenCarga.PDF_JOTAPARTES, java.util.List.of(bujia()),
                Dinero.de(308_274), null, null, "123456789", "X1", null));

        assertThat(detalle.carga().getProveedorId()).isNull();
        assertThat(detalle.carga().getNitProveedor()).isEqualTo("123456789");
    }
}

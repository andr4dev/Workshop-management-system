package com.workshopmanagement.rdmotors.carga.aplicacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.workshopmanagement.rdmotors.carga.dominio.EstadoCarga;
import com.workshopmanagement.rdmotors.carga.dominio.RenglonDeCarga;
import com.workshopmanagement.rdmotors.carga.dominio.ResumenCarga;
import com.workshopmanagement.rdmotors.compartido.dominio.Dinero;
import com.workshopmanagement.rdmotors.compartido.dominio.FormaPago;
import com.workshopmanagement.rdmotors.compartido.dominio.NoPermitidoException;
import com.workshopmanagement.rdmotors.compartido.dominio.ReglaDeNegocioException;
import com.workshopmanagement.rdmotors.compras.dominio.CuentaPago;
import com.workshopmanagement.rdmotors.inventario.dominio.Categoria;

/** Los gestos sobre la pre-carga, cada uno guardado en el momento (spec 0012, RF-005 a RF-014). */
class EditarCargaTest {

    private final EscenarioCargas tienda = new EscenarioCargas();

    private RenglonDeCarga renglon(DetalleCarga detalle, String codigo) {
        return detalle.carga().getRenglones().stream().filter(r -> codigo.equals(r.getCodigo())).findFirst()
                .orElseThrow();
    }

    @Test
    @DisplayName("CADA GESTO BLOQUEA LA CARGA, se guarda, y devuelve la carga revisada de nuevo")
    void cadaGesto() {
        UUID id = tienda.subirLaMag().carga().getId();

        DetalleCarga detalle = tienda.editar.ajustarPrecio(id, 0, Dinero.de(68_500), tienda.dueno);

        assertThat(tienda.cargas.vecesBuscadaParaModificar).isEqualTo(1);
        assertThat(renglon(detalle, "524XRE3IJ").getPrecioFinal()).isEqualTo(Dinero.de(68_500));
        // Lo guardado es lo que se ve al volver a abrirla, desde otro equipo.
        DetalleCarga otraVez = tienda.consultar.detalle(id, tienda.dueno).orElseThrow();
        assertThat(renglon(otraVez, "524XRE3IJ").getPrecioFinal()).isEqualTo(Dinero.de(68_500));
        assertThat(otraVez.carga().getModificadaEn()).isEqualTo(tienda.reloj.ahora());
    }

    @Test
    @DisplayName("LA GANANCIA AL 50% y la bujía ajustada a mano: la bujía se queda, el resto se mueve")
    void ganancia() {
        UUID id = tienda.subirLaMag().carga().getId();
        tienda.editar.ajustarPrecio(id, 0, Dinero.de(68_500), tienda.dueno);

        DetalleCarga detalle = tienda.editar.cambiarRegla(id, new BigDecimal("19"), new BigDecimal("50"), 100,
                tienda.dueno);

        assertThat(renglon(detalle, "524XRE3IJ").getPrecioFinal()).isEqualTo(Dinero.de(68_500));
        assertThat(renglon(detalle, "082T3S").getPrecioFinal()).isEqualTo(Dinero.de(14_300));
    }

    @Test
    @DisplayName("LOS DATOS DE LA COMPRA: con proveedor, fecha, efectivo y la marca que faltaba, ya se puede confirmar")
    void datos() {
        UUID id = tienda.subirLaMag().carga().getId();

        tienda.editar.cambiarDatos(id, tienda.jotapartes.getId(), "MAG477", LocalDate.of(2026, 8, 31),
                FormaPago.EFECTIVO, null, tienda.dueno);
        DetalleCarga detalle = tienda.editar.cambiarMarca(id, List.of(), true, "NACIONAL", tienda.dueno);

        assertThat(detalle.revision().problemas()).isEmpty();
        assertThat(detalle.revision().sePuedeConfirmar()).isTrue();
    }

    @Test
    @DisplayName("un proveedor, una cuenta o una categoría que no existen o están desactivados no se aceptan")
    void loQueNoExiste() {
        UUID id = tienda.subirLaMag().carga().getId();
        CuentaPago vieja = tienda.cuentas.sembrar(CuentaPago.nueva("Bancolombia vieja"));
        vieja.desactivar();
        Categoria apagada = tienda.categorias.sembrar(Categoria.nueva("VIEJA", 9));
        apagada.desactivar();

        assertThatThrownBy(() -> tienda.editar.cambiarDatos(id, UUID.randomUUID(), null, null, null, null,
                tienda.dueno)).isInstanceOf(ReglaDeNegocioException.class).hasMessage("El proveedor no existe");
        assertThatThrownBy(() -> tienda.editar.cambiarDatos(id, tienda.jotapartes.getId(), null, null,
                FormaPago.TRANSFERENCIA, vieja.getId(), tienda.dueno))
                .isInstanceOf(ReglaDeNegocioException.class).hasMessageContaining("desactivada");
        assertThatThrownBy(() -> tienda.editar.cambiarCategoria(id, List.of(0), false, apagada.getId(),
                tienda.dueno)).isInstanceOf(ReglaDeNegocioException.class).hasMessageContaining("desactivada");
        assertThatThrownBy(() -> tienda.editar.cambiarCategoria(id, List.of(0), false, UUID.randomUUID(),
                tienda.dueno)).isInstanceOf(ReglaDeNegocioException.class).hasMessage("La categoría no existe");
    }

    @Test
    @DisplayName("quitar y restaurar un renglón, y aplicar el precio nuevo a una reposición")
    void quitarYPrecioNuevo() {
        tienda.yaExiste("093AKTCLKI", 9_000, 4);
        UUID id = tienda.subirLaMag().carga().getId();

        DetalleCarga quitado = tienda.editar.quitar(id, 2, true, tienda.dueno);
        assertThat(quitado.revision().totales().quitados()).isEqualTo(1);
        DetalleCarga restaurado = tienda.editar.quitar(id, 2, false, tienda.dueno);
        assertThat(restaurado.revision().totales().quitados()).isZero();

        DetalleCarga conPrecio = tienda.editar.aplicarPrecioNuevo(id, 1, true, tienda.dueno);
        assertThat(renglon(conPrecio, "093AKTCLKI").isAplicarPrecioNuevo()).isTrue();
    }

    @Test
    @DisplayName("corregir la lectura de un renglón, mirando el papel")
    void corregirLectura() {
        UUID id = tienda.subirLaMag().carga().getId();

        DetalleCarga detalle = tienda.editar.corregirLectura(id, 2, "082T3S", "TENSOR CADENILLA CB110 INOKI", 5,
                Dinero.de(39_950), null, null, tienda.dueno);

        assertThat(renglon(detalle, "082T3S").getPrecioUnitario()).isNull();
        assertThat(detalle.revision().renglones().get(2).problemas()).isEmpty();
    }

    @Test
    @DisplayName("EL CAJERO NO VE NI EDITA LA PRE-CARGA (RF-019)")
    void cajero() {
        UUID id = tienda.subirLaMag().carga().getId();

        assertThatThrownBy(() -> tienda.consultar.detalle(id, tienda.cajero)).isInstanceOf(NoPermitidoException.class);
        assertThatThrownBy(() -> tienda.consultar.recientes(tienda.cajero)).isInstanceOf(NoPermitidoException.class);
        assertThatThrownBy(() -> tienda.editar.ajustarPrecio(id, 0, Dinero.de(1), tienda.cajero))
                .isInstanceOf(NoPermitidoException.class);
        assertThatThrownBy(() -> tienda.descartar.ejecutar(id, tienda.cajero)).isInstanceOf(NoPermitidoException.class);
    }

    @Test
    @DisplayName("DESCARTADA NO SE EDITA, y en la lista queda debajo de los borradores")
    void descartada() {
        UUID vieja = tienda.subirLaMag().carga().getId();
        tienda.descartar.ejecutar(vieja, tienda.dueno);
        UUID nueva = tienda.subirLaMag().carga().getId();

        assertThatThrownBy(() -> tienda.editar.ajustarPrecio(vieja, 0, Dinero.de(1_000), tienda.dueno))
                .isInstanceOf(ReglaDeNegocioException.class).hasMessageContaining("se descartó");
        List<ResumenCarga> lista = tienda.consultar.recientes(tienda.dueno);
        assertThat(lista).extracting(ResumenCarga::id).containsExactly(nueva, vieja);
        assertThat(lista).extracting(ResumenCarga::estado).containsExactly(EstadoCarga.BORRADOR,
                EstadoCarga.DESCARTADA);
        assertThat(lista.getFirst().renglones()).isEqualTo(3);
    }

    @Test
    @DisplayName("una carga que no existe")
    void noExiste() {
        assertThat(tienda.consultar.detalle(UUID.randomUUID(), tienda.dueno)).isEmpty();
        assertThatThrownBy(() -> tienda.editar.quitar(UUID.randomUUID(), 0, true, tienda.dueno))
                .isInstanceOf(ReglaDeNegocioException.class).hasMessage("Esa carga no existe");
    }
}

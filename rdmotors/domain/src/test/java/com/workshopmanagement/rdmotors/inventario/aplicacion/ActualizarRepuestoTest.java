package com.workshopmanagement.rdmotors.inventario.aplicacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.workshopmanagement.rdmotors.compartido.ActoresDePrueba;
import com.workshopmanagement.rdmotors.compartido.Falsos;
import com.workshopmanagement.rdmotors.compartido.dominio.AccionAuditada;
import com.workshopmanagement.rdmotors.compartido.dominio.Actor;
import com.workshopmanagement.rdmotors.compartido.dominio.Dinero;
import com.workshopmanagement.rdmotors.compartido.dominio.ReglaDeNegocioException;
import com.workshopmanagement.rdmotors.inventario.aplicacion.ActualizarRepuesto.ComandoActualizarRepuesto;
import com.workshopmanagement.rdmotors.inventario.dominio.Categoria;
import com.workshopmanagement.rdmotors.inventario.dominio.CodigoDuplicadoException;
import com.workshopmanagement.rdmotors.inventario.dominio.Producto;
import com.workshopmanagement.rdmotors.inventario.dominio.Variante;

/**
 * Corregir un repuesto mal creado (RF-009).
 *
 * <p>Las dos pruebas que más protegen son {@link #corregirNoTocaStockNiCosto()} —la frontera con
 * el kardex— y {@link #guardarSinCambiarElCodigoNoChocaConsigoMismo()}, que es el caso sutil que
 * rompería el flujo más común: abrir la ficha, cambiar una letra del nombre, guardar.
 */
class ActualizarRepuestoTest {

    private Falsos.VariantesEnMemoria variantes;
    private Falsos.ProductosEnMemoria productos;
    private Falsos.CategoriasEnMemoria categorias;
    private Falsos.AuditoriaEnMemoria auditoria;
    private ActualizarRepuesto actualizar;
    private final Actor usuario = ActoresDePrueba.administrador();

    private Categoria filtros;
    private Categoria motor;
    private Producto concepto;
    private Variante inoki;

    @BeforeEach
    void preparar() {
        variantes = new Falsos.VariantesEnMemoria();
        productos = new Falsos.ProductosEnMemoria();
        categorias = new Falsos.CategoriasEnMemoria();
        // Spec 0002, fase 6: corregir la ficha deja auditoría. Cambia el cableado, no las aserciones.
        auditoria = new Falsos.AuditoriaEnMemoria();
        actualizar = new ActualizarRepuesto(variantes, categorias, auditoria,
                new Falsos.RelojFijo("2026-09-13T15:00:00Z"));

        filtros = categorias.sembrar(Categoria.nueva("FILTROS", 11));
        motor = categorias.sembrar(Categoria.nueva("MOTOR", 1));

        concepto = productos.sembrar(
                Producto.nuevo("FILTRO ACEIT", filtros, "PULSAR NS 200"));   // mal escrito
        inoki = variantes.sembrar(
                Variante.nueva(concepto, "352B59K", "INOKI", Dinero.de(6_000), 5));
    }

    private ComandoActualizarRepuesto comando(String nombre, UUID categoriaId, String codigo,
                                              String marca, long precio, int stockMinimo) {
        return new ComandoActualizarRepuesto(nombre, categoriaId, "PULSAR NS 200/FI/AS 200",
                codigo, marca, Dinero.de(precio), stockMinimo, usuario);
    }

    @Test
    @DisplayName("corrige la ficha completa")
    void corrigeLaFicha() {
        Variante r = actualizar.ejecutar(inoki.getId(),
                comando("FILTRO ACEITE", motor.getId(), "352B59K-A", "INOKI JAPAN", 7_500, 8));

        assertThat(r.getProducto().getNombre()).isEqualTo("FILTRO ACEITE");
        assertThat(r.getProducto().getCategoria()).isEqualTo(motor);
        assertThat(r.getProducto().getAplicacionOriginal()).isEqualTo("PULSAR NS 200/FI/AS 200");
        assertThat(r.getCodigo()).isEqualTo("352B59K-A");
        assertThat(r.getMarcaRepuesto()).isEqualTo("INOKI JAPAN");
        assertThat(r.getPrecio()).isEqualTo(Dinero.de(7_500));
        assertThat(r.getStockMinimo()).isEqualTo(8);
    }

    @Test
    @DisplayName("LA FRONTERA: corregir NO toca el stock ni el costo promedio")
    void corregirNoTocaStockNiCosto() {
        inoki.reponerPorCompra(15, new BigDecimal("13333.3333"));

        actualizar.ejecutar(inoki.getId(),
                comando("FILTRO ACEITE", filtros.getId(), "352B59K", "INOKI", 20_000, 5));

        // Son resultado del kardex. Si la corrección pudiera moverlos, quedaría un saldo que
        // ningún movimiento explica y el kardex dejaría de ser auditable.
        assertThat(inoki.getStock()).isEqualTo(15);
        assertThat(inoki.getCostoPromedio()).isEqualByComparingTo("13333.3333");
    }

    @Test
    @DisplayName("corregir el concepto afecta a TODAS las marcas que cuelgan de él")
    void corregirElConceptoAfectaATodasLasMarcas() {
        Variante factory = variantes.sembrar(
                Variante.nueva(concepto, "370P2NN", "FACTORY", Dinero.de(12_000), 5));

        actualizar.ejecutar(inoki.getId(),
                comando("FILTRO ACEITE", filtros.getId(), "352B59K", "INOKI", 6_000, 5));

        // Es lo correcto —es el mismo repuesto— pero la pantalla tiene que avisarlo: el admin
        // cree estar corrigiendo una fila y está corrigiendo dos.
        assertThat(factory.getProducto().getNombre()).isEqualTo("FILTRO ACEITE");
    }

    @Test
    @DisplayName("EL CASO SUTIL: guardar sin cambiar el código no choca consigo mismo")
    void guardarSinCambiarElCodigoNoChocaConsigoMismo() {
        // El flujo más común: abrir la ficha, corregir una letra del nombre, guardar. Sin el
        // guard, el propio repuesto aparecería como dueño de su código y el admin leería
        // "el código 352B59K ya lo usa: FILTRO ACEIT" señalando al que está editando.
        Variante r = actualizar.ejecutar(inoki.getId(),
                comando("FILTRO ACEITE", filtros.getId(), "352B59K", "INOKI", 6_000, 5));

        assertThat(r.getCodigo()).isEqualTo("352B59K");
    }

    @Test
    @DisplayName("cambiar a un código que ya usa OTRO repuesto se rechaza")
    void codigoDeOtroSeRechaza() {
        variantes.sembrar(
                Variante.nueva(concepto, "370P2NN", "FACTORY", Dinero.de(12_000), 5));

        assertThatThrownBy(() -> actualizar.ejecutar(inoki.getId(),
                comando("FILTRO ACEITE", filtros.getId(), "370P2NN", "INOKI", 6_000, 5)))
                .isInstanceOf(CodigoDuplicadoException.class);
    }

    @Test
    @DisplayName("el código se normaliza al corregir")
    void normalizaElCodigo() {
        Variante r = actualizar.ejecutar(inoki.getId(),
                comando("FILTRO ACEITE", filtros.getId(), "  352b59k-b ", "INOKI", 6_000, 5));

        assertThat(r.getCodigo()).isEqualTo("352B59K-B");
    }

    @Test
    @DisplayName("un repuesto inexistente se rechaza")
    void repuestoInexistente() {
        assertThatThrownBy(() -> actualizar.ejecutar(UUID.randomUUID(),
                comando("X", filtros.getId(), "X-1", "CBI", 1_000, 1)))
                .isInstanceOf(ReglaDeNegocioException.class)
                .hasMessageContaining("no existe");
    }

    @Test
    @DisplayName("una categoría inexistente se rechaza")
    void categoriaInexistente() {
        assertThatThrownBy(() -> actualizar.ejecutar(inoki.getId(),
                comando("FILTRO ACEITE", UUID.randomUUID(), "352B59K", "INOKI", 6_000, 5)))
                .isInstanceOf(ReglaDeNegocioException.class)
                .hasMessageContaining("categoria");
    }

    @Test
    @DisplayName("spec 0005, RF-011: no se puede dejar sin categoría, y la ficha queda como estaba")
    void sinCategoriaNo() {
        assertThatThrownBy(() -> actualizar.ejecutar(inoki.getId(),
                comando("FILTRO ACEITE", null, "999ZZZ", "OTRA", 9_000, 9)))
                .isInstanceOf(ReglaDeNegocioException.class)
                .hasMessage(Producto.SIN_CATEGORIA);

        assertThat(inoki.getProducto().getCategoria()).isEqualTo(filtros);
        assertThat(inoki.getProducto().getNombre()).isEqualTo("FILTRO ACEIT");
        assertThat(inoki.getCodigo()).isEqualTo("352B59K");
        assertThat(auditoria.eventos).isEmpty();
    }

    @Test
    @DisplayName("spec 0005, RF-011: una ficha vieja sin categoría no se guarda sin escoger una; escogiéndola, sí")
    void fichaViejaSinCategoria() throws Exception {
        // Así llega de la base un concepto creado antes de la regla: JPA no pasa por Producto.nuevo.
        var campo = Producto.class.getDeclaredField("categoria");
        campo.setAccessible(true);
        campo.set(concepto, null);

        assertThatThrownBy(() -> actualizar.ejecutar(inoki.getId(),
                comando("FILTRO ACEITE", null, "352B59K", "INOKI", 7_000, 5)))
                .hasMessage(Producto.SIN_CATEGORIA);

        Variante r = actualizar.ejecutar(inoki.getId(),
                comando("FILTRO ACEITE", motor.getId(), "352B59K", "INOKI", 7_000, 5));
        assertThat(r.getProducto().getCategoria()).isEqualTo(motor);
    }

    @Test
    @DisplayName("precio negativo se rechaza")
    void precioNegativo() {
        assertThatThrownBy(() -> actualizar.ejecutar(inoki.getId(),
                new ComandoActualizarRepuesto("FILTRO ACEITE", filtros.getId(), null,
                        "352B59K", "INOKI", Dinero.de(-100), 5, usuario)))
                .isInstanceOf(ReglaDeNegocioException.class)
                .hasMessageContaining("negativo");
    }

    // ── Auditoría (spec 0002, H7) ────────────────────────────────────────────

    @Test
    @DisplayName("corregir la ficha deja el evento con quién, y la ficha antes y después")
    void dejaAuditoria() {
        actualizar.ejecutar(inoki.getId(), comando("FILTRO ACEITE", motor.getId(), "352B59K", "INOKI", 6_000, 8));

        assertThat(auditoria.eventos).singleElement().satisfies(e -> {
            assertThat(e.accion()).isEqualTo(AccionAuditada.CORREGIR_REPUESTO);
            assertThat(e.entidadTipo()).isEqualTo(ActualizarRepuesto.TIPO_AUDITORIA);
            assertThat(e.entidadId()).isEqualTo(inoki.getId());
            assertThat(e.usuarioId()).isEqualTo(usuario.id());
            assertThat(e.antes()).containsEntry("nombre", "FILTRO ACEIT")
                    .containsEntry("categoria", "FILTROS").containsEntry("stockMinimo", 5);
            assertThat(e.despues()).containsEntry("nombre", "FILTRO ACEITE")
                    .containsEntry("categoria", "MOTOR").containsEntry("stockMinimo", 8);
        });
    }

    @Test
    @DisplayName("guardar la ficha sin cambiar nada no deja evento: el rastro se llenaría de ruido")
    void sinCambiosNoDejaEvento() {
        actualizar.ejecutar(inoki.getId(), comando("FILTRO ACEIT", filtros.getId(), "352B59K", "INOKI", 6_000, 5));

        // La aplicación del comando del helper es "PULSAR NS 200/FI/AS 200", distinta de la sembrada:
        // se iguala primero para que de verdad no cambie nada.
        auditoria.eventos.clear();
        actualizar.ejecutar(inoki.getId(), comando("FILTRO ACEIT", filtros.getId(), "352B59K", "INOKI", 6_000, 5));

        assertThat(auditoria.eventos).isEmpty();
    }
}


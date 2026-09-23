package com.workshopmanagement.rdmotors.clientes.aplicacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.workshopmanagement.rdmotors.clientes.dominio.Cliente;
import com.workshopmanagement.rdmotors.clientes.dominio.ClienteRepetidoException;
import com.workshopmanagement.rdmotors.clientes.dominio.DatosCliente;
import com.workshopmanagement.rdmotors.clientes.dominio.Deuda;
import com.workshopmanagement.rdmotors.compartido.ActoresDePrueba;
import com.workshopmanagement.rdmotors.compartido.Falsos;
import com.workshopmanagement.rdmotors.compartido.dominio.AccionAuditada;
import com.workshopmanagement.rdmotors.compartido.dominio.Actor;
import com.workshopmanagement.rdmotors.compartido.dominio.Dinero;
import com.workshopmanagement.rdmotors.compartido.dominio.NoPermitidoException;

/** Crear, buscar y cambiar los datos de un cliente (spec 0008, RF-001, RF-003 y RF-004). */
class ClientesTest {

    private final Falsos.RelojFijo reloj = new Falsos.RelojFijo("2026-09-21T15:00:00Z");
    private final Falsos.ClientesEnMemoria clientes = new Falsos.ClientesEnMemoria();
    private final Falsos.DeudasEnMemoria deudas = new Falsos.DeudasEnMemoria();
    private final Falsos.AuditoriaEnMemoria auditoria = new Falsos.AuditoriaEnMemoria();
    private final CrearCliente crear = new CrearCliente(clientes, reloj);
    private final ActualizarCliente actualizar = new ActualizarCliente(clientes, auditoria, reloj);
    private final BuscarClientes buscar = new BuscarClientes(clientes, deudas);
    private final Actor cajero = ActoresDePrueba.cajero();
    private final Actor administrador = ActoresDePrueba.administrador();

    private Cliente juan() {
        return crear.ejecutar(new DatosCliente("Juan Pérez", "1.234.567", "300 123 4567", null, null), cajero);
    }

    @Test
    @DisplayName("el cajero crea un cliente en el mostrador")
    void elCajeroCrea() {
        Cliente juan = juan();

        assertThat(clientes.datos).containsKey(juan.getId());
        assertThat(juan.getCreadoPorId()).isEqualTo(cajero.id());
    }

    @Test
    @DisplayName("una cédula que ya existe, escrita de otra forma, no crea otro cliente: dice de quién es")
    void cedulaRepetida() {
        Cliente juan = juan();

        assertThatThrownBy(() -> crear.ejecutar(new DatosCliente("Juancho", "1234567", null, null, null), cajero))
                .isInstanceOf(ClienteRepetidoException.class)
                .hasMessage("Esa cédula es de Juan Pérez")
                .satisfies(e -> assertThat(((ClienteRepetidoException) e).getExistenteId()).isEqualTo(juan.getId()));
        assertThat(clientes.datos).hasSize(1);
    }

    @Test
    @DisplayName("se busca por nombre sin tildes, por la cédula sin puntos y por el celular; con lo que debe cada uno")
    void buscar() {
        Cliente juan = juan();
        crear.ejecutar(new DatosCliente("Ana Gómez", null, null, null, null), cajero);
        deudas.guardar(Deuda.porVenta(juan.getId(), UUID.randomUUID(), 41, LocalDate.of(2026, 9, 12),
                Dinero.de(50_000), cajero.id(), reloj.ahora()));

        assertThat(buscar.porTexto("juan perez")).extracting(ClienteEncontrado::nombre).containsExactly("Juan Pérez");
        assertThat(buscar.porTexto("1234567")).extracting(ClienteEncontrado::nombre).containsExactly("Juan Pérez");
        assertThat(buscar.porTexto("300123")).extracting(ClienteEncontrado::nombre).containsExactly("Juan Pérez");
        assertThat(buscar.porTexto("GOMEZ")).singleElement().satisfies(ana -> {
            assertThat(ana.debe()).isEqualTo(Dinero.CERO);
            assertThat(ana.datosQueFaltan()).containsExactly("la cédula", "el celular");
        });
        assertThat(buscar.porTexto("juan").getFirst().debe()).isEqualTo(Dinero.de(50_000));
        assertThat(buscar.porTexto("   ")).isEmpty();
    }

    @Test
    @DisplayName("el cajero completa lo que falta; cambiar lo escrito no puede, y no cambia nada")
    void elCajeroCompleta() {
        Cliente ana = crear.ejecutar(new DatosCliente("Ana Gómez", null, null, null, null), cajero);

        actualizar.ejecutar(ana.getId(), new DatosCliente("Ana Gómez", "7654321", "3109876543", null, null), cajero);

        assertThat(ana.datosQueFaltan()).as("ya no le falta nada por anotar").isEmpty();
        assertThat(auditoria.eventos).as("completar no es corregir").isEmpty();
        assertThatThrownBy(() -> actualizar.ejecutar(ana.getId(),
                new DatosCliente("Ana Gómez", "1111111", "3109876543", null, null), cajero))
                .isInstanceOf(NoPermitidoException.class)
                .hasMessage(ActualizarCliente.CORREGIR_ES_DEL_ADMINISTRADOR);
        assertThat(ana.getDocumentoNormalizado()).isEqualTo("7654321");
    }

    @Test
    @DisplayName("el administrador corrige, y queda en la auditoría con el antes y el después")
    void elAdministradorCorrige() {
        Cliente juan = juan();

        actualizar.ejecutar(juan.getId(), new DatosCliente("Juan Pérez", "7654321", "3001234567", null, null),
                administrador);

        assertThat(juan.getDocumentoNormalizado()).isEqualTo("7654321");
        assertThat(auditoria.eventos).singleElement().satisfies(e -> {
            assertThat(e.accion()).isEqualTo(AccionAuditada.CORREGIR_CLIENTE);
            assertThat(e.usuarioId()).isEqualTo(administrador.id());
            assertThat(e.antes()).containsEntry("documento", "1.234.567");
            assertThat(e.despues()).containsEntry("documento", "7654321");
        });
    }

    @Test
    @DisplayName("corregir a una cédula que ya es de otro no se puede")
    void corregirAUnaCedulaAjena() {
        Cliente juan = juan();
        Cliente ana = crear.ejecutar(new DatosCliente("Ana Gómez", "7654321", null, null, null), cajero);

        assertThatThrownBy(() -> actualizar.ejecutar(ana.getId(),
                new DatosCliente("Ana Gómez", "1234567", null, null, null), administrador))
                .isInstanceOf(ClienteRepetidoException.class)
                .hasMessage("Esa cédula es de " + juan.getNombre());
        assertThat(ana.getDocumentoNormalizado()).isEqualTo("7654321");
    }
}

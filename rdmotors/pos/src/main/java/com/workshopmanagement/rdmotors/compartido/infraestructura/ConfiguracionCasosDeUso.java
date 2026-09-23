package com.workshopmanagement.rdmotors.compartido.infraestructura;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.workshopmanagement.rdmotors.caja.aplicacion.AbrirTurno;
import com.workshopmanagement.rdmotors.caja.aplicacion.ActualizarCategoriaGasto;
import com.workshopmanagement.rdmotors.caja.aplicacion.AnularGasto;
import com.workshopmanagement.rdmotors.caja.aplicacion.AnularRetiro;
import com.workshopmanagement.rdmotors.caja.aplicacion.CalcularArqueo;
import com.workshopmanagement.rdmotors.caja.aplicacion.CerrarTurno;
import com.workshopmanagement.rdmotors.caja.aplicacion.ConsultarGastos;
import com.workshopmanagement.rdmotors.caja.aplicacion.ConsultarTurnos;
import com.workshopmanagement.rdmotors.caja.aplicacion.DesactivarCategoriaGasto;
import com.workshopmanagement.rdmotors.caja.aplicacion.EscribirObservaciones;
import com.workshopmanagement.rdmotors.caja.aplicacion.RegistrarCategoriaGasto;
import com.workshopmanagement.rdmotors.caja.aplicacion.RegistrarGasto;
import com.workshopmanagement.rdmotors.caja.aplicacion.RegistrarRetiro;
import com.workshopmanagement.rdmotors.caja.dominio.puerto.RepositorioCategoriasGasto;
import com.workshopmanagement.rdmotors.caja.dominio.puerto.RepositorioGastos;
import com.workshopmanagement.rdmotors.caja.dominio.puerto.RepositorioRetiros;
import com.workshopmanagement.rdmotors.caja.dominio.puerto.RepositorioTurnos;
import com.workshopmanagement.rdmotors.clientes.aplicacion.ActualizarCliente;
import com.workshopmanagement.rdmotors.clientes.aplicacion.AnularAbono;
import com.workshopmanagement.rdmotors.clientes.aplicacion.BuscarClientes;
import com.workshopmanagement.rdmotors.clientes.aplicacion.CambiarFiado;
import com.workshopmanagement.rdmotors.clientes.aplicacion.CargarSaldoDelCuaderno;
import com.workshopmanagement.rdmotors.clientes.aplicacion.ConsultarCartera;
import com.workshopmanagement.rdmotors.clientes.aplicacion.CrearCliente;
import com.workshopmanagement.rdmotors.clientes.aplicacion.RegistrarAbono;
import com.workshopmanagement.rdmotors.clientes.aplicacion.FiarVenta;
import com.workshopmanagement.rdmotors.clientes.dominio.puerto.ConsultasDeCartera;
import com.workshopmanagement.rdmotors.clientes.dominio.puerto.RepositorioAbonos;
import com.workshopmanagement.rdmotors.clientes.dominio.puerto.RepositorioClientes;
import com.workshopmanagement.rdmotors.clientes.dominio.puerto.RepositorioDeudas;
import com.workshopmanagement.rdmotors.compartido.aplicacion.ActualizarDatosTienda;
import com.workshopmanagement.rdmotors.compartido.dominio.puerto.RepositorioDatosTienda;
import com.workshopmanagement.rdmotors.compartido.dominio.puerto.Reloj;
import com.workshopmanagement.rdmotors.compartido.dominio.puerto.RepositorioAuditoria;
import com.workshopmanagement.rdmotors.compras.aplicacion.AnularCompra;
import com.workshopmanagement.rdmotors.compras.aplicacion.ConsultarCompras;
import com.workshopmanagement.rdmotors.compras.aplicacion.CorregirCompra;
import com.workshopmanagement.rdmotors.compras.aplicacion.DesactivarCuenta;
import com.workshopmanagement.rdmotors.compras.aplicacion.RegistrarCompra;
import com.workshopmanagement.rdmotors.compras.aplicacion.RegistrarCuenta;
import com.workshopmanagement.rdmotors.compras.aplicacion.RegistrarProveedor;
import com.workshopmanagement.rdmotors.compras.dominio.puerto.RepositorioCompras;
import com.workshopmanagement.rdmotors.compras.dominio.puerto.RepositorioCuentas;
import com.workshopmanagement.rdmotors.compras.dominio.puerto.RepositorioProveedores;
import com.workshopmanagement.rdmotors.inventario.aplicacion.ActualizarRepuesto;
import com.workshopmanagement.rdmotors.inventario.aplicacion.BuscarRepuestos;
import com.workshopmanagement.rdmotors.inventario.aplicacion.CrearRepuesto;
import com.workshopmanagement.rdmotors.inventario.dominio.puerto.RepositorioCategorias;
import com.workshopmanagement.rdmotors.inventario.dominio.puerto.RepositorioKardex;
import com.workshopmanagement.rdmotors.inventario.dominio.puerto.RepositorioProductos;
import com.workshopmanagement.rdmotors.inventario.dominio.puerto.RepositorioVariantes;
import com.workshopmanagement.rdmotors.correo.aplicacion.AdministrarCorreos;
import com.workshopmanagement.rdmotors.correo.aplicacion.Cartero;
import com.workshopmanagement.rdmotors.correo.aplicacion.ConsultarCorreos;
import com.workshopmanagement.rdmotors.correo.aplicacion.EncolarCorreoDelCierre;
import com.workshopmanagement.rdmotors.correo.aplicacion.MandarCorreosPendientes;
import com.workshopmanagement.rdmotors.correo.dominio.PoliticaDeReintentos;
import com.workshopmanagement.rdmotors.correo.dominio.puerto.EnviadorDeCorreos;
import com.workshopmanagement.rdmotors.correo.dominio.puerto.RepositorioAjustesDeCorreo;
import com.workshopmanagement.rdmotors.correo.dominio.puerto.RepositorioCorreos;
import com.workshopmanagement.rdmotors.reportes.aplicacion.ConsultarResultados;
import com.workshopmanagement.rdmotors.reportes.dominio.Periodo;
import com.workshopmanagement.rdmotors.respaldo.aplicacion.ConsultarRespaldos;
import com.workshopmanagement.rdmotors.respaldo.aplicacion.BajarRespaldo;
import com.workshopmanagement.rdmotors.respaldo.dominio.PoliticaDeRespaldo;
import com.workshopmanagement.rdmotors.respaldo.dominio.puerto.Archivos;
import com.workshopmanagement.rdmotors.respaldo.dominio.puerto.RepositorioRespaldos;
import com.workshopmanagement.rdmotors.respaldo.dominio.puerto.Volcador;
import com.workshopmanagement.rdmotors.respaldo.infraestructura.ConfiguracionDeRespaldo;
import com.workshopmanagement.rdmotors.reportes.dominio.puerto.RepositorioReportes;
import com.workshopmanagement.rdmotors.usuarios.aplicacion.ActivarUsuario;
import com.workshopmanagement.rdmotors.usuarios.aplicacion.CambiarContrasena;
import com.workshopmanagement.rdmotors.usuarios.aplicacion.CambiarRol;
import com.workshopmanagement.rdmotors.usuarios.aplicacion.ConsultarEntradas;
import com.workshopmanagement.rdmotors.usuarios.aplicacion.ConsultarSesion;
import com.workshopmanagement.rdmotors.usuarios.aplicacion.ConsultarUsuarios;
import com.workshopmanagement.rdmotors.usuarios.aplicacion.CrearPrimerAdministrador;
import com.workshopmanagement.rdmotors.usuarios.aplicacion.CrearUsuario;
import com.workshopmanagement.rdmotors.usuarios.aplicacion.DesactivarUsuario;
import com.workshopmanagement.rdmotors.usuarios.aplicacion.Entrar;
import com.workshopmanagement.rdmotors.usuarios.aplicacion.RestablecerContrasena;
import com.workshopmanagement.rdmotors.usuarios.aplicacion.RestablecerDesdeLaTienda;
import com.workshopmanagement.rdmotors.usuarios.dominio.puerto.Contrasenas;
import com.workshopmanagement.rdmotors.usuarios.dominio.puerto.RepositorioEntradas;
import com.workshopmanagement.rdmotors.usuarios.dominio.puerto.RepositorioUsuarios;
import com.workshopmanagement.rdmotors.ventas.aplicacion.AnularVenta;
import com.workshopmanagement.rdmotors.ventas.aplicacion.CobrarVenta;
import com.workshopmanagement.rdmotors.ventas.aplicacion.ConsultarVentas;
import com.workshopmanagement.rdmotors.ventas.aplicacion.RevisarPerdida;
import com.workshopmanagement.rdmotors.ventas.dominio.puerto.RepositorioVentas;

/**
 * Aqui se enchufan los adaptadores a los casos de uso.
 *
 * <p><b>Por que los casos de uso NO llevan {@code @Service}:</b> esa anotacion los ataria a Spring
 * y el modulo {@code domain} dejaria de poder construirse solo. Al declararlos aqui, el dominio
 * sigue siendo Java puro —se instancia con {@code new} en una prueba— y es la infraestructura la
 * que sabe como armarlo.
 *
 * <p>Spring si envuelve el bean en un proxy transaccional, porque {@code RegistrarCompra} lleva
 * {@code @Transactional} a nivel de clase. Esa es la unica dependencia de Spring que se permite en
 * el paquete {@code aplicacion}, y la razon esta en el skill {@code backend}: la transaccion tiene
 * que declararse en algun lado y el caso de uso es su dueno.
 */
@Configuration
class ConfiguracionCasosDeUso {

    @Bean
    RegistrarCompra registrarCompra(RepositorioCompras compras,
                                    CrearRepuesto crearRepuesto,
                                    RepositorioProveedores proveedores,
                                    RepositorioCuentas cuentas,
                                    RepositorioTurnos turnos,
                                    RepositorioVariantes variantes,
                                    RepositorioKardex kardex,
                                    Reloj reloj) {
        return new RegistrarCompra(compras, proveedores, cuentas, turnos, variantes, kardex, crearRepuesto,
                reloj);
    }

    @Bean
    CrearRepuesto crearRepuesto(RepositorioVariantes variantes,
                                RepositorioProductos productos,
                                RepositorioCategorias categorias) {
        return new CrearRepuesto(variantes, productos, categorias);
    }

    @Bean
    ActualizarRepuesto actualizarRepuesto(RepositorioVariantes variantes,
                                          RepositorioCategorias categorias,
                                          RepositorioAuditoria auditoria,
                                          Reloj reloj) {
        return new ActualizarRepuesto(variantes, categorias, auditoria, reloj);
    }

    @Bean
    BuscarRepuestos buscarRepuestos(RepositorioVariantes variantes) {
        return new BuscarRepuestos(variantes);
    }

    @Bean
    RegistrarProveedor registrarProveedor(RepositorioProveedores proveedores) {
        return new RegistrarProveedor(proveedores);
    }

    @Bean
    RegistrarCuenta registrarCuenta(RepositorioCuentas cuentas) {
        return new RegistrarCuenta(cuentas);
    }

    @Bean
    DesactivarCuenta desactivarCuenta(RepositorioCuentas cuentas) {
        return new DesactivarCuenta(cuentas);
    }

    @Bean
    ConsultarCompras consultarCompras(RepositorioCompras compras, RepositorioAuditoria auditoria,
                                      RepositorioUsuarios usuarios) {
        return new ConsultarCompras(compras, auditoria, usuarios);
    }

    @Bean
    CorregirCompra corregirCompra(RepositorioCompras compras,
                                  RepositorioProveedores proveedores,
                                  RepositorioCuentas cuentas,
                                  RepositorioTurnos turnos,
                                  RepositorioVariantes variantes,
                                  RepositorioKardex kardex,
                                  CrearRepuesto crearRepuesto,
                                  RepositorioAuditoria auditoria,
                                  Reloj reloj) {
        return new CorregirCompra(compras, proveedores, cuentas, turnos, variantes, kardex, crearRepuesto,
                auditoria, reloj);
    }

    @Bean
    AnularCompra anularCompra(RepositorioCompras compras,
                              RepositorioTurnos turnos,
                              RepositorioVariantes variantes,
                              RepositorioKardex kardex,
                              RepositorioAuditoria auditoria,
                              Reloj reloj) {
        return new AnularCompra(compras, turnos, variantes, kardex, auditoria, reloj);
    }

    @Bean
    AbrirTurno abrirTurno(RepositorioTurnos turnos, Reloj reloj) {
        return new AbrirTurno(turnos, reloj);
    }

    @Bean
    CobrarVenta cobrarVenta(RepositorioVentas ventas,
                            RepositorioTurnos turnos,
                            RepositorioVariantes variantes,
                            RepositorioKardex kardex,
                            RepositorioAuditoria auditoria,
                            FiarVenta fiar,
                            Reloj reloj) {
        return new CobrarVenta(ventas, turnos, variantes, kardex, auditoria, fiar, reloj);
    }

    @Bean
    AnularVenta anularVenta(RepositorioVentas ventas,
                            RepositorioTurnos turnos,
                            RepositorioVariantes variantes,
                            RepositorioKardex kardex,
                            RepositorioAuditoria auditoria,
                            FiarVenta fiar,
                            Reloj reloj) {
        return new AnularVenta(ventas, turnos, variantes, kardex, auditoria, fiar, reloj);
    }

    @Bean
    RevisarPerdida revisarPerdida(RepositorioVariantes variantes) {
        return new RevisarPerdida(variantes);
    }

    @Bean
    ConsultarVentas consultarVentas(RepositorioVentas ventas, RepositorioTurnos turnos, RepositorioUsuarios usuarios,
                                    RepositorioClientes clientes, RepositorioDeudas deudas) {
        return new ConsultarVentas(ventas, turnos, usuarios, clientes, deudas);
    }

    // ── Clientes y fiado (spec 0008) ─────────────────────────────────────────

    /** Sin transacción propia: la usan cobrar y anular, dentro de la suya. */
    @Bean
    FiarVenta fiarVenta(RepositorioClientes clientes, RepositorioDeudas deudas, RepositorioAbonos abonos) {
        return new FiarVenta(clientes, deudas, abonos);
    }

    @Bean
    CrearCliente crearCliente(RepositorioClientes clientes, Reloj reloj) {
        return new CrearCliente(clientes, reloj);
    }

    @Bean
    ActualizarCliente actualizarCliente(RepositorioClientes clientes, RepositorioAuditoria auditoria, Reloj reloj) {
        return new ActualizarCliente(clientes, auditoria, reloj);
    }

    @Bean
    BuscarClientes buscarClientes(RepositorioClientes clientes, RepositorioDeudas deudas) {
        return new BuscarClientes(clientes, deudas);
    }

    @Bean
    ConsultarCartera consultarCartera(ConsultasDeCartera consultas, RepositorioClientes clientes,
                                      RepositorioDeudas deudas, RepositorioAbonos abonos, RepositorioUsuarios usuarios) {
        return new ConsultarCartera(consultas, clientes, deudas, abonos, usuarios);
    }

    @Bean
    CambiarFiado cambiarFiado(RepositorioClientes clientes, RepositorioAuditoria auditoria, Reloj reloj) {
        return new CambiarFiado(clientes, auditoria, reloj);
    }

    @Bean
    CargarSaldoDelCuaderno cargarSaldoDelCuaderno(RepositorioClientes clientes, RepositorioDeudas deudas,
                                                  RepositorioAbonos abonos, RepositorioAuditoria auditoria,
                                                  Reloj reloj) {
        return new CargarSaldoDelCuaderno(clientes, deudas, abonos, auditoria, reloj);
    }

    @Bean
    RegistrarAbono registrarAbono(RepositorioAbonos abonos, RepositorioClientes clientes, RepositorioDeudas deudas,
                                  RepositorioTurnos turnos, Reloj reloj) {
        return new RegistrarAbono(abonos, clientes, deudas, turnos, reloj);
    }

    @Bean
    AnularAbono anularAbono(RepositorioAbonos abonos, RepositorioClientes clientes, RepositorioDeudas deudas,
                            RepositorioTurnos turnos, RepositorioAuditoria auditoria, Reloj reloj) {
        return new AnularAbono(abonos, clientes, deudas, turnos, auditoria, reloj);
    }

    @Bean
    ActualizarDatosTienda actualizarDatosTienda(RepositorioDatosTienda tienda) {
        return new ActualizarDatosTienda(tienda);
    }

    // ── Caja: gastos, retiros y cierre (spec 0006) ───────────────────────────

    /** Sin transacción propia: lo usan casos de uso que ya tienen el turno bloqueado. */
    @Bean
    CalcularArqueo calcularArqueo(RepositorioVentas ventas, RepositorioGastos gastos, RepositorioRetiros retiros,
                                  RepositorioCompras compras, RepositorioAbonos abonos) {
        return new CalcularArqueo(ventas, gastos, retiros, compras, abonos);
    }

    @Bean
    RegistrarCategoriaGasto registrarCategoriaGasto(RepositorioCategoriasGasto categorias) {
        return new RegistrarCategoriaGasto(categorias);
    }

    @Bean
    ActualizarCategoriaGasto actualizarCategoriaGasto(RepositorioCategoriasGasto categorias) {
        return new ActualizarCategoriaGasto(categorias);
    }

    @Bean
    DesactivarCategoriaGasto desactivarCategoriaGasto(RepositorioCategoriasGasto categorias) {
        return new DesactivarCategoriaGasto(categorias);
    }

    @Bean
    RegistrarGasto registrarGasto(RepositorioGastos gastos, RepositorioCategoriasGasto categorias,
                                  RepositorioCuentas cuentas, RepositorioTurnos turnos, CalcularArqueo arqueo,
                                  Reloj reloj) {
        return new RegistrarGasto(gastos, categorias, cuentas, turnos, arqueo, reloj);
    }

    @Bean
    AnularGasto anularGasto(RepositorioGastos gastos, RepositorioTurnos turnos, RepositorioAuditoria auditoria,
                            Reloj reloj) {
        return new AnularGasto(gastos, turnos, auditoria, reloj);
    }

    @Bean
    ConsultarGastos consultarGastos(RepositorioGastos gastos, RepositorioTurnos turnos,
                                    RepositorioUsuarios usuarios) {
        return new ConsultarGastos(gastos, turnos, usuarios);
    }

    @Bean
    RegistrarRetiro registrarRetiro(RepositorioRetiros retiros, RepositorioTurnos turnos, CalcularArqueo arqueo,
                                    Reloj reloj) {
        return new RegistrarRetiro(retiros, turnos, arqueo, reloj);
    }

    @Bean
    AnularRetiro anularRetiro(RepositorioRetiros retiros, RepositorioTurnos turnos, RepositorioAuditoria auditoria,
                              Reloj reloj) {
        return new AnularRetiro(retiros, turnos, auditoria, reloj);
    }

    @Bean
    CerrarTurno cerrarTurno(RepositorioTurnos turnos, CalcularArqueo arqueo, RepositorioAuditoria auditoria,
                            EncolarCorreoDelCierre correoDelCierre, Reloj reloj) {
        return new CerrarTurno(turnos, arqueo, auditoria, correoDelCierre, reloj);
    }

    @Bean
    EscribirObservaciones escribirObservaciones(RepositorioTurnos turnos) {
        return new EscribirObservaciones(turnos);
    }

    @Bean
    ConsultarTurnos consultarTurnos(RepositorioTurnos turnos, RepositorioGastos gastos, RepositorioRetiros retiros,
                                    RepositorioCompras compras, RepositorioVentas ventas, CalcularArqueo arqueo,
                                    RepositorioUsuarios usuarios, RepositorioAbonos abonos,
                                    RepositorioClientes clientes) {
        return new ConsultarTurnos(turnos, gastos, retiros, compras, ventas, arqueo, usuarios, abonos, clientes);
    }

    // ── Reportes (spec 0007) ─────────────────────────────────────────────────

    @Bean
    ConsultarResultados consultarResultados(RepositorioReportes reportes, Reloj reloj) {
        return new ConsultarResultados(reportes, reloj);
    }

    // ── Usuarios (spec 0004) ─────────────────────────────────────────────────

    @Bean
    CrearPrimerAdministrador crearPrimerAdministrador(RepositorioUsuarios usuarios, Contrasenas contrasenas,
                                                      RepositorioAuditoria auditoria, Reloj reloj) {
        return new CrearPrimerAdministrador(usuarios, contrasenas, auditoria, reloj);
    }

    @Bean
    Entrar entrar(RepositorioUsuarios usuarios, Contrasenas contrasenas, RepositorioEntradas entradas, Reloj reloj) {
        return new Entrar(usuarios, contrasenas, entradas, reloj);
    }

    @Bean
    CambiarContrasena cambiarContrasena(RepositorioUsuarios usuarios, Contrasenas contrasenas) {
        return new CambiarContrasena(usuarios, contrasenas);
    }

    @Bean
    ConsultarSesion consultarSesion(RepositorioUsuarios usuarios) {
        return new ConsultarSesion(usuarios);
    }

    // ── Administrar usuarios (spec 0004, fase 4) ───────────────────────────

    @Bean
    ConsultarUsuarios consultarUsuarios(RepositorioUsuarios usuarios) {
        return new ConsultarUsuarios(usuarios);
    }

    @Bean
    ConsultarEntradas consultarEntradas(RepositorioEntradas entradas, RepositorioUsuarios usuarios) {
        return new ConsultarEntradas(entradas, usuarios);
    }

    @Bean
    CrearUsuario crearUsuario(RepositorioUsuarios usuarios, Contrasenas contrasenas, RepositorioAuditoria auditoria,
                              Reloj reloj) {
        return new CrearUsuario(usuarios, contrasenas, auditoria, reloj);
    }

    @Bean
    CambiarRol cambiarRol(RepositorioUsuarios usuarios, RepositorioAuditoria auditoria, Reloj reloj) {
        return new CambiarRol(usuarios, auditoria, reloj);
    }

    @Bean
    DesactivarUsuario desactivarUsuario(RepositorioUsuarios usuarios, RepositorioAuditoria auditoria, Reloj reloj) {
        return new DesactivarUsuario(usuarios, auditoria, reloj);
    }

    @Bean
    ActivarUsuario activarUsuario(RepositorioUsuarios usuarios, RepositorioAuditoria auditoria, Reloj reloj) {
        return new ActivarUsuario(usuarios, auditoria, reloj);
    }

    @Bean
    RestablecerContrasena restablecerContrasena(RepositorioUsuarios usuarios, Contrasenas contrasenas,
                                                RepositorioAuditoria auditoria, Reloj reloj) {
        return new RestablecerContrasena(usuarios, contrasenas, auditoria, reloj);
    }

    @Bean
    RestablecerDesdeLaTienda restablecerDesdeLaTienda(RepositorioUsuarios usuarios, Contrasenas contrasenas,
                                                      RepositorioAuditoria auditoria, Reloj reloj) {
        return new RestablecerDesdeLaTienda(usuarios, contrasenas, auditoria, reloj);
    }
    // ── El respaldo (spec 0009, replanteado por el spec 0011) ────────────────

    @Bean
    PoliticaDeRespaldo politicaDeRespaldo(ConfiguracionDeRespaldo configuracion) {
        return new PoliticaDeRespaldo(configuracion.getDiasSinBajarParaAvisar(), Periodo.ZONA);
    }

    /**
     * La carpeta de trabajo es solo de paso: el archivo se saca ahí, se manda y se borra. Ya no hay una carpeta de
     * copias, ni memoria USB, ni poda: el archivo se va con quien lo baja (spec 0011, decisión 1).
     */
    @Bean
    BajarRespaldo bajarRespaldo(Volcador volcador, Archivos archivos, RepositorioRespaldos respaldos,
                                ConfiguracionDeRespaldo configuracion, PoliticaDeRespaldo politica, Reloj reloj) {
        return new BajarRespaldo(volcador, archivos, respaldos,
                java.nio.file.Path.of(configuracion.getCarpetaDeTrabajo()), politica, reloj);
    }

    @Bean
    ConsultarRespaldos consultarRespaldos(RepositorioRespaldos respaldos, PoliticaDeRespaldo politica, Reloj reloj) {
        return new ConsultarRespaldos(respaldos, politica, reloj);
    }

    // ── El correo del cierre (spec 0010) ─────────────────────────────────────

    @Bean
    EncolarCorreoDelCierre encolarCorreoDelCierre(RepositorioAjustesDeCorreo ajustes, RepositorioCorreos correos) {
        return new EncolarCorreoDelCierre(ajustes, correos);
    }

    /**
     * El cartero recibe el detalle del turno y el nombre de la tienda como funciones: el correo no depende de cómo la
     * caja arma su detalle, solo de que se lo den.
     */
    @Bean
    Cartero cartero(EnviadorDeCorreos enviador, ConsultarTurnos consultarTurnos, RepositorioDatosTienda tienda,
                    Reloj reloj) {
        return new Cartero(enviador, consultarTurnos::detalleDelCierre, () -> tienda.actuales().getNombreComercial(),
                new PoliticaDeReintentos(), reloj);
    }

    @Bean
    MandarCorreosPendientes mandarCorreosPendientes(RepositorioCorreos correos, Cartero cartero, Reloj reloj) {
        return new MandarCorreosPendientes(correos, cartero, reloj);
    }

    @Bean
    ConsultarCorreos consultarCorreos(RepositorioAjustesDeCorreo ajustes, RepositorioCorreos correos,
                                      EnviadorDeCorreos enviador) {
        return new ConsultarCorreos(ajustes, correos, enviador);
    }

    @Bean
    AdministrarCorreos administrarCorreos(RepositorioAjustesDeCorreo ajustes, RepositorioCorreos correos,
                                          Cartero cartero, Reloj reloj) {
        return new AdministrarCorreos(ajustes, correos, cartero, reloj);
    }

}

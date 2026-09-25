package com.workshopmanagement.rdmotors.integracion;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * La pregunta que la suite normal no puede hacer: <b>¿la migración funciona en una base que YA
 * tiene filas?</b>
 *
 * <p>Las demás pruebas de integración arrancan con la base vacía y todas las migraciones aplicadas
 * de una vez. Así nunca se enteran de que una columna {@code NOT NULL} nueva revienta sobre las
 * compras que ya existían, o de que un relleno dejó valores equivocados.
 *
 * <p>Aquí se migra por partes: hasta la versión anterior, se insertan datos como estaban entonces,
 * y se migra el resto. No levanta Spring: solo Flyway, JDBC y un Postgres real.
 */
@Testcontainers
class MigracionesIntegracionTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    /**
     * Las pruebas de V3 a V16 insertan filas con un "quién" inventado, como eran entonces. Desde la V17 la base
     * exige que sea un usuario: esas pruebas migran hasta la V16, y la V17 tiene la suya con usuarios de verdad.
     */
    private static final String ANTES_DE_V17 = "16";

    private Flyway flywayHasta(String version) {
        var config = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .cleanDisabled(false);
        return (version == null ? config : config.target(version)).load();
    }

    private Connection conexion() throws SQLException {
        return DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    @Test
    @DisplayName("V3: las compras que ya existían quedan en EFECTIVO, sin cuenta")
    void v3MarcaLasComprasViejasEnEfectivo() throws SQLException {
        Flyway hastaV2 = flywayHasta("2");
        hastaV2.clean();
        hastaV2.migrate();

        UUID proveedorId = UUID.randomUUID();
        UUID compraId = UUID.randomUUID();
        try (Connection c = conexion()) {
            try (PreparedStatement ps = c.prepareStatement(
                    "insert into proveedor (id, nombre) values (?, 'Importadora Jotapartes')")) {
                ps.setObject(1, proveedorId);
                ps.executeUpdate();
            }
            // La compra tal como se guardaba antes de V3: sin forma de pago.
            try (PreparedStatement ps = c.prepareStatement("""
                    insert into compra (id, proveedor_id, fecha_documento, fecha_registro,
                                        numero_factura, total, registrado_por_id)
                    values (?, ?, date '2026-08-13', ?, 'FV-VIEJA', 238172, ?)
                    """)) {
                ps.setObject(1, compraId);
                ps.setObject(2, proveedorId);
                ps.setObject(3, OffsetDateTime.parse("2026-09-11T20:00:00Z"));
                ps.setObject(4, UUID.randomUUID());
                ps.executeUpdate();
            }
        }

        flywayHasta(ANTES_DE_V17).migrate();

        try (Connection c = conexion();
             PreparedStatement ps = c.prepareStatement(
                     "select forma_pago, cuenta_id from compra where id = ?")) {
            ps.setObject(1, compraId);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString("forma_pago")).isEqualTo("EFECTIVO");
                assertThat(rs.getObject("cuenta_id")).isNull();
            }
        }
    }

    @Test
    @DisplayName("V4: los renglones que ya existían reciben su posición en el orden en que se insertaron")
    void v4NumeraLosRenglonesViejos() throws SQLException {
        Flyway hastaV3 = flywayHasta("3");
        hastaV3.clean();
        hastaV3.migrate();

        UUID proveedorId = UUID.randomUUID();
        UUID productoId = UUID.randomUUID();
        UUID compraId = UUID.randomUUID();
        UUID[] lineas = {UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()};
        try (Connection c = conexion()) {
            ejecutar(c, "insert into proveedor (id, nombre) values (?, 'Jotapartes')", proveedorId);
            ejecutar(c, "insert into producto (id, nombre) values (?, 'FILTRO ACEITE')", productoId);
            ejecutar(c, """
                    insert into compra (id, proveedor_id, fecha_documento, fecha_registro, total,
                                        registrado_por_id, forma_pago)
                    values (?, ?, date '2026-08-13', now(), 3000, gen_random_uuid(), 'EFECTIVO')
                    """, compraId, proveedorId);
            for (int i = 0; i < lineas.length; i++) {
                UUID varianteId = UUID.randomUUID();
                ejecutar(c, """
                        insert into variante (id, producto_id, codigo, marca_repuesto, precio)
                        values (?, ?, ?, 'INOKI', 5000)
                        """, varianteId, productoId, "COD-" + i);
                // Un renglón de antes de V4: sin posición.
                ejecutar(c, """
                        insert into linea_compra (id, compra_id, variante_id, cantidad, costo_total,
                                                  costo_unitario, modo_captura)
                        values (?, ?, ?, 1, 1000, 1000, 'TOTAL')
                        """, lineas[i], compraId, varianteId);
            }
        }

        flywayHasta(ANTES_DE_V17).migrate();

        try (Connection c = conexion();
             PreparedStatement ps = c.prepareStatement(
                     "select id, posicion from linea_compra where compra_id = ? order by posicion")) {
            ps.setObject(1, compraId);
            try (ResultSet rs = ps.executeQuery()) {
                for (int esperada = 0; esperada < lineas.length; esperada++) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getInt("posicion")).isEqualTo(esperada);
                    assertThat(rs.getObject("id")).isEqualTo(lineas[esperada]);
                }
                assertThat(rs.next()).isFalse();
            }
        }
    }

    @Test
    @DisplayName("V6: la secuencia sigue el orden de las fechas, no el físico, y cada renglón encuentra su entrada")
    void v6NumeraElKardexYEmparejaRenglones() throws SQLException {
        Flyway hastaV5 = flywayHasta("5");
        hastaV5.clean();
        hastaV5.migrate();

        UUID proveedorId = UUID.randomUUID();
        UUID productoId = UUID.randomUUID();
        UUID varianteId = UUID.randomUUID();
        UUID compraVieja = UUID.randomUUID();
        UUID compraNueva = UUID.randomUUID();
        UUID lineaVieja = UUID.randomUUID();
        UUID lineaNueva = UUID.randomUUID();
        UUID entradaVieja = UUID.randomUUID();
        UUID entradaNueva = UUID.randomUUID();
        try (Connection c = conexion()) {
            ejecutar(c, "insert into proveedor (id, nombre) values (?, 'Jotapartes')", proveedorId);
            ejecutar(c, "insert into producto (id, nombre) values (?, 'FILTRO ACEITE')", productoId);
            ejecutar(c, """
                    insert into variante (id, producto_id, codigo, marca_repuesto, precio, stock, costo_promedio)
                    values (?, ?, 'COD-1', 'INOKI', 5000, 20, 1500)
                    """, varianteId, productoId);
            for (UUID compra : new UUID[] {compraVieja, compraNueva}) {
                ejecutar(c, """
                        insert into compra (id, proveedor_id, fecha_documento, fecha_registro, total,
                                            registrado_por_id, forma_pago)
                        values (?, ?, date '2026-08-13', now(), 10000, gen_random_uuid(), 'EFECTIVO')
                        """, compra, proveedorId);
            }
            ejecutar(c, """
                    insert into linea_compra (id, compra_id, variante_id, posicion, cantidad, costo_total,
                                              costo_unitario, modo_captura)
                    values (?, ?, ?, 0, 10, 10000, 1000, 'UNITARIO'), (?, ?, ?, 0, 10, 20000, 2000, 'UNITARIO')
                    """, lineaVieja, compraVieja, varianteId, lineaNueva, compraNueva, varianteId);
            // A propósito al revés: primero se inserta la entrada MÁS NUEVA. El orden físico de la
            // tabla no es el cronológico, y la secuencia tiene que seguir la fecha.
            ejecutar(c, """
                    insert into movimiento_kardex (id, variante_id, tipo, cantidad_delta, costo_unitario,
                        costo_total, saldo_despues, costo_promedio_despues, origen_tipo, origen_id,
                        registrado_por_id, creado_en)
                    values (?, ?, 'COMPRA', 10, 2000, 20000, 20, 1500, 'COMPRA', ?, gen_random_uuid(), timestamptz '2026-09-02 10:00:00+00'),
                           (?, ?, 'COMPRA', 10, 1000, 10000, 10, 1000, 'COMPRA', ?, gen_random_uuid(), timestamptz '2026-09-01 10:00:00+00')
                    """, entradaNueva, varianteId, compraNueva, entradaVieja, varianteId, compraVieja);
        }

        flywayHasta(ANTES_DE_V17).migrate();

        try (Connection c = conexion()) {
            assertThat(valor(c, "select secuencia from movimiento_kardex where id = ?", entradaVieja))
                    .isEqualTo(1L);
            assertThat(valor(c, "select secuencia from movimiento_kardex where id = ?", entradaNueva))
                    .isEqualTo(2L);
            assertThat(valor(c, "select movimiento_entrada_id from linea_compra where id = ?", lineaVieja))
                    .isEqualTo(entradaVieja);
            assertThat(valor(c, "select movimiento_entrada_id from linea_compra where id = ?", lineaNueva))
                    .isEqualTo(entradaNueva);
            assertThat(valor(c, "select vigente from linea_compra where id = ?", lineaVieja)).isEqualTo(true);

            // La identidad arranca después de la última: el siguiente movimiento es el 3, y el
            // CHECK del tipo ya acepta la reversión de compra.
            ejecutar(c, """
                    insert into movimiento_kardex (id, variante_id, tipo, cantidad_delta, costo_unitario,
                        costo_total, saldo_despues, costo_promedio_despues, origen_tipo, origen_id,
                        registrado_por_id, creado_en)
                    values (?, ?, 'CORRECCION_COMPRA', -10, 1000, 10000, 10, 2000, 'COMPRA', ?, gen_random_uuid(), now())
                    """, UUID.randomUUID(), varianteId, compraVieja);
            assertThat(valor(c, "select max(secuencia) from movimiento_kardex where variante_id = ?", varianteId))
                    .isEqualTo(3L);
        }
    }

    @Test
    @DisplayName("V12 a V14 sobre la base de QA: el turno abierto sigue abierto y sin cifras, las compras viejas no son del cajón, y la auditoría acepta las acciones de caja")
    void v12AV14SobreUnaBaseConFilas() throws SQLException {
        Flyway hastaV11 = flywayHasta("11");
        hastaV11.clean();
        hastaV11.migrate();

        UUID proveedorId = UUID.randomUUID();
        UUID compraId = UUID.randomUUID();
        UUID turnoId = UUID.randomUUID();
        try (Connection c = conexion()) {
            ejecutar(c, "insert into proveedor (id, nombre) values (?, 'Jotapartes')", proveedorId);
            ejecutar(c, """
                    insert into compra (id, proveedor_id, fecha_documento, fecha_registro, total,
                                        registrado_por_id, forma_pago)
                    values (?, ?, date '2026-09-14', now(), 50000, gen_random_uuid(), 'EFECTIVO')
                    """, compraId, proveedorId);
            // Como el turno de QA: abierto desde el 14 de septiembre.
            ejecutar(c, """
                    insert into turno_caja (id, abierto_por_id, abierto_en, fondo, estado)
                    values (?, gen_random_uuid(), timestamptz '2026-09-14 13:00:00+00', 100000, 'ABIERTO')
                    """, turnoId);
        }

        flywayHasta(ANTES_DE_V17).migrate();

        try (Connection c = conexion()) {
            assertThat(valor(c, "select pagada_de_caja from compra where id = ?", compraId)).isEqualTo(false);
            assertThat(valor(c, "select turno_id is null from compra where id = ?", compraId)).isEqualTo(true);
            assertThat(valor(c, "select estado from turno_caja where id = ?", turnoId)).isEqualTo("ABIERTO");
            assertThat(valor(c, "select esperado is null and contado is null and observaciones is null "
                    + "from turno_caja where id = ?", turnoId)).isEqualTo(true);
            assertThat(valor(c, "select count(*) from categoria_gasto where naturaleza = ?", "GASTO")).isEqualTo(11L);

            // El cierre de ese turno cabe en las columnas nuevas, y la auditoría conoce la acción.
            ejecutar(c, """
                    update turno_caja set estado = 'CERRADO', cerrado_en = now(), cerrado_por_id = gen_random_uuid(),
                        ventas_efectivo = 223400, ventas_transferencia = 111000, descuentos = 0,
                        devoluciones_efectivo = 70000, gastos_cajon = 15000, retiros = 100000, compras_cajon = 50000,
                        esperado = 88400, contado = 87000, diferencia = -1400
                    where id = ?
                    """, turnoId);
            ejecutar(c, """
                    insert into evento_auditoria (id, ocurrido_en, usuario_id, accion, entidad_tipo, entidad_id)
                    values (gen_random_uuid(), now(), gen_random_uuid(), 'CERRAR_CAJA_CON_DIFERENCIA', 'TURNO_CAJA', ?)
                    """, turnoId);
            // Una compra nueva tiene que decir si fue del cajón: el default solo rellenó las viejas.
            try (PreparedStatement ps = c.prepareStatement("""
                    insert into compra (id, proveedor_id, fecha_documento, fecha_registro, total, registrado_por_id,
                                        forma_pago)
                    values (gen_random_uuid(), ?, date '2026-09-16', now(), 1000, gen_random_uuid(), 'EFECTIVO')
                    """)) {
                ps.setObject(1, proveedorId);
                org.assertj.core.api.Assertions.assertThatThrownBy(ps::executeUpdate)
                        .hasMessageContaining("pagada_de_caja");
            }
        }
    }

    @Test
    @DisplayName("V15 sobre una base con gastos: los que existían quedan de su día, y las cuatro categorías del mes quedan mensuales")
    void v15GastosDelMes() throws SQLException {
        Flyway hastaV14 = flywayHasta("14");
        hastaV14.clean();
        hastaV14.migrate();

        UUID gastoId = UUID.randomUUID();
        try (Connection c = conexion()) {
            ejecutar(c, """
                    insert into gasto (id, categoria_id, monto, descripcion, del_cajon, forma_pago, fecha,
                                       registrado_por_id, registrado_en, llave_idempotencia)
                    select ?, id, 800000, 'Arriendo de agosto', false, 'EFECTIVO', date '2026-08-01',
                           gen_random_uuid(), now(), gen_random_uuid()
                    from categoria_gasto where nombre = 'Arriendo'
                    """, gastoId);
        }

        flywayHasta(ANTES_DE_V17).migrate();

        try (Connection c = conexion()) {
            assertThat(valor(c, "select del_mes from gasto where id = ?", gastoId)).isEqualTo(false);
            assertThat(valor(c, "select string_agg(nombre, ', ' order by nombre) from categoria_gasto where mensual = ?", true))
                    .isEqualTo("Arriendo, Internet y teléfono, Nómina, Servicios públicos");
            // Un gasto nuevo tiene que decir si es del mes: el default solo rellenó los viejos.
            try (PreparedStatement ps = c.prepareStatement("""
                    insert into gasto (id, categoria_id, monto, descripcion, del_cajon, forma_pago, fecha,
                                       registrado_por_id, registrado_en, llave_idempotencia)
                    select gen_random_uuid(), id, 1000, 'x', false, 'EFECTIVO', current_date,
                           gen_random_uuid(), now(), gen_random_uuid()
                    from categoria_gasto where nombre = 'Otros'
                    """)) {
                org.assertj.core.api.Assertions.assertThatThrownBy(ps::executeUpdate).hasMessageContaining("del_mes");
            }
        }
    }

    @Test
    @DisplayName("V16 sobre una base con datos: la tabla de usuarios no repite un usuario sin distinguir mayúsculas, y la auditoría acepta las acciones de usuarios")
    void v16Usuarios() throws SQLException {
        Flyway hastaV15 = flywayHasta("15");
        hastaV15.clean();
        hastaV15.migrate();

        flywayHasta(ANTES_DE_V17).migrate();

        try (Connection c = conexion()) {
            String insert = """
                    insert into usuario (id, usuario, usuario_normalizado, nombre, hash, rol, activo,
                                         debe_cambiar_contrasena, version_sesion, intentos_fallidos, creado_en)
                    values (gen_random_uuid(), ?, 'carolina', 'Carolina', '{bcrypt}x', 'CAJERO', true, false, 1, 0, now())
                    """;
            try (PreparedStatement ps = c.prepareStatement(insert)) {
                ps.setString(1, "carolina");
                ps.executeUpdate();
                ps.setString(1, "Carolina");
                org.assertj.core.api.Assertions.assertThatThrownBy(ps::executeUpdate)
                        .hasMessageContaining("ux_usuario_normalizado");
            }
            try (PreparedStatement ps = c.prepareStatement("""
                    insert into usuario (id, usuario, usuario_normalizado, nombre, hash, rol, activo,
                                         debe_cambiar_contrasena, version_sesion, intentos_fallidos, creado_en)
                    values (gen_random_uuid(), 'x', 'x', 'X', 'h', 'DUENO', true, false, 1, 0, now())
                    """)) {
                org.assertj.core.api.Assertions.assertThatThrownBy(ps::executeUpdate).hasMessageContaining("rol");
            }
            ejecutar(c, """
                    insert into evento_auditoria (id, ocurrido_en, usuario_id, accion, entidad_tipo, entidad_id)
                    values (gen_random_uuid(), now(), gen_random_uuid(), 'CREAR_USUARIO', 'USUARIO', ?)
                    """, UUID.randomUUID());
        }
    }

    @Test
    @DisplayName("V17 sobre una base con datos: lo que ya estaba a nombre de un usuario sigue igual, y un \"quién\" inventado se rechaza")
    void v17QuienEsUnUsuario() throws SQLException {
        Flyway hastaV16 = flywayHasta("16");
        hastaV16.clean();
        hastaV16.migrate();

        UUID carolina = UUID.randomUUID();
        UUID proveedorId = UUID.randomUUID();
        UUID compraId = UUID.randomUUID();
        UUID turnoId = UUID.randomUUID();
        try (Connection c = conexion()) {
            ejecutar(c, """
                    insert into usuario (id, usuario, usuario_normalizado, nombre, hash, rol, activo,
                                         debe_cambiar_contrasena, version_sesion, intentos_fallidos, creado_en)
                    values (?, 'carolina', 'carolina', 'Carolina', '{bcrypt}x', 'CAJERO', true, false, 1, 0, now())
                    """, carolina);
            ejecutar(c, "insert into proveedor (id, nombre) values (?, 'Jotapartes')", proveedorId);
            ejecutar(c, """
                    insert into compra (id, proveedor_id, fecha_documento, fecha_registro, total,
                                        registrado_por_id, forma_pago, pagada_de_caja)
                    values (?, ?, date '2026-09-18', now(), 50000, ?, 'EFECTIVO', false)
                    """, compraId, proveedorId, carolina);
            ejecutar(c, """
                    insert into turno_caja (id, abierto_por_id, abierto_en, fondo, estado)
                    values (?, ?, now(), 100000, 'ABIERTO')
                    """, turnoId, carolina);
        }

        flywayHasta(null).migrate();

        try (Connection c = conexion()) {
            assertThat(valor(c, "select registrado_por_id from compra where id = ?", compraId)).isEqualTo(carolina);
            assertThat(valor(c, "select abierto_por_id from turno_caja where id = ?", turnoId)).isEqualTo(carolina);
            // Las doce columnas de "quién", cada una con su llave hacia usuario.
            assertThat(valor(c, """
                    select count(*) from information_schema.referential_constraints r
                    join information_schema.constraint_column_usage u on u.constraint_name = r.unique_constraint_name
                    where u.table_name = ? and r.constraint_name like 'fk\\_%'
                    """, "usuario")).isEqualTo(12L);

            try (PreparedStatement ps = c.prepareStatement("""
                    insert into compra (id, proveedor_id, fecha_documento, fecha_registro, total,
                                        registrado_por_id, forma_pago, pagada_de_caja, llave_idempotencia)
                    values (gen_random_uuid(), ?, date '2026-09-18', now(), 1000, gen_random_uuid(), 'EFECTIVO',
                            false, gen_random_uuid())
                    """)) {
                ps.setObject(1, proveedorId);
                org.assertj.core.api.Assertions.assertThatThrownBy(ps::executeUpdate)
                        .hasMessageContaining("fk_compra_registrado_por");
            }
            try (PreparedStatement ps = c.prepareStatement("""
                    insert into evento_auditoria (id, ocurrido_en, usuario_id, accion, entidad_tipo, entidad_id)
                    values (gen_random_uuid(), now(), gen_random_uuid(), 'CREAR_USUARIO', 'USUARIO', ?)
                    """)) {
                ps.setObject(1, carolina);
                org.assertj.core.api.Assertions.assertThatThrownBy(ps::executeUpdate)
                        .hasMessageContaining("fk_auditoria_usuario");
            }
        }
    }

    @Test
    @DisplayName("V19: el registro de entradas acepta un usuario que no existe (nadie se llama así), pero no un id inventado")
    void v19RegistroDeEntradas() throws SQLException {
        Flyway hastaV18 = flywayHasta("18");
        hastaV18.clean();
        hastaV18.migrate();
        UUID carolina = UUID.randomUUID();
        try (Connection c = conexion()) {
            ejecutar(c, """
                    insert into usuario (id, usuario, usuario_normalizado, nombre, hash, rol, activo,
                                         debe_cambiar_contrasena, version_sesion, intentos_fallidos, creado_en)
                    values (?, 'carolina', 'carolina', 'Carolina', '{bcrypt}x', 'CAJERO', true, false, 1, 0, now())
                    """, carolina);
        }

        flywayHasta(null).migrate();

        try (Connection c = conexion()) {
            ejecutar(c, """
                    insert into entrada (id, usuario_id, usuario_escrito, exito, momento, ip, navegador)
                    values (gen_random_uuid(), ?, 'carolina', true, now(), '192.168.1.50', 'Chrome')
                    """, carolina);
            // Un intento con un usuario que no existe: queda escrito, sin apuntar a nadie.
            ejecutar(c, """
                    insert into entrada (id, usuario_id, usuario_escrito, exito, momento, ip, navegador)
                    values (gen_random_uuid(), null, 'admin', false, now(), '192.168.1.99', null)
                    """);
            assertThat(valor(c, "select count(*) from entrada where usuario_escrito = ?", "admin")).isEqualTo(1L);

            try (PreparedStatement ps = c.prepareStatement("""
                    insert into entrada (id, usuario_id, usuario_escrito, exito, momento)
                    values (gen_random_uuid(), gen_random_uuid(), 'x', false, now())
                    """)) {
                org.assertj.core.api.Assertions.assertThatThrownBy(ps::executeUpdate)
                        .hasMessageContaining("entrada_usuario_id_fkey");
            }
        }
    }

    @Test
    @DisplayName("V20 sobre una base con ventas: quedan sin cliente y con $0 fiado, y el contador de abonos arranca en 0")
    void v20ClientesYFiado() throws SQLException {
        Flyway hastaV19 = flywayHasta("19");
        hastaV19.clean();
        hastaV19.migrate();
        UUID carolina = UUID.randomUUID();
        UUID turnoId = UUID.randomUUID();
        UUID ventaId = UUID.randomUUID();
        try (Connection c = conexion()) {
            ejecutar(c, """
                    insert into usuario (id, usuario, usuario_normalizado, nombre, hash, rol, activo,
                                         debe_cambiar_contrasena, version_sesion, intentos_fallidos, creado_en)
                    values (?, 'carolina', 'carolina', 'Carolina', '{bcrypt}x', 'CAJERO', true, false, 1, 0, now())
                    """, carolina);
            ejecutar(c, """
                    insert into turno_caja (id, abierto_por_id, abierto_en, fondo, estado)
                    values (?, ?, now(), 100000, 'ABIERTO')
                    """, turnoId, carolina);
            ejecutar(c, """
                    insert into venta (id, numero, turno_id, vendido_por_id, cobrada_en, subtotal, total, estado,
                                       llave_idempotencia)
                    values (?, 1, ?, ?, now(), 38000, 38000, 'COBRADA', gen_random_uuid())
                    """, ventaId, turnoId, carolina);
            ejecutar(c, "insert into pago_venta (id, venta_id, forma, monto) values (gen_random_uuid(), ?, 'EFECTIVO', 38000)",
                    ventaId);
        }

        flywayHasta(null).migrate();

        try (Connection c = conexion()) {
            assertThat(valor(c, "select fiado = 0 and cliente_id is null from venta where id = ?", ventaId))
                    .isEqualTo(true);
            assertThat(valor(c, "select ultimo from consecutivo where nombre = ?", "ABONO")).isEqualTo(0L);
            // Fiar la venta de antes sin decir a quién no se puede, ni desde la base.
            try (PreparedStatement ps = c.prepareStatement("update venta set fiado = 1000 where id = ?")) {
                ps.setObject(1, ventaId);
                org.assertj.core.api.Assertions.assertThatThrownBy(ps::executeUpdate)
                        .hasMessageContaining("ck_venta_fiado");
            }
            ejecutar(c, """
                    insert into evento_auditoria (id, ocurrido_en, usuario_id, accion, entidad_tipo, entidad_id)
                    values (gen_random_uuid(), now(), ?, 'CORREGIR_CLIENTE', 'CLIENTE', gen_random_uuid())
                    """, carolina);
        }
    }

    @Test
    @DisplayName("V21 sobre una base con turnos cerrados: las partes nuevas nacen en $0 y el cierre sigue cuadrando")
    void v21AbonosEnElCajon() throws SQLException {
        Flyway hastaV20 = flywayHasta("20");
        hastaV20.clean();
        hastaV20.migrate();
        UUID carolina = UUID.randomUUID();
        UUID turnoId = UUID.randomUUID();
        try (Connection c = conexion()) {
            ejecutar(c, """
                    insert into usuario (id, usuario, usuario_normalizado, nombre, hash, rol, activo,
                                         debe_cambiar_contrasena, version_sesion, intentos_fallidos, creado_en)
                    values (?, 'carolina', 'carolina', 'Carolina', '{bcrypt}x', 'CAJERO', true, false, 1, 0, now())
                    """, carolina);
            // Un turno cerrado como los de QA: con sus cifras de antes de los abonos.
            ejecutar(c, """
                    insert into turno_caja (id, abierto_por_id, abierto_en, fondo, estado, cerrado_por_id, cerrado_en,
                                            ventas_efectivo, ventas_transferencia, descuentos, devoluciones_efectivo,
                                            gastos_cajon, retiros, compras_cajon, esperado, contado, diferencia)
                    values (?, ?, now(), 100000, 'CERRADO', ?, now(), 223400, 111000, 0, 70000, 15000, 100000, 50000,
                            88400, 87000, -1400)
                    """, turnoId, carolina, carolina);
        }

        flywayHasta(null).migrate();

        try (Connection c = conexion()) {
            assertThat(valor(c, "select ventas_fiado = 0 and abonos_efectivo = 0 and abonos_transferencia = 0 "
                    + "from turno_caja where id = ?", turnoId)).isEqualTo(true);
            assertThat(valor(c, "select esperado from turno_caja where id = ?", turnoId))
                    .isEqualTo(new java.math.BigDecimal("88400.00"));
            // Un cierre nuevo tiene que traer las partes nuevas: el default solo rellenó los viejos.
            try (PreparedStatement ps = c.prepareStatement("""
                    insert into turno_caja (id, abierto_por_id, abierto_en, fondo, estado, cerrado_por_id, cerrado_en,
                                            ventas_efectivo, ventas_transferencia, descuentos, devoluciones_efectivo,
                                            gastos_cajon, retiros, compras_cajon, esperado, contado, diferencia)
                    values (gen_random_uuid(), ?, now(), 0, 'CERRADO', ?, now(), 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)
                    """)) {
                ps.setObject(1, carolina);
                ps.setObject(2, carolina);
                org.assertj.core.api.Assertions.assertThatThrownBy(ps::executeUpdate)
                        .hasMessageContaining("ck_turno_montos_de_cierre");
            }
            // Y el esperado tiene que contar los abonos en efectivo.
            try (PreparedStatement ps = c.prepareStatement(
                    "update turno_caja set abonos_efectivo = 30000 where id = ?")) {
                ps.setObject(1, turnoId);
                org.assertj.core.api.Assertions.assertThatThrownBy(ps::executeUpdate)
                        .hasMessageContaining("ck_turno_esperado_suma_sus_partes");
            }
        }
    }

    @Test
    @DisplayName("V22 sobre una base con compras: cada compra vieja recibe su propia llave y el índice único aguanta")
    void v22LlaveEnCompras() throws SQLException {
        Flyway hastaV21 = flywayHasta("21");
        hastaV21.clean();
        hastaV21.migrate();
        UUID carolina = UUID.randomUUID();
        UUID proveedorId = UUID.randomUUID();
        UUID primera = UUID.randomUUID();
        UUID segunda = UUID.randomUUID();
        try (Connection c = conexion()) {
            ejecutar(c, """
                    insert into usuario (id, usuario, usuario_normalizado, nombre, hash, rol, activo,
                                         debe_cambiar_contrasena, version_sesion, intentos_fallidos, creado_en)
                    values (?, 'carolina', 'carolina', 'Carolina', '{bcrypt}x', 'CAJERO', true, false, 1, 0, now())
                    """, carolina);
            ejecutar(c, "insert into proveedor (id, nombre) values (?, 'Importadora Jotapartes')", proveedorId);
            // Dos compras como las de QA: sin llave, porque antes de V22 no existía.
            for (UUID id : new UUID[]{primera, segunda}) {
                ejecutar(c, """
                        insert into compra (id, proveedor_id, fecha_documento, fecha_registro, numero_factura,
                                            total, registrado_por_id, forma_pago, pagada_de_caja)
                        values (?, ?, date '2026-08-13', now(), 'FV-VIEJA', 238172, ?, 'EFECTIVO', false)
                        """, id, proveedorId, carolina);
            }
        }

        flywayHasta(null).migrate();

        try (Connection c = conexion()) {
            // Cada una con la suya: un DEFAULT fijo habría hecho chocar la segunda contra el índice único.
            assertThat(valor(c, "select llave_idempotencia is not null from compra where id = ?", primera))
                    .isEqualTo(true);
            assertThat(valor(c, """
                    select (select llave_idempotencia from compra where id = ?)
                         <> (select llave_idempotencia from compra where id = ?)
                    """, primera, segunda)).isEqualTo(true);

            // De aquí en adelante la llave la pone quien registra: sin ella, la compra no entra.
            try (PreparedStatement ps = c.prepareStatement("""
                    insert into compra (id, proveedor_id, fecha_documento, fecha_registro, total,
                                        registrado_por_id, forma_pago, pagada_de_caja)
                    values (gen_random_uuid(), ?, date '2026-09-21', now(), 1000, ?, 'EFECTIVO', false)
                    """)) {
                ps.setObject(1, proveedorId);
                ps.setObject(2, carolina);
                org.assertj.core.api.Assertions.assertThatThrownBy(ps::executeUpdate)
                        .hasMessageContaining("llave_idempotencia");
            }

            // Y la misma llave dos veces no entra, aunque alguien se salte el caso de uso.
            try (PreparedStatement ps = c.prepareStatement(
                    "update compra set llave_idempotencia = (select llave_idempotencia from compra where id = ?) "
                    + "where id = ?")) {
                ps.setObject(1, primera);
                ps.setObject(2, segunda);
                org.assertj.core.api.Assertions.assertThatThrownBy(ps::executeUpdate)
                        .hasMessageContaining("ux_compra_llave");
            }
        }
    }

    @Test
    @DisplayName("V23: la tabla de respaldos no deja decir «hecho» sin tamaño, ni «falló» sin el porqué")
    void v23Respaldo() throws SQLException {
        Flyway hastaV22 = flywayHasta("22");
        hastaV22.clean();
        hastaV22.migrate();

        flywayHasta(null).migrate();

        try (Connection c = conexion()) {
            ejecutar(c, """
                    insert into respaldo (id, hecho_en, archivo, bytes, duracion_ms, estado, origen)
                    values (gen_random_uuid(), now(), 'C:/respaldos/x.dump', 4096, 900, 'HECHO', 'AUTOMATICO')
                    """);
            ejecutar(c, """
                    insert into respaldo (id, hecho_en, archivo, duracion_ms, estado, error, origen)
                    values (gen_random_uuid(), now(), 'C:/respaldos/y.dump', 30, 'FALLO', 'no encontré pg_dump',
                            'AUTOMATICO')
                    """);

            // Un "hecho" sin tamaño sería una fila que dice que hay copia sin que exista nada.
            try (PreparedStatement ps = c.prepareStatement("""
                    insert into respaldo (id, hecho_en, archivo, duracion_ms, estado, origen)
                    values (gen_random_uuid(), now(), 'C:/respaldos/z.dump', 900, 'HECHO', 'AUTOMATICO')
                    """)) {
                org.assertj.core.api.Assertions.assertThatThrownBy(ps::executeUpdate)
                        .hasMessageContaining("ck_respaldo_hecho_o_fallo");
            }
            // Y un "falló" sin el porqué no le sirve a nadie.
            try (PreparedStatement ps = c.prepareStatement("""
                    insert into respaldo (id, hecho_en, archivo, duracion_ms, estado, origen)
                    values (gen_random_uuid(), now(), 'C:/respaldos/z.dump', 30, 'FALLO', 'AUTOMATICO')
                    """)) {
                org.assertj.core.api.Assertions.assertThatThrownBy(ps::executeUpdate)
                        .hasMessageContaining("ck_respaldo_hecho_o_fallo");
            }
            assertThat(valor(c, "select count(*) from respaldo where estado = ?", "HECHO")).isEqualTo(1L);
        }
    }

    @Test
    @DisplayName("V25: el respaldo deja de vivir en un disco nuestro, Y LAS FILAS QUE YA ESTABAN SOBREVIVEN")
    void v25ElRespaldoYaNoViveEnUnDisco() throws SQLException {
        Flyway hastaV24 = flywayHasta("24");
        hastaV24.clean();
        hastaV24.migrate();

        // Una base como la de QA: con copias viejas, su memoria USB anotada y una ya podada del disco.
        try (Connection c = conexion()) {
            ejecutar(c, """
                    insert into respaldo (id, hecho_en, archivo, bytes, duracion_ms, estado, origen,
                                          segunda_copia, aviso_segunda_copia, archivo_borrado_en)
                    values (gen_random_uuid(), now(), 'C:/respaldos/vieja.dump', 4096, 900, 'HECHO', 'AUTOMATICO',
                            'E:/respaldos/vieja.dump', 'la memoria no estaba puesta', now())
                    """);
        }

        flywayHasta(null).migrate();

        try (Connection c = conexion()) {
            // La fila se queda: es la historia de que ese día sí hubo copia, y es lo que enciende el aviso.
            assertThat(valor(c, "select count(*) from respaldo")).isEqualTo(1L);
            assertThat(valor(c, "select archivo from respaldo")).isEqualTo("C:/respaldos/vieja.dump");
            // Y lo que hablaba de discos nuestros ya no está.
            for (String columna : new String[] { "segunda_copia", "aviso_segunda_copia", "archivo_borrado_en" }) {
                assertThat(valor(c, """
                        select count(*) from information_schema.columns
                        where table_name = 'respaldo' and column_name = ?
                        """, columna)).as(columna).isEqualTo(0L);
            }
        }
    }

    @Test
    @DisplayName("V26 sobre una base con compras: solo agrega; un borrador guarda renglones malos, y lo confirmado exige su compra")
    void v26CargaDeInventario() throws SQLException {
        Flyway hastaV25 = flywayHasta("25");
        hastaV25.clean();
        hastaV25.migrate();
        UUID ruben = UUID.randomUUID();
        UUID proveedorId = UUID.randomUUID();
        try (Connection c = conexion()) {
            ejecutar(c, """
                    insert into usuario (id, usuario, usuario_normalizado, nombre, hash, rol, activo,
                                         debe_cambiar_contrasena, version_sesion, intentos_fallidos, creado_en)
                    values (?, 'ruben', 'ruben', 'Rubén', '{bcrypt}x', 'ADMINISTRADOR', true, false, 1, 0, now())
                    """, ruben);
            ejecutar(c, "insert into proveedor (id, nombre) values (?, 'Importadora Jotapartes')", proveedorId);
            ejecutar(c, """
                    insert into compra (id, proveedor_id, fecha_documento, fecha_registro, numero_factura, total,
                                        registrado_por_id, forma_pago, pagada_de_caja, llave_idempotencia)
                    values (gen_random_uuid(), ?, date '2026-09-20', now(), 'DE-PRUEBA', 238172, ?, 'EFECTIVO',
                            false, gen_random_uuid())
                    """, proveedorId, ruben);
        }

        flywayHasta(null).migrate();

        String nuevaCarga = """
                insert into carga_inventario (id, estado, origen, nit_proveedor, numero_factura, subtotal_leido,
                                              iva_pct, ganancia_pct, redondeo, creada_por_id, creada_en,
                                              modificada_en, cerrada_por_id, cerrada_en)
                values (?, ?, 'PDF_JOTAPARTES', '900576528', 'MAG477', true, 19, 45, 100, ?, now(), now(), ?, ?)
                """;
        try (Connection c = conexion()) {
            assertThat(valor(c, "select count(*) from compra")).isEqualTo(1L);

            // Un borrador guarda lo leído aunque esté mal: sin código y sin cantidad. Es para mostrarlo marcado.
            UUID borrador = UUID.randomUUID();
            ejecutar(c, nuevaCarga, borrador, "BORRADOR", ruben, null, null);
            ejecutar(c, """
                    insert into renglon_carga (id, carga_id, posicion, codigo, cantidad, valor_total,
                                               marca_propuesta, categoria_propuesta, ajustado_a_mano, quitado,
                                               aplicar_precio_nuevo)
                    values (gen_random_uuid(), ?, 0, null, null, 308274, false, false, false, false, false)
                    """, borrador);

            // Dos borradores de la misma factura no: confirmar los dos entraría la mercancía dos veces.
            try (PreparedStatement ps = c.prepareStatement(nuevaCarga)) {
                ps.setObject(1, UUID.randomUUID());
                ps.setObject(2, "BORRADOR");
                ps.setObject(3, ruben);
                ps.setObject(4, null);
                ps.setObject(5, null);
                org.assertj.core.api.Assertions.assertThatThrownBy(ps::executeUpdate)
                        .hasMessageContaining("ux_carga_factura_en_borrador");
            }
            // Confirmada sin la compra que dejó, tampoco.
            try (PreparedStatement ps = c.prepareStatement(nuevaCarga)) {
                ps.setObject(1, UUID.randomUUID());
                ps.setObject(2, "CONFIRMADA");
                ps.setObject(3, ruben);
                ps.setObject(4, ruben);
                ps.setObject(5, java.sql.Timestamp.from(java.time.Instant.now()));
                org.assertj.core.api.Assertions.assertThatThrownBy(ps::executeUpdate)
                        .hasMessageContaining("ck_carga_confirmada_con_compra");
            }
            // Una descartada de la misma factura sí convive con el borrador: se descartó para subirla de nuevo.
            ejecutar(c, nuevaCarga, UUID.randomUUID(), "DESCARTADA", ruben, ruben,
                    java.sql.Timestamp.from(java.time.Instant.now()));
            assertThat(valor(c, "select count(*) from carga_inventario")).isEqualTo(2L);
        }
    }

    private static Object valor(Connection c, String sql, Object... parametros) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < parametros.length; i++) {
                ps.setObject(i + 1, parametros[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                return rs.getObject(1);
            }
        }
    }

    private static void ejecutar(Connection c, String sql, Object... parametros) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < parametros.length; i++) {
                ps.setObject(i + 1, parametros[i]);
            }
            ps.executeUpdate();
        }
    }
}

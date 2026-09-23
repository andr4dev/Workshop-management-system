package com.workshopmanagement.rdmotors.reportes.infraestructura;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.workshopmanagement.rdmotors.compartido.dominio.Actor;
import com.workshopmanagement.rdmotors.compartido.dominio.Dinero;
import com.workshopmanagement.rdmotors.compartido.infraestructura.seguridad.ActorActual;
import com.workshopmanagement.rdmotors.reportes.aplicacion.ConsultarResultados;
import com.workshopmanagement.rdmotors.reportes.aplicacion.ReporteDeResultados;
import com.workshopmanagement.rdmotors.reportes.dominio.Agrupacion;
import com.workshopmanagement.rdmotors.reportes.dominio.CarteraDelPeriodo;
import com.workshopmanagement.rdmotors.reportes.dominio.Control;
import com.workshopmanagement.rdmotors.reportes.dominio.ModoGastosDelMes;
import com.workshopmanagement.rdmotors.reportes.dominio.ResultadosDelPeriodo;

import lombok.RequiredArgsConstructor;

/**
 * ADAPTADOR DE ENTRADA — los reportes de resultados (spec 0007).
 *
 * <p>Solo lee: no lleva llave ni auditoría. Un período al revés, de más de 366 días o que termina después de hoy
 * responde 422 con el porqué.
 *
 * <p>Una sola respuesta con todo: las cifras, los desgloses, el día por día, los repuestos y las categorías, las
 * cifras del período anterior y el control. Salen del mismo cálculo; la pantalla solo ordena.
 */
@RestController
@RequestMapping("/api/reportes")
@RequiredArgsConstructor
class ReporteController {

    private final ConsultarResultados consultarResultados;

    /** Las fechas son días de Colombia, los dos incluidos. Los montos, pesos enteros. */
    @GetMapping("/resultados")
    RespuestaResultados resultados(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(defaultValue = "REPARTIDOS") ModoGastosDelMes gastosDelMes,
            @ActorActual Actor actor) {
        return RespuestaResultados.de(consultarResultados.ejecutar(desde, hasta, gastosDelMes, actor));
    }

    record RespuestaResultados(LocalDate desde, LocalDate hasta, int dias, Agrupacion agrupacion,
                               ModoGastosDelMes modoGastosDelMes, RespuestaCifras cifras,
                               List<RespuestaCategoria> costosPorCategoria, List<RespuestaCategoria> gastosPorCategoria,
                               RespuestaGastosDelMes gastosDelMes, RespuestaSinCosto sinCosto,
                               List<RespuestaFila> filas, RespuestaFila filaGastosDelMes,
                               List<RespuestaVendidos> repuestos, List<RespuestaVendidos> categoriasDeRepuesto,
                               RespuestaAnterior anterior, RespuestaControl control, RespuestaCartera cartera) {
        static RespuestaResultados de(ReporteDeResultados reporte) {
            ResultadosDelPeriodo r = reporte.resultados();
            return new RespuestaResultados(r.periodo().desde(), r.periodo().hasta(), r.periodo().dias(),
                    r.agrupacion(), r.modo(), RespuestaCifras.de(r.cifras()),
                    r.costosPorCategoria().stream().map(RespuestaCategoria::de).toList(),
                    r.gastosPorCategoria().stream().map(RespuestaCategoria::de).toList(),
                    new RespuestaGastosDelMes(pesos(r.gastosDelMes().incluidos()), pesos(r.gastosDelMes().fuera())),
                    RespuestaSinCosto.de(r.sinCosto()),
                    r.filas().stream().map(RespuestaFila::de).toList(),
                    r.filaGastosDelMes() == null ? null : RespuestaFila.de(r.filaGastosDelMes()),
                    r.repuestos().stream().map(RespuestaVendidos::de).toList(),
                    r.categoriasDeRepuesto().stream().map(RespuestaVendidos::de).toList(),
                    new RespuestaAnterior(reporte.periodoAnterior().desde(), reporte.periodoAnterior().hasta(),
                            RespuestaCifras.de(reporte.anterior())),
                    RespuestaControl.de(reporte.control()),
                    RespuestaCartera.de(reporte.cartera()));
        }
    }

    /** Un repuesto o una categoría de repuestos. {@code utilidad} y {@code margen} en blanco si hubo renglones sin costo. */
    record RespuestaVendidos(UUID id, String codigo, String nombre, String marca, String categoria, int renglones,
                             int unidades, long ventasNetas, long costo, int renglonesSinCosto, Long utilidad,
                             BigDecimal margen, boolean conPerdida) {
        static RespuestaVendidos de(ResultadosDelPeriodo.Vendidos v) {
            return new RespuestaVendidos(v.id(), v.codigo(), v.nombre(), v.marca(), v.categoria(), v.renglones(),
                    v.unidades(), pesos(v.ventasNetas()), pesos(v.costo()), v.renglonesSinCosto(),
                    v.utilidad() == null ? null : pesos(v.utilidad()), v.margen(), v.conPerdida());
        }
    }

    record RespuestaAnterior(LocalDate desde, LocalDate hasta, RespuestaCifras cifras) {
    }

    /**
     * El fiado y la cartera (spec 0008, RF-024). {@code cobrado} es de <b>este período</b>; {@code porCobrar}, lo que
     * deben hoy, sea de cuando sea.
     */
    record RespuestaCartera(long abonosEfectivo, long abonosTransferencia, long cobrado, long porCobrar,
                            int clientesQueDeben) {
        static RespuestaCartera de(CarteraDelPeriodo c) {
            return new RespuestaCartera(pesos(c.abonosEfectivo()), pesos(c.abonosTransferencia()), pesos(c.cobrado()),
                    pesos(c.porCobrar()), c.clientesQueDeben());
        }
    }

    /** {@code faltantes} y {@code sobrantes} en positivo; la {@code diferencia} de cada turno, con su signo. */
    record RespuestaControl(int ventasAnuladas, long montoAnuladas, long faltantes, long sobrantes,
                            List<RespuestaTurno> turnos) {
        static RespuestaControl de(Control c) {
            return new RespuestaControl(c.ventasAnuladas(), pesos(c.montoAnuladas()), pesos(c.faltantes()),
                    pesos(c.sobrantes()),
                    c.turnos().stream()
                            .map(t -> new RespuestaTurno(t.id(), t.abiertoEn(), t.cerradoEn(), pesos(t.esperado()),
                                    pesos(t.contado()), pesos(t.diferencia())))
                            .toList());
        }
    }

    record RespuestaTurno(UUID id, Instant abiertoEn, Instant cerradoEn, long esperado, long contado, long diferencia) {
    }

    /** {@code efectivo + transferencia + fiado = ventasNetas} (spec 0008). */
    record RespuestaCifras(int ventas, int unidades, long renglones, long descuentos, int ventasConDescuento,
                           long ventasNetas, Long ticketPromedio, long efectivo, long transferencia, long fiado,
                           long costoVendido, long costosAdicionales, long utilidadBruta, BigDecimal margenBruto,
                           long gastos, long utilidadOperativa, BigDecimal margenOperativo) {
        static RespuestaCifras de(ResultadosDelPeriodo.Cifras c) {
            return new RespuestaCifras(c.ventas(), c.unidades(), pesos(c.renglones()), pesos(c.descuentos()),
                    c.ventasConDescuento(), pesos(c.ventasNetas()),
                    c.ticketPromedio() == null ? null : pesos(c.ticketPromedio()), pesos(c.efectivo()),
                    pesos(c.transferencia()), pesos(c.fiado()), pesos(c.costoVendido()), pesos(c.costosAdicionales()),
                    pesos(c.utilidadBruta()), c.margenBruto(), pesos(c.gastos()), pesos(c.utilidadOperativa()),
                    c.margenOperativo());
        }
    }

    record RespuestaCategoria(UUID categoriaId, String categoria, long monto) {
        static RespuestaCategoria de(ResultadosDelPeriodo.PorCategoria c) {
            return new RespuestaCategoria(c.categoriaId(), c.categoria(), pesos(c.monto()));
        }
    }

    record RespuestaGastosDelMes(long incluidos, long fuera) {
    }

    record RespuestaSinCosto(int renglones, int unidades, long vendido, List<RespuestaRepuestoSinCosto> repuestos) {
        static RespuestaSinCosto de(ResultadosDelPeriodo.SinCosto s) {
            return new RespuestaSinCosto(s.renglones(), s.unidades(), pesos(s.vendido()),
                    s.repuestos().stream()
                            .map(r -> new RespuestaRepuestoSinCosto(r.varianteId(), r.codigo(), r.nombre(), r.marca(),
                                    r.unidades(), pesos(r.vendido())))
                            .toList());
        }
    }

    record RespuestaRepuestoSinCosto(UUID varianteId, String codigo, String nombre, String marca, int unidades,
                                     long vendido) {
    }

    record RespuestaFila(LocalDate desde, LocalDate hasta, int ventas, long ventasNetas, long costoVendido,
                         long costosAdicionales, long utilidadBruta, long gastos, long utilidadOperativa,
                         int renglonesSinCosto) {
        static RespuestaFila de(ResultadosDelPeriodo.Fila f) {
            return new RespuestaFila(f.desde(), f.hasta(), f.ventas(), pesos(f.ventasNetas()), pesos(f.costoVendido()),
                    pesos(f.costosAdicionales()), pesos(f.utilidadBruta()), pesos(f.gastos()),
                    pesos(f.utilidadOperativa()), f.renglonesSinCosto());
        }
    }

    private static long pesos(Dinero dinero) {
        return dinero.valor().longValueExact();
    }
}

package com.workshopmanagement.rdmotors.correo.aplicacion;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import com.workshopmanagement.rdmotors.caja.aplicacion.DetalleGasto;
import com.workshopmanagement.rdmotors.caja.aplicacion.DetalleTurno;
import com.workshopmanagement.rdmotors.compartido.dominio.Dinero;
import com.workshopmanagement.rdmotors.compartido.dominio.Persona;
import com.workshopmanagement.rdmotors.compras.dominio.EstadoCompra;
import com.workshopmanagement.rdmotors.correo.dominio.CorreoArmado;
import com.workshopmanagement.rdmotors.ventas.dominio.EstadoVenta;

/**
 * El resumen de un turno cerrado, listo para el correo (spec 0010, RF-005 y RF-006).
 *
 * <p>Los mismos cuatro bloques de la pantalla de Caja (portados del corte de caja del car-wash), que no se suman
 * entre sí porque responden preguntas distintas:
 * <ol>
 *   <li><b>El producido</b>: cuánto se vendió, fiado incluido, sin las anuladas.</li>
 *   <li><b>Cómo lo pagaron</b>: los MISMOS pesos, por forma de pago.</li>
 *   <li><b>La cartera del turno</b>: lo que se fió y los abonos. Un abono nunca suma al producido: paga una venta de
 *       otro día.</li>
 *   <li><b>El cajón</b>: debería haber, contaron, diferencia. Lo único por lo que se responde.</li>
 * </ol>
 *
 * <p>Las cifras del cajón son las que se <b>firmaron al cerrar</b> ({@code turno.cierre()}), no un cálculo nuevo.
 * Aquí no se calcula nada que no esté ya en el detalle del turno: se ordena y se dice.
 */
public final class CorreoDelCierre {

    private static final ZoneId COLOMBIA = ZoneId.of("America/Bogota");
    private static final String[] MESES =
            {"ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sept", "oct", "nov", "dic"};

    private CorreoDelCierre() {
    }

    public static CorreoArmado de(DetalleTurno turno, String tienda) {
        DetalleTurno.Cierre c = turno.cierre();
        if (c == null) {
            throw new IllegalArgumentException("El turno " + turno.id() + " no está cerrado: no tiene resumen");
        }
        Cifras cifras = Cifras.de(turno);
        String asunto = "Cierre de caja · " + tienda + " · " + diaCorto(turno.cerradoEn()) + " · "
                + nombre(turno.cerradoPor()) + " · " + diferenciaEnPalabras(c.diferencia());
        return new CorreoArmado(asunto, html(turno, tienda, cifras), texto(turno, tienda, cifras));
    }

    // ── Las cifras, sacadas del detalle ─────────────────────────────────────

    /** Lo del producido sale de las ventas vigentes del turno; lo del cajón, de lo firmado. */
    record Cifras(long producido, int ventas, int anuladas, long descuentos, long efectivo, long transferencia,
                  long fiado, List<String> salidas) {

        static Cifras de(DetalleTurno t) {
            long producido = 0;
            long efectivo = 0;
            long transferencia = 0;
            long fiado = 0;
            long descuentos = 0;
            int ventas = 0;
            int anuladas = 0;
            for (DetalleTurno.VentaDelTurno v : t.ventas()) {
                if (v.estado() == EstadoVenta.ANULADA) {
                    anuladas++;
                    continue;
                }
                ventas++;
                producido += pesos(v.total());
                efectivo += pesos(v.efectivo());
                transferencia += pesos(v.transferencia());
                fiado += pesos(v.fiado());
                descuentos += pesos(v.descuento());
            }
            List<String> salidas = new ArrayList<>();
            for (DetalleGasto g : t.gastos()) {
                if (g.anuladoEn() == null) {
                    salidas.add("Gasto · " + g.descripcion() + " · " + formato(pesos(g.monto())));
                }
            }
            for (DetalleTurno.DetalleRetiro r : t.retiros()) {
                if (r.anuladoEn() == null) {
                    salidas.add("Retiro · " + r.motivo() + " · " + formato(pesos(r.monto())));
                }
            }
            for (DetalleTurno.CompraDeCaja compra : t.compras()) {
                if (compra.estado() != EstadoCompra.ANULADA) {
                    salidas.add("Compra · " + compra.proveedor()
                            + (compra.numeroFactura() == null ? "" : " " + compra.numeroFactura())
                            + " · " + formato(pesos(compra.total())));
                }
            }
            return new Cifras(producido, ventas, anuladas, descuentos, efectivo, transferencia, fiado, salidas);
        }
    }

    // ── Texto plano ─────────────────────────────────────────────────────────

    private static String texto(DetalleTurno t, String tienda, Cifras x) {
        DetalleTurno.Cierre c = t.cierre();
        StringBuilder s = new StringBuilder();
        s.append("CIERRE DE CAJA · ").append(tienda).append("\n\n");
        s.append("Abrió  : ").append(momento(t.abiertoEn())).append(" · ").append(nombre(t.abiertoPor())).append('\n');
        s.append("Cerró  : ").append(momento(t.cerradoEn())).append(" · ").append(nombre(t.cerradoPor())).append('\n');
        s.append("Fondo  : ").append(formato(pesos(t.fondo()))).append("\n\n");

        s.append("PRODUCIDO DEL TURNO: ").append(formato(x.producido())).append('\n');
        s.append("  ").append(notaDelProducido(x)).append("\n\n");

        s.append("CÓMO LO PAGARON\n");
        fila(s, "Efectivo (al cajón)", x.efectivo());
        fila(s, "Transferencia (a la cuenta)", x.transferencia());
        fila(s, "Fiado (a la cartera)", x.fiado());
        fila(s, "Suman lo producido", x.efectivo() + x.transferencia() + x.fiado());
        s.append('\n');

        s.append("CARTERA DEL TURNO\n");
        fila(s, "Se fió", x.fiado());
        fila(s, "Abonos en efectivo (al cajón)", pesos(c.abonosEfectivo()));
        fila(s, "Abonos por transferencia", pesos(c.abonosTransferencia()));
        s.append("  Los abonos no suman al producido: pagan ventas de otros días.\n\n");

        s.append("EL CAJÓN · POR ESTO SE RESPONDE\n");
        fila(s, "Fondo", pesos(t.fondo()));
        fila(s, "+ Ventas en efectivo", pesos(c.ventasEfectivo()));
        filaSiHay(s, "+ Abonos de clientes", pesos(c.abonosEfectivo()));
        filaSiHay(s, "− Devuelto por anuladas", pesos(c.devolucionesEfectivo()));
        filaSiHay(s, "− Gastos del cajón", pesos(c.gastosCajon()));
        filaSiHay(s, "− Retiros", pesos(c.retiros()));
        filaSiHay(s, "− Compras del cajón", pesos(c.comprasCajon()));
        fila(s, "Debería haber", pesos(c.esperado()));
        fila(s, "Contaron", pesos(c.contado()));
        s.append("  ").append(diferenciaEnPalabras(c.diferencia()).toUpperCase()).append("\n\n");

        if (!x.salidas().isEmpty()) {
            s.append("LO QUE SALIÓ DEL CAJÓN\n");
            x.salidas().forEach(linea -> s.append("  ").append(linea).append('\n'));
            s.append('\n');
        }
        if (t.observaciones() != null && !t.observaciones().isBlank()) {
            s.append("OBSERVACIONES\n  ").append(t.observaciones().strip()).append("\n\n");
        }
        s.append("— ").append(tienda).append(" · resumen automático del cierre de caja\n");
        return s.toString();
    }

    private static void fila(StringBuilder s, String etiqueta, long monto) {
        s.append("  ").append(String.format("%-32s", etiqueta)).append(formato(monto)).append('\n');
    }

    private static void filaSiHay(StringBuilder s, String etiqueta, long monto) {
        if (monto != 0) {
            fila(s, etiqueta, monto);
        }
    }

    // ── HTML ────────────────────────────────────────────────────────────────

    private static String html(DetalleTurno t, String tienda, Cifras x) {
        DetalleTurno.Cierre c = t.cierre();
        String colorDiferencia = c.diferencia().esCero() ? "#15803d" : c.diferencia().esNegativo() ? "#9b1c1c" : "#b45309";
        StringBuilder h = new StringBuilder();
        h.append("<!doctype html><html lang=\"es\"><body style=\"margin:0;padding:0;background:#f3f4f6;")
                .append("font-family:Segoe UI,Roboto,Arial,sans-serif;color:#14161a;\">")
                .append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" ")
                .append("style=\"background:#f3f4f6;padding:24px 12px;\"><tr><td align=\"center\">")
                .append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" ")
                .append("style=\"max-width:560px;background:#ffffff;border-radius:12px;overflow:hidden;")
                .append("border:1px solid #e5e7eb;\">");

        // Encabezado: la tienda, y de un vistazo si cuadró.
        h.append("<tr><td style=\"background:#14161a;padding:18px 24px;\">")
                .append("<span style=\"color:#ffffff;font-size:18px;font-weight:800;\">").append(esc(tienda))
                .append("</span><span style=\"color:#e11d2e;font-size:12px;font-weight:700;margin-left:8px;")
                .append("letter-spacing:.08em;\">CIERRE DE CAJA</span></td></tr>");
        h.append("<tr><td style=\"padding:20px 24px 4px;\">")
                .append("<div style=\"font-size:13px;color:#5c636e;\">Cerró ").append(esc(nombre(t.cerradoPor())))
                .append(" · ").append(esc(momento(t.cerradoEn()))).append("</div>")
                .append("<div style=\"font-size:26px;font-weight:800;margin-top:6px;color:").append(colorDiferencia)
                .append(";\">").append(esc(capitalizar(diferenciaEnPalabras(c.diferencia())))).append("</div>")
                .append("<div style=\"font-size:13px;color:#5c636e;margin-top:2px;\">Debería haber ")
                .append(formato(pesos(c.esperado()))).append(" · contaron ").append(formato(pesos(c.contado())))
                .append("</div></td></tr>");

        // 1 y 2 · El producido y cómo lo pagaron.
        seccion(h, "Producido del turno", formato(x.producido()), notaDelProducido(x));
        tabla(h, List.of(
                fila("Efectivo", "al cajón", x.efectivo(), true),
                fila("Transferencia", "a la cuenta", x.transferencia(), false),
                fila("Fiado", "a la cartera", x.fiado(), false)),
                "Suman lo producido", x.efectivo() + x.transferencia() + x.fiado());

        // 3 · La cartera del turno.
        seccion(h, "Cartera del turno", null, "Los abonos no suman al producido: pagan ventas de otros días.");
        tabla(h, List.of(
                fila("Se fió", "a la cartera", x.fiado(), false),
                fila("Abonos en efectivo", "al cajón", pesos(c.abonosEfectivo()), true),
                fila("Abonos por transferencia", "a la cuenta", pesos(c.abonosTransferencia()), false)),
                null, 0);

        // 4 · El cajón: por esto se responde.
        seccion(h, "El cajón · por esto se responde", formato(pesos(c.esperado())), null);
        List<String[]> cajon = new ArrayList<>();
        cajon.add(fila("Fondo del turno", "", pesos(t.fondo()), false));
        cajon.add(fila("Ventas en efectivo", "+", pesos(c.ventasEfectivo()), false));
        if (!c.abonosEfectivo().esCero()) cajon.add(fila("Abonos de clientes", "+", pesos(c.abonosEfectivo()), false));
        if (!c.devolucionesEfectivo().esCero()) cajon.add(fila("Devuelto por ventas anuladas", "−", pesos(c.devolucionesEfectivo()), false));
        if (!c.gastosCajon().esCero()) cajon.add(fila("Gastos pagados del cajón", "−", pesos(c.gastosCajon()), false));
        if (!c.retiros().esCero()) cajon.add(fila("Retiros", "−", pesos(c.retiros()), false));
        if (!c.comprasCajon().esCero()) cajon.add(fila("Compras pagadas del cajón", "−", pesos(c.comprasCajon()), false));
        tabla(h, cajon, "Debería haber", pesos(c.esperado()));
        tabla(h, List.<String[]>of(fila("Contaron", "", pesos(c.contado()), false)), "Diferencia", pesos(c.diferencia()));

        if (!x.salidas().isEmpty()) {
            seccion(h, "Lo que salió del cajón", null, null);
            h.append("<tr><td style=\"padding:0 24px 8px;font-size:14px;line-height:1.6;\">");
            x.salidas().forEach(linea -> h.append("<div>").append(esc(linea)).append("</div>"));
            h.append("</td></tr>");
        }
        if (t.observaciones() != null && !t.observaciones().isBlank()) {
            seccion(h, "Observaciones", null, null);
            h.append("<tr><td style=\"padding:0 24px 8px;\"><div style=\"background:#f3f4f6;border-radius:8px;")
                    .append("padding:10px 12px;font-size:14px;line-height:1.5;\">").append(esc(t.observaciones().strip()))
                    .append("</div></td></tr>");
        }

        h.append("<tr><td style=\"padding:18px 24px;font-size:12px;color:#8a919c;border-top:1px solid #eef0f3;\">")
                .append("Abrió ").append(esc(nombre(t.abiertoPor()))).append(" · ").append(esc(momento(t.abiertoEn())))
                .append(" · fondo ").append(formato(pesos(t.fondo()))).append("<br>Resumen automático del cierre de caja.")
                .append("</td></tr></table></td></tr></table></body></html>");
        return h.toString();
    }

    private static void seccion(StringBuilder h, String titulo, String cifra, String nota) {
        h.append("<tr><td style=\"padding:20px 24px 6px;\">")
                .append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\"><tr>")
                .append("<td style=\"font-size:12px;font-weight:700;letter-spacing:.06em;text-transform:uppercase;")
                .append("color:#5c636e;\">").append(esc(titulo)).append("</td>");
        if (cifra != null) {
            h.append("<td align=\"right\" style=\"font-size:18px;font-weight:800;\">").append(esc(cifra)).append("</td>");
        }
        h.append("</tr></table>");
        if (nota != null) {
            h.append("<div style=\"font-size:12px;color:#8a919c;margin-top:2px;\">").append(esc(nota)).append("</div>");
        }
        h.append("</td></tr>");
    }

    /** {etiqueta, destino, monto formateado, "1" si entra al cajón}. */
    private static String[] fila(String etiqueta, String destino, long monto, boolean alCajon) {
        return new String[] {etiqueta, destino, formato(monto), alCajon ? "1" : ""};
    }

    private static void tabla(StringBuilder h, List<String[]> filas, String total, long montoTotal) {
        h.append("<tr><td style=\"padding:0 24px;\"><table role=\"presentation\" width=\"100%\" cellpadding=\"0\" ")
                .append("cellspacing=\"0\" style=\"font-size:14px;\">");
        for (String[] f : filas) {
            String marca = f[3].isEmpty() ? "" : "border-left:3px solid #e11d2e;padding-left:8px;font-weight:600;";
            h.append("<tr><td style=\"padding:6px 0;border-bottom:1px solid #eef0f3;").append(marca).append("\">")
                    .append(esc(f[0])).append("</td><td style=\"padding:6px 8px;border-bottom:1px solid #eef0f3;")
                    .append("font-size:12px;color:#8a919c;\">").append(esc(f[1])).append("</td>")
                    .append("<td align=\"right\" style=\"padding:6px 0;border-bottom:1px solid #eef0f3;")
                    .append("white-space:nowrap;\">").append(esc(f[2])).append("</td></tr>");
        }
        if (total != null) {
            h.append("<tr><td colspan=\"2\" style=\"padding:8px 0;border-top:2px solid #c9cdd3;font-weight:700;\">")
                    .append(esc(total)).append("</td><td align=\"right\" style=\"padding:8px 0;border-top:2px solid ")
                    .append("#c9cdd3;font-weight:700;white-space:nowrap;\">").append(formato(montoTotal))
                    .append("</td></tr>");
        }
        h.append("</table></td></tr>");
    }

    // ── Palabras y formatos ────────────────────────────────────────────────

    static String diferenciaEnPalabras(Dinero diferencia) {
        long d = pesos(diferencia);
        if (d == 0) return "cuadra al peso";
        return d > 0 ? "sobran " + formato(d) : "faltan " + formato(-d);
    }

    private static String notaDelProducido(Cifras x) {
        List<String> partes = new ArrayList<>();
        partes.add(x.ventas() + (x.ventas() == 1 ? " venta" : " ventas"));
        if (x.descuentos() > 0) partes.add(formato(x.descuentos()) + " en descuentos");
        if (x.anuladas() > 0) partes.add(x.anuladas() + (x.anuladas() == 1 ? " anulada no suma" : " anuladas no suman"));
        return String.join(" · ", partes);
    }

    /** "$ 159.000", con punto de miles como en Colombia. */
    static String formato(long pesos) {
        String cifra = String.format("%,d", Math.abs(pesos)).replace(',', '.');
        return (pesos < 0 ? "−$ " : "$ ") + cifra;
    }

    private static long pesos(Dinero dinero) {
        return dinero == null ? 0 : dinero.valor().longValueExact();
    }

    private static String nombre(Persona persona) {
        return persona == null || persona.nombre() == null ? "—" : persona.nombre();
    }

    /** "22 sept" */
    private static String diaCorto(Instant instante) {
        if (instante == null) return "—";
        ZonedDateTime z = instante.atZone(COLOMBIA);
        return z.getDayOfMonth() + " " + MESES[z.getMonthValue() - 1];
    }

    /** "22 sept 2026, 6:36 p. m." */
    static String momento(Instant instante) {
        if (instante == null) return "—";
        ZonedDateTime z = instante.atZone(COLOMBIA);
        int hora = z.getHour() % 12 == 0 ? 12 : z.getHour() % 12;
        return z.getDayOfMonth() + " " + MESES[z.getMonthValue() - 1] + " " + z.getYear() + ", " + hora + ":"
                + String.format("%02d", z.getMinute()) + (z.getHour() < 12 ? " a. m." : " p. m.");
    }

    private static String capitalizar(String texto) {
        return texto.isEmpty() ? texto : Character.toUpperCase(texto.charAt(0)) + texto.substring(1);
    }

    /** Lo que escribió una persona (observaciones, nombres) no puede meter etiquetas en el correo. */
    static String esc(String texto) {
        if (texto == null) return "";
        return texto.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}

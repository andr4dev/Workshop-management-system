package com.workshopmanagement.rdmotors.carga.infraestructura;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import org.dhatim.fastexcel.reader.Cell;
import org.dhatim.fastexcel.reader.CellType;
import org.dhatim.fastexcel.reader.ReadableWorkbook;
import org.dhatim.fastexcel.reader.Row;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.workshopmanagement.rdmotors.carga.dominio.FacturaLeida;
import com.workshopmanagement.rdmotors.carga.dominio.FacturaNoReconocidaException;
import com.workshopmanagement.rdmotors.carga.dominio.OrigenCarga;
import com.workshopmanagement.rdmotors.carga.dominio.RenglonLeido;
import com.workshopmanagement.rdmotors.carga.dominio.puerto.LectorDeFactura;
import com.workshopmanagement.rdmotors.carga.infraestructura.PlantillaDeCarga.Celda;

/**
 * ADAPTADOR — la plantilla en Excel (spec 0012, RF-001): para otros proveedores y para mercancía sin factura.
 *
 * <p>Lee la primera hoja. Las celdas que Excel guarda como número se toman como número —{@code 12943} es 12943, sin
 * reinterpretar—; las que se escribieron como texto ({@code "$12.943"}) pasan por {@link NumerosColombianos}.
 */
@Component
@Order(2)
class LectorExcel implements LectorDeFactura {

    /** Los títulos tienen que estar en las primeras filas: más abajo ya no es una plantilla, es otra cosa. */
    private static final int FILAS_PARA_BUSCAR_TITULOS = 15;

    @Override
    public boolean reconoce(String nombreArchivo, byte[] contenido) {
        // Un .docx también es un zip: se mira el nombre y el contenido a la vez.
        return nombreArchivo != null && nombreArchivo.toLowerCase(Locale.ROOT).endsWith(".xlsx")
                && ReadableWorkbook.isOOXMLZipHeader(contenido);
    }

    @Override
    public FacturaLeida leer(byte[] contenido) {
        try (ReadableWorkbook libro = new ReadableWorkbook(new ByteArrayInputStream(contenido));
             Stream<Row> filas = libro.getFirstSheet().openStream()) {
            PlantillaDeCarga plantilla = null;
            List<RenglonLeido> renglones = new ArrayList<>();
            Iterator<Row> it = filas.iterator();
            while (it.hasNext()) {
                Row fila = it.next();
                List<Celda> celdas = celdas(fila);
                if (plantilla == null) {
                    if (fila.getRowNum() > FILAS_PARA_BUSCAR_TITULOS) {
                        break;
                    }
                    if (PlantillaDeCarga.pareceTitulos(celdas)) {
                        plantilla = PlantillaDeCarga.desdeTitulos(celdas);
                    }
                    continue;
                }
                if (!plantilla.vacia(celdas)) {
                    renglones.add(plantilla.renglon(celdas, "fila " + fila.getRowNum()));
                }
            }
            if (plantilla == null) {
                throw new FacturaNoReconocidaException("No encontré los títulos de la plantilla (CODIGO, DESCRIPCION, "
                        + "CANTIDAD, VALOR TOTAL) en las primeras filas del Excel. Baja la plantilla para ver cómo va.");
            }
            if (renglones.isEmpty()) {
                throw new FacturaNoReconocidaException("El Excel tiene los títulos pero ningún renglón debajo.");
            }
            return FacturaLeida.soloRenglones(OrigenCarga.EXCEL, renglones);
        } catch (IOException e) {
            throw new FacturaNoReconocidaException("Ese Excel no se puede abrir: puede estar dañado o protegido con "
                    + "contraseña.");
        }
    }

    private static List<Celda> celdas(Row fila) {
        List<Celda> celdas = new ArrayList<>();
        for (int i = 0; i < fila.getCellCount(); i++) {
            celdas.add(celda(fila.getOptionalCell(i).orElse(null)));
        }
        return celdas;
    }

    private static Celda celda(Cell celda) {
        if (celda == null || celda.getType() == CellType.EMPTY) {
            return Celda.deTexto(null);
        }
        if (celda.getType() == CellType.NUMBER) {
            return Celda.deNumero(celda.asNumber());
        }
        if (celda.getType() == CellType.FORMULA) {
            // El valor que Excel guardó la última vez que calculó la fórmula. Si es un número, viene sin formato.
            String crudo = celda.getRawValue();
            try {
                return crudo == null ? Celda.deTexto(null) : Celda.deNumero(new BigDecimal(crudo));
            } catch (NumberFormatException noEsNumero) {
                return Celda.deTexto(crudo);
            }
        }
        return Celda.deTexto(celda.getText());
    }
}

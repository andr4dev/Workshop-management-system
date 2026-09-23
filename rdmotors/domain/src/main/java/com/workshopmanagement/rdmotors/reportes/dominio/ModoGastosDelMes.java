package com.workshopmanagement.rdmotors.reportes.dominio;

/**
 * Cómo se leen los gastos del mes (el arriendo, la nómina) en el reporte (spec 0007, RF-010a).
 *
 * <p>Los demás gastos cuentan siempre en su fecha.
 */
public enum ModoGastosDelMes {

    /** Cada día de su mes carga una cuota igual; un período carga las cuotas de sus días. Es el de por defecto. */
    REPARTIDOS,

    /** Cuentan enteros si el período cubre su mes; si no, quedan fuera y el reporte dice cuánto. */
    SOLO_EN_EL_MES
}

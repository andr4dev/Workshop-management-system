package com.workshopmanagement.rdmotors.compartido.dominio;

/**
 * Lo que puede cada persona que entra al sistema (spec 0004, §5). Dos roles fijos: no hay permisos sueltos por
 * persona.
 */
public enum Rol {

    /** El dueño o quien él designe: todo, incluidos costos, compras, reportes y usuarios. */
    ADMINISTRADOR,

    /** Vende, cobra, anula con motivo, registra gastos y retiros del cajón, y responde por su turno. */
    CAJERO;

    /**
     * Si ve costos y ganancia (spec 0004, decisión 1). El cajero no los ve en ninguna parte: el servidor no se los
     * manda, no basta con esconderlos en la pantalla.
     */
    public boolean veCostos() {
        return this == ADMINISTRADOR;
    }
}

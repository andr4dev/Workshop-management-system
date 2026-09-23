package com.workshopmanagement.rdmotors.compartido.dominio;

/**
 * Qué se hizo, en el registro de auditoría (SPEC_Modelo_Datos §3.7).
 *
 * <p>Las que decidió el modelo de datos para caja y ajustes se agregan cuando llegue su rebanada.
 */
public enum AccionAuditada {

    /** Cambió la cabecera o los renglones de una compra ya registrada (spec 0002, H3 y H4). */
    CORREGIR_COMPRA,

    /** Una compra que no debió registrarse salió del inventario (spec 0002, H5). */
    ANULAR_COMPRA,

    /** Se corrigió la ficha de un repuesto (spec 0001, H5; spec 0002, H7). */
    CORREGIR_REPUESTO,

    /** Una venta cobrada salió con descuento: quién lo dio, cuánto y por qué (spec 0003, RF-016). */
    APLICAR_DESCUENTO,

    /** Una venta cobrada se anuló y su stock volvió (spec 0003, RF-025). */
    ANULAR_VENTA,

    /** Un turno se cerró y lo contado no cuadró con lo que debería haber (spec 0006, RF-015). */
    CERRAR_CAJA_CON_DIFERENCIA,

    /** Un gasto mal registrado se anuló y dejó de restar (spec 0006, RF-006). */
    ANULAR_GASTO,

    /** Un retiro mal registrado se anuló y dejó de restar (spec 0006, RF-006). */
    ANULAR_RETIRO,

    /** Se creó un usuario, o el primer administrador (spec 0004, RF-020). */
    CREAR_USUARIO,

    /** A un usuario le cambiaron el rol (spec 0004, RF-020). */
    CAMBIAR_ROL,

    /** Un usuario dejó de poder entrar; lo que hizo lo sigue nombrando (spec 0004, RF-015). */
    DESACTIVAR_USUARIO,

    /** Un usuario desactivado volvió a poder entrar (spec 0004, RF-015). */
    ACTIVAR_USUARIO,

    /** El administrador restableció la contraseña de alguien, que la cambia al entrar (spec 0004, RF-017). */
    RESTABLECER_CONTRASENA,

    /** El administrador corrigió un dato ya escrito de un cliente: nombre, cédula, celular (spec 0008, RF-004). */
    CORREGIR_CLIENTE,

    /** A un cliente no se le fía más; sigue comprando de contado y abonando (spec 0008, RF-005). */
    CERRAR_FIADO,

    /** A un cliente al que se le había cerrado el fiado se le vuelve a fiar (spec 0008, RF-005). */
    ABRIR_FIADO,

    /** Lo que un cliente ya debía en el cuaderno, cargado una sola vez (spec 0008, RF-028). */
    CARGAR_SALDO_CUADERNO,

    /** Un abono mal registrado se anuló y las ventas a las que se aplicó vuelven a deber eso (spec 0008, RF-017). */
    ANULAR_ABONO
}

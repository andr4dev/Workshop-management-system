package com.workshopmanagement.rdmotors.compartido.infraestructura;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import com.workshopmanagement.rdmotors.caja.dominio.MasDeLoQueDeberiaHaberException;
import com.workshopmanagement.rdmotors.caja.dominio.MovimientoRepetidoException;
import com.workshopmanagement.rdmotors.caja.dominio.TurnoAjenoException;
import com.workshopmanagement.rdmotors.caja.dominio.TurnoYaAbiertoException;
import com.workshopmanagement.rdmotors.caja.dominio.TurnoYaCerradoException;
import com.workshopmanagement.rdmotors.clientes.dominio.ClienteRepetidoException;
import com.workshopmanagement.rdmotors.compartido.dominio.NoPermitidoException;
import com.workshopmanagement.rdmotors.compartido.dominio.ReglaDeNegocioException;
import com.workshopmanagement.rdmotors.compras.dominio.CompraModificadaException;
import com.workshopmanagement.rdmotors.compras.dominio.RenglonesBloqueadosException;
import com.workshopmanagement.rdmotors.inventario.dominio.CodigoDuplicadoException;
import com.workshopmanagement.rdmotors.inventario.dominio.StockInsuficienteException;
import com.workshopmanagement.rdmotors.usuarios.dominio.CredencialesInvalidasException;
import com.workshopmanagement.rdmotors.usuarios.dominio.UsuarioBloqueadoException;
import com.workshopmanagement.rdmotors.respaldo.dominio.RespaldoFallidoException;
import com.workshopmanagement.rdmotors.usuarios.dominio.YaHayUsuariosException;
import com.workshopmanagement.rdmotors.usuarios.dominio.puerto.RepositorioUsuarios;
import com.workshopmanagement.rdmotors.ventas.dominio.ProblemaDeRenglon;
import com.workshopmanagement.rdmotors.ventas.dominio.RenglonesConProblemaException;
import com.workshopmanagement.rdmotors.ventas.dominio.VentaRepetidaException;

import lombok.RequiredArgsConstructor;

/**
 * Traduce excepciones del dominio a codigos HTTP.
 *
 * <p>Esta traduccion vive en {@code pos} y no en el dominio a proposito: el dominio no sabe que
 * existe el HTTP. Si manana la misma regla se dispara desde la cola offline o desde un importador,
 * la excepcion es la misma y solo cambia quien la interpreta.
 */
@RestControllerAdvice
@RequiredArgsConstructor
class ManejadorDeErrores {

    private final RepositorioUsuarios usuarios;

    /** Stock insuficiente lleva los numeros para que el cajero pueda cerrar la venta por lo que hay. */
    @ExceptionHandler(StockInsuficienteException.class)
    ResponseEntity<ErrorStock> stockInsuficiente(StockInsuficienteException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorStock(e.getMessage(), e.getCodigo(), e.getDisponible(),
                        e.getSolicitado(), Instant.now()));
    }

    /**
     * 409 y no 422: es un conflicto con algo que ya existe. Lleva el nombre del repuesto que tiene
     * el codigo para que la pantalla pueda ofrecer "¿te referias a este?" en vez de solo negarse.
     */
    @ExceptionHandler(CodigoDuplicadoException.class)
    ResponseEntity<ErrorCodigo> codigoDuplicado(CodigoDuplicadoException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorCodigo(e.getMessage(), e.getCodigo(), e.getNombreDelQueLoTiene(),
                        Instant.now()));
    }

    /**
     * 409: otra corrección se guardó mientras la pantalla tenía la compra abierta. No es un error
     * del administrador; la salida es volver a abrirla con lo nuevo.
     */
    @ExceptionHandler(CompraModificadaException.class)
    ResponseEntity<Error> compraModificada(CompraModificadaException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new Error(e.getMessage(), Instant.now()));
    }

    /**
     * 409 con cada renglón y su problema (precio cambiado, sin stock, sin precio, inactivo): la pantalla
     * marca esos renglones, actualiza los precios y no le cobra al cliente algo distinto a lo que vio.
     */
    @ExceptionHandler(RenglonesConProblemaException.class)
    ResponseEntity<ErrorRenglones> renglonesConProblema(RenglonesConProblemaException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorRenglones(e.getMessage(), e.getProblemas(), Instant.now()));
    }

    /**
     * Red de seguridad: el controlador de ventas responde un cobro repetido con la venta guardada. Si
     * esta excepción llega hasta aquí, es un 409 y no un 500.
     */
    @ExceptionHandler(VentaRepetidaException.class)
    ResponseEntity<Error> ventaRepetida(VentaRepetidaException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new Error("Esa venta ya se cobró", Instant.now()));
    }

    /**
     * 409: ya hay un turno abierto. Lleva desde cuándo, para que la pantalla lo diga y muestre ese
     * turno en vez de solo negarse. {@code abiertoEn} viene vacío si dos aperturas llegaron a la vez.
     */
    @ExceptionHandler(TurnoYaAbiertoException.class)
    ResponseEntity<ErrorTurnoAbierto> turnoYaAbierto(TurnoYaAbiertoException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorTurnoAbierto(e.getMessage(), e.getAbiertoEn(), Instant.now()));
    }

    /**
     * 409 con código: un gasto o retiro mayor que lo que debería haber en el cajón (spec 0006, decisión 3). No
     * es un rechazo: la pantalla pregunta si está seguro y reenvía confirmado.
     */
    @ExceptionHandler(MasDeLoQueDeberiaHaberException.class)
    ResponseEntity<ErrorConCodigo> masDeLoQueDeberiaHaber(MasDeLoQueDeberiaHaberException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorConCodigo(e.getMessage(), MasDeLoQueDeberiaHaberException.CODIGO, Instant.now()));
    }

    /**
     * 409: el turno ya se cerró (doble clic, dos pestañas). Lleva cuál y cuándo, para que la pantalla cargue ese
     * cierre en vez de solo negarse (spec 0006, RF-018).
     */
    @ExceptionHandler(TurnoYaCerradoException.class)
    ResponseEntity<ErrorTurnoCerrado> turnoYaCerrado(TurnoYaCerradoException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorTurnoCerrado(e.getMessage(), "TURNO_CERRADO", e.getTurnoId(), e.getCerradoEn(),
                        Instant.now()));
    }

    /** Red de seguridad, como la venta repetida: los controladores responden con el que ya quedó. */
    @ExceptionHandler(MovimientoRepetidoException.class)
    ResponseEntity<Error> movimientoRepetido(MovimientoRepetidoException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new Error("Ese movimiento ya se registró", Instant.now()));
    }

    /**
     * 422 con la lista de códigos bloqueados, para que la pantalla marque esos renglones en vez de
     * dejar al administrador adivinando cuáles son.
     */
    @ExceptionHandler(RenglonesBloqueadosException.class)
    ResponseEntity<ErrorBloqueados> renglonesBloqueados(RenglonesBloqueadosException e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT)
                .body(new ErrorBloqueados(e.getMessage(), e.getCodigos(), Instant.now()));
    }

    /**
     * Red de seguridad, como la venta repetida: el controlador de clientes responde el choque de cédula con el cliente
     * que ya la tiene. Si llega hasta aquí, es un 409 con su código y no un 500.
     */
    @ExceptionHandler(ClienteRepetidoException.class)
    ResponseEntity<ErrorConCodigo> clienteRepetido(ClienteRepetidoException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorConCodigo(e.getMessage(), ClienteRepetidoException.CODIGO, Instant.now()));
    }

    /** Algo que el rol no puede (spec 0004, RF-010): 403, y no cambió nada. */
    @ExceptionHandler(NoPermitidoException.class)
    ResponseEntity<ErrorConCodigo> noPermitido(NoPermitidoException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ErrorConCodigo(e.getMessage(), NoPermitidoException.CODIGO, Instant.now()));
    }

    /**
     * El turno de otra persona (spec 0004, RF-012): 403 con el nombre de quien lo abrió, que el turno no conoce.
     * Aparte de {@link #noPermitido}: la pantalla lo reconoce por su código.
     */
    @ExceptionHandler(TurnoAjenoException.class)
    ResponseEntity<ErrorConCodigo> turnoAjeno(TurnoAjenoException e) {
        String nombre = usuarios.nombresDe(List.of(e.getAbiertoPorId())).get(e.getAbiertoPorId());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ErrorConCodigo(TurnoAjenoException.mensajePara(nombre), TurnoAjenoException.CODIGO,
                        Instant.now()));
    }

    /** Usuario o contraseña mal (spec 0004, RF-003): el mismo mensaje, exista o no el usuario. */
    @ExceptionHandler(CredencialesInvalidasException.class)
    ResponseEntity<ErrorConCodigo> credencialesInvalidas(CredencialesInvalidasException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorConCodigo(e.getMessage(), CredencialesInvalidasException.CODIGO, Instant.now()));
    }

    /** Cinco intentos fallidos: espera cinco minutos (spec 0004, RF-003). */
    @ExceptionHandler(UsuarioBloqueadoException.class)
    ResponseEntity<ErrorConCodigo> usuarioBloqueado(UsuarioBloqueadoException e) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(new ErrorConCodigo(e.getMessage(), UsuarioBloqueadoException.CODIGO, Instant.now()));
    }

    /** El primer administrador ya existe: la opción ya no está (spec 0004, RF-018). */
    @ExceptionHandler(YaHayUsuariosException.class)
    ResponseEntity<ErrorConCodigo> yaHayUsuarios(YaHayUsuariosException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorConCodigo(e.getMessage(), YaHayUsuariosException.CODIGO, Instant.now()));
    }

    /**
     * 422 y no 400: la peticion esta bien formada, lo que no se puede es lo que pide. El mensaje
     * va tal cual porque esta escrito para que lo lea el cajero, no un programador.
     */
    @ExceptionHandler(ReglaDeNegocioException.class)
    ResponseEntity<Error> reglaDeNegocio(ReglaDeNegocioException e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT)
                .body(new Error(e.getMessage(), Instant.now()));
    }

    /**
     * No se pudo sacar la copia de la base (spec 0011, §6).
     *
     * <p>503 y no 500: casi siempre es algo de afuera —el motor no respondió, no cabe en el disco, falta el
     * programa que la saca— y no un error del sistema. El mensaje va tal cual porque es lo que hay que leerle a
     * quien vaya a arreglarlo, y sin él en la pantalla solo quedaría "el servidor falló".
     *
     * <p>El intento fallido <b>ya quedó registrado</b> antes de llegar aquí: el caso de uso lo guarda en su propia
     * transacción justo para que lanzar esto no lo deshaga.
     */
    @ExceptionHandler(RespaldoFallidoException.class)
    ResponseEntity<Error> respaldoFallido(RespaldoFallidoException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new Error(e.getMessage(), Instant.now()));
    }

    /**
     * Los errores que decide el borde (un 404 de un id que no existe). Se reescriben con la misma
     * forma que los demás para que el frontend lea siempre {@code mensaje} y no tenga que saber que
     * Spring los manda de otra manera.
     */
    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<Error> delBorde(ResponseStatusException e) {
        return ResponseEntity.status(e.getStatusCode())
                .body(new Error(e.getReason(), Instant.now()));
    }

    record Error(String mensaje, Instant momento) {
    }

    record ErrorBloqueados(String mensaje, List<String> codigosBloqueados, Instant momento) {
    }

    record ErrorCodigo(String mensaje, String codigo, String nombreDelQueLoTiene,
                       Instant momento) {
    }

    record ErrorRenglones(String mensaje, List<ProblemaDeRenglon> problemas, Instant momento) {
    }

    record ErrorTurnoAbierto(String mensaje, Instant abiertoEn, Instant momento) {
    }

    record ErrorConCodigo(String mensaje, String codigo, Instant momento) {
    }

    record ErrorTurnoCerrado(String mensaje, String codigo, UUID turnoId, Instant cerradoEn, Instant momento) {
    }

    record ErrorStock(String mensaje, String codigo, int disponible, int solicitado,
                      Instant momento) {
    }
}

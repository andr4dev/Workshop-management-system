package com.workshopmanagement.rdmotors.respaldo.infraestructura;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.stereotype.Component;

import com.workshopmanagement.rdmotors.respaldo.dominio.RespaldoFallidoException;
import com.workshopmanagement.rdmotors.respaldo.dominio.puerto.Archivos;

/** ADAPTADOR — el disco de verdad. Traduce lo que rompe {@code java.nio} al idioma del respaldo. */
@Component
class ArchivosDelDisco implements Archivos {

    @Override
    public void asegurarCarpeta(Path carpeta) {
        try {
            Files.createDirectories(carpeta);
        } catch (IOException e) {
            throw new RespaldoFallidoException("No se pudo crear la carpeta de respaldos " + carpeta + ": "
                    + e.getMessage(), e);
        }
    }

    @Override
    public void borrar(Path archivo) {
        try {
            Files.deleteIfExists(archivo);
        } catch (IOException e) {
            throw new RespaldoFallidoException("No se pudo borrar " + archivo + ": " + e.getMessage(), e);
        }
    }

    @Override
    public boolean existe(Path archivo) {
        return Files.isRegularFile(archivo);
    }
}

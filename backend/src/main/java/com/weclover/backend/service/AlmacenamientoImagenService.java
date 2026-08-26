package com.weclover.backend.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.weclover.backend.exception.InvalidFileException;

/**
 * Guardado en disco local de imágenes subidas (moldería, diseño de prendas, etc.), compartido
 * entre features para no duplicar la validación de formato ni el manejo de archivos.
 */
@Service
public class AlmacenamientoImagenService {

    private static final Map<String, String> EXTENSIONES_POR_CONTENT_TYPE = Map.of(
        "image/jpeg", ".jpg",
        "image/png", ".png"
    );

    public String guardar(MultipartFile imagen, String directorioDestino, String urlBase) {
        if (imagen == null || imagen.isEmpty()) {
            throw new InvalidFileException("Debe adjuntar una imagen");
        }

        String contentType = imagen.getContentType();
        String extension = contentType != null ? EXTENSIONES_POR_CONTENT_TYPE.get(contentType.toLowerCase()) : null;
        if (extension == null) {
            throw new InvalidFileException("Formato de imagen no válido: solo se aceptan archivos JPG o PNG");
        }

        try {
            Path directorio = Path.of(directorioDestino);
            Files.createDirectories(directorio);

            String nombreArchivo = UUID.randomUUID() + extension;
            imagen.transferTo(directorio.resolve(nombreArchivo));

            return urlBase + nombreArchivo;
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo guardar la imagen", e);
        }
    }
}

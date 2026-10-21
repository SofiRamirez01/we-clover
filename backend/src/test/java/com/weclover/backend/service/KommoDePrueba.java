package com.weclover.backend.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import tools.jackson.databind.json.JsonMapper;
import com.weclover.backend.config.KommoProperties;
import com.weclover.backend.entity.EstadoPedido;

/** Configuración de Kommo igual a application-local.example.yml y lectura de los fixtures. */
final class KommoDePrueba {

    static final String BASE_URL = "https://kommo.test";
    static final JsonMapper JSON = JsonMapper.builder().build();

    private KommoDePrueba() {
    }

    static KommoProperties properties() {
        return properties("token-de-prueba");
    }

    static KommoProperties properties(String token) {
        return new KommoProperties(BASE_URL, token, 9516980L,
            Map.of(
                73480596L, EstadoPedido.SENADO,
                102814047L, EstadoPedido.SENADO,
                102807927L, EstadoPedido.SENADO,
                103172111L, EstadoPedido.SENADO,
                102794939L, EstadoPedido.SENADO),
            new KommoProperties.Campos(
                691725L, 691325L, 691723L, 693252L, 693254L, 693250L, 691345L, 693292L,
                693420L, 693422L, 693438L, 693424L, 693426L, 693428L, 693418L, 693432L,
                693434L, 693436L, 693472L, 693578L));
    }

    static <T> T leer(String archivo, Class<T> tipo) {
        return JSON.readValue(texto(archivo), tipo);
    }

    static String texto(String archivo) {
        try (InputStream in = KommoDePrueba.class.getResourceAsStream("/kommo/" + archivo)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}

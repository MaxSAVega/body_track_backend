package com.rumi.body_track_backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Habilita el borrado programado de refresh tokens caducados.
 *
 * Sin esto, [com.rumi.body_track_backend.service.RefreshTokenService.purgeExpired]
 * se declara pero nunca se ejecuta: la tabla crece de forma indefinida porque la
 * caducidad sola no borra nada.
 */
@SpringBootApplication
@EnableScheduling
public class BodyTrackBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(BodyTrackBackendApplication.class, args);
    }

}

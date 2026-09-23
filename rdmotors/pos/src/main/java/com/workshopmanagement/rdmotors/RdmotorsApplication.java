package com.workshopmanagement.rdmotors;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Punto de venta de RD Motors. Corre en la PC de la tienda y es la <b>fuente de verdad</b>.
 *
 * <p>La tablet y el celular del pasillo son pantallas que se conectan por la red local a esta
 * maquina. No necesitan internet — necesitan WiFi, que es distinto.
 *
 * <p>No hace falta {@code @EntityScan}: los dos modulos comparten el paquete raiz
 * {@code com.workshopmanagement.rdmotors}, asi que el escaneo por defecto de Spring Boot
 * encuentra las entidades del modulo {@code domain} aunque vengan en otro jar.
 */
@SpringBootApplication
public class RdmotorsApplication {

	public static void main(String[] args) {
		SpringApplication.run(RdmotorsApplication.class, args);
	}

}

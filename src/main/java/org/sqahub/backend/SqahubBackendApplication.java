package org.sqahub.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class SqahubBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(SqahubBackendApplication.class, args);
		System.out.println("SQAHUB Backend sedang berjalan...");
	}

}

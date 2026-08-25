package org.sqahub.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling // Dipakai TokenBlacklistService untuk membersihkan entri kadaluarsa secara berkala
public class SqahubBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(SqahubBackendApplication.class, args);
		System.out.println("SQAHUB Backend sedang berjalan...");
	}

}

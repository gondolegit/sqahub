package org.sqahub.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

// Profil "test" -> pakai H2 in-memory (lihat application-test.properties),
// supaya context bisa load tanpa MySQL sungguhan berjalan di mesin/CI.
@SpringBootTest
@ActiveProfiles("test")
class SqahubBackendApplicationTests {

	@Test
	void contextLoads() {
	}

}

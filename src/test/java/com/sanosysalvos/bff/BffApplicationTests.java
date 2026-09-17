package com.sanosysalvos.bff;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
class BffApplicationTests {

	/* evita que el contexto intente descargar el JWKS real de Cognito al levantar */
	@MockitoBean
	private NimbusJwtDecoder jwtDecoder;

	@Test
	void contextLoads() {
	}

}

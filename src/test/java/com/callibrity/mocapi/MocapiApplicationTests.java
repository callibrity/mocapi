package com.callibrity.mocapi;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class MocapiApplicationTests {

	@Test
	void contextLoads() {
	}

}

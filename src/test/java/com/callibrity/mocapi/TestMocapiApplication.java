package com.callibrity.mocapi;

import org.springframework.boot.SpringApplication;

public class TestMocapiApplication {

	public static void main(String[] args) {
		SpringApplication.from(MocapiApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}

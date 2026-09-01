package com.jucasoliveira.kitchensink;

import org.springframework.boot.SpringApplication;

public class TestKitchensinkApplication {

	public static void main(String[] args) {
		SpringApplication.from(KitchensinkApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}

package com.khuda.khuda_clue_api;

import org.springframework.boot.SpringApplication;

public class TestKhudaClueApiApplication {

	public static void main(String[] args) {
		SpringApplication.from(KhudaClueApiApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}

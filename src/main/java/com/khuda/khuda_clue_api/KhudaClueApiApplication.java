package com.khuda.khuda_clue_api;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class KhudaClueApiApplication {

	public static void main(String[] args) {
		loadEnvFile();
		SpringApplication.run(KhudaClueApiApplication.class, args);
	}

	private static void loadEnvFile() {
		Dotenv dotenv = Dotenv.configure()
				.filename(".env")
				.ignoreIfMissing()
				.load();
		setSystemProperties(dotenv);
	}

	private static void setSystemProperties(Dotenv dotenv) {
		dotenv.entries().forEach(entry -> {
			String key = entry.getKey();
			String value = entry.getValue();
			// 시스템 환경 변수가 이미 설정되어 있으면 덮어쓰지 않음
			if (System.getenv(key) == null) {
				System.setProperty(key, value);
			}
		});
	}

}

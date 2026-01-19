package com.khuda.khuda_clue_api;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.nio.file.Files;
import java.nio.file.Paths;

@SpringBootApplication
public class KhudaClueApiApplication {

	public static void main(String[] args) {
		loadEnvFile();
		SpringApplication.run(KhudaClueApiApplication.class, args);
	}

	private static void loadEnvFile() {
		String envFilePath = ".env.local";
		
		// 로컬 환경: .env.local 파일이 있으면 사용
		if (Files.exists(Paths.get(envFilePath))) {
			Dotenv dotenv = Dotenv.configure()
					.filename(".env.local")
					.ignoreIfMissing()
					.load();
			setSystemProperties(dotenv);
		} else {
			// 배포 환경: .env 파일 사용
			Dotenv dotenv = Dotenv.configure()
					.filename(".env")
					.ignoreIfMissing()
					.load();
			setSystemProperties(dotenv);
		}
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

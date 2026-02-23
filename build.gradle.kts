plugins {
	java
	id("org.springframework.boot") version "4.0.2"
	id("io.spring.dependency-management") version "1.1.7"
}

group = "com.khuda"
version = "0.0.1-SNAPSHOT"
description = "Uncover evidence, elevate the applicant’s cover letter fast."

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(21)
	}
}

configurations {
	compileOnly {
		extendsFrom(configurations.annotationProcessor.get())
	}
}

repositories {
	mavenCentral()
	maven { url = uri("https://repo.spring.io/milestone") }
}

// integrationTest SourceSet이 아직 생성되지 않았을 때만 생성
if (!sourceSets.names.contains("integrationTest")) {
	sourceSets {
		create("integrationTest") {
			compileClasspath += sourceSets.main.get().output
			runtimeClasspath += sourceSets.main.get().output
		}
	}
}

configurations {
	"integrationTestImplementation" { extendsFrom(configurations.testImplementation.get()) }
	"integrationTestRuntimeOnly" { extendsFrom(configurations.testRuntimeOnly.get()) }
}

dependencies {
	// Spring AI BOM을 통한 버전 관리 (Spring Boot 4.x 호환 버전)
	implementation(platform("org.springframework.ai:spring-ai-bom:2.0.0-M2"))

	// Spring AI OpenAI 스타터
	implementation("org.springframework.ai:spring-ai-starter-model-openai")

	implementation("org.springframework.boot:spring-boot-starter-actuator")
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	implementation("org.springframework.boot:spring-boot-starter-flyway")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.springframework.boot:spring-boot-starter-webmvc")
	implementation("org.flywaydb:flyway-mysql")
	implementation("io.github.cdimascio:dotenv-java:3.0.0")
	compileOnly("org.projectlombok:lombok")
	developmentOnly("org.springframework.boot:spring-boot-devtools")
	developmentOnly("org.springframework.boot:spring-boot-docker-compose")
	runtimeOnly("com.mysql:mysql-connector-j")
	annotationProcessor("org.projectlombok:lombok")
	annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testImplementation("org.mockito:mockito-core")
	testImplementation("org.springframework.boot:spring-boot-starter-actuator-test")
	testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
	testImplementation("org.springframework.boot:spring-boot-starter-flyway-test")
	testImplementation("org.springframework.boot:spring-boot-starter-validation-test")
	testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
	testImplementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
	testImplementation("org.springframework.boot:spring-boot-testcontainers")
	testImplementation("org.testcontainers:testcontainers-junit-jupiter")
	testImplementation("org.testcontainers:testcontainers-mysql")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.register<Test>("integrationTest") {
	description = "Runs integration tests."
	group = "verification"

	testClassesDirs = sourceSets["integrationTest"].output.classesDirs
	classpath = sourceSets["integrationTest"].runtimeClasspath

	useJUnitPlatform()

	// 단위 테스트와 분리
	shouldRunAfter(tasks.test)
}

tasks.check { dependsOn(tasks.named("integrationTest")) }

tasks.named<Test>("test") {
	useJUnitPlatform()
	failOnNoDiscoveredTests = false
}

tasks.withType<Test> {
	useJUnitPlatform()
}

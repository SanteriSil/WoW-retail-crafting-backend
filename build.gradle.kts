plugins {
	java
	id("org.springframework.boot") version "4.0.0"
	id("io.spring.dependency-management") version "1.1.7"
}

tasks.bootJar {
    archiveFileName.set("wow-crafting-backend.jar")
}

group = "com.crafting"
version = "0.0.1-SNAPSHOT"
description = "Demo project for Spring Boot"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(21)
	}
}

repositories {
	mavenCentral()
}

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	implementation("org.springframework.boot:spring-boot-starter-security")
	implementation("org.springframework.boot:spring-boot-starter-webmvc")
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	runtimeOnly("org.postgresql:postgresql")
	runtimeOnly("com.h2database:h2")
	implementation("org.springframework.boot:spring-boot-starter-validation")

	// DB migrations
	implementation("org.springframework.boot:spring-boot-starter-flyway")
	runtimeOnly("org.flywaydb:flyway-database-postgresql")

	// Lombok compile-only and annotation processor
	val lombokVersion = "1.18.32"
	compileOnly("org.projectlombok:lombok:$lombokVersion")
	annotationProcessor("org.projectlombok:lombok:$lombokVersion")
	testCompileOnly("org.projectlombok:lombok:$lombokVersion")
	testAnnotationProcessor("org.projectlombok:lombok:$lombokVersion")
	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
	testImplementation("org.springframework.boot:spring-boot-starter-security-test")
	testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
	useJUnitPlatform()
}
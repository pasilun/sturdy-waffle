plugins {
	java
	id("org.springframework.boot") version "3.5.0"
	id("io.spring.dependency-management") version "1.1.7"
}

group = "com.sturdywaffle"
version = "0.0.1-SNAPSHOT"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(21)
	}
}

repositories {
	mavenCentral()
}

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-data-jdbc")
	implementation("org.springframework.boot:spring-boot-starter-web")
	implementation("org.flywaydb:flyway-core")
	implementation("org.flywaydb:flyway-database-postgresql")
	runtimeOnly("org.postgresql:postgresql")
	implementation("io.zonky.test:embedded-postgres:2.0.7")
	// Phase 2: LLM calls
	// implementation("com.anthropic:anthropic-java:1.3.0")
	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// Eval task — runs PipelineService against golden fixtures
tasks.register<JavaExec>("eval") {
	dependsOn("compileJava")
	classpath = sourceSets["main"].runtimeClasspath
	mainClass = "com.sturdywaffle.eval.EvalRunner"
	systemProperty("spring.profiles.active", "eval")
}

tasks.withType<Test> {
	useJUnitPlatform()
}

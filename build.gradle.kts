plugins {
	java
	id("org.springframework.boot") version "3.5.7"
	id("io.spring.dependency-management") version "1.1.7"
    // Linting
    id("com.diffplug.spotless") version "8.2.1"
}

group = "org.smalltech"
version = "0.0.1-SNAPSHOT"
description = "hashtag local backend"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(25)
	}
}

repositories {
	mavenCentral()
}

dependencies {
    implementation("org.postgresql:postgresql:42.7.7")
    implementation("org.hibernate.orm:hibernate-spatial:6.4.4.Final")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.0")
    implementation("org.springframework.boot:spring-boot-starter-security")
    // Added to send admin notification emails when a user requests account deletion
    implementation("org.springframework.boot:spring-boot-starter-mail")
    implementation("com.google.cloud:google-cloud-storage:2.55.0")
    implementation("com.google.firebase:firebase-admin:9.2.0")
    implementation("org.locationtech.jts:jts-core:1.19.0")
    implementation("com.vladmihalcea:hibernate-types-60:2.21.1")
    // Feed link-preview scraping (Open Graph / oEmbed parsing)
    implementation("org.jsoup:jsoup:1.18.1")
	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testImplementation("org.springframework.boot:spring-boot-starter-webflux")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("com.nimbusds:nimbus-jose-jwt:9.40")
    // Versioned SQL migrations (src/main/resources/db/migration); off unless FLYWAY_ENABLED=true
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
}

dependencies {
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    testCompileOnly("org.projectlombok:lombok")
    testAnnotationProcessor("org.projectlombok:lombok")
}

// Variables from .env (if present), minus any already set in the real environment, which wins.
val dotEnv: Map<String, String> =
    file(".env").takeIf { it.exists() }?.readLines().orEmpty()
        .filter { it.isNotBlank() && !it.trim().startsWith("#") }
        .map { it.split("=", limit = 2) }
        .filter { it.size == 2 }
        .associate { (key, value) -> key.trim() to value.trim() }
        .filterKeys { !System.getenv().containsKey(it) }

tasks.withType<Test> {
	useJUnitPlatform()

    // So tests can read TEST_DB_* etc.
    environment(dotEnv)
}


spotless {
    lineEndings = com.diffplug.spotless.LineEnding.UNIX

    java {
        googleJavaFormat()
        importOrder()
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
    }
}

// Make `./gradlew check` run everything
tasks.named("check") {
    dependsOn("spotlessCheck")
}


tasks.named<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") {
    // Load environment variables from system (includes shell export commands)
    environment(System.getenv())
    environment(dotEnv)
}





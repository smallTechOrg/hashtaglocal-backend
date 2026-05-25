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
    implementation("org.locationtech.jts:jts-core:1.19.0")
    implementation("com.vladmihalcea:hibernate-types-60:2.21.1")
	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testImplementation("org.springframework.boot:spring-boot-starter-webflux")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("com.nimbusds:nimbus-jose-jwt:9.40")
}

dependencies {
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    testCompileOnly("org.projectlombok:lombok")
    testAnnotationProcessor("org.projectlombok:lombok")
}

tasks.withType<Test> {
	useJUnitPlatform()
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
    
    // Optionally load from .env file if it exists
    val envFile = file(".env")
    if (envFile.exists()) {
        envFile.readLines()
            .filter { it.isNotBlank() && !it.trim().startsWith("#") }
            .forEach { line ->
                val parts = line.split("=", limit = 2)
                if (parts.size == 2) {
                    val key = parts[0].trim()
                    val value = parts[1].trim()
                    // Only set if not already in system environment
                    if (!System.getenv().containsKey(key)) {
                        environment(key, value)
                    }
                }
            }
    }
}





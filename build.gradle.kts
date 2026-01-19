plugins {
	java
	id("org.springframework.boot") version "3.5.7"
	id("io.spring.dependency-management") version "1.1.7"
    // Linting
    id("com.diffplug.spotless") version "6.25.0"
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
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.6.0")
    implementation("com.google.cloud:google-cloud-storage:2.55.0")
    implementation("org.locationtech.jts:jts-core:1.19.0")
    implementation("com.vladmihalcea:hibernate-types-60:2.21.1")
	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testImplementation("org.springframework.boot:spring-boot-starter-webflux")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    testImplementation("com.h2database:h2")
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
    java {
        eclipse()                // works with Java 25
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
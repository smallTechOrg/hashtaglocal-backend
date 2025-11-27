# backend-boilerplate-java
a barebones enterprise application boilerplate in Java (using Spring Boot)

## Tech Stack

- [Gradle](https://gradle.org/) - Flexible build system 
- [SpringBoot](https://www.typescriptlang.org/) – Multi-purpose Framework

## Features
- Built on top of the [Spring Initializer](https://start.spring.io/) setup
- Fast, easy startup for any new service

## Prerequisites

### Setup IntelliJ IDE
We are not only going to use IntelliJ's IDE as a code editor,
but we will also be using to install and maintain different Java runtimes.

### Install Java
Install Java using the IntelliJ console.
Refer `.tool-versions` for the exact java version.

### Install Postgres (with PostGIS)
We are currently using version 18 of postgres.
And version 3.6 of PostGIS.

### Build using Gradle
Once you have downloaded the JDK onto IntelliJ, you can build using gradle.
You can do this via the GUI or via this command:
```declarative
./gradlew build
```

## Running the Application
You can run the app via GUI or via this command
```declarative
./gradlew bootRun
```
Verify if the app is up on http://localhost:8080/actuator/health

## Linting
We are using [spotless](https://github.com/diffplug/spotless) to do linting checks.
The linting checks will automatically run at build. 
But if you want to check the linting without building, use this command:
```declarative
./gradlew check
```
To fix any linting issues, run this:
```declarative
./gradlew :spotlessApply
```
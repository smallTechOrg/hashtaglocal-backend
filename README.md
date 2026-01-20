# hashtag-local
the backend for #local, a location based community platform

## Prerequisites

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

### Connect with GCS buckey
Once you have downloaded the SA key in your local, you can set your credential using this command:
```declarative
$env:GOOGLE_APPLICATION_CREDENTIALS="C:\GCS-key.json" (path of the downloaded json file)
```
If Gradle is running, stop it using:
```declarative
./gradlew --stop
```
After setting the credentials, run your Gradle build command as usual.

## Running the Application
You can run the app via GUI or via this command
```declarative
./gradlew bootRun --args='--spring.profiles.active=local'
```
Verify if the app is up on http://localhost:8080/actuator/health
You can also check the OpenAPI docs on http://localhost:8080/v1/swagger-ui/index.html

## Configuring Local
You can override the `src/main/resources/application.yaml` by creating a `src/main/resources/application-local.yaml` and override the values you need starting with DB.
You will have to do the same for running the tests: `src/test/resources/application-test.yaml`.

Also, make sure to create the necessary DBs as well on your local:
"hashtaglocal" and "hashtaglocaltest".

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

## Testing
To run all the test cases, use this command:
```declarative
./gradlew test
```
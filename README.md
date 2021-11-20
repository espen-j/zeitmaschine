# zeitmaschine

## Run the application

```
$ docker-compose up
```

## Development

Initialize the accompanying services minio, elasticsearch and imaginary:
```
$ docker-compose -f docker-compose-dev.yml up
```
Open the project in e.g. IntelliJ and run the Spring Boot application.

### Bootstrap demo content

Activate `demo` profile and pass the `demo.dir` property to the spring boot application, e.g.: 

```
java -jar application.jar --spring.profiles.active=demo --demo.dir=<path_to_jpeg_folder>
```

Or add it to your IntelliJ Application launcher during development.

### Maven build

The tests rely on testcontainers framework, which in turn needs docker installed on your development machine.
```
$ mvn clean verify -U
$ mvn clean verify -U -Pintegration-tests
```

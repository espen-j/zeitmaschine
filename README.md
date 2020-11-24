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

### Maven build

The tests rely on testcontainers framework, which in turn needs docker installed on your development machine.
```
$ mvn clean verify -U
$ mvn clean verify -U -Pintegration-tests
```

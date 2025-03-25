FROM eclipse-temurin:21-jre-alpine as build

WORKDIR /build

# Copy the local code to the container
COPY . .
RUN ./mvnw package

FROM eclipse-temurin:21-jre-alpine

COPY --from=build /build/target/indy-advice-transformer-*-jar-with-dependencies.jar /transformer.jar

CMD ["java", "-jar", "./transformer.jar", "/srcdir"]
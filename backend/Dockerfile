FROM maven:3.9-eclipse-temurin-21

WORKDIR /app

# Слой зависимостей кэшируется, пока pom.xml не меняется
COPY pom.xml .
RUN mvn -B dependency:go-offline

COPY src ./src

EXPOSE 8080
CMD ["mvn", "spring-boot:run"]

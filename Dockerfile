# ---- Build stage ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Copy all POMs first so Maven can cache dependencies
COPY pom.xml .
COPY common/pom.xml   common/
COPY auth/pom.xml     auth/
COPY template/pom.xml template/
COPY core/pom.xml     core/
COPY billing/pom.xml  billing/
COPY admin/pom.xml    admin/
COPY api/pom.xml      api/
RUN mvn -B -q dependency:go-offline -pl api -am || true

# Copy sources and build only api + its dependencies
COPY . .
RUN mvn -B clean package -DskipTests -pl api -am

# ---- Runtime stage ----
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/api/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY ai-cs-common/pom.xml ai-cs-common/
COPY ai-cs-open/pom.xml ai-cs-open/
RUN mvn dependency:go-offline -pl ai-cs-open -am -B
COPY . .
RUN mvn package -pl ai-cs-open -am -DskipTests -B

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/ai-cs-open/target/*.jar app.jar
EXPOSE 8086
ENV JAVA_OPTS="-Xms256m -Xmx512m"
ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS} -jar app.jar"]

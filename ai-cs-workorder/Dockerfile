FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY ai-cs-common/pom.xml ai-cs-common/
COPY ai-cs-api/pom.xml ai-cs-api/
COPY ai-cs-workorder/pom.xml ai-cs-workorder/
RUN mvn dependency:go-offline -pl ai-cs-workorder -am -B
COPY . .
RUN mvn package -pl ai-cs-workorder -am -DskipTests -B

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/ai-cs-workorder/target/*.jar app.jar
EXPOSE 8085
ENV JAVA_OPTS="-Xms256m -Xmx512m"
ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS} -jar app.jar"]

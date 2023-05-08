# build and run spring boot application
FROM amd64/eclipse-temurin:17-jdk-alpine as build
ENV elasticsearch_version=7.17.10

LABEL name="index-bot"
LABEL version="2.0.0-next"

RUN mkdir -p /opt/index-bot/lang /opt/index-bot/data /usr/src/index-bot
COPY docker/lang /opt/index-bot/
COPY index-bot /usr/src/index-bot
COPY index-bot/src/main/resources/application.yaml /opt/index-bot/application.yaml
WORKDIR /usr/src/index-bot
RUN chmod 775 gradlew && ./gradlew --no-build-cache --no-configuration-cache  bootJar
RUN cp build/libs/telegram-index-bot-2.0.0-next.jar /opt/index-bot/index-bot.jar && \
    rm -rf /usr/src/index-bot

FROM amd64/eclipse-temurin:17-jre-alpine as run
COPY --from=build /opt/index-bot /opt/index-bot
WORKDIR /opt/index-bot
CMD ["java", "-jar", "/opt/index-bot/index-bot.jar", "--spring.config.location=/opt/index-bot/application.yaml"]


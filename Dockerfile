# syntax=docker/dockerfile:1

# args gradle base
FROM amd64/eclipse-temurin:17-jdk-alpine as base
ARG elasticsearch_version_arg=7.17.10
ENV elasticsearch_version=${elasticsearch_version_arg}
ENV GRADLE_USER_HOME=/usr/src/index-bot/.gradle
COPY index-bot/gradle/ /usr/src/index-bot/gradle
COPY index-bot/gradlew /usr/src/index-bot/
RUN --mount=type=cache,target=/usr/src/index-bot/.gradle \
    cd /usr/src/index-bot/ && chmod 775 gradlew && ./gradlew  --version

# cache
FROM base as cache
RUN  mkdir -p /usr/src/index-bot/gradle
WORKDIR /usr/src/index-bot
COPY index-bot/build.gradle.kts /usr/src/index-bot/
COPY index-bot/settings.gradle.kts /usr/src/index-bot/
COPY index-bot/gradle/ /usr/src/index-bot/gradle
COPY index-bot/gradlew /usr/src/index-bot/
COPY index-bot/gradle.properties /usr/src/index-bot/
RUN --mount=type=cache,target=/usr/src/index-bot/.gradle \
    --mount=type=cache,target=/usr/src/index-bot/build \
    ./gradlew dependencies compileKotlin classes

# build and run spring boot application
FROM cache as build
# making dirs
RUN mkdir -p /opt/index-bot/lang /opt/index-bot/data /usr/src/index-bot
COPY docker/lang /opt/index-bot/
COPY index-bot /usr/src/index-bot
COPY index-bot/src/main/resources/application.yaml /opt/index-bot/application.yaml
# in build
RUN apk add libstdc++
WORKDIR /usr/src/index-bot
RUN --mount=type=cache,target=/usr/src/index-bot/.gradle \
    --mount=type=cache,target=/usr/src/index-bot/build \
 ./gradlew build bootJar -x test || ./gradlew build bootJar -x test || ./gradlew build bootJar -x test || ./gradlew build bootJar -x test|| ./gradlew build bootJar -x test

# RUN --mount=type=bind,source=./index-bot/build,target=./build./gradlew bootJar
# RUN --mount=type=cache,target=/usr/src/index-bot/build ./gradlew bootJar
#
RUN --mount=type=cache,target=/usr/src/index-bot/.gradle \
    --mount=type=cache,target=/usr/src/index-bot/build \
    cp build/libs/telegram-index-bot-2.0.0-next.jar /opt/index-bot/index-bot.jar

FROM ghcr.io/graalvm/native-image:muslib-ol9-java17-22.3.2 as test
COPY --from=build /opt/index-bot /opt/index-bot

WORKDIR /opt/index-bot
COPY docker/ib/index-bot-entrypoint.sh /opt/index-bot/
RUN chmod 775 index-bot-entrypoint.sh

ENV APP_CONFIG=/opt/index-bot/application.yaml
ENTRYPOINT [ "sh","index-bot-entrypoint.sh" ]


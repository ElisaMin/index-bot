# syntax=docker/dockerfile:1
FROM ubuntu:mantic as graalvm
ARG JDK_VERSION=20.0.1
ARG ARCH=x64
ENV GRAALVM_HOME=/opt/graalvm/
WORKDIR /tmp
RUN mkdir -p $GRAALVM_HOME
RUN set -e && \
    apt update && apt install -y wget && \
    GRAALVM_FILES=graalvm-community-jdk-${JDK_VERSION}_linux-${ARCH}_bin && \
    # download tar file using wget
    wget https://github.com/graalvm/graalvm-ce-builds/releases/download/jdk-${JDK_VERSION}/${GRAALVM_FILES}.tar.gz && \
    # extract tar file to /opt
    tar -xvzf ${GRAALVM_FILES}.tar.gz -C ${GRAALVM_HOME} && \
    # remove tar file
    rm -rf $GRAALVM_FILES.tar.gz && \
    cd ${GRAALVM_HOME} && dir="$(find . -maxdepth 1 -type d -name graalvm* )" && \
    mv $dir/* $GRAALVM_HOME && rm -rf $dir && \
    apt remove -y wget && apt autoremove -y && apt clean && \
    ./bin/gu install native-image

ENV JAVA_HOME=${GRAALVM_HOME} \
    PATH="${GRAALVM_HOME}bin:$PATH"


FROM graalvm as base
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
RUN #apk add libstdc++
WORKDIR /usr/src/index-bot
RUN --mount=type=cache,target=/usr/src/index-bot/.gradle \
    --mount=type=cache,target=/usr/src/index-bot/build  \
    ./gradlew build -x test && ./gradlew bootJar
# RUN --mount=type=bind,source=./index-bot/build,target=./build./gradlew bootJar
# RUN --mount=type=cache,target=/usr/src/index-bot/build ./gradlew bootJar
#
RUN --mount=type=cache,target=/usr/src/index-bot/.gradle \
    --mount=type=cache,target=/usr/src/index-bot/build \
    cp build/libs/telegram-index-bot-2.0.0-next.jar /opt/index-bot/index-bot.jar

FROM graalvm as test
RUN set -e && \
    apt update && apt install -y curl && apt clean
ENV APP_CONFIG=/opt/index-bot/application.yaml
COPY --from=build /opt/index-bot /opt/index-bot
COPY docker/ib/index-bot-entrypoint.sh /opt/index-bot/
WORKDIR /opt/index-bot
RUN chmod 775 index-bot-entrypoint.sh
ENTRYPOINT [ "bash","index-bot-entrypoint.sh" ]
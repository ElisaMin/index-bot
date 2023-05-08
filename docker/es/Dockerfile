FROM amd64/eclipse-temurin:17-jdk-alpine

LABEL maintainer="https://github.com/blacktop"

ENV VERSION 7.17.10
# downloading
RUN wget -O /usr/share/elasticsearch.tar.gz    "https://artifacts.elastic.co/downloads/elasticsearch/elasticsearch-${VERSION}-no-jdk-linux-x86_64.tar.gz"
RUN wget -O /tmp/elasticsearch-analysis-ik.zip "https://github.com/ElisaMin/elasticsearch-analysis-ik/releases/download/${VERSION}/elasticsearch-analysis-ik-${VERSION}.zip"
WORKDIR /usr/share/
# installing
RUN tar -zxvf elasticsearch.tar.gz  >> /dev/null
RUN mv -f elasticsearch-${VERSION} elasticsearch
RUN unzip /tmp/elasticsearch-analysis-ik.zip -d /usr/share/elasticsearch/plugins/ik
# del zip
RUN rm -rf elasticsearch.tar.gz && \
    rm -rf /tmp/* /usr/share/elasticsearch/jdk

WORKDIR /usr/share/elasticsearch

RUN adduser -D -h /usr/share/elasticsearch elasticsearch && \
    chown -R elasticsearch:elasticsearch /usr/share/elasticsearch/ && \
    for path in \
        /usr/share/elasticsearch/data \
        /usr/share/elasticsearch/logs \
        /usr/share/elasticsearch/config \
        /usr/share/elasticsearch/config/scripts \
        /usr/share/elasticsearch/tmp \
        /usr/share/elasticsearch/plugins ;\
    do \
      mkdir -p "$path"; chown -R elasticsearch:elasticsearch "$path"; \
    done

### DONE ###

RUN apk add --no-cache su-exec bash
RUN rm -rf /usr/share/elasticsearch/modules/x-pack-ml/platform/linux-x86_64

COPY elasticsearch.yml /usr/share/elasticsearch/config/elasticsearch.yml
COPY elastic-entrypoint.sh /
COPY docker-healthcheck /usr/local/bin/

RUN chmod +x /elastic-entrypoint.sh
RUN chmod +x /usr/share/elasticsearch/bin/elasticsearch

ENV PATH /usr/share/elasticsearch/bin:$PATH
ENV ES_JAVA_OPTS="-Xms512m -Xmx512m"
ENV ES_TMPDIR /usr/share/elasticsearch/tmp

# VOLUME ["/usr/share/elasticsearch/data"]

EXPOSE 9900

ENTRYPOINT ["/elastic-entrypoint.sh"]
CMD ["elasticsearch"]

# HEALTHCHECK CMD ["docker-healthcheck"]

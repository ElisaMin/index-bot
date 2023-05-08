
FROM amd64/eclipse-temurin:17-jdk-alpine
ENV elastic_search_version=7.17.10
ENV IK_PLGUIN=https://github.com/ElisaMin/elasticsearch-analysis-ik/releases/download/$elastic_search_version/elasticsearch-analysis-ik-${elastic_search_version}.zip
ENV ELASTIC_SEARCH_URL=https://artifacts.elastic.co/downloads/elasticsearch/elasticsearch-${elastic_search_version}-linux-x86_64.tar.gz
WORKDIR /usr/share/
# download and unzip elasticsearch
RUN wget -O elasticsearch.tar.gz ${ELASTIC_SEARCH_URL}
RUN tar -zxvf elasticsearch.tar.gz  >> /dev/null
RUN mv -f elasticsearch-${elastic_search_version} elasticsearch && \
    rm -rf elasticsearch.tar.gz

WORKDIR /tmp
# download and unzip elasticsearch-analysis-ik
RUN wget -O elasticsearch-analysis-ik.zip ${IK_PLGUIN} && \
    unzip elasticsearch-analysis-ik.zip -d /usr/share/elasticsearch/plugins/ik && \
    rm -rf elasticsearch-analysis-ik.zip

# copy elasticsearch.yml
COPY elasticsearch.yml /usr/share/elasticsearch/config/elasticsearch.yml
ENV PATH /usr/share/elasticsearch/bin:$PATH
RUN chmod +x /usr/share/elasticsearch/bin/elasticsearch
WORKDIR /usr/share/elasticsearch
EXPOSE 9900
ENV ES_JAVA_OPTS="-Xms512m -Xmx512m"
CMD sh /usr/share/elasticsearch/bin/elasticsearch

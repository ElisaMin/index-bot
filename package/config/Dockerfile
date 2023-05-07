FROM elasticsearch:7.17.10
ENV elastic_search_version=7.17.10
ENV IK_PLGUIN=https://github.com/ElisaMin/elasticsearch-analysis-ik/releases/download/$elastic_search_version/elasticsearch-analysis-ik-${elastic_search_version}.zip

RUN mkdir /usr/share/elasticsearch/plugins/ik
# download and unzip elasticsearch-analysis-ik
RUN curl -L -o elasticsearch-analysis-ik.zip ${IK_PLGUIN} && \
    unzip elasticsearch-analysis-ik.zip -d /usr/share/elasticsearch/plugins/ik && \
    rm -rf elasticsearch-analysis-ik.zip

# copy elasticsearch.yml
COPY elasticsearch.yml /usr/share/elasticsearch/config/elasticsearch.yml
# ENV PATH /opt/elasticsearch/bin:$PATH
# RUN chmod +x /opt/elasticsearch/bin/elasticsearch
# WORKDIR /opt/elasticsearch

package com.tgse.index.infrastructure.provider

import com.tgse.index.ElasticProperties
import com.tgse.index.ElasticSearchException
import org.apache.http.HttpHost
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest
import org.elasticsearch.action.delete.DeleteRequest
import org.elasticsearch.action.delete.DeleteResponse
import org.elasticsearch.action.get.GetRequest
import org.elasticsearch.action.get.GetResponse
import org.elasticsearch.action.index.IndexRequest
import org.elasticsearch.action.search.SearchRequest
import org.elasticsearch.action.search.SearchResponse
import org.elasticsearch.action.update.UpdateRequest
import org.elasticsearch.client.RequestOptions
import org.elasticsearch.client.RestClient
import org.elasticsearch.client.RestHighLevelClient
import org.elasticsearch.client.core.CountRequest
import org.elasticsearch.client.indices.CreateIndexRequest
import org.elasticsearch.client.indices.GetIndexRequest
import org.elasticsearch.index.query.QueryBuilder
import org.elasticsearch.index.query.QueryBuilders
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class ElasticsearchProvider(
    elasticProperties: ElasticProperties
) : AutoCloseable {

    private inline fun <T> wrapError(crossinline block:()->T):T = runCatching(block).getOrElse {
        throw ElasticSearchException(it)
    }
    var i = 0
    val client get() = if (i++>4) wrapError {
        throw NotImplementedError("test")
    } else {
        println("its #$i")
        clientR
    }

    private val clientR = wrapError {
        RestHighLevelClient(
            RestClient.builder(
                HttpHost(elasticProperties.hostname, elasticProperties.port, elasticProperties.schema),
            )
        )
    }

    /**
     * 检查索引是否存在
     */
    fun checkIndexExist(indexName: String): Boolean = wrapError {
        val getRequest = GetIndexRequest(indexName)
        client.indices().exists(getRequest, RequestOptions.DEFAULT)
    }

    /**
     * 创建索引
     */
    fun createIndex(indexName: String) = wrapError {
        val createRequest = CreateIndexRequest(indexName)
        val response = client.indices().create(createRequest, RequestOptions.DEFAULT)
        response.isAcknowledged
    }

    /**
     * 创建索引
     */
    fun createIndex(createRequest: CreateIndexRequest): Boolean = wrapError {
        val response = client.indices().create(createRequest, RequestOptions.DEFAULT)
        response.isAcknowledged
    }

    /**
     * 删除索引
     */
    fun deleteIndex(indexName: String): Boolean = wrapError {
        val deleteRequest = DeleteIndexRequest(indexName)
        val response = client.indices().delete(deleteRequest, RequestOptions.DEFAULT)
        response.isAcknowledged
    }

    /**
     * 索引文档
     */
    fun indexDocument(request: IndexRequest): Boolean = wrapError {
        runCatching {
            client.index(request, RequestOptions.DEFAULT)
        }.onFailure {it.printStackTrace()}.isSuccess
    }

    /**
     * 更新文档
     */
    fun updateDocument(request: UpdateRequest): Boolean = wrapError {
        runCatching {
            client.update(request, RequestOptions.DEFAULT)
        }.onFailure { it.printStackTrace() }.isSuccess
    }

    /**
     * 获取文档
     */
    fun getDocument(request: GetRequest): GetResponse = wrapError {
        client.get(request, RequestOptions.DEFAULT)
    }

    /**
     * 删除文档
     */
    fun deleteDocument(request: DeleteRequest): DeleteResponse = wrapError {
        client.delete(request, RequestOptions.DEFAULT)
    }

    /**
     * 文档数量
     */
    fun countOfDocument(index: String): Long = wrapError {
        val countRequest = CountRequest(index)
        countRequest.query(QueryBuilders.matchAllQuery())
        val response = client.count(countRequest, RequestOptions.DEFAULT)
        response.count
    }

    fun countOfQuery(index: String, query: QueryBuilder): Long = wrapError {
        val countRequest = CountRequest(index)
        countRequest.query(query)
        val response = client.count(countRequest, RequestOptions.DEFAULT)
        response.count
    }

    fun search(searchRequest: SearchRequest): SearchResponse = wrapError {
        client.search(searchRequest, RequestOptions.DEFAULT)
    }

    override fun close() {
        val logger = LoggerFactory.getLogger(this::class.java)
        logger.warn("close elasticsearch client")
        runCatching {
            clientR.close()
            client.close()
        }.onFailure {
            logger.error("got error when close elasticsearch client")
            it.printStackTrace(System.err)
        }
    }

}
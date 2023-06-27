package com.tgse.index.infrastructure.provider

import com.tgse.index.ElasticProperties
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
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Component

@Component
@Retryable
class ElasticsearchProvider(
    elasticProperties: ElasticProperties
) : AutoCloseable {

    private val client = RestHighLevelClient(
        RestClient.builder(
            HttpHost(elasticProperties.hostname, elasticProperties.port, elasticProperties.schema),
        )
    )

    /**
     * 检查索引是否存在
     */
    fun checkIndexExist(indexName: String): Boolean {
        val getRequest = GetIndexRequest(indexName)
        return client.indices().exists(getRequest, RequestOptions.DEFAULT)
    }

    /**
     * 创建索引
     */
    fun createIndex(indexName: String): Boolean {
        val createRequest = CreateIndexRequest(indexName)
        val response = client.indices().create(createRequest, RequestOptions.DEFAULT)
        return response.isAcknowledged
    }

    /**
     * 创建索引
     */
    fun createIndex(createRequest: CreateIndexRequest): Boolean {
        val response = client.indices().create(createRequest, RequestOptions.DEFAULT)
        return response.isAcknowledged
    }

    /**
     * 删除索引
     */
    fun deleteIndex(indexName: String): Boolean {
        val deleteRequest = DeleteIndexRequest(indexName)
        val response = client.indices().delete(deleteRequest, RequestOptions.DEFAULT)
        return response.isAcknowledged
    }

    /**
     * 索引文档
     */
    fun indexDocument(request: IndexRequest): Boolean {
        return try {
            client.index(request, RequestOptions.DEFAULT)
            true
        } catch (e: Throwable) {
            e.printStackTrace()
            false
        }
    }

    /**
     * 更新文档
     */
    fun updateDocument(request: UpdateRequest): Boolean {
        return try {
            client.update(request, RequestOptions.DEFAULT)
            true
        } catch (e: Throwable) {
            e.printStackTrace()
            false
        }
    }

    /**
     * 获取文档
     */
    fun getDocument(request: GetRequest): GetResponse {
        return client.get(request, RequestOptions.DEFAULT)
    }

    /**
     * 删除文档
     */
    fun deleteDocument(request: DeleteRequest): DeleteResponse {
        return client.delete(request, RequestOptions.DEFAULT)
    }

    /**
     * 文档数量
     */
    fun countOfDocument(index: String): Long {
        val countRequest = CountRequest(index)
        countRequest.query(QueryBuilders.matchAllQuery())
        val response = client.count(countRequest, RequestOptions.DEFAULT)
        return response.count
    }

    fun countOfQuery(index: String, query: QueryBuilder): Long {
        val countRequest = CountRequest(index)
        countRequest.query(query)
        val response = client.count(countRequest, RequestOptions.DEFAULT)
        return response.count
    }

    fun search(searchRequest: SearchRequest): SearchResponse {
        return client.search(searchRequest, RequestOptions.DEFAULT)
    }

    override fun close() {
        client.close()
    }

}
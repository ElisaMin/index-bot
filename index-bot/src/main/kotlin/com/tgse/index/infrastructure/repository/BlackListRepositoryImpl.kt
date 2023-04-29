package com.tgse.index.infrastructure.repository

import com.tgse.index.domain.repository.BlackListRepository
import com.tgse.index.domain.service.BlackListService
import com.tgse.index.infrastructure.provider.ElasticsearchProvider
import org.elasticsearch.action.delete.DeleteRequest
import org.elasticsearch.action.index.IndexRequest
import org.elasticsearch.action.search.SearchRequest
import org.elasticsearch.action.update.UpdateRequest
import org.elasticsearch.index.query.MatchQueryBuilder
import org.elasticsearch.index.query.QueryBuilders
import org.elasticsearch.search.builder.SearchSourceBuilder
import org.elasticsearch.xcontent.XContentBuilder
import org.elasticsearch.xcontent.XContentFactory
import org.springframework.stereotype.Repository

@Repository
class BlackListRepositoryImpl(
    private val elasticsearchProvider: ElasticsearchProvider
) : BlackListRepository {

    private val index = "blacklist"

    init {
        initializeBlacklist()
    }

    private fun initializeBlacklist() {
        val exist = elasticsearchProvider.checkIndexExist(index)
        if (exist) return
        elasticsearchProvider.createIndex(index)
    }

    override fun get(username: String): BlackListService.Black? {
        val queryBuilder = QueryBuilders.matchQuery("username", username)
        return getBlack(queryBuilder)
    }

    override fun get(chatId: Long): BlackListService.Black? {
        val queryBuilder = QueryBuilders.matchQuery("chatId", chatId)
        return getBlack(queryBuilder)
    }

    private fun getBlack(queryBuilder: MatchQueryBuilder): BlackListService.Black? {
        val searchRequest = SearchRequest(index)
        val searchSourceBuilder = SearchSourceBuilder()
        searchSourceBuilder.query(queryBuilder)
        searchRequest.source(searchSourceBuilder)
        val response = elasticsearchProvider.search(searchRequest)
        return if (response.hits.totalHits!!.value < 1) null
        else generateBlackFromHashMap(response.hits.hits[0].id, response.hits.hits[0].sourceAsMap)
    }

    override fun add(black: BlackListService.Black): Boolean {
        val builder = generateXContentFromBlack(black)
        val indexRequest = IndexRequest(index)
        indexRequest.id(black.uuid).source(builder)
        return elasticsearchProvider.indexDocument(indexRequest)
    }

    override fun update(black: BlackListService.Black): Boolean {
        val builder = generateXContentFromBlack(black)
        val updateRequest = UpdateRequest(index, black.uuid).doc(builder)
        return elasticsearchProvider.updateDocument(updateRequest)
    }

    override fun delete(uuid: String) {
        val deleteRequest = DeleteRequest(index, uuid)
        elasticsearchProvider.deleteDocument(deleteRequest)
    }

    private fun generateXContentFromBlack(black: BlackListService.Black): XContentBuilder {
        val builder = XContentFactory.jsonBuilder()
        builder.startObject()
        builder.field("type", black.type.name)
        builder.field("displayName", black.displayName)
        builder.field("level", black.level)
        builder.field("chatId", black.chatId)
        builder.field("username", black.username)
        builder.field("unfreezeTime", black.unfreezeTime)
        builder.endObject()
        return builder
    }

    private fun generateBlackFromHashMap(uuid: String, map: MutableMap<String, Any?>): BlackListService.Black {
        return BlackListService.Black(
            uuid,
            BlackListService.BlackType.valueOf(map["type"] as String),
            map["displayName"] as String,
            map["level"] as Int,
            when (map["chatId"]) {
                is Int -> (map["chatId"] as Int).toLong()
                is Long -> map["chatId"] as Long
                else -> null
            },
            map["username"] as String?,
            when (map["unfreezeTime"]) {
                is Int -> (map["unfreezeTime"] as Int).toLong()
                is Long -> map["unfreezeTime"] as Long
                else -> 0L
            }
        )
    }

}
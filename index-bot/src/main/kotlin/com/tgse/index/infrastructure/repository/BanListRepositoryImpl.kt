package com.tgse.index.infrastructure.repository

import com.tgse.index.domain.repository.BanListRepository
import com.tgse.index.domain.service.BanListService
import com.tgse.index.infrastructure.provider.ElasticsearchProvider
import org.elasticsearch.action.delete.DeleteRequest
import org.elasticsearch.action.index.IndexRequest
import org.elasticsearch.action.search.SearchRequest
import org.elasticsearch.index.query.MatchQueryBuilder
import org.elasticsearch.index.query.QueryBuilders
import org.elasticsearch.search.builder.SearchSourceBuilder
import org.elasticsearch.search.sort.SortOrder
import org.elasticsearch.xcontent.XContentBuilder
import org.elasticsearch.xcontent.XContentFactory
import org.springframework.stereotype.Repository

@Repository
class BanListRepositoryImpl(
    private val elasticsearchProvider: ElasticsearchProvider
) : BanListRepository {

    private val index = "banlist"

    init {
        initializeBanlist()
    }

    private fun initializeBanlist() {
        val exist = elasticsearchProvider.checkIndexExist(index)
        if (exist) return
        elasticsearchProvider.createIndex(index)
    }

    override fun add(ban: BanListService.Ban): Boolean {
        val builder = generateXContentFromBan(ban)
        val indexRequest = IndexRequest(index)
        indexRequest.id(ban.uuid).source(builder)
        return elasticsearchProvider.indexDocument(indexRequest)
    }

    override fun get(chatId: Long): BanListService.Ban? {
        val queryBuilder = QueryBuilders.matchQuery("chatId", chatId)
        return getBan(queryBuilder)
    }

    override fun all(): Pair<Collection<BanListService.Ban>, Long> {
        val searchRequest = SearchRequest(index)
        val searchSourceBuilder = SearchSourceBuilder()
        val queryBuilder = QueryBuilders.matchAllQuery()
        searchSourceBuilder.query(queryBuilder).sort("createTime", SortOrder.DESC)
        searchRequest.source(searchSourceBuilder)
        return try {
            val response = elasticsearchProvider.search(searchRequest)
            val records = arrayListOf<BanListService.Ban>()
            response.hits.hits.forEach {
                val record = generateBanFromHashMap(it.id, it.sourceAsMap)
                records.add(record)
            }
            val totalCount = response.hits.totalHits?.value ?: 0L
            Pair(records.toMutableList(), totalCount)
        } catch (e: Throwable) {
            Pair(mutableListOf(), 0L)
        }
    }

    override fun delete(uuid: String) {
        val deleteRequest = DeleteRequest(index, uuid)
        elasticsearchProvider.deleteDocument(deleteRequest)
    }

    private fun getBan(queryBuilder: MatchQueryBuilder): BanListService.Ban? {
        val searchRequest = SearchRequest(index)
        val searchSourceBuilder = SearchSourceBuilder()
        searchSourceBuilder.query(queryBuilder)
        searchRequest.source(searchSourceBuilder)
        val response = elasticsearchProvider.search(searchRequest)
        return if (response.hits.totalHits!!.value < 1) null
        else generateBanFromHashMap(response.hits.hits[0].id, response.hits.hits[0].sourceAsMap)
    }

    private fun generateXContentFromBan(ban: BanListService.Ban): XContentBuilder {
        val builder = XContentFactory.jsonBuilder()
        builder.startObject()
        builder.field("chatId", ban.chatId)
        builder.field("createTime", ban.createTime)
        builder.endObject()
        return builder
    }

    private fun generateBanFromHashMap(uuid: String, map: MutableMap<String, Any?>): BanListService.Ban {
        return BanListService.Ban(
            uuid,
            when (map["chatId"]) {
                is Int -> (map["chatId"] as Int).toLong()
                is Long -> map["chatId"] as Long
                else -> null
            },
            map["createTime"] as Long
        )
    }

}
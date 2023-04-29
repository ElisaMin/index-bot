package com.tgse.index.infrastructure.repository

import com.pengrad.telegrambot.model.User
import com.tgse.index.domain.repository.RecordRepository
import com.tgse.index.domain.service.RecordService
import com.tgse.index.domain.service.TelegramService
import com.tgse.index.infrastructure.provider.ElasticsearchProvider
import org.elasticsearch.action.delete.DeleteRequest
import org.elasticsearch.action.get.GetRequest
import org.elasticsearch.action.index.IndexRequest
import org.elasticsearch.action.search.SearchRequest
import org.elasticsearch.action.update.UpdateRequest
import org.elasticsearch.client.indices.CreateIndexRequest
import org.elasticsearch.common.settings.Settings
import org.elasticsearch.index.query.MatchQueryBuilder
import org.elasticsearch.index.query.QueryBuilders
import org.elasticsearch.search.builder.SearchSourceBuilder
import org.elasticsearch.search.sort.SortOrder
import org.elasticsearch.xcontent.XContentBuilder
import org.elasticsearch.xcontent.XContentFactory
import org.elasticsearch.xcontent.XContentType
import org.springframework.stereotype.Repository

@Repository
class RecordRepositoryImpl(
    private val elasticsearchProvider: ElasticsearchProvider
) : RecordRepository {


    private val index = "record"

    init {
        initializeRecord()
    }

    private fun initializeRecord() {
        val exist = elasticsearchProvider.checkIndexExist(index)
        if (exist) return

        val request = CreateIndexRequest(index)
        request.settings(
            Settings.builder()
                .put("index.number_of_shards", 3)
                .put("index.number_of_replicas", 1)
        )
        request.mapping(
            """
            {
                "properties": {
                    "bulletinMessageId": {
                        "type": "integer"
                    },
                    "type": {
                        "type": "keyword"
                    },
                    "chatId": {
                        "type": "long"
                    },
                    "title": {
                        "type": "text",
                        "analyzer": "ik_max_word",
                        "search_analyzer": "ik_max_word"
                    },
                    "description": {
                        "type": "text"
                    },
                    "tags": {
                        "type": "text",
                        "analyzer": "ik_max_word",
                        "search_analyzer": "ik_max_word"
                    },
                    "classification": {
                        "type": "text",
                        "analyzer": "ik_max_word",
                        "search_analyzer": "ik_max_word"
                    },
                    "username": {
                        "type": "text"
                    },
                    "link": {
                        "type": "text"
                    },
                    "members": {
                        "type": "long"
                    },
                    "createTime": {
                        "type": "date"
                    },
                    "createUser": {
                        "type": "text"
                    },
                    "createUserName": {
                        "type": "text"
                    }
                }
            }
            """.trimIndent(),
            XContentType.JSON
        )
        elasticsearchProvider.createIndex(request)
    }

    /**
     * 查询所有
     */
    override fun getAllRecords(from: Int, size: Int): Pair<MutableList<RecordService.Record>, Long> {
        val searchRequest = SearchRequest(index)
        val searchSourceBuilder = SearchSourceBuilder()
        val queryBuilder = QueryBuilders.matchAllQuery()
        searchSourceBuilder.query(queryBuilder).from(from).size(size).sort("updateTime", SortOrder.ASC)
        searchRequest.source(searchSourceBuilder)
        return searchRecords(searchRequest)
    }

    /**
     * 根据分类查询
     */
    override fun searchRecordsByClassification(classification: String, from: Int, size: Int): Pair<MutableList<RecordService.Record>, Long> {
        val searchRequest = SearchRequest(index)
        val searchSourceBuilder = SearchSourceBuilder()
        val queryBuilder = QueryBuilders.matchQuery("classification", classification)
        searchSourceBuilder.query(queryBuilder).from(from).size(size).sort("members", SortOrder.DESC)
        searchRequest.source(searchSourceBuilder)
        return searchRecords(searchRequest)
    }

    /**
     * 根据关键词查询
     */
    override fun searchRecordsByKeyword(keyword: String, from: Int, size: Int): Pair<MutableList<RecordService.Record>, Long> {
        val searchRequest = SearchRequest(index)
        val searchSourceBuilder = SearchSourceBuilder()
        val queryBuilder = QueryBuilders.multiMatchQuery(keyword, "title", "tags", "classification")
        searchSourceBuilder.query(queryBuilder).from(from).size(size).sort("_score", SortOrder.DESC).sort("members", SortOrder.DESC)
        searchRequest.source(searchSourceBuilder)
        return searchRecords(searchRequest)
    }

    /**
     * 根据提交者查询
     */
    override fun searchRecordsByCreator(user: User, from: Int, size: Int): Pair<MutableList<RecordService.Record>, Long> {
        val searchRequest = SearchRequest(index)
        val searchSourceBuilder = SearchSourceBuilder()
        val queryBuilder = QueryBuilders.matchQuery("createUser", user.id())
        searchSourceBuilder.query(queryBuilder).from(from).size(size).sort("createTime", SortOrder.DESC)
        searchRequest.source(searchSourceBuilder)
        return searchRecords(searchRequest)
    }

    private fun searchRecords(searchRequest: SearchRequest): Pair<MutableList<RecordService.Record>, Long> {
        return try {
            val response = elasticsearchProvider.search(searchRequest)

            val records = arrayListOf<RecordService.Record>()
            response.hits.hits.forEach {
                val record = generateRecordFromHashMap(it.id, it.sourceAsMap)
                records.add(record)
            }
            val totalCount = response.hits.totalHits?.value ?: 0L
            Pair(records.toMutableList(), totalCount)
        } catch (e: Throwable) {
            Pair(mutableListOf(), 0L)
        }
    }

    override fun addRecord(record: RecordService.Record): Boolean {
        val builder = generateXContentFromRecord(record)
        val indexRequest = IndexRequest(index)
        indexRequest.id(record.uuid).source(builder)
        return elasticsearchProvider.indexDocument(indexRequest)
    }

    override fun updateRecord(record: RecordService.Record) {
        val builder = generateXContentFromRecord(record)
        val updateRequest = UpdateRequest(index, record.uuid).doc(builder)
        elasticsearchProvider.updateDocument(updateRequest)
    }

    override fun deleteRecord(uuid: String, manager: User) {
        val deleteRequest = DeleteRequest(index, uuid)
        elasticsearchProvider.deleteDocument(deleteRequest)
    }

    override fun getRecord(uuid: String): RecordService.Record? {
        val request = GetRequest(index, uuid)
        val response = elasticsearchProvider.getDocument(request)
        if (!response.isExists) return null
        return generateRecordFromHashMap(uuid, response.sourceAsMap)
    }

    override fun getRecordByUsername(username: String): RecordService.Record? {
        val queryBuilder = QueryBuilders.matchQuery("username", username)
        return getRecord(queryBuilder)
    }

    override fun getRecordByChatId(chatId: Long): RecordService.Record? {
        val queryBuilder = QueryBuilders.matchQuery("chatId", chatId)
        return getRecord(queryBuilder)
    }

    private fun getRecord(queryBuilder: MatchQueryBuilder): RecordService.Record? {
        val searchRequest = SearchRequest(index)
        val searchSourceBuilder = SearchSourceBuilder()
        searchSourceBuilder.query(queryBuilder)
        searchRequest.source(searchSourceBuilder)
        val response = elasticsearchProvider.search(searchRequest)
        return if (response.hits.totalHits!!.value < 1) null
        else generateRecordFromHashMap(response.hits.hits[0].id, response.hits.hits[0].sourceAsMap)
    }

    override fun count(): Long {
        return elasticsearchProvider.countOfDocument(index)
    }

    private fun generateXContentFromRecord(record: RecordService.Record): XContentBuilder {
        val tagsString =
            if (record.tags == null) null
            else record.tags.joinToString(" ")
        val builder = XContentFactory.jsonBuilder()
        builder.startObject()
        builder.field("bulletinMessageId", record.bulletinMessageId)
        builder.field("type", record.type.name)
        builder.field("chatId", record.chatId)
        builder.field("title", record.title)
        builder.field("description", record.description)
        builder.field("tags", tagsString)
        builder.field("classification", record.classification)
        builder.field("username", record.username)
        builder.field("link", record.link)
        builder.field("members", record.members)
        builder.field("createTime", record.createTime)
        builder.field("createUser", record.createUser)
        builder.field("updateTime", record.updateTime)
        builder.endObject()
        return builder
    }

    private fun generateRecordFromHashMap(uuid: String, map: MutableMap<String, Any?>): RecordService.Record {
        val tagsString = map["tags"].toString()
        val tags = when {
            tagsString.contains(" ") -> tagsString.split(" ")
            tagsString == "null" -> null
            else -> listOf(tagsString)
        }
        return RecordService.Record(
            uuid,
            map["bulletinMessageId"] as Int,
            TelegramService.TelegramModType.valueOf(map["type"] as String),
            when (map["chatId"]) {
                is Int -> (map["chatId"] as Int).toLong()
                is Long -> map["chatId"] as Long
                else -> null
            },
            map["title"] as String,
            map["description"] as String?,
            tags,
            map["classification"] as String?,
            map["username"] as String?,
            map["link"] as String?,
            when (map["members"]) {
                is Int -> (map["members"] as Int).toLong()
                is Long -> map["members"] as Long
                else -> null
            },
            map["createTime"] as Long,
            map["createUser"].toString().toLong(),
            map["updateTime"] as Long
        )
    }
}
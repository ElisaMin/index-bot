package com.tgse.index.infrastructure.repository

import com.pengrad.telegrambot.model.User
import com.tgse.index.domain.repository.EnrollRepository
import com.tgse.index.domain.service.EnrollService
import com.tgse.index.domain.service.TelegramService
import com.tgse.index.infrastructure.provider.ElasticsearchProvider
import org.elasticsearch.action.delete.DeleteRequest
import org.elasticsearch.action.get.GetRequest
import org.elasticsearch.action.index.IndexRequest
import org.elasticsearch.action.search.SearchRequest
import org.elasticsearch.action.update.UpdateRequest
import org.elasticsearch.index.query.BoolQueryBuilder
import org.elasticsearch.index.query.QueryBuilders
import org.elasticsearch.search.builder.SearchSourceBuilder
import org.elasticsearch.search.sort.SortOrder
import org.elasticsearch.xcontent.XContentBuilder
import org.elasticsearch.xcontent.XContentFactory
import org.springframework.stereotype.Repository

@Repository
class EnrollRepositoryImpl(
    private val elasticsearchProvider: ElasticsearchProvider
) : EnrollRepository {

    private val index = "enroll"

    init {
        initializeEnroll()
    }

    private fun initializeEnroll() {
        val exist = elasticsearchProvider.checkIndexExist(index)
        if (exist) return
        elasticsearchProvider.createIndex(index)
    }

    override fun searchEnrolls(user: User, from: Int, size: Int): Pair<MutableList<EnrollService.Enroll>, Long> {
        try {
            val searchRequest = SearchRequest(index)
            val searchSourceBuilder = SearchSourceBuilder()
            val creatorMatch = QueryBuilders.matchQuery("createUser", user.id())
            val statusMatch = QueryBuilders.matchQuery("isSubmit", true)
            val approveMatch = QueryBuilders.matchQuery("approve", null)
            val boolQuery = QueryBuilders.boolQuery().must(creatorMatch).must(statusMatch).must(approveMatch)
            searchSourceBuilder.query(boolQuery).from(from).size(size).sort("createTime", SortOrder.DESC)
            searchRequest.source(searchSourceBuilder)
            val response = elasticsearchProvider.search(searchRequest)

            val enrolls = arrayListOf<EnrollService.Enroll>()
            response.hits.hits.forEach {
                val enroll = generateEnrollFromHashMap(it.id, it.sourceAsMap)
                enrolls.add(enroll)
            }
            val totalCount = response.hits.totalHits?.value ?: 0L
            return Pair(enrolls.toMutableList(), totalCount)
        } catch (e: Throwable) {
            return Pair(mutableListOf(), 0L)
        }
    }

    override fun addEnroll(enroll: EnrollService.Enroll): Boolean {
        val builder = generateXContentFromEnroll(enroll)
        val indexRequest = IndexRequest(index)
        indexRequest.id(enroll.uuid).source(builder)
        return elasticsearchProvider.indexDocument(indexRequest)
    }

    override fun updateEnroll(enroll: EnrollService.Enroll): Boolean {
        val builder = generateXContentFromEnroll(enroll)
        val updateRequest = UpdateRequest(index, enroll.uuid).doc(builder)
        return elasticsearchProvider.updateDocument(updateRequest)
    }

    override fun deleteEnroll(uuid: String) {
        val deleteRequest = DeleteRequest(index, uuid)
        elasticsearchProvider.deleteDocument(deleteRequest)
    }

    override fun getEnroll(uuid: String): EnrollService.Enroll? {
        val request = GetRequest(index, uuid)
        val response = elasticsearchProvider.getDocument(request)
        if (!response.isExists) return null
        return generateEnrollFromHashMap(uuid, response.sourceAsMap)
    }

    override fun getSubmittedEnroll(username: String): EnrollService.Enroll? {
        val usernameMatch = QueryBuilders.matchQuery("username", username)
        val statusMatch = QueryBuilders.matchQuery("isSubmit", true)
        val passMatch = QueryBuilders.existsQuery("approve")
        val queryBuilder = QueryBuilders.boolQuery().must(usernameMatch).must(statusMatch).mustNot(passMatch)
        return getSubmittedEnrollByQuery(queryBuilder)
    }

    override fun getSubmittedEnroll(chatId: Long): EnrollService.Enroll? {
        val chatIdMatch = QueryBuilders.matchQuery("chatId", chatId)
        val submitStatusMatch = QueryBuilders.matchQuery("isSubmit", true)
        val passMatch = QueryBuilders.existsQuery("approve")
        val queryBuilder = QueryBuilders.boolQuery().must(chatIdMatch).must(submitStatusMatch).mustNot(passMatch)
        return getSubmittedEnrollByQuery(queryBuilder)
    }

    private fun getSubmittedEnrollByQuery(queryBuilder: BoolQueryBuilder): EnrollService.Enroll? {
        val searchRequest = SearchRequest(index)
        val searchSourceBuilder = SearchSourceBuilder()
        searchSourceBuilder.query(queryBuilder)
        searchRequest.source(searchSourceBuilder)
        val response = elasticsearchProvider.search(searchRequest)
        return if (response.hits.totalHits!!.value < 1) null
        else generateEnrollFromHashMap(response.hits.hits[0].id, response.hits.hits[0].sourceAsMap)
    }

    private fun generateXContentFromEnroll(enroll: EnrollService.Enroll): XContentBuilder {
        val tagsString =
            if (enroll.tags == null) null
            else enroll.tags.joinToString(" ")
        val builder = XContentFactory.jsonBuilder()
        builder.startObject()
        builder.field("type", enroll.type.name)
        builder.field("chatId", enroll.chatId)
        builder.field("title", enroll.title)
        builder.field("description", enroll.description)
        builder.field("tags", tagsString)
        builder.field("classification", enroll.classification)
        builder.field("username", enroll.username)
        builder.field("link", enroll.link)
        builder.field("members", enroll.members)
        builder.field("createTime", enroll.createTime)
        builder.field("createUser", enroll.createUser)
        builder.field("createUserName", enroll.createUserNick)
        builder.field("isSubmit", enroll.isSubmit)
        builder.field("approve", enroll.approve)
        builder.endObject()
        return builder
    }

    private fun generateEnrollFromHashMap(uuid: String, map: MutableMap<String, Any?>): EnrollService.Enroll {
        val tagsString = map["tags"].toString()
        val tags = when {
            tagsString.contains(" ") -> tagsString.split(" ")
            tagsString == "null" -> null
            else -> listOf(tagsString)
        }
        return EnrollService.Enroll(
            uuid,
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
            map["createUserName"] as String,
            map["isSubmit"] as Boolean,
            map["approve"] as Boolean?
        )
    }

}
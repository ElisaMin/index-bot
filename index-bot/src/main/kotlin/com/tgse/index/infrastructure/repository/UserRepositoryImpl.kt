package com.tgse.index.infrastructure.repository

import com.tgse.index.domain.service.UserService
import com.tgse.index.domain.repository.UserRepository
import com.tgse.index.infrastructure.provider.ElasticsearchProvider
import org.elasticsearch.action.delete.DeleteRequest
import org.elasticsearch.action.get.GetRequest
import org.elasticsearch.action.index.IndexRequest
import org.elasticsearch.action.update.UpdateRequest
import org.elasticsearch.client.indices.CreateIndexRequest
import org.elasticsearch.common.settings.Settings
import org.elasticsearch.index.query.QueryBuilders
import org.elasticsearch.xcontent.XContentBuilder
import org.elasticsearch.xcontent.XContentFactory
import org.elasticsearch.xcontent.XContentType
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Repository
import java.util.*

@Repository
class UserRepositoryImpl(
    private val elasticsearchProvider: ElasticsearchProvider,
    @Value("\${user.footprint.cycle}")
    private val footprintCycle: Long
) : UserRepository {

    private val logger = LoggerFactory.getLogger(this::class.java)
    private val index = "user"

    init {
        initializeUser()
    }

    private fun initializeUser() {
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
                    "createTime": {
                        "type": "date"
                    },
                    "updateTime": {
                        "type": "date"
                    }
                }
            }
            """.trimIndent(),
            XContentType.JSON
        )
        elasticsearchProvider.createIndex(request)
    }

    override fun footprint(id: Long) {
        try {
            val now = Date().time
            // 是否应记录
            val user = get(id)
            val exist = user != null
            val shouldPrint = !exist || now - user!!.updateTime < footprintCycle * 1000
            if (!shouldPrint) return
            // 记录用户
            if (exist) {
                val newUser = user!!.copy(updateTime = now)
                update(newUser)
            } else {
                val newUser = UserService.User(id, now, now)
                add(newUser)
            }
        } catch (t: Throwable) {
            logger.error("footprint error,user id is '$id'", t)
        }
    }

    private fun get(id: Long): UserService.User? {
        val request = GetRequest(index, id.toString())
        val response = elasticsearchProvider.getDocument(request)
        if (!response.isExists) return null
        return generateUserFromHashMap(id, response.sourceAsMap)
    }

    private fun add(user: UserService.User): Boolean {
        val builder = generateXContentFromUser(user)
        val indexRequest = IndexRequest(index)
        indexRequest.id(user.id.toString()).source(builder)
        return elasticsearchProvider.indexDocument(indexRequest)
    }

    private fun update(user: UserService.User): Boolean {
        val builder = generateXContentFromUser(user)
        val updateRequest = UpdateRequest(index, user.id.toString()).doc(builder)
        return elasticsearchProvider.updateDocument(updateRequest)
    }

    private fun delete(id: Long) {
        val deleteRequest = DeleteRequest(index, id.toString())
        elasticsearchProvider.deleteDocument(deleteRequest)
    }

    override fun statistics(): Triple<Long, Long, Long> {
        // 用户总量
        val count = elasticsearchProvider.countOfDocument(index)
        // 日增
        val dailyIncreaseQuery = QueryBuilders.rangeQuery("createTime").gte("now-24h")
        val dailyIncrease = elasticsearchProvider.countOfQuery(index, dailyIncreaseQuery)
        // 日活
        val dailyActiveQuery = QueryBuilders.rangeQuery("updateTime").gte("now-24h")
        val dailyActive = elasticsearchProvider.countOfQuery(index, dailyActiveQuery)

        return Triple(count, dailyIncrease, dailyActive)
    }

    private fun generateXContentFromUser(user: UserService.User): XContentBuilder {
        val builder = XContentFactory.jsonBuilder()
        builder.startObject()
        builder.field("createTime", user.createTime)
        builder.field("updateTime", user.updateTime)
        builder.endObject()
        return builder
    }

    private fun generateUserFromHashMap(id: Long, map: MutableMap<String, Any?>): UserService.User {
        return UserService.User(
            id,
            map["createTime"] as Long,
            map["updateTime"] as Long,
        )
    }
}
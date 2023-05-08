package com.tgse.index.infrastructure.repository

import com.tgse.index.domain.repository.AwaitStatusRepository
import com.tgse.index.domain.service.AwaitStatusService
import org.iq80.leveldb.Options
import org.iq80.leveldb.WriteOptions
import org.iq80.leveldb.impl.Iq80DBFactory
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Repository
import java.io.File
import java.nio.ByteBuffer

@Repository
class AwaitStatusRepositoryImpl : AwaitStatusRepository {

    private val logger = LoggerFactory.getLogger(this::class.java)
    private val regex = """(<[0-9]*>)(<[\w&:-]*>)""".toRegex()
    private val levelDBFile = File("./data/await-status")
    private val createOptions = Options().createIfMissing(true)
    private val writeOption = WriteOptions().sync(false)
    private val db = Iq80DBFactory.factory.open(levelDBFile, createOptions)

    override fun setAwaitStatus(chatId: Long, await: AwaitStatusService.Await) {
        try {
            val key = ByteBuffer.allocate(8).putLong(chatId).array()
            val value = await2bytes(await)
            db.put(key, value, writeOption)
        } catch (t: Throwable) {
            logger.error("set await status failed", t)
        }
    }

    override fun getAwaitStatus(chatId: Long): AwaitStatusService.Await? {
        val key = ByteBuffer.allocate(8).putLong(chatId).array()
        val value = db[key] ?: return null
        return bytes2await(value)
    }

    override fun clearAwaitStatus(chatId: Long) {
        val key = ByteBuffer.allocate(8).putLong(chatId).array()
        db[key] ?: return
        db.delete(key)
    }

    private fun await2bytes(await: AwaitStatusService.Await): ByteArray {
        val content = "<${await.messageId}><${await.callbackData}>"
        return content.toByteArray()
    }

    private fun bytes2await(bytes: ByteArray): AwaitStatusService.Await {
        val content = String(bytes)
        val values = regex.find(content)?.groupValues
        if (values == null || values.size != 3) throw RuntimeException("parse bytes to AwaitStatusService.Await failed")
        val messageId = values[1].substringAfter('<').substringBefore('>').toInt()
        val callbackData = values[2].substringAfter('<').substringBefore('>')
        return AwaitStatusService.Await(messageId, callbackData)
    }

}
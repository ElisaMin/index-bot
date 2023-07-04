package com.tgse.index.domain.service

import com.pengrad.telegrambot.model.Update
import com.pengrad.telegrambot.model.User
import com.tgse.index.domain.repository.RecordRepository
import com.tgse.index.domain.repository.TelegramRepository
import com.tgse.index.infrastructure.provider.ElasticSearchScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.broadcast
import kotlinx.coroutines.channels.onFailure
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.cancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.springframework.stereotype.Service
import java.util.*

@Service
class RecordService(
    private val recordRepository: RecordRepository,
    private val scope: ElasticSearchScope,
    private val telegramRepository: TelegramRepository,
):AutoCloseable {

    data class Record(
        val uuid: String,
        val bulletinMessageId: Int?,
        val type: TelegramService.TelegramModType,
        val chatId: Long?,
        val title: String,
        val description: String?,
        /**
         * 包含#
         * 如：#apple #iphone
         */
        val tags: Collection<String>?,
        val classification: String?,
        val username: String?,
        val link: String?,
        val members: Long?,
        val createTime: Long,
        val createUser: Long,
        val updateTime: Long,
    )
    fun <R : Any?> blockWithContext(block:suspend CoroutineScope.()->R) = runBlocking(context = scope.coroutineContext,block)

    private var updateSender:SendChannel<Record>?=null
    private var deleteSender:SendChannel<Pair<Record,User>>?=null
    val updated = callbackFlow {
        updateSender = this
    }.cancellable()
    val deletes = callbackFlow {
        deleteSender = this
    }
    fun subscribeUpdate(onUpdate:(Record)->Unit) = scope.launch {
        updated.collect(onUpdate)
    }

    fun searchRecordsByClassification(classification: String, from: Int, size: Int): Pair<MutableList<Record>, Long> = blockWithContext {
        recordRepository.searchRecordsByClassification(classification, from, size)
    }

    fun searchRecordsByKeyword(keyword: String, from: Int, size: Int): Pair<MutableList<Record>, Long> = blockWithContext {
        recordRepository.searchRecordsByKeyword(keyword, from, size)
    }

    fun searchRecordsByCreator(user: User, from: Int, size: Int): Pair<MutableList<Record>, Long> = blockWithContext {
        recordRepository.searchRecordsByCreator(user, from, size)
    }

    fun getRecordByUsername(username: String): Record?  = blockWithContext {
        recordRepository.getRecordByUsername(username)
    }

    fun getRecordByChatId(chatId: Long): Record? = blockWithContext {
        recordRepository.getRecordByChatId(chatId)
    }

    fun addRecord(record: Record): Boolean = blockWithContext {
        recordRepository.addRecord(record)
    }

    fun updateRecord(record: Record) = blockWithContext {
        val newRecord = record.copy(updateTime = Date().time)
        recordRepository.updateRecord(newRecord)
        updateSender!!.send(newRecord)
    }

    fun deleteRecord(uuid: String, manager: User) = blockWithContext {
        val record = getRecord(uuid)!!
        recordRepository.deleteRecord(uuid, manager)
        deletes.emit(Pair(record, manager))
    }

    fun count(): Long = blockWithContext {
        recordRepository.count()
    }

    fun getRecord(uuid: String): Record? = blockWithContext {
        runCatching {
            recordRepository.getRecord(uuid)
        }.getOrNull()
    }

    override fun close() {
    }

}
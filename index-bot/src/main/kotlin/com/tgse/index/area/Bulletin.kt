package com.tgse.index.area

import com.tgse.index.area.msgFactory.BulletinMsgFactory
import com.tgse.index.domain.service.RecordService
import com.tgse.index.infrastructure.provider.BotProvider
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.telegram.telegrambots.meta.api.methods.*
import org.telegram.telegrambots.meta.api.methods.botapimethods.*
import org.telegram.telegrambots.meta.api.methods.groupadministration.*
import org.telegram.telegrambots.meta.api.methods.send.*
import org.telegram.telegrambots.meta.api.methods.updatingmessages.*
import org.telegram.telegrambots.meta.api.objects.*
/**
 * 公告板
 */
@Service
class Bulletin(
    private val botProvider: BotProvider,
    private val bulletinMsgFactory: BulletinMsgFactory,
    private val recordService: RecordService,
    @Value("\${channel.bulletin.id}")
    private val bulletinChatId: Long
) {

    private val logger = LoggerFactory.getLogger(Bulletin::class.java)

    init {
        subscribeUpdateRecord()
        subscribeDeleteRecord()
    }

    /**
     * 发布公告
     */
    fun publish(record: RecordService.Record) {
        val msg = bulletinMsgFactory.makeBulletinMsg(bulletinChatId, record)
        val response = botProvider.send(msg)
        // 补充公告消息ID
        val newRecord = record.copy(bulletinMessageId = response.messageId)
        recordService.addRecord(newRecord)
    }

    /**
     * 同步数据-更新公告
     */
    private fun subscribeUpdateRecord() = recordService.subscribeUpdates { record ->
        runCatching {
            val msg = bulletinMsgFactory.makeBulletinMsg(bulletinChatId, record.bulletinMessageId!!, record)
            botProvider.send(msg)
        }.onFailure { e ->
            botProvider.sendErrorMessage(e)
            logger.error("Bulletin.subscribeUpdateRecord.error",e)
        }
    }


    /**
     * 同步数据-删除公告
     */
    private fun subscribeDeleteRecord() = recordService.subscribeDeletes { (record,_) ->
        runCatching {
            val msg = bulletinMsgFactory.makeRemovedBulletinMsg(bulletinChatId, record.bulletinMessageId!!)
            botProvider.send(msg)
        }.onFailure { e ->
            logger.error("Bulletin.subscribeDeleteRecord.error", e)
            botProvider.sendErrorMessage(e)
        }
    }

}
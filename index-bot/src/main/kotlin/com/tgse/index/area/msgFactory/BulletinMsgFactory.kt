package com.tgse.index.area.msgFactory


import com.tgse.index.domain.service.RecordService
import com.tgse.index.domain.service.ReplyService
import com.tgse.index.infrastructure.provider.BotProvider
import org.springframework.stereotype.Component
import org.telegram.telegrambots.meta.api.methods.ParseMode
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup

@Component
class BulletinMsgFactory(
    override val replyService: ReplyService,
    override val botProvider: BotProvider
) : BaseMsgFactory(replyService, botProvider) {

    fun makeBulletinMsg(chatId: Long, record: RecordService.Record) = SendMessage().apply {
        text = makeRecordDetail(record)
        this.chatId = chatId.toString()
        parseMode = ParseMode.HTML
        disableWebPagePreview = true
    }

    fun makeBulletinMsg(chatId: Long, messageId: Int, record: RecordService.Record) = EditMessageText().apply {
        text = makeRecordDetail(record)
        this.chatId = chatId.toString()
        this.messageId = messageId
        parseMode = ParseMode.HTML
        disableWebPagePreview = true
    }

    fun makeRemovedBulletinMsg(chatId: Long, messageId: Int)=EditMessageText().apply {
        this.chatId = chatId.toString()
        this.messageId = messageId
        text = replyService.messages["record-removed"]!!
        this.replyMarkup = InlineKeyboardMarkup()
    }

}
package com.tgse.index.area.msgFactory


import com.tgse.index.MismatchException
import com.tgse.index.domain.service.BlackListService
import com.tgse.index.infrastructure.provider.BotProvider
import com.tgse.index.domain.service.ClassificationService
import com.tgse.index.domain.service.ReplyService
import org.springframework.stereotype.Component
import org.telegram.telegrambots.meta.api.methods.ParseMode
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageReplyMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboard
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow

@Component
class NormalMsgFactory(
    private val classificationService: ClassificationService,
    override val replyService: ReplyService,
    override val botProvider: BotProvider
) : BaseMsgFactory(replyService, botProvider) {
    @Suppress("NOTHING_TO_INLINE")
    companion object {
        inline fun SendMessage(chatId: Long, content: String): SendMessage = SendMessage(chatId.toString(), content)
        inline fun SendMessage.parseMode(html: String): SendMessage = apply { parseMode = html }
        inline fun SendMessage.disableWebPagePreview(b: Boolean): SendMessage = apply { disableWebPagePreview = b }
        inline fun SendMessage.replyMarkup(keyboard: ReplyKeyboard): SendMessage = apply { replyMarkup = keyboard }
    }

    fun makeStartMsg(chatId: Long): SendMessage {
        val content = replyService.messages["start"]!!.replace("\\{bot.username\\}".toRegex(), botProvider.username)
        val keyboard = makeReplyKeyboardMarkup()
        return SendMessage(chatId, content).disableWebPagePreview(false).replyMarkup(keyboard)
    }

    fun makeClearMarkupMsg(chatId: Long, messageId: Int) = EditMessageReplyMarkup().apply {
        this.chatId = chatId.toString()
        this.messageId = messageId
        this.replyMarkup = InlineKeyboardMarkup()
    }

    fun makeStatisticsDailyReplyMsg(
        chatId: Long,
        dailyIncreaseOfUser: Long,
        dailyActiveOfUser: Long,
        countOfUser: Long,
        countOfRecord: Long
    ): SendMessage {
        return SendMessage(
            chatId,
            replyService.messages["statistics-daily"]!!
                .replace("\\{dailyIncreaseOfUser\\}".toRegex(), dailyIncreaseOfUser.toString())
                .replace("\\{dailyActiveOfUser\\}".toRegex(), dailyActiveOfUser.toString())
                .replace("\\{countOfUser\\}".toRegex(), countOfUser.toString())
                .replace("\\{countOfRecord\\}".toRegex(), countOfRecord.toString())
        ).parseMode(ParseMode.HTML)
    }

    fun makeBlacklistJoinedReplyMsg(
        chatId: Long,
        replyType: String,
        manager: String,
        black: BlackListService.Black
    ): SendMessage {
        return SendMessage(
            chatId,
            replyService.messages[replyType]!!
                .replace("\\{code\\}".toRegex(), black.chatId?.toString() ?: black.username!!)
                .replace("\\{manager\\}".toRegex(), manager)
                .replace("\\{black\\}".toRegex(), black.displayName)
        )
    }

    fun makeBlacklistExistReplyMsg(chatId: Long, replyType: String, type: String): SendMessage {
        return SendMessage(
            chatId,
            replyService.messages[replyType]!!.replace("\\{type\\}".toRegex(), type)
        )
    }

    fun makeRemoveRecordReplyMsg(chatId: Long, manager: String, recordTitle: String): SendMessage {
        return SendMessage(
            chatId,
            replyService.messages["remove-record-manager"]!!
                .replace("\\{manager\\}".toRegex(), manager)
                .replace("\\{record\\}".toRegex(), recordTitle)
        )
    }

    fun makeRemoveRecordReplyMsg(chatId: Long, recordTitle: String): SendMessage {
        return SendMessage(
            chatId,
            replyService.messages["remove-record-user"]!!
                .replace("\\{record\\}".toRegex(), recordTitle)
        )
    }

    fun makeExceptionMsg(chatId: Long, e: Exception): SendMessage {
        return when (e) {
            is MismatchException -> SendMessage(chatId, e.message!!)
            else -> SendMessage(chatId, "未知错误")
        }
    }

    fun makeBanReplyMsg(
        chatId: Long,
        replyType: String,
        banChatId: String
    ): SendMessage {
        return SendMessage(
            chatId,
            replyService.messages[replyType]!! .replace("\\{chat-id\\}".toRegex(), banChatId)
        )
    }

    private fun makeReplyKeyboardMarkup(): ReplyKeyboardMarkup {
        // 每行countInRow数量个按钮
        val countInRow = 3
        // 将多个类型按照countInRow拆分为多行
        var counter = 0
        val rows = mutableListOf<List<String>>()
        while (counter < classificationService.classifications.size) {
            var endOfIndex = counter + countInRow
            endOfIndex = if (endOfIndex <= classificationService.classifications.size) endOfIndex else classificationService.classifications.size
            val row = classificationService.classifications.copyOfRange(counter, endOfIndex)
            counter += countInRow
            rows.add(row.toList())
        }
        // 制作键盘
        rows.map {
            KeyboardRow(it.map { s -> KeyboardButton(s) })
        }.let {
            ReplyKeyboardMarkup(it)
        }.apply {
            oneTimeKeyboard = false
            resizeKeyboard = true
            selective = true
            return this
        }
    }
}
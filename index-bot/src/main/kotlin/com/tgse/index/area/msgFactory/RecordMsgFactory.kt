package com.tgse.index.area.msgFactory

import com.pengrad.telegrambot.model.User
import com.pengrad.telegrambot.model.request.InlineKeyboardButton
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup
import com.pengrad.telegrambot.model.request.ParseMode
import com.pengrad.telegrambot.request.EditMessageReplyMarkup
import com.pengrad.telegrambot.request.SendMessage
import com.tgse.index.domain.repository.nick
import com.tgse.index.domain.service.*
import com.tgse.index.infrastructure.provider.BotProvider
import org.springframework.stereotype.Component

@Component
class RecordMsgFactory(
    private val classificationService: ClassificationService,
    override val replyService: ReplyService,
    override val botProvider: BotProvider
) : BaseMsgFactory(replyService, botProvider) {

    fun makeEnrollMsg(chatId: Long, enroll: EnrollService.Enroll): SendMessage {
        val detail = makeRecordDetail(enroll)
        val keyboard = makeEnrollKeyboardMarkup(enroll.uuid)
        return SendMessage(chatId, detail)
            .parseMode(ParseMode.HTML)
            .disableWebPagePreview(true)
            .replyMarkup(keyboard)
    }

    fun makeEnrollChangeClassificationMsg(chatId: Long, enroll: EnrollService.Enroll): SendMessage {
        val detail = makeRecordDetail(enroll)
        val keyboard = makeInlineKeyboardMarkup(enroll.uuid,"enroll-class")
        val msg = SendMessage(chatId, detail)
        return msg.parseMode(ParseMode.HTML).disableWebPagePreview(true).replyMarkup(keyboard)
    }

    fun makeApproveMsg(chatId: Long, enroll: EnrollService.Enroll): SendMessage {
        val detail = makeApproveDetail(enroll, isRecording = true, showId = true)
        val keyboard = makeApproveKeyboardMarkup(enroll.uuid)
        return SendMessage(chatId, detail)
            .replyMarkup(keyboard)
            .parseMode(ParseMode.HTML)
            .disableWebPagePreview(true)
    }

    fun makeApproveChangeClassificationMsg(chatId: Long, enroll: EnrollService.Enroll): SendMessage {
        val detail = makeApproveDetail(enroll, isRecording = true)
        val keyboard = makeInlineKeyboardMarkup(enroll.uuid,"enroll-class")
        val msg = SendMessage(chatId, detail)
        return msg.parseMode(ParseMode.HTML).disableWebPagePreview(true).replyMarkup(keyboard)
    }

    fun makeRecordChangeClassificationMsg(chatId: Long, record: RecordService.Record): SendMessage {
        val detail = makeRecordDetail(record)
        val keyboard = makeInlineKeyboardMarkup(record.uuid,"record-class")
        val msg = SendMessage(chatId, detail)
        return msg.parseMode(ParseMode.HTML).disableWebPagePreview(true).replyMarkup(keyboard)
    }

    fun makeApproveResultMsg(
        chatId: Long,
        enroll: EnrollService.Enroll,
        manager: User,
        isPassed: Boolean
    ): SendMessage {
        val detail = makeApproveDetail(enroll, manager, isPassed = isPassed, isRecording = true, showId = true)
        val keyboard = makeJoinBlacklistKeyboardMarkup(enroll)
        val msg = SendMessage(chatId, detail)
        return if (isPassed) msg.parseMode(ParseMode.HTML).disableWebPagePreview(true)
        else msg.parseMode(ParseMode.HTML).disableWebPagePreview(true).replyMarkup(keyboard)
    }

    fun makeApproveResultMsgPrivate(chatId: Long, enroll: EnrollService.Enroll, isPassed: Boolean): SendMessage {
        val detail = makeApproveDetail(enroll, isRecording = false, isPassed = isPassed,)
        val msg = SendMessage(chatId, detail)
        return msg.parseMode(ParseMode.HTML).disableWebPagePreview(true)
    }

    fun makeRecordMsg(chatId: Long, record: RecordService.Record): SendMessage {
        val detail = makeRecordDetail(record)
        val keyboard =
            if (chatId == record.createUser) makeUpdateKeyboardMarkup(record.uuid)
            else makeFeedbackKeyboardMarkup(record.uuid)
        return SendMessage(chatId, detail).parseMode(ParseMode.HTML).disableWebPagePreview(true).replyMarkup(keyboard)
    }

    fun makeFeedbackMsg(chatId: Long, record: RecordService.Record): SendMessage {
        val detail = makeRecordDetail(record)
        val keyboard = makeManageKeyboardMarkup(record.uuid)
        return SendMessage(chatId, detail).parseMode(ParseMode.HTML).disableWebPagePreview(true).replyMarkup(keyboard)
    }

    fun makeClearMarkupMsg(chatId: Long, messageId: Int): EditMessageReplyMarkup {
        return EditMessageReplyMarkup(chatId, messageId).replyMarkup(InlineKeyboardMarkup())
    }

    private fun makeApproveDetail(
        enroll: EnrollService.Enroll,
        checker: User?=null,
        isPassed: Boolean?=null,
        showId:Boolean =false,
        isRecording: Boolean = true
    ): String = buildString {

        if (isRecording) {
            append(makeRecordDetail(enroll))
            appendLine()
        }
        //language=HTML
        var submitter =  """<code>${enroll.createUserNick}</code>"""
        enroll.username?.let { "https://t.me/$it" }?.let {
            //language=HTML
            submitter = """<a href="$it">$submitter</a>"""
        }
        //language=HTML
        append("""<b>提交者</b>： $submitter""")
        if (showId) {
            //language=HTML
            append(""" | <i>ID： <code>${enroll.createUser}</code></i>""")
        }
        appendLine()
        if (checker != null) {
            //language=HTML
            appendLine("<b>审核者</b>： <code>${checker.nick()}</code>")
        }
        if (isPassed!=null) {
            //language=HTML
            appendLine("<b>审核结果</b>： ${if (!isPassed) "未" else ""}通过")
        }

    }
    private fun makeInlineKeyboardMarkup(id: String, oper: String): InlineKeyboardMarkup {
        // 每行countInRow数量个按钮
        val countInRow = 3
        // 将多个类型按照countInRow拆分为多行
        var counter = 0
        val buttonLines = mutableListOf<Array<InlineKeyboardButton>>()
        while (counter < classificationService.classifications.size) {
            var endOfIndex = counter + countInRow
            endOfIndex = if (endOfIndex <= classificationService.classifications.size) endOfIndex else classificationService.classifications.size
            val row = classificationService.classifications.copyOfRange(counter, endOfIndex)
            val buttons = row.map {
                InlineKeyboardButton(it).callbackData("$oper:$it&$id")
            }.toTypedArray()
            buttonLines.add(buttons)
            counter += countInRow
        }
        return InlineKeyboardMarkup(*buttonLines.toTypedArray())
    }

    private fun makeFeedbackKeyboardMarkup(recordUUID: String): InlineKeyboardMarkup {
        return InlineKeyboardMarkup(
            arrayOf(
                InlineKeyboardButton("反馈").callbackData("feedback:$recordUUID")
            )
        )
    }

    private fun makeManageKeyboardMarkup(recordUUID: String): InlineKeyboardMarkup {
        return InlineKeyboardMarkup(
            arrayOf(
                InlineKeyboardButton("移除").callbackData("remove:$recordUUID")
            )
        )
    }

    private fun makeJoinBlacklistKeyboardMarkup(enroll: EnrollService.Enroll): InlineKeyboardMarkup {
        val type = when (enroll.type) {
            TelegramService.TelegramModType.Channel -> "频道"
            TelegramService.TelegramModType.Group -> "群组"
            TelegramService.TelegramModType.Bot -> "机器人"
            TelegramService.TelegramModType.Person -> throw RuntimeException("收录对象为用户")
        }
        return InlineKeyboardMarkup(
            arrayOf(
                run {
                    val callbackData = "blacklist:join&${BlackListService.BlackType.Record}&${enroll.uuid}"
                    InlineKeyboardButton("将${type}加入黑名单").callbackData(callbackData)
                }
            ),
            arrayOf(
                run {
                    val callbackData = "blacklist:join&${BlackListService.BlackType.User}&${enroll.uuid}"
                    InlineKeyboardButton("将提交者加入黑名单").callbackData(callbackData)
                }
            )
        )
    }

    private fun makeEnrollKeyboardMarkup(id: String): InlineKeyboardMarkup {
        return InlineKeyboardMarkup(
            arrayOf(
                InlineKeyboardButton("✍编辑标题").callbackData("enroll:title&$id"),
                InlineKeyboardButton("✍编辑简介").callbackData("enroll:about&$id"),
            ),
            arrayOf(
                InlineKeyboardButton("✍编辑标签").callbackData("enroll:tags&$id"),
                InlineKeyboardButton("✍编辑分类").callbackData("enroll:enroll-class&$id"),
            ),
            arrayOf(
                InlineKeyboardButton("✅提交").callbackData("enroll:submit&$id"),
                InlineKeyboardButton("❎取消").callbackData("enroll:cancel&$id"),
            )
        )
    }

    private fun makeApproveKeyboardMarkup(id: String): InlineKeyboardMarkup {
        return InlineKeyboardMarkup(
            arrayOf(
                InlineKeyboardButton("✍编辑标题").callbackData("approve:title&$id"),
                InlineKeyboardButton("✍编辑简介").callbackData("approve:about&$id"),
            ),
            arrayOf(
                InlineKeyboardButton("✍编辑标签").callbackData("approve:tags&$id"),
                InlineKeyboardButton("✍编辑分类").callbackData("approve:enroll-class&$id"),
            ),
            arrayOf(
                InlineKeyboardButton("✅通过").callbackData("approve:pass&$id"),
                InlineKeyboardButton("❎不通过").callbackData("approve:fail&$id"),
            )
        )
    }

    private fun makeUpdateKeyboardMarkup(id: String): InlineKeyboardMarkup {
        return InlineKeyboardMarkup(
            arrayOf(
                InlineKeyboardButton("✍更新链接").callbackData("update:link&$id"),
            ),
            arrayOf(
                InlineKeyboardButton("✍编辑标题").callbackData("update:title&$id"),
                InlineKeyboardButton("✍编辑简介").callbackData("update:about&$id"),
            ),
            arrayOf(
                InlineKeyboardButton("✍编辑标签").callbackData("update:tags&$id"),
                InlineKeyboardButton("✍编辑分类").callbackData("update:record-class&$id"),
            ),
            arrayOf(
                InlineKeyboardButton("移除收录").callbackData("update:remove&$id"),
            )
        )
    }
}
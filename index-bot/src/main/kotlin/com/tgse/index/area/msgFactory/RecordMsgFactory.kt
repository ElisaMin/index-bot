package com.tgse.index.area.msgFactory

import com.tgse.index.domain.repository.nick
import com.tgse.index.domain.service.*
import com.tgse.index.infrastructure.provider.BotProvider
import org.springframework.stereotype.Component
import org.telegram.telegrambots.meta.api.methods.ParseMode
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageReplyMarkup
import org.telegram.telegrambots.meta.api.objects.User
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton


@Component
class RecordMsgFactory(
    private val classificationService: ClassificationService,
    override val replyService: ReplyService,
    override val botProvider: BotProvider
) : BaseMsgFactory(replyService, botProvider) {

    companion object {
        @Suppress("NOTHING_TO_INLINE")
        inline fun InlineKeyboardButton.callbackData(s: String): InlineKeyboardButton = apply {
            callbackData = s
        }
    }

    @Suppress("NOTHING_TO_INLINE")
    private inline fun SendMessage.default(chatId: Long) = apply {
        parseMode = ParseMode.HTML
        disableWebPagePreview = true
        this.chatId = chatId.toString()
    }


    fun makeEnrollMsg(chatId: Long, enroll: EnrollService.Enroll) = SendMessage().apply {
        text = makeRecordDetail(enroll)
        replyMarkup = makeEnrollKeyboardMarkup(enroll.uuid)
        default(chatId)
    }

    fun makeEnrollChangeClassificationMsg(chatId: Long, enroll: EnrollService.Enroll) = SendMessage().apply {
        text = makeRecordDetail(enroll)
        replyMarkup = makeInlineKeyboardMarkup(enroll.uuid,"enroll-class")
        default(chatId)
    }

    fun makeApproveMsg(chatId: Long, enroll: EnrollService.Enroll) = SendMessage().apply {
        text = makeApproveRecordDetail(enroll)
        replyMarkup = makeApproveKeyboardMarkup(enroll.uuid)
        default(chatId)
    }


    fun makeApproveChangeClassificationMsg(chatId: Long, enroll: EnrollService.Enroll) = SendMessage().apply {
        text = makeApproveRecordDetail(enroll)
        replyMarkup = makeInlineKeyboardMarkup(enroll.uuid, "enroll-class")
        default(chatId)
    }

    fun makeRecordChangeClassificationMsg(chatId: Long, record: RecordService.Record) = SendMessage().apply {
        text = makeRecordDetail(record)
        replyMarkup = makeInlineKeyboardMarkup(record.uuid,"record-class")
        default(chatId)
    }

    fun makeApproveResultMsg(
        chatId: Long,
        enroll: EnrollService.Enroll,
        manager: User,
        isPassed: Boolean
    ) = SendMessage().apply {
        text = makeApproveResultDetail(enroll, manager, isPassed)
        default(chatId)
        if (isPassed) replyMarkup = makeJoinBlacklistKeyboardMarkup(enroll)
    }

    fun makeApproveResultMsg(chatId: Long, enroll: EnrollService.Enroll, isPassed: Boolean) = SendMessage().apply {
        text = makeApproveResultDetail(enroll, isPassed)
        default(chatId)
    }

    fun makeRecordMsg(chatId: Long, record: RecordService.Record) = SendMessage().apply {
        text = makeRecordDetail(record)
        replyMarkup =
            if (chatId == record.createUser) makeUpdateKeyboardMarkup(record.uuid)
            else makeFeedbackKeyboardMarkup(record.uuid)
        default(chatId)
    }

    fun makeFeedbackMsg(chatId: Long, record: RecordService.Record) = SendMessage().apply {
        text = makeRecordDetail(record)
        replyMarkup = makeFeedbackKeyboardMarkup(record.uuid)
        default(chatId)
    }

    fun makeClearMarkupMsg(chatId: Long, messageId: Int): EditMessageReplyMarkup {
        return EditMessageReplyMarkup(chatId.toString(), messageId,null,InlineKeyboardMarkup())
    }

    private fun makeApproveRecordDetail(enroll: EnrollService.Enroll): String {
        return makeRecordDetail(enroll) + "\n<b>提交者</b>： ${enroll.createUserNick}\n"
    }

    private fun makeApproveResultDetail(enroll: EnrollService.Enroll, checker: User, isPassed: Boolean): String {
        val result = if (isPassed) "通过" else "未通过"
        return makeRecordDetail(enroll) +
                "\n<b>提交者</b>： ${enroll.createUserNick}" +
                "\n<b>审核者</b>： ${checker.nick()}" +
                "\n<b>审核结果</b>： $result\n"
    }

    private fun makeApproveResultDetail(enroll: EnrollService.Enroll, isPassed: Boolean): String {
        val result = if (isPassed) "通过" else "未通过"
        return makeRecordDetail(enroll) +
                "\n<b>审核结果</b>： $result\n"
    }

    private fun makeInlineKeyboardMarkup(id: String, oper: String): InlineKeyboardMarkup {
        // 每行countInRow数量个按钮
        val countInRow = 3
        // 将多个类型按照countInRow拆分为多行
        var counter = 0
        val buttonLines = mutableListOf<List<InlineKeyboardButton>>()
        while (counter < classificationService.classifications.size) {
            var endOfIndex = counter + countInRow
            endOfIndex = if (endOfIndex <= classificationService.classifications.size) endOfIndex else classificationService.classifications.size
            val row = classificationService.classifications.copyOfRange(counter, endOfIndex)
            val buttons = row.map {
                InlineKeyboardButton(it).apply {
                    callbackData = "$oper:$it&$id"
                }
            }
            buttonLines.add(buttons)
            counter += countInRow
        }
        return InlineKeyboardMarkup(buttonLines)
    }

    private fun makeFeedbackKeyboardMarkup(recordUUID: String): InlineKeyboardMarkup {
        return InlineKeyboardMarkup( listOf(
            listOf(
                InlineKeyboardButton("反馈").callbackData("feedback:$recordUUID")
            )
        ))
    }

    private fun makeManageKeyboardMarkup(recordUUID: String): InlineKeyboardMarkup {
        return InlineKeyboardMarkup( listOf(
            listOf(
                InlineKeyboardButton("移除").callbackData("remove:$recordUUID")
            )
        ))
    }

    private fun makeJoinBlacklistKeyboardMarkup(enroll: EnrollService.Enroll): InlineKeyboardMarkup {
        val type = when (enroll.type) {
            TelegramService.TelegramModType.Channel -> "频道"
            TelegramService.TelegramModType.Group -> "群组"
            TelegramService.TelegramModType.Bot -> "机器人"
            TelegramService.TelegramModType.Person -> throw RuntimeException("收录对象为用户")
        }
        return InlineKeyboardMarkup( listOf(
            listOf(
                run {
                    val callbackData = "blacklist:join&${BlackListService.BlackType.Record}&${enroll.uuid}"
                    InlineKeyboardButton("将${type}加入黑名单").callbackData(callbackData)
                }
            ),
            listOf(
                run {
                    val callbackData = "blacklist:join&${BlackListService.BlackType.User}&${enroll.uuid}"
                    InlineKeyboardButton("将提交者加入黑名单").callbackData(callbackData)
                }
            )
        ))
    }

    private fun makeEnrollKeyboardMarkup(id: String): InlineKeyboardMarkup {
        return InlineKeyboardMarkup( listOf(
            listOf(
                InlineKeyboardButton("✍编辑标题").callbackData("enroll:title&$id"),
                InlineKeyboardButton("✍编辑简介").callbackData("enroll:about&$id"),
            ),
            listOf(
                InlineKeyboardButton("✍编辑标签").callbackData("enroll:tags&$id"),
                InlineKeyboardButton("✍编辑分类").callbackData("enroll:enroll-class&$id"),
            ),
            listOf(
                InlineKeyboardButton("✅提交").callbackData("enroll:submit&$id"),
                InlineKeyboardButton("❎取消").callbackData("enroll:cancel&$id"),
            )
        ))
    }

    private fun makeApproveKeyboardMarkup(id: String): InlineKeyboardMarkup {
        return InlineKeyboardMarkup( listOf(
            listOf(
                InlineKeyboardButton("✍编辑标题").callbackData("approve:title&$id"),
                InlineKeyboardButton("✍编辑简介").callbackData("approve:about&$id"),
            ),
            listOf(
                InlineKeyboardButton("✍编辑标签").callbackData("approve:tags&$id"),
                InlineKeyboardButton("✍编辑分类").callbackData("approve:enroll-class&$id"),
            ),
            listOf(
                InlineKeyboardButton("✅通过").callbackData("approve:pass&$id"),
                InlineKeyboardButton("❎不通过").callbackData("approve:fail&$id"),
            )
        ))
    }

    private fun makeUpdateKeyboardMarkup(id: String): InlineKeyboardMarkup {
        return InlineKeyboardMarkup( listOf(
            listOf(
                InlineKeyboardButton("✍更新链接").callbackData("update:link&$id"),
            ),
            listOf(
                InlineKeyboardButton("✍编辑标题").callbackData("update:title&$id"),
                InlineKeyboardButton("✍编辑简介").callbackData("update:about&$id"),
            ),
            listOf(
                InlineKeyboardButton("✍编辑标签").callbackData("update:tags&$id"),
                InlineKeyboardButton("✍编辑分类").callbackData("update:record-class&$id"),
            ),
            listOf(
                InlineKeyboardButton("移除收录").callbackData("update:remove&$id"),
            )
        ))
    }
}

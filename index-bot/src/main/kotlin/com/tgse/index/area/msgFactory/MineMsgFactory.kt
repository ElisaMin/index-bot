package com.tgse.index.area.msgFactory


import com.tgse.index.area.msgFactory.NormalMsgFactory.Companion.parseMode
import com.tgse.index.area.msgFactory.NormalMsgFactory.Companion.SendMessage
import com.tgse.index.area.msgFactory.NormalMsgFactory.Companion.disableWebPagePreview
import com.tgse.index.area.msgFactory.NormalMsgFactory.Companion.replyMarkup
import com.tgse.index.area.msgFactory.RecordMsgFactory.Companion.callbackData
import com.tgse.index.infrastructure.provider.BotProvider
import com.tgse.index.domain.service.EnrollService
import com.tgse.index.domain.service.RecordService
import com.tgse.index.domain.service.ReplyService
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.telegram.telegrambots.meta.api.methods.ParseMode
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText
import org.telegram.telegrambots.meta.api.objects.User
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton

@Component
class MineMsgFactory(
    override val botProvider: BotProvider,
    override val replyService: ReplyService,
    private val recordService: RecordService,
    private val enrollService: EnrollService,
    @Value("\${secretary.list.size}")
    private val perPageSize: Int
) : BaseMsgFactory(replyService, botProvider) {

    fun makeListFirstPageMsg(user: User) = SendMessage().apply {
        val (detail, keyboard) =
            makeListDetailAndKeyboard(user, 1) ?: Pair(replyService.messages["empty"], InlineKeyboardMarkup())
        detail?.let { text = it }
        chatId = user.id.toString()
        parseMode(ParseMode.HTML).disableWebPagePreview(true).replyMarkup(keyboard)
    }

    fun makeListNextPageMsg(user: User, messageId: Int, pageIndex: Int) = EditMessageText().apply {
        val (detail, keyboard) =
            makeListDetailAndKeyboard(user, pageIndex) ?: Pair(replyService.messages["empty"], InlineKeyboardMarkup())
        detail?.let { text = it }
        chatId = user.id.toString()
        setMessageId(messageId)
        parseMode = ParseMode.HTML
        disableWebPagePreview = true
        replyMarkup = keyboard
    }

    private fun makeListDetailAndKeyboard(
        user: User,
        pageIndex: Int
    ): Pair<String, InlineKeyboardMarkup>? {
        val range = IntRange(((pageIndex - 1) * perPageSize), pageIndex * perPageSize)
        val (records, enrolls, totalCount) = searchForMine(user, range.first)
        if (totalCount == 0L) return null
        val sb = StringBuffer()
        records.forEach {
            val item = generateRecordItem(it)
            sb.append(item)
        }
        enrolls.forEach {
            val item = generateEnrollItem(it)
            sb.append(item)
        }
        val keyboard = makeMinePageKeyboardMarkup(totalCount, pageIndex, perPageSize, range)
        return Pair(sb.toString(), keyboard)
    }

    private fun searchForMine(
        user: User,
        from: Int
    ): Triple<MutableList<RecordService.Record>, MutableList<EnrollService.Enroll>, Long> {
        val (records, recordTotalCount) = recordService.searchRecordsByCreator(user, from, perPageSize)
        val enrollsFrom = if (from > recordTotalCount) from - recordTotalCount.toInt() else 0
        val (enrolls, enrollTotalCount) = enrollService.searchEnrolls(user, enrollsFrom, perPageSize - records.size)
        val totalCount = enrollTotalCount + recordTotalCount
        return Triple(records, enrolls, totalCount)
    }

    private fun makeMinePageKeyboardMarkup(
        totalCount: Long,
        pageIndex: Int,
        perPageSize: Int,
        range: IntRange
    ): InlineKeyboardMarkup {
        return when {
            totalCount > perPageSize && range.first == 0 ->
                InlineKeyboardMarkup(
                    listOf(
                        InlineKeyboardButton("下一页").callbackData("mine:${pageIndex + 1}"),
                    )
                )
            totalCount > perPageSize && range.first != 0 && range.last < totalCount ->
                InlineKeyboardMarkup(
                    listOf(
                        InlineKeyboardButton("上一页").callbackData("mine:${pageIndex - 1}"),
                        InlineKeyboardButton("下一页").callbackData("mine:${pageIndex + 1}"),
                    )
                )
            totalCount > perPageSize && range.last >= totalCount ->
                InlineKeyboardMarkup(
                    listOf(
                        InlineKeyboardButton("上一页").callbackData("mine:${pageIndex - 1}"),
                    )
                )
            else -> InlineKeyboardMarkup()
        }
    }
    companion object {
        fun InlineKeyboardMarkup(vararg listOf: List<InlineKeyboardButton>): InlineKeyboardMarkup {
            return InlineKeyboardMarkup(listOf(*listOf))
        }
    }

}
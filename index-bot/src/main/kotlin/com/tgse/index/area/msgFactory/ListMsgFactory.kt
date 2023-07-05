package com.tgse.index.area.msgFactory


import com.tgse.index.area.msgFactory.NormalMsgFactory.Companion.parseMode
import com.tgse.index.area.msgFactory.NormalMsgFactory.Companion.disableWebPagePreview
import com.tgse.index.area.msgFactory.NormalMsgFactory.Companion.SendMessage
import com.tgse.index.area.msgFactory.NormalMsgFactory.Companion.replyMarkup
import com.tgse.index.area.msgFactory.RecordMsgFactory.Companion.callbackData
import com.tgse.index.infrastructure.provider.BotProvider
import com.tgse.index.domain.service.ClassificationService
import com.tgse.index.domain.service.RecordService
import com.tgse.index.domain.service.ReplyService
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.telegram.telegrambots.meta.api.methods.ParseMode
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton
import java.util.*

@Component
class ListMsgFactory(
    override val botProvider: BotProvider,
    override val replyService: ReplyService,
    private val classificationService: ClassificationService,
    private val recordService: RecordService,
    @Value("\${secretary.list.size}")
    private val perPageSize: Int,
    @Value("\${secretary.memory.size}")
    private val memorySize: Int,
    @Value("\${secretary.memory.cycle}")
    private val memoryCycle: Int
) : BaseMsgFactory(replyService, botProvider) {

    private val searchListSaved = mutableMapOf<String, MutableMap<Int, Pair<MutableList<RecordService.Record>, Long>>>()
    private val searchListSavedTimers = mutableMapOf<String, MutableMap<Int, Timer>>()
    private var searchListSavedCount = 0

    fun makeListFirstPageMsg(chatId: Long, keywords: String, pageIndex: Int): SendMessage? {
        val range = IntRange(((pageIndex - 1) * perPageSize), pageIndex * perPageSize)
        val (records, totalCount) = searchList(keywords,range.first)
        if (totalCount == 0L) return null
        val sb = StringBuffer()
        records.forEach {
            val item = generateListItem(it)
            sb.append(item)
        }
        val keyboard = makeListPageKeyboardMarkup(keywords, totalCount, pageIndex, perPageSize, range)
        return SendMessage(chatId, sb.toString()).apply {
            parseMode = ParseMode.HTML
            disableWebPagePreview = true
            replyMarkup = keyboard

        }
    }

    fun makeListNextPageMsg(chatId: Long, messageId: Int, keywords: String, pageIndex: Int) = EditMessageText().apply {
        val range = IntRange(((pageIndex - 1) * perPageSize), pageIndex * perPageSize)
        val (records, totalCount) = searchList(keywords, range.first)
        val sb = StringBuffer()
        records.forEach {
            val item = generateListItem(it)
            sb.append(item)
        }
        val keyboard = makeListPageKeyboardMarkup(keywords, totalCount, pageIndex, perPageSize, range)
        this.chatId = chatId.toString()
        this.messageId = messageId
        this.text = sb.toString()
        disableWebPagePreview = true
        parseMode = ParseMode.HTML
        replyMarkup = keyboard
    }

    private fun searchList(keywords: String, from: Int): Pair<MutableList<RecordService.Record>, Long> {
        // 如若已暂存直接返回
        val saved = get(keywords, from)
        if (saved != null) return saved
        // 如若未暂存，去elasticsearch中查询
        val isShouldConsiderKeywords = classificationService.contains(keywords)
        val searched =
            if (isShouldConsiderKeywords) recordService.searchRecordsByClassification(keywords, from, perPageSize)
            else recordService.searchRecordsByKeyword(keywords, from, perPageSize)
        save(keywords,from,searched)
        return searched
    }

    private fun makeListPageKeyboardMarkup(
        keywords: String,
        totalCount: Long,
        pageIndex: Int,
        perPageSize: Int,
        range: IntRange
    ): InlineKeyboardMarkup {
        val next = InlineKeyboardButton("下一页").callbackData("page:$keywords&${pageIndex + 1}")
        val prev = InlineKeyboardButton("上一页").callbackData("page:$keywords&${pageIndex - 1}")
        return when {
            totalCount > perPageSize && range.first == 0 ->
                InlineKeyboardMarkup(listOf(listOf(next)))
            totalCount > perPageSize && range.first != 0 && range.last < totalCount ->
                InlineKeyboardMarkup(listOf(listOf(prev,next)))
            totalCount > perPageSize && range.last >= totalCount ->
                InlineKeyboardMarkup(listOf(listOf(prev)))
            else -> InlineKeyboardMarkup()
        }
    }

    @Synchronized
    private fun get(keywords: String, from: Int): Pair<MutableList<RecordService.Record>, Long>? {
        return if (searchListSaved[keywords] != null && searchListSaved[keywords]!![from] != null)
            searchListSaved[keywords]!![from]!!
        else
            null
    }

    @Synchronized
    private fun save(keywords: String, from: Int, searchList: Pair<MutableList<RecordService.Record>, Long>) {
        if (searchListSavedCount >= memorySize) return
        if (searchListSaved[keywords] == null) searchListSaved[keywords] = mutableMapOf()
        searchListSaved[keywords]!![from] = searchList
        searchListSavedCount += 1

        val timer = Timer("saved-list-cancel", true)
        val timerTask = object : TimerTask() {
            override fun run() {
                try {
                    remove(keywords, from)
                } catch (e: Throwable) {
                    // ignore
                }
            }
        }
        timer.schedule(timerTask, memoryCycle * 1000L)
        if (searchListSavedTimers[keywords] == null) searchListSavedTimers[keywords] = mutableMapOf()
        if (searchListSavedTimers[keywords]!![from] != null) searchListSavedTimers[keywords]!![from]!!.cancel()
        searchListSavedTimers[keywords]!![from] = timer
    }

    @Synchronized
    private fun remove(keywords: String, from: Int) {
        if (searchListSaved[keywords]!!.size == 1) searchListSaved.remove(keywords)
        else searchListSaved[keywords]!!.remove(from)
        searchListSavedCount -= 1
    }

}
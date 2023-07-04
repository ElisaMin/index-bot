package com.tgse.index.domain.service

import com.pengrad.telegrambot.model.Chat
import com.pengrad.telegrambot.model.Update
import com.pengrad.telegrambot.model.User
import com.tgse.index.domain.repository.BanListRepository
import com.tgse.index.infrastructure.provider.BotLifecycle
import com.tgse.index.infrastructure.provider.BotProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filterIsInstance
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

/**
 * 信息来源分水岭
 * 区分 群组、私聊、管理 信息来源
 */
@Service
class RequestService(
    private val banListRepository: BanListRepository,
    private val botProvider: BotProvider,
    @Value("\${group.approve.id}")
    private val approveGroupChatId: Long
): BotLifecycle() {

    open class BotRequest(
        open val chatId: Long?,
        @Suppress("unused") open val chatType: Chat.Type?,
        open val messageId: Int?,
        open val update: Update
    )

    class BotPrivateRequest(
        override val chatId: Long,
        override val messageId: Int?,
        override val update: Update
    ) : BotRequest(
        chatId,
        Chat.Type.Private,
        messageId,
        update
    )

    class BotGroupRequest(
        override val chatId: Long,
        override val messageId: Int?,
        override val update: Update
    ) : BotRequest(
        chatId,
        Chat.Type.group,
        messageId,
        update
    )

    class BotApproveRequest(
        override val chatId: Long,
        override val messageId: Int?,
        override val update: Update
    ) : BotRequest(
        chatId,
        Chat.Type.group,
        messageId,
        update
    )

    private val logger = LoggerFactory.getLogger(this::class.java)
    private val requestFlow = MutableSharedFlow<BotRequest>()
    private val feedbacks = MutableSharedFlow<Feedback>()
    val requests: Flow<BotRequest> get() = requestFlow
    final suspend inline fun <reified T:BotRequest> blockRequest(noinline block: suspend (T)->Unit) = requests.filterIsInstance<T>().collect(block)
    suspend fun emit(feedback: Feedback) = feedbacks.emit(feedback)
    suspend fun blockFeedbacks(block:suspend (Feedback)->Unit): Nothing = feedbacks.collect(block)



    private suspend inline fun handle(u:Update) =
        makeBotRequest(u)?.runCatching {
            logger.error("made request $chatId")
            if (chatId?.let { banListRepository.get(it) } == null) {
                logger.error("not banded")
                requestFlow.emit(this)
            }
        }?.onFailure { throwable ->
            logger.error("handling request",throwable)
            botProvider.sendErrorMessage(throwable)
        }
    init {
        makeCoroutine {
            botProvider.blockUpdates(::handle)
        }
    }
    private fun makeBotRequest(update: Update): BotRequest? {
        // 仅处理文字信息或按钮回执
        val messageContentIsNull = update.message() == null || update.message().text() == null
        if (messageContentIsNull && update.callbackQuery() == null) return null

        // 获取概要内容
        val callbackQuery = update.callbackQuery()

        val chatType = when {
            update.message() == null && callbackQuery == null -> null
            update.message() != null -> update.message().chat().type()
            callbackQuery != null -> callbackQuery.message().chat().type()
            else -> null
        }

        val chatId = when {
            update.message() == null && callbackQuery == null -> null
            callbackQuery?.message() != null -> callbackQuery.message().chat().id()
            callbackQuery != null -> callbackQuery.from().id().toLong()
            update.message() != null -> update.message().chat().id()
            else -> null
        }

        val messageId = callbackQuery?.message()?.messageId()

        return when (chatType) {
            Chat.Type.Private -> BotPrivateRequest(chatId!!, messageId, update)
            Chat.Type.supergroup, Chat.Type.group -> {
                if (chatId == approveGroupChatId) BotApproveRequest(chatId, messageId, update)
                else BotGroupRequest(chatId!!, messageId, update)
            }
            else -> null
        }
    }

}

typealias Feedback = Triple<RecordService.Record, User, String>
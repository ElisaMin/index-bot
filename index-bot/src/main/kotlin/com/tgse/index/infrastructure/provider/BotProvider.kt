package com.tgse.index.infrastructure.provider

import com.google.common.util.concurrent.MoreExecutors
import com.pengrad.telegrambot.TelegramBot
import com.pengrad.telegrambot.UpdatesListener
import com.pengrad.telegrambot.model.BotCommand
import com.pengrad.telegrambot.model.Update
import com.pengrad.telegrambot.model.request.ChatAction
import com.pengrad.telegrambot.request.*
import com.pengrad.telegrambot.response.*
import com.tgse.index.BotProperties
import com.tgse.index.ElasticSearchException
import com.tgse.index.ProxyProperties
import com.tgse.index.SetCommandException
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.subjects.BehaviorSubject
import okhttp3.OkHttpClient
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.*
import java.util.concurrent.Executors

@Component
class BotProvider(
    private val botProperties: BotProperties,
    private val proxyProperties: ProxyProperties,
    @Value("\${secretary.autoDeleteMsgCycle}")
    private val autoDeleteMsgCycle: Long
) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    private val requestExecutorService = MoreExecutors.listeningDecorator(
        Executors.newCachedThreadPool {
            Thread(it).apply {
                name = "用户请求处理线程"
                isDaemon = true
            }
        }
    )

    private val bot: TelegramBot
    = TelegramBot.Builder(botProperties.token)
        .apply {
            if (proxyProperties.enabled) {
                val socketAddress = InetSocketAddress(proxyProperties.ip, proxyProperties.port)
                val proxy = Proxy(proxyProperties.type, socketAddress)
                val okHttpClient = OkHttpClient().newBuilder().proxy(proxy).build()
                okHttpClient(okHttpClient)
            }
        }
        .build()

    private val updateSubject = BehaviorSubject.create<Update>()
    val updateObservable: Observable<Update> = updateSubject.distinct()
    val username: String by lazy {
        val request = GetMe()
        val response = bot.execute(request)
        response.user().username()
    }

    init {
        setCommands()
        handleUpdate()
        logger.info("Bot ready.")
    }

    private fun setCommands() {
        try {
            val setCommands = SetMyCommands(
                BotCommand("start", "开 始"),
                BotCommand("enroll", "申请收录"),
                BotCommand("update", "修改收录信息"),
                BotCommand("mine", "我提交的"),
                BotCommand("cancel", "取消操作"),
                BotCommand("help", "帮 助"),
            )
            val setResponse = bot.execute(setCommands)
            if (!setResponse.isOk)
                throw SetCommandException(setResponse.description())
        } catch (e: Throwable) {
            sendErrorMessage(e)
        }
    }

    private fun handleUpdate() {
        bot.setUpdatesListener { updates ->
            val exceptions = updates
                .map { update -> requestExecutorService.submit { updateSubject.onNext(update) } }
                .asSequence()
                .mapNotNull { future ->
                    runCatching {
                        future.get()
                    }.onFailure {
                        it.printStackTrace()
                    }.exceptionOrNull()
                }.filterIsInstance<ElasticSearchException>()
                .toList()
            if (exceptions.isNotEmpty()) {
                exceptions.forEach {
                    it.printStackTrace()
                }
                throw RuntimeException("ElasticSearch ERROR!")
            }
            UpdatesListener.CONFIRMED_UPDATES_ALL
        }
    }

    fun send(message: SendMessage): SendResponse {
        return bot.execute(message)
    }

    fun sendDeleteMessage(chatId: Long, messageId: Int): BaseResponse {
        val deleteMessage = DeleteMessage(chatId, messageId)
        return bot.execute(deleteMessage)
    }

    /**
     * 发送自毁消息
     */
    fun sendAutoDeleteMessage(message: SendMessage): BaseResponse {
        val sendResponse = send(message)
        val timer = Timer("auto-delete-message", true)
        val timerTask = object : TimerTask() {
            override fun run() {
                try {
                    val chatId = sendResponse.message().chat().id()
                    val messageId = sendResponse.message().messageId()
                    sendDeleteMessage(chatId, messageId)
                } catch (e: Throwable) {
                    // ignore
                }
            }
        }
        timer.schedule(timerTask, autoDeleteMsgCycle * 1000)
        return sendResponse
    }

    fun send(answer: AnswerCallbackQuery): BaseResponse {
        return bot.execute(answer)
    }

    fun send(message: EditMessageText): BaseResponse {
        return bot.execute(message)
    }

    fun send(message: EditMessageReplyMarkup): BaseResponse {
        return bot.execute(message)
    }

    fun sendDelay(message: EditMessageReplyMarkup, delay: Long) {
        val timer = Timer("delay-message", true)
        val timerTask = object : TimerTask() {
            override fun run() {
                try {
                    send(message)
                } catch (e: Throwable) {
                    // ignore
                }
            }
        }
        timer.schedule(timerTask, delay)
    }

    fun send(action: GetChat): GetChatResponse {
        return bot.execute(action)
    }

    fun send(action: GetChatMemberCount): GetChatMemberCountResponse {
        return bot.execute(action)
    }

    fun send(action: GetChatAdministrators): GetChatAdministratorsResponse {
        return bot.execute(action)
    }

    fun sendTyping(chatId: Long) {
        val chatAction = SendChatAction(chatId, ChatAction.typing)
        send(chatAction)
    }

    private fun send(action: SendChatAction): BaseResponse {
        return bot.execute(action)
    }

    fun sendErrorMessage(error: Throwable) {
        try {
            val msgContent = "Error:\n" + (error.message ?: error.stackTrace.copyOfRange(0, 4).joinToString("\n"))
            val errorMessage = SendMessage(botProperties.creator, msgContent)
            bot.execute(errorMessage)
        } catch (e: Throwable) {
            // ignore
        }
        if (error is ElasticSearchException)
            throw error
    }
}
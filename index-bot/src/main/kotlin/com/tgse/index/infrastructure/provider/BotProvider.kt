package com.tgse.index.infrastructure.provider

import com.google.common.util.concurrent.ListeningExecutorService
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
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.exceptions.CompositeException
import io.reactivex.rxjava3.subjects.BehaviorSubject
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import org.apache.commons.lang3.concurrent.BasicThreadFactory
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.DisposableBean
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.event.ApplicationFailedEvent
import org.springframework.context.ApplicationEvent
import org.springframework.context.ApplicationListener
import org.springframework.context.SmartLifecycle
import org.springframework.context.event.ContextClosedEvent
import org.springframework.stereotype.Component
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.*
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.collections.List
import kotlin.jvm.Throws

@Component
class Exit(
    val botProvider: BotProvider
): ApplicationListener<ApplicationEvent> {
    val logger = LoggerFactory.getLogger(this::class.java)
    override fun onApplicationEvent(event: ApplicationEvent) {
        logger.warn("event ${event::class.java}")
        when (event) {
            is ApplicationFailedEvent,
            is ContextClosedEvent -> {
                botProvider.stop()
            }
        }
    }
}

abstract class BotBase(
    private val botProperties: BotProperties,
    private var proxyProperties: ProxyProperties?
):SmartLifecycle {
    private val logger = LoggerFactory.getLogger(this::class.java)
    private val updateSubject = BehaviorSubject.create<Update>()
    private val disposable = CompositeDisposable()
    val updateObservable: Observable<Update> = updateSubject
        .distinct()
        .doOnSubscribe {
            disposable.add(it)
        }


    private val pool by lazy {
        Executors.newCachedThreadPool(
            BasicThreadFactory.Builder().namingPattern("%d-用户请求处理").daemon(true).build()
        )
    }
    private val httpClient by lazy {
        OkHttpClient().newBuilder().apply {
            dispatcher(Dispatcher(pool))
            val proxyProperties = proxyProperties!!
            if (proxyProperties.enabled) {
                val socketAddress = InetSocketAddress(proxyProperties.ip, proxyProperties.port)
                val proxy = Proxy(proxyProperties.type, socketAddress)
                proxy(proxy)
            }
            this@BotBase.proxyProperties = null
        }.build()
    }
    private lateinit var makingFuture: ListeningExecutorService
    protected lateinit var bot: TelegramBot
        private set

    lateinit var username: String
        private set

    @Throws(Throwable::class)
    fun handleUpdate(update:Update) {
        val throwable = runCatching {
            updateSubject.onNext(update)
        }.exceptionOrNull()?: updateSubject.throwable
        throwable?.let {
            stop()
            if (it is CompositeException) {
                if (it.exceptions.size==1)
                    throw it.exceptions[0]
            }
            throw it
        }
    }
    final override fun start() {
        makingFuture = MoreExecutors.listeningDecorator(pool)
        bot = TelegramBot.Builder(botProperties.token).okHttpClient(httpClient).build()
        setName()
        setCommands()
        setListener()
        sendAdminMessage("bot started")
        logger.info("started.")
    }

    final override fun stop() {
        val logger = LoggerFactory.getLogger("BOT STOPPING")
        val errors = mutableListOf<Throwable>()
        val warp: (String?,() -> Unit,) -> Unit = { s,block:()->Unit->
            if (s!=null) logger.warn(s)
            runCatching {
                block()
            }.onFailure {
                errors.add(it)
            }
        }
        warp("stopping.") {
            sendAdminMessage("bot stopping")
        }
        warp("closing updates channel.") {
            disposable.dispose()
        }
        warp("waiting for future to terminate.") {
            makingFuture.awaitTermination(15, TimeUnit.SECONDS)
        }
        warp(null) {
            makingFuture.shutdown()
        }
        warp("removing listener.") {
            bot.removeGetUpdatesListener()
        }
        warp("waiting for pool to terminate.") {
            pool.awaitTermination(2, TimeUnit.SECONDS)
        }
        warp(null) {
            pool.shutdown()
        }
        warp("waiting for http client to terminate.") {
            httpClient.dispatcher.executorService.awaitTermination(2, TimeUnit.SECONDS)
        }
        warp(null) {
            httpClient.dispatcher.executorService.shutdown()
        }
        warp("waiting for http client to terminate.") {
            httpClient.connectionPool.evictAll()
        }
        if (errors.isNotEmpty()) {
            CompositeException(errors).printStackTrace(System.err)
        }
    }

    final override fun isRunning(): Boolean =
        !pool.isTerminated && !pool.isShutdown
    final override fun getPhase(): Int  = 1
    final override fun isAutoStartup(): Boolean = true

    @Suppress("NOTHING_TO_INLINE")
    private inline fun setName() {
        val me = bot.execute(GetMe())
        require(me.isOk) {
            me.description()
        }
        username = me.user().username()
    }

    @Suppress("NOTHING_TO_INLINE")
    private inline fun setListener() {
        bot.setUpdatesListener { updates ->
            updates.handle()
            UpdatesListener.CONFIRMED_UPDATES_ALL
        }
    }
    @Suppress("NOTHING_TO_INLINE")
    private inline fun List<Update>.handle() {
        distinct().asSequence().map {
            Callable {
                runCatching {
                    handleUpdate(it)
                }.onFailure {
                    logger.error("handle update error", it)
                    sendErrorMessage(it)
                }
            }
        }.map {
            makingFuture.submit(it)
        }.toList() // tasks

        .runCatching {
            asSequence().mapIndexed { i, it ->
                logger.info("waiting for task $i")
                it.get().exceptionOrNull()
            }.forEach {
                logger.warn("on catchings", it)
            }
        }
        logger.info("handle done $size")
    }
    @Suppress("NOTHING_TO_INLINE")
    private inline fun setCommands() {
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
    private fun sendAdminMessage(msg: String) {
        val sendMessage = SendMessage(botProperties.creator, msg)
        bot.execute(sendMessage)
    }


}

@Component
class BotProvider(
    botProperties: BotProperties,
    proxyProperties: ProxyProperties,
    @Value("\${secretary.autoDeleteMsgCycle}")
    private val autoDeleteMsgCycle: Long
): BotBase(botProperties,proxyProperties) {
    private val logger = LoggerFactory.getLogger(this::class.java)
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

}
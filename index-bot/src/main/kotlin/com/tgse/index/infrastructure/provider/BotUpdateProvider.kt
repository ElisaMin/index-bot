package com.tgse.index.infrastructure.provider

import com.google.common.util.concurrent.ListeningExecutorService
import com.google.common.util.concurrent.MoreExecutors
import com.pengrad.telegrambot.TelegramBot
import com.pengrad.telegrambot.UpdatesListener
import com.pengrad.telegrambot.model.BotCommand
import com.pengrad.telegrambot.model.Chat
import com.pengrad.telegrambot.model.Update
import com.pengrad.telegrambot.model.request.ParseMode
import com.pengrad.telegrambot.request.GetChat
import com.pengrad.telegrambot.request.GetMe
import com.pengrad.telegrambot.request.SendMessage
import com.pengrad.telegrambot.request.SetMyCommands
import com.tgse.index.BotProperties
import com.tgse.index.ElasticSearchException
import com.tgse.index.ProxyProperties
import com.tgse.index.SetCommandException
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.exceptions.CompositeException
import io.reactivex.rxjava3.plugins.RxJavaPlugins
import io.reactivex.rxjava3.subjects.BehaviorSubject
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import org.apache.commons.lang3.concurrent.BasicThreadFactory
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ExitCodeGenerator
import org.springframework.boot.SpringApplication
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.SmartLifecycle
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.jvm.Throws

/**
 * provide a behavior subject to receive update from telegram bot
 * it will exit while receive a [ElasticSearchException]
 * @see ElasticSearchException
 * @see RxJavaPlugins.setErrorHandler
 *
 * @see observable
 */
abstract class BotUpdateProvider(
    private val botProperties: BotProperties,
    private var proxyProperties: ProxyProperties?,
    private val app: ConfigurableApplicationContext,
    private val approveChatId: Long,
    private val bulletinChatId: Long
): SmartLifecycle {
    private val disposable = CompositeDisposable()
    private val logger = LoggerFactory.getLogger(this::class.java)


    private val updateSubject = BehaviorSubject.create<Update>()
    val observable: Observable<Update> = updateSubject
        .doOnSubscribe(disposable::add)
        .distinct()

    private val pool by lazy {
        Executors.newCachedThreadPool(
            BasicThreadFactory.Builder().namingPattern("%d-用户请求处理").daemon(true)
                .uncaughtExceptionHandler { _, e -> onError(e) }
                .build()
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
            this@BotUpdateProvider.proxyProperties = null
        }.build()
    }
    private lateinit var poolEar: ListeningExecutorService
    protected lateinit var bot: TelegramBot
        private set

    lateinit var username: String
        private set

    @Throws(Throwable::class)
    fun handleUpdate(update: Update) {
        val throwable = runCatching {
            updateSubject.onNext(update)
        }.exceptionOrNull()?: updateSubject.throwable

        throwable?.let { onError(it) }
    }
    final override fun start() {
        poolEar = MoreExecutors.listeningDecorator(pool)
        bot = TelegramBot.Builder(botProperties.token).okHttpClient(httpClient).build()
        setErrorHandler()
        setName()
        checkChats()
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
        warp("stopping.") { sendAdminMessage("bot stopping") }
        warp("closing updates channel.") { disposable.dispose() }
        warp("waiting for future to terminate.") { poolEar.awaitTermination(15, TimeUnit.SECONDS) }
        warp(null) { poolEar.shutdown() }
        warp("removing listener.") { bot.removeGetUpdatesListener() }
        warp("waiting for pool to terminate.") { pool.awaitTermination(2, TimeUnit.SECONDS) }
        warp(null) { pool.shutdown() }
        warp("waiting for http client to terminate.") { httpClient.dispatcher.executorService.awaitTermination(2,
            TimeUnit.SECONDS
        ) }
        warp(null) { httpClient.dispatcher.executorService.shutdown() }
        warp("waiting for http client to terminate.") { httpClient.connectionPool.evictAll() }
        if (errors.isNotEmpty()) { CompositeException(errors).printStackTrace(System.err) }
    }

    final override fun isRunning(): Boolean {
        return pool.isShutdown
    }
    final override fun getPhase(): Int  = -1
    final override fun isAutoStartup(): Boolean = true

    private fun onError(error: Throwable) {
        error.printStackTrace(System.err)
        when (error) {
            is ElasticSearchException -> error
            is CompositeException -> {
                error.exceptions.find { it is ElasticSearchException }
            }
            else -> null
        }?.let {
            logger.error("elastic search error")
            SpringApplication.exit(app, ExitCodeGenerator { 255 })
        }
    }

    @Suppress("NOTHING_TO_INLINE")
    private inline fun setErrorHandler() {
        RxJavaPlugins.setErrorHandler(::onError)
    }
    @Suppress("NOTHING_TO_INLINE")
    private inline fun infoOf(id:Long,vararg types:Chat.Type): String {
        bot.execute(GetChat(id)).let {
            require(it.isOk) {
                it.description()
            }
            val chat = it.chat()
            require(chat.type() in types) {
                "chat $id is not a ${types.joinToString(",") { it.name }}"
            }
            require(chat.id()==id) {
                "id not match: input `$id` != received `${chat.id()}`"
            }
            return chat.title()!!
        }
    }
    @Suppress("NOTHING_TO_INLINE")
    private inline fun checkChats() {
        val approveGroup = infoOf(approveChatId, Chat.Type.group, Chat.Type.supergroup)
        val bulletinChannel = infoOf(bulletinChatId, Chat.Type.channel)
        logger.info("checked approve group: $approveGroup")
        logger.info("checked bulletin channel: $bulletinChannel")


    }
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
        distinct().map {
            Callable {
                runCatching {
                    handleUpdate(it)
                }.exceptionOrNull()
            }
        }.map {
            poolEar.submit(it)
        }.mapNotNull {future ->
            runCatching { future.get() }
                .getOrElse { throwable -> throwable }
        }.takeIf { it.isNotEmpty() }?.let {
            onError(CompositeException(it))
        }
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
            val msgContent = buildString {
                appendLine("```")
                appendLine(error.message?:"No ERROR MSG")
                appendLine("stacktrace:")
                error.stackTrace.asSequence()
                    .take(5)
                    .forEach {
                        append("    ")
                        it.className.split(".").run {
                            val last = last()
                            dropLast(1).forEach { s ->
                                append(s.first())
                                append(".")
                            }
                            append(last)
                        }
                        append("-")
                        append(it.methodName)
                        append(" (")
                        append(it.fileName)
                        append(":")
                        append(it.lineNumber)
                        append(")")
                        appendLine()
                    }
                appendLine("...")
                appendLine("```")
            }
            val errorMessage = SendMessage(botProperties.creator, msgContent).parseMode(ParseMode.Markdown)
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
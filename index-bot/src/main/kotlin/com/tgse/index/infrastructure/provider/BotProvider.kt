package com.tgse.index.infrastructure.provider


import com.tgse.index.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.UnsatisfiedDependencyException
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.event.ApplicationFailedEvent
import org.springframework.context.ApplicationEvent
import org.springframework.context.ApplicationListener
import org.springframework.stereotype.Component
import org.telegram.telegrambots.bots.DefaultBotOptions
import org.telegram.telegrambots.bots.TelegramLongPollingBot
import org.telegram.telegrambots.meta.TelegramBotsApi
import org.telegram.telegrambots.meta.api.methods.*
import org.telegram.telegrambots.meta.api.methods.botapimethods.*
import org.telegram.telegrambots.meta.api.methods.groupadministration.*
import org.telegram.telegrambots.meta.api.methods.send.*
import org.telegram.telegrambots.meta.api.methods.updatingmessages.*
import org.telegram.telegrambots.meta.api.objects.*
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand
import org.telegram.telegrambots.meta.exceptions.TelegramApiRequestException
import org.telegram.telegrambots.meta.exceptions.TelegramApiValidationException
import org.telegram.telegrambots.meta.generics.BotSession
import org.telegram.telegrambots.starter.AfterBotRegistration
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession
import java.util.*
import kotlin.coroutines.EmptyCoroutineContext

@Component
class EXIT(
    val botProvider: BotProvider
): ApplicationListener<ApplicationFailedEvent> {
    val logger = LoggerFactory.getLogger(this::class.java)
    override fun onApplicationEvent(event: ApplicationFailedEvent) {
        (event.exception as? UnsatisfiedDependencyException)?.let {
            logger.info("interface: ${it.beanName}, injectpont: ${it.injectionPoint}")
        }
        logger.error("${event.exception::class.simpleName} error !")
        botProvider.destroy()
    }
}
@Component
class LISTENER: ApplicationListener<ApplicationEvent> {
    val logger = LoggerFactory.getLogger(this::class.java)
    override fun onApplicationEvent(event: ApplicationEvent) {
        logger.info("event: ${event::class.simpleName}")
    }
}
@Component
class Bot(
    defaultBotOptions: DefaultBotOptions,
    botProperties: BotProperties,
):TelegramLongPollingBot(defaultBotOptions,botProperties.token) {
    override fun getBotUsername(): String = "INDEX_BOT"
    override fun onUpdateReceived(update: Update) {
        callback(update)
    }
    var callback:TelegramLongPollingBot.(Update)->Unit = {
        throw NotImplementedError("callback not set")
    }
    val session by lazy {
        TelegramBotsApi(DefaultBotSession::class.java).registerBot(this)
    }
//    final var isReady = false
//        private set

    @AfterBotRegistration
    fun afterBotRegistration() {
//        isReady = true
        println("afterBotRegistration")
    }
}
@Component
class BotProvider(
    private val botProperties: BotProperties,
    @Value("\${secretary.autoDeleteMsgCycle}")
    private val autoDeleteMsgCycle: Long,
    private val bot: Bot
): BotLifecycle() {

    private val logger = LoggerFactory.getLogger(this::class.java)

    val username: String by lazy {
        bot.execute(GetMe()).userName
    }

    init {
        CoroutineScope(EmptyCoroutineContext,).launch(start = CoroutineStart.ATOMIC) {

            while (true) {
                delay(1000)
//                LoggerFactory.getLogger(this::class.java).warn("RUNNING $")
            }
        }.start()
    }

    private val updatesFlow  = MutableSharedFlow<Update>().run {
        setCommands()
        bot.callback = {
            runBlocking {
                withCoroutine {
                    emit(it)
                }
            }
        }
        if (!bot.session.isRunning)
        bot.session.start()
        send(SendMessage(botProperties.creator, "Bot started."))
        logger.info("Bot started.")
        this
    }

    suspend fun blockUpdates(block:suspend (Update)->Unit) {
        updatesFlow.collect(block)
    }


    private fun setCommands() {
        try {
            val setCommands = SetMyCommands.builder().commands( listOf(
                BotCommand("start", "开 始"),
                BotCommand("enroll", "申请收录"),
                BotCommand("update", "修改收录信息"),
                BotCommand("mine", "我提交的"),
                BotCommand("cancel", "取消操作"),
                BotCommand("help", "帮 助"),
            )).build()
            runCatching {
                bot.execute(setCommands)
            }.onFailure {
                var throws = it
                if (it is TelegramApiRequestException) {
                    throws = SetCommandException(it.apiResponse)
                }
                throw throws
            }
        } catch (e: Throwable) {
            sendErrorMessage(e)
        }
    }

    init {
        logger.info("Bot ready.")
    }

//    private final inline fun <R> catchApiException(crossinline action:()->R):R? {
//        return runCatching(action)
//            .onFailure {
//                when (it) {
//                    is TelegramApiRequestException -> {
//                        logger.info("TelegramApiRequestException: ${it.apiResponse}")
//                    }
//                    is TelegramApiValidationException -> {
//                        logger.info("TelegramApiValidationException: ${it.method}")
//                    }
//                    else -> throw it
//                }
//            }.getOrNull()
//    }


    fun send(message: SendMessage) = bot.execute(message)

    fun sendDeleteMessage(chatId: Long, messageId: Int) = bot.execute(DeleteMessage.builder().chatId(chatId).messageId(messageId).build())

    /**
     * 发送自毁消息
     */
    fun sendAutoDeleteMessage(message: SendMessage): Message? {
        val sendResponse = send(message)
        val timer = Timer("auto-delete-message", true)
        val timerTask = object : TimerTask() {
            override fun run() {
                try {
                    val chatId = sendResponse.chat.id
                    val messageId = sendResponse.messageId
                    sendDeleteMessage(chatId, messageId)
                } catch (e: Throwable) {
                    // ignore
                }
            }
        }
        timer.schedule(timerTask, autoDeleteMsgCycle * 1000)
        return sendResponse
    }

    fun send(answer: AnswerCallbackQuery) =  bot.execute(answer)

    fun send(message: EditMessageText) = bot.execute(message)

    fun send(message: EditMessageReplyMarkup) = bot.execute(message)

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

    fun send(action: GetChat) = bot.execute(action)

    fun send(action: GetChatMemberCount) = bot.execute(action)

    fun send(action: GetChatAdministrators) = bot.execute(action)

    fun sendTyping(chatId: Long) = send(SendChatAction.builder().chatId(chatId).action(ActionType.TYPING.name).build())

    private fun send(action: SendChatAction) = bot.execute(action)

    @Throws(ElasticSearchException::class)
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

    override fun destroy() {
        bot.session.let { if (it.isRunning) it.stop() }
        bot.onClosing()
        pool.shutdownNow()
        super.destroy()
    }
}
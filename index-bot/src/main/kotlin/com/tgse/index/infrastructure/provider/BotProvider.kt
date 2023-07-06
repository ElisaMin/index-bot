package com.tgse.index.infrastructure.provider

import com.pengrad.telegrambot.model.ChatMember
import com.pengrad.telegrambot.model.request.ChatAction
import com.pengrad.telegrambot.request.*
import com.pengrad.telegrambot.response.*
import com.tgse.index.BotProperties
import com.tgse.index.ProxyProperties
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.stereotype.Component
import java.util.*

@Component
class BotProvider(
    botProperties: BotProperties,
    proxyProperties: ProxyProperties,
    app: ConfigurableApplicationContext,
    @Value("\${group.approve.id}")
    private val approveGroupChatId: Long,
    @Value("\${channel.bulletin.id}")
    bulletinChatId: Long,
    @Value("\${secretary.autoDeleteMsgCycle}")
    private val autoDeleteMsgCycle: Long,
): BotUpdateProvider(botProperties,proxyProperties,app,approveGroupChatId,bulletinChatId) {

    private val executingLogger = LoggerFactory.getLogger("BOT_RESULT")

    private inline fun <R:BaseResponse?> wrap(crossinline block: () -> R): R & Any = block().let {
        if (!it!!.isOk) {
            executingLogger.warn(
                "${it.errorCode()}",IllegalStateException(it.description())
            )
        }
        it
    }

    fun send(message: SendMessage)=wrap {
        bot.execute(message)
    }

    fun sendDeleteMessage(chatId: Long, messageId: Int) = wrap {
        val deleteMessage = DeleteMessage(chatId, messageId)
        bot.execute(deleteMessage)
    }

    /**
     * 发送自毁消息
     */
    fun sendAutoDeleteMessage(message: SendMessage) = wrap {
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
        sendResponse
    }

    fun send(answer: AnswerCallbackQuery) = wrap  {
        bot.execute(answer)
    }

    fun send(message: EditMessageText) = wrap {
        bot.execute(message)
    }

    fun send(message: EditMessageReplyMarkup) = wrap {
        bot.execute(message)
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

    fun send(action: GetChat) = wrap { bot.execute(action) }

    fun send(action: GetChatMemberCount) = wrap {
        bot.execute(action)
    }

    fun send(action: GetChatAdministrators) = wrap {
        bot.execute(action)
    }

    fun sendTyping(chatId: Long) = wrap {
        val chatAction = SendChatAction(chatId, ChatAction.typing)
        send(chatAction)
    }

    private fun send(action: SendChatAction) = wrap {
        bot.execute(action)
    }

    fun getApproveChatMemberSafe(memberId:Long) =
        bot.runCatching {
            val result = wrap { execute(
                GetChatMember(approveGroupChatId, memberId))
            }
            result.chatMember()!!
        }.onFailure { e -> e
            .printStackTrace()
        }.map {
            if (it.user().isBot) return@map null
            val status = it.status()
            if (status == ChatMember.Status.restricted) {
                bot.execute(
                    SendMessage(
                        approveGroupChatId,
                        "用户 @${it.user().username()} (${it.user().id()}) 权限受限！"
                    )
                ).isOk
                return@map null
            }
            return@map when(it.status()) {
                null,
                ChatMember.Status.left,
                ChatMember.Status.kicked->null
                else -> {
                    send(
                        SendMessage(approveGroupChatId,
                            "检测到用户 @${it.user().username()}是群员！通过提交！"
                        ))
                }
            }

        }.getOrNull()


}
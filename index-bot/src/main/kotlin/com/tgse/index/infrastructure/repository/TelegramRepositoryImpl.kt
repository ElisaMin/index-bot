package com.tgse.index.infrastructure.repository


import com.tgse.index.ProxyProperties
import com.tgse.index.domain.repository.TelegramRepository
import com.tgse.index.domain.service.TelegramService
import com.tgse.index.infrastructure.provider.BotProvider
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Repository
import org.telegram.telegrambots.bots.DefaultBotOptions
import org.telegram.telegrambots.bots.TelegramLongPollingBot
import org.telegram.telegrambots.meta.api.methods.groupadministration.GetChat
import org.telegram.telegrambots.meta.api.methods.groupadministration.GetChatMemberCount
import org.telegram.telegrambots.meta.api.objects.Chat
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.exceptions.TelegramApiRequestException

@Repository
class TelegramRepositoryImpl(
    private val botProvider: BotProvider,
    @Value("\${secretary.poppy-bot}")
    private val poppyTokens: List<String>,
    private val defaultBotOptions: DefaultBotOptions
) : TelegramRepository {

    private val tooManyRequestRegex = """^Too Many Requests:.*""".toRegex()
    private val chatNotFoundRegex = """^Bad Request: chat not found$""".toRegex()

    private val logger = LoggerFactory.getLogger(this::class.java)
    private val poppies by lazy { poppyTokens.mapNotNull {key -> key.toBot() } }
    fun String.toBot() = runCatching {
        val bot: TelegramLongPollingBot =
        object: TelegramLongPollingBot(defaultBotOptions, this) {
            override fun getBotUsername(): String = "INDEX_BOT"
            override fun onUpdateReceived(update: Update) {
                callback(update)
            }
            var callback: TelegramLongPollingBot.(Update) -> Unit = {
                throw NotImplementedError("callback not set")
            }
        }
        bot
    }.getOrNull()



    @PostConstruct
    private fun init() {
        logger.info("The poppy count is: ${poppies.size}")
    }
    /**
     * 公开群组、频道
     */
    override fun getTelegramMod(username: String): TelegramService.TelegramMod? {
        return try {
            if (username.isEmpty()) return null
            val getChat = GetChat("@$username")
            val getChatMembersCount = GetChatMemberCount("@$username")
            poppies.forEach { poppy ->
                val getChatResponse = runCatching {
                    poppy.execute(getChat)
                }
                if (!getChatResponse.isSuccess) {
                    val description = getChatResponse.description() ?: return null
                    tooManyRequestRegex.find(description)?.let {
                        return@forEach
                    }
                    chatNotFoundRegex.find(description)?.let {
                        return null
                    }
                }
                val chat = getChatResponse.getOrNull() ?: return null
                val getChatMembersCountResponse = runCatching { botProvider.send(getChatMembersCount) }
                val membersCount = if (!getChatMembersCountResponse.isSuccess) {
//                    val description = getChatMembersCountResponse.description() ?: return null
//                    tooManyRequestRegex.find(description)?.let {
//                        return@forEach
//                    }
                    0
                } else {
                    getChatMembersCountResponse.getOrThrow()
                }
//                val membersCount = getChatMembersCountResponse.count() ?: 0
                return when (chat.type.lowercase()) {
                    "group","supergroup" ->
                        TelegramService.TelegramGroup(
                            chat.id,
                            username,
                            chat.inviteLink,
                            chat.title,
                            chat.description,
                            membersCount.toLong()
                        )
                    "channel" ->
                        TelegramService.TelegramChannel(
                            username,
                            chat.title,
                            chat.description,
                            membersCount.toLong()
                        )
                    else -> null
                }
            }
            null
        } catch (t: Throwable) {
            logger.error("get telegram info error,the telegram username is '$username'", t)
            null
        }
    }

    /**
     * 群组
     */
    override fun getTelegramMod(id: Long): TelegramService.TelegramGroup? {
        return try {
            val getChat = GetChat(id.toString())
            val chat: Chat = botProvider.send(getChat) ?: return null

            val getChatMembersCount = GetChatMemberCount(id.toString())
            val count = botProvider.send(getChatMembersCount)?:0

            val link = if (chat.userName != null) null else chat.inviteLink
            TelegramService.TelegramGroup(id, chat.userName, link, chat.title, chat.description, count.toLong())
        } catch (t: Throwable) {
            logger.error("get telegram info error,the telegram chatId is '$id'", t)
            null
        }
    }

}
private fun <T> Result<T>.description() = exceptionOrNull()?.let {
    it as? TelegramApiRequestException
}?.apiResponse

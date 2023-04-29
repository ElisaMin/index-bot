package com.tgse.index.infrastructure.repository

import com.pengrad.telegrambot.TelegramBot
import com.pengrad.telegrambot.model.Chat
import com.pengrad.telegrambot.request.GetChat
import com.pengrad.telegrambot.request.GetChatMemberCount
import com.tgse.index.ProxyProperties
import com.tgse.index.domain.repository.TelegramRepository
import com.tgse.index.domain.service.TelegramService
import com.tgse.index.infrastructure.provider.BotProvider
import jakarta.annotation.PostConstruct
import okhttp3.OkHttpClient
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Repository
import java.net.InetSocketAddress
import java.net.Proxy

@Repository
class TelegramRepositoryImpl(
    private val proxyProperties: ProxyProperties,
    private val botProvider: BotProvider,
    @Value("\${secretary.poppy-bot}")
    private val poppyTokens: List<String>
) : TelegramRepository {

    private val tooManyRequestRegex = """^Too Many Requests:.*""".toRegex()
    private val chatNotFoundRegex = """^Bad Request: chat not found$""".toRegex()

    private val logger = LoggerFactory.getLogger(this::class.java)
    private val poppies = mutableListOf<TelegramBot>()

    @PostConstruct
    private fun init() {
        logger.info("The poppy count is: ${poppyTokens.size}")

        poppyTokens.forEach { tokens ->
            val bot = if (proxyProperties.enabled) {
                val socketAddress = InetSocketAddress(proxyProperties.ip, proxyProperties.port)
                val proxy = Proxy(proxyProperties.type, socketAddress)
                val okHttpClient = OkHttpClient().newBuilder().proxy(proxy).build()
                TelegramBot.Builder(tokens).okHttpClient(okHttpClient).build()
            } else {
                TelegramBot(tokens)
            }
            poppies.add(bot)
        }
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
                val getChatResponse = poppy.execute(getChat)
                if (!getChatResponse.isOk) {
                    val description = getChatResponse.description() ?: return null
                    tooManyRequestRegex.find(description)?.let {
                        return@forEach
                    }
                    chatNotFoundRegex.find(description)?.let {
                        return null
                    }
                }
                val chat = getChatResponse.chat() ?: return null
                val getChatMembersCountResponse = botProvider.send(getChatMembersCount)
                val membersCount = if (!getChatMembersCountResponse.isOk) {
//                    val description = getChatMembersCountResponse.description() ?: return null
//                    tooManyRequestRegex.find(description)?.let {
//                        return@forEach
//                    }
                    0
                } else {
                    getChatMembersCountResponse.count()
                }
//                val membersCount = getChatMembersCountResponse.count() ?: 0
                return when (chat.type()) {
                    Chat.Type.group, Chat.Type.supergroup ->
                        TelegramService.TelegramGroup(
                            chat.id(),
                            username,
                            chat.inviteLink(),
                            chat.title(),
                            chat.description(),
                            membersCount.toLong()
                        )
                    Chat.Type.channel ->
                        TelegramService.TelegramChannel(
                            username,
                            chat.title(),
                            chat.description(),
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
            val getChat = GetChat(id)
            val chat = botProvider.send(getChat).chat() ?: return null

            val getChatMembersCount = GetChatMemberCount(id)
            val count = botProvider.send(getChatMembersCount).count()

            val link = if (chat.username() != null) null else chat.inviteLink()
            TelegramService.TelegramGroup(id, chat.username(), link, chat.title(), chat.description(), count.toLong())
        } catch (t: Throwable) {
            logger.error("get telegram info error,the telegram chatId is '$id'", t)
            null
        }
    }

}
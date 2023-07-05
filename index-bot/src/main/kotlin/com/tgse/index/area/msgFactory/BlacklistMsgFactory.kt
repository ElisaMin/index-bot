package com.tgse.index.area.msgFactory

import com.tgse.index.domain.service.BlackListService
import com.tgse.index.domain.service.ReplyService
import com.tgse.index.infrastructure.provider.BotProvider
import org.springframework.stereotype.Component
import org.telegram.telegrambots.meta.api.methods.send.SendMessage

@Component
class BlacklistMsgFactory(
    override val replyService: ReplyService,
    override val botProvider: BotProvider
) : BaseMsgFactory(replyService, botProvider) {

    fun makeBlacklistJoinedReplyMsg(
        chatId: Long,
        replyType: String,
        manager: String,
        black: BlackListService.Black
    ) = SendMessage(
        chatId.toString(),
        replyService.messages[replyType]!!
            .replace("\\{manager\\}".toRegex(), manager)
            .replace("\\{black\\}".toRegex(), black.displayName)
    )

    fun makeBlacklistExistReplyMsg(chatId: Long, replyType: String, type: String): SendMessage {
        return SendMessage(
            chatId.toString(),
            replyService.messages[replyType]!!.replace("\\{type\\}".toRegex(), type)
        )
    }

}
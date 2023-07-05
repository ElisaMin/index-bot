package com.tgse.index.area.execute

import com.tgse.index.area.msgFactory.NormalMsgFactory
import com.tgse.index.domain.repository.nick
import com.tgse.index.infrastructure.provider.BotProvider
import com.tgse.index.domain.service.BlackListService
import com.tgse.index.domain.service.EnrollService
import com.tgse.index.domain.service.RequestService
import com.tgse.index.domain.service.TelegramService
import org.springframework.stereotype.Component
import java.util.*

@Component
class BlacklistExecute(
    private val botProvider: BotProvider,
    private val msgFactory: NormalMsgFactory,
    private val enrollService: EnrollService,
    private val blackListService: BlackListService
) {

    fun executeByBlacklistButton(request: RequestService.BotRequest) {
        val manager = request.update.callbackQuery.from.nick()

        val callbackData = request.update.callbackQuery.data
        val callbackDataVal = callbackData.replace("blacklist:", "").split("&")
        val oper = callbackDataVal[0]
        val type = BlackListService.BlackType.valueOf(callbackDataVal[1])
        val enrollUUID = callbackDataVal[2]
        val enroll = enrollService.getEnroll(enrollUUID)!!

        // 检查是否已在黑名单
        val blackExist = when (type) {
            BlackListService.BlackType.Record -> {
                if (enroll.chatId != null) blackListService.get(enroll.chatId)
                else blackListService.get(enroll.username!!)
            }
            BlackListService.BlackType.User -> {
                blackListService.get(enroll.createUser)
            }
        }
        when (oper) {
            // 加入黑名单
            "join" -> {
                // 若不存在则添加至黑名单，若存在则升级黑名单
                val black = if (blackExist == null) {
                    val newBlack = BlackListService.Black(
                        UUID.randomUUID().toString(),
                        type,
                        when (type) {
                            BlackListService.BlackType.Record -> enroll.title
                            BlackListService.BlackType.User -> enroll.createUserNick
                        },
                        1,
                        when (type) {
                            BlackListService.BlackType.Record -> enroll.chatId
                            BlackListService.BlackType.User -> enroll.createUser
                        },
                        when (type) {
                            BlackListService.BlackType.Record -> enroll.username
                            BlackListService.BlackType.User -> null
                        },
                        Date().time
                    )
                    blackListService.add(newBlack)
                    newBlack
                } else {
                    val newBlack = blackExist.copy(
                        displayName = when (type) {
                            BlackListService.BlackType.Record -> enroll.title
                            BlackListService.BlackType.User -> enroll.createUserNick
                        },
                        level = blackExist.level + 1,
                        unfreezeTime = calcUnfreezeTime(blackExist.level + 1)
                    )
                    blackListService.update(newBlack)
                    newBlack
                }
                val msg = msgFactory.makeBlacklistJoinedReplyMsg(request.chatId!!, "blacklist-join", manager, black)
                botProvider.send(msg)
            }
            // 移出黑名单
            "left" -> {
                if (blackExist == null) return
                blackListService.delete(blackExist.uuid)
                val msg = msgFactory.makeBlacklistJoinedReplyMsg(request.chatId!!, "blacklist-left", manager, blackExist)
                botProvider.send(msg)
            }
        }
    }

    fun notify(chatId: Long, telegramMod: TelegramService.TelegramMod) {
        val type = when (telegramMod) {
            is TelegramService.TelegramGroup -> "群组"
            is TelegramService.TelegramChannel -> "频道"
            is TelegramService.TelegramBot -> "机器人"
            is TelegramService.TelegramPerson -> "用户"
            else -> throw RuntimeException("不应执行到此处")
        }
        val msg =
            if (telegramMod is TelegramService.TelegramPerson)
                msgFactory.makeBlacklistExistReplyMsg(chatId, "blacklist-exist-user", type)
            else
                msgFactory.makeBlacklistExistReplyMsg(chatId, "blacklist-exist-record", type)
        botProvider.send(msg)
    }

    private fun calcUnfreezeTime(level: Int): Long {
        val now = Date().time
        return when (level) {
            // 一周
            1 -> now + 604800000
            // 一个月
            2 -> now + 2592000000
            // 六个月
            3 -> now + 15552000000
            // 一年
            4 -> now + 31536000000
            // 五年
            else -> now + 157680000000
        }
    }

}
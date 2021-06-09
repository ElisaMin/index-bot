package com.tgse.index.bot

import com.pengrad.telegrambot.model.request.ParseMode
import com.pengrad.telegrambot.request.AnswerCallbackQuery
import com.pengrad.telegrambot.request.GetChatAdministrators
import com.pengrad.telegrambot.request.SendMessage
import com.tgse.index.bot.execute.BlacklistExecute
import com.tgse.index.datasource.*
import com.tgse.index.msgFactory.NormalMsgFactory
import com.tgse.index.msgFactory.RecordMsgFactory
import com.tgse.index.provider.BotProvider
import com.tgse.index.provider.WatershedProvider
import com.tgse.index.provider.WatershedProvider.BotGroupRequest
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.lang.RuntimeException
import java.util.*

@Service
class Group(
    private val botProvider: BotProvider,
    private val watershedProvider: WatershedProvider,
    private val recordElastic: RecordElastic,
    private val enrollElastic: EnrollElastic,
    private val telegram: Telegram,
    private val blacklist: Blacklist,
    private val blacklistExecute: BlacklistExecute,
    private val normalMsgFactory: NormalMsgFactory,
    private val recordMsgFactory: RecordMsgFactory,
) {

    private val logger = LoggerFactory.getLogger(Group::class.java)

    init {
        subscribeUpdate()
    }

    private fun subscribeUpdate() {
        watershedProvider.requestObservable.subscribe(
            { request ->
                try {
                    if (request !is BotGroupRequest) return@subscribe
                    // 回执
                    when {
                        request.update.callbackQuery() != null ->{
                            // 输入状态
                            botProvider.sendTyping(request.chatId)
                            executeByButton(request)
                        }
                        request.update.message().text().startsWith("/") && request.update.message().text().endsWith("@${botProvider.username}") ->{
                            // 输入状态
                            botProvider.sendTyping(request.chatId)
                            executeByCommand(request)
                        }
                        else ->
                            executeByText(request)
                    }
                } catch (e: Throwable) {
                    botProvider.sendErrorMessage(e)
                    e.printStackTrace()
                }
            },
            { throwable ->
                throwable.printStackTrace()
                logger.error("Group.error")
                botProvider.sendErrorMessage(throwable)
            },
            {
                logger.error("Group.complete")
            }
        )
    }

    private fun executeByText(request: BotGroupRequest) {
        // todo: 群组中查找群组……
    }

    private fun executeByCommand(request: BotGroupRequest) {
        // 获取命令内容
        val cmd = request.update.message().text().replaceFirst("/", "").replace("@${botProvider.username}", "")
        // 回执
        val sendMessage = when (cmd) {
            "start" -> normalMsgFactory.makeReplyMsg(request.chatId, "only-private")
            "enroll" -> executeByEnroll(request)
            "update" -> executeByUpdate(request)
            // todo: 开启后，可以在群组中查找群组……
            "list" -> normalMsgFactory.makeReplyMsg(request.chatId, "only-private")
            "mine" -> normalMsgFactory.makeReplyMsg(request.chatId, "only-private")
            // todo： 配置是否开启索引服务群组 /list
//            "setting" -> msgFactory.makeReplyMsg(request.chatId, "setting")
            "help" -> normalMsgFactory.makeReplyMsg(request.chatId, "help-group")
            else -> normalMsgFactory.makeReplyMsg(request.chatId, "can-not-understand")
        } ?: return
        sendMessage.disableWebPagePreview(true)
        sendMessage.parseMode(ParseMode.HTML)
        botProvider.sendAutoDeleteMessage(sendMessage)
    }

    private fun executeByButton(request: BotGroupRequest) {
        val answer = AnswerCallbackQuery(request.update.callbackQuery().id())
        botProvider.send(answer)

        val callbackData = request.update.callbackQuery().data()
        when {
            callbackData.startsWith("page") -> {

            }
        }
    }

    private fun executeByEnroll(request: BotGroupRequest): SendMessage? {
        // 校验bot权限
        if (!checkMyAuthority(request.chatId))
            return normalMsgFactory.makeReplyMsg(request.chatId, "group-bot-authority")
        // 校验提交者权限
        val user = request.update.message().from()
        if (!checkUserAuthority(request.chatId, user.id().toLong()))
            return normalMsgFactory.makeReplyMsg(request.chatId, "group-user-authority")
        // 人员黑名单检测
        val userBlack = blacklist.get(user.id().toLong())
        if (userBlack != null) {
            val telegramPerson = Telegram.TelegramPerson(user.id().toLong(), user.username(), user.nick(), null)
            blacklistExecute.notify(request.chatId, telegramPerson)
            return null
        }
        // 群组信息
        val telegramGroup = telegram.getTelegramGroupFromChat(request.chatId) ?: throw RuntimeException("群组信息获取失败")
        // 收录对象黑名单检测
        val recordBlack = blacklist.get(telegramGroup.chatId!!)
        if (recordBlack != null) {
            blacklistExecute.notify(request.chatId, telegramGroup)
            return null
        }
        // 检测是否已有提交或已收录
        val enrollExist = enrollElastic.getSubmittedEnrollByChatId(telegramGroup.chatId)
        val record = recordElastic.getRecordByChatId(telegramGroup.chatId)
        if (enrollExist != null || record != null) {
            val msg = normalMsgFactory.makeReplyMsg(request.chatId, "exist")
            botProvider.send(msg)
            return null
        }
        // 入库
        val enroll = EnrollElastic.Enroll(
            UUID.randomUUID().toString(),
            Telegram.TelegramModType.Group,
            telegramGroup.chatId,
            telegramGroup.title,
            telegramGroup.description,
            null,
            null,
            telegramGroup.username,
            telegramGroup.link,
            telegramGroup.members,
            Date().time,
            user.id().toLong(),
            user.nick(),
            false,
            null
        )
        val createEnroll = enrollElastic.addEnroll(enroll)
        if (!createEnroll) throw RuntimeException("群组信息存储失败")
        // 回执
        val sendMessage = recordMsgFactory.makeEnrollMsg(user.id().toLong(), enroll)
        botProvider.send(sendMessage)
        return normalMsgFactory.makeReplyMsg(request.chatId, "pls-check-private")
    }

    private fun executeByUpdate(request: BotGroupRequest): SendMessage? {
        // 校验bot权限
        if (!checkMyAuthority(request.chatId))
            return normalMsgFactory.makeReplyMsg(request.chatId, "group-bot-authority")
        // 校验提交者权限
        val user = request.update.message().from()
        if (!checkUserAuthority(request.chatId, user.id().toLong()))
            return normalMsgFactory.makeReplyMsg(request.chatId, "group-user-authority")
        // 校验权限
        val record = recordElastic.getRecordByChatId(request.chatId)
        if (record == null) return normalMsgFactory.makeReplyMsg(request.chatId, "group-not-enroll")
        if (record.createUser != user.id().toLong()) return normalMsgFactory.makeReplyMsg(request.chatId, "group-enroller-fail")
        // 更新
        val telegramGroup = telegram.getTelegramGroupFromChat(request.chatId) ?: throw RuntimeException("群组信息获取失败")
        val newRecord = record.copy(username = telegramGroup.username,link = telegramGroup.link)
        recordElastic.updateRecord(newRecord)
        // 回执
        val msg = recordMsgFactory.makeRecordMsg(newRecord.createUser,newRecord)
        botProvider.send(msg)
        return normalMsgFactory.makeReplyMsg(request.chatId, "pls-check-private")
    }

    /**
     * 检查bot权限
     * 必须为管理员且有邀请用户的权限
     */
    private fun checkMyAuthority(chatId: Long): Boolean {
        val getAdministrators = GetChatAdministrators(chatId)
        val administrators = botProvider.send(getAdministrators)
        val me = administrators.administrators().firstOrNull { it.user().username() == botProvider.username }
        return me != null && me.canInviteUsers()
    }

    /**
     * 检查用户权限
     * 必须为管理员
     */
    private fun checkUserAuthority(chatId: Long, userId: Long): Boolean {
        val getAdministrators = GetChatAdministrators(chatId)
        val administrators = botProvider.send(getAdministrators)
        val user = administrators.administrators().firstOrNull { it.user().id().toLong() == userId }
        return user != null
    }

}
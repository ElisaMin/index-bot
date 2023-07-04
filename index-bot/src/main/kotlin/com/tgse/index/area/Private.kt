package com.tgse.index.area

import com.pengrad.telegrambot.model.User
import com.pengrad.telegrambot.model.request.ParseMode
import com.pengrad.telegrambot.request.*
import com.tgse.index.area.execute.BlacklistExecute
import com.tgse.index.area.execute.EnrollExecute
import com.tgse.index.area.execute.RecordExecute
import com.tgse.index.area.msgFactory.ListMsgFactory
import com.tgse.index.area.msgFactory.MineMsgFactory
import com.tgse.index.area.msgFactory.NormalMsgFactory
import com.tgse.index.area.msgFactory.RecordMsgFactory
import com.tgse.index.infrastructure.provider.BotProvider
import com.tgse.index.domain.repository.nick
import com.tgse.index.domain.service.*
import com.tgse.index.infrastructure.provider.BotLifecycle
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.*

/**
 * 私聊
 */
@Service
class Private(
    private val blacklistExecute: BlacklistExecute,
    private val enrollExecute: EnrollExecute,
    private val recordExecute: RecordExecute,
    private val botProvider: BotProvider,
    private val requestService: RequestService,
    private val normalMsgFactory: NormalMsgFactory,
    private val recordMsgFactory: RecordMsgFactory,
    private val mineMsgFactory: MineMsgFactory,
    private val listMsgFactory: ListMsgFactory,
    private val enrollService: EnrollService,
    private val recordService: RecordService,
    private val userService: UserService,
    private val blackListService: BlackListService,
    private val telegramService: TelegramService,
    private val awaitStatusService: AwaitStatusService
): BotLifecycle() {

    private val logger = LoggerFactory.getLogger(Private::class.java)

    init {
        makeCoroutine {
            requestService.blockRequest<RequestService.BotPrivateRequest>(::handle)
        }
        subscribeApprove()
    }

    private suspend inline fun handle(request: RequestService.BotPrivateRequest) {
        runCatching {
            // 输入状态
            botProvider.sendTyping(request.chatId)
            // 回执
            when {
                request.update.callbackQuery() != null -> executeByButton(request)
                request.update.message().text().startsWith("/") -> executeByCommand(request)
                awaitStatusService.getAwaitStatus(request.chatId) != null -> {
                    try {
                        val callbackData = awaitStatusService.getAwaitStatus(request.chatId)!!.callbackData
                        if (callbackData.startsWith("approve") || callbackData.startsWith("enroll"))
                            enrollExecute.executeByStatus(EnrollExecute.Type.Enroll, request)
                        else if (callbackData.startsWith("update"))
                            recordExecute.executeByStatus(request)
                        else
                            executeByStatus(EnrollExecute.Type.Enroll, request)
                    } catch (e: Throwable) {
                        awaitStatusService.clearAwaitStatus(request.chatId)
                    }
                }
                request.update.message().text().startsWith("@") -> executeByEnroll(request)
                request.update.message().text().startsWith("https://t.me/") -> executeByEnroll(request)
                else -> executeByText(request)
            }
        }.onFailure { e ->
            logger.error("Private",e)
            botProvider.sendErrorMessage(e)
        }
        // 记录日活用户
        val user = request.update.message()?.from()
        if (user != null) footprint(user)
    }
    private fun subscribeApprove() =  {
        enrollService.submitApproveObservable.subscribe(
            { (enroll, manager, isPassed) ->
                try {
                    val msg = recordMsgFactory.makeApproveResultMsg(enroll.createUser, enroll, isPassed)
                    botProvider.send(msg)
                } catch (e: Throwable) {
                    logger.error("Private.approve.error",e)
                    botProvider.sendErrorMessage(e)

                }
            },
            { throwable ->
                logger.error("Private.approve.error",throwable)
                botProvider.sendErrorMessage(throwable)
            },
            {
                logger.error("Private.approve.complete")
            }
        )
    }

    private fun executeByText(request: RequestService.BotPrivateRequest) {
        val keywords = request.update.message().text()
        val msg = listMsgFactory.makeListFirstPageMsg(request.chatId, keywords, 1) ?: normalMsgFactory.makeReplyMsg(
            request.chatId,
            "nothing"
        )
        botProvider.send(msg)
    }

    private fun executeByEnroll(request: RequestService.BotPrivateRequest) {
        // 人员黑名单检测
        val userBlack = blackListService.get(request.chatId)
        if (userBlack != null) {
            val user = request.update.message().from()
            val telegramPerson = TelegramService.TelegramPerson(request.chatId, user.username(), user.nick(), null)
            blacklistExecute.notify(request.chatId, telegramPerson)
            return
        }
        // 获取收录内容
        val username = request.update.message().text().replaceFirst("@", "").replaceFirst("https://t.me/", "")
        val telegramMod = telegramService.getTelegramMod(username)
        // 收录对象黑名单检测
        val recordBlack = blackListService.get(username)
        if (recordBlack != null && telegramMod != null) {
            blacklistExecute.notify(request.chatId, telegramMod)
            return
        }
        // 检测是否已有提交或已收录
        val enrollExist = enrollService.getSubmittedEnrollByUsername(username)
        val record = recordService.getRecordByUsername(username)
        if (enrollExist != null || record != null) {
            val msg = normalMsgFactory.makeReplyMsg(request.chatId, "exist")
            botProvider.send(msg)
            return
        }
        // 回执
        val sendMessage = when (telegramMod) {
            is TelegramService.TelegramGroup -> {
                normalMsgFactory.makeReplyMsg(request.chatId, "enroll-need-join-group")
            }
            is TelegramService.TelegramChannel -> {
                val enroll = EnrollService.Enroll(
                    UUID.randomUUID().toString(),
                    TelegramService.TelegramModType.Channel,
                    null,
                    telegramMod.title,
                    telegramMod.description,
                    null,
                    null,
                    telegramMod.username,
                    null,
                    telegramMod.members,
                    Date().time,
                    request.chatId,
                    request.update.message().from().nick(),
                    false,
                    null
                )
                val createEnroll = enrollService.addEnroll(enroll)
                if (!createEnroll) return
                recordMsgFactory.makeEnrollMsg(request.chatId, enroll)
            }
            is TelegramService.TelegramBot -> {
                val enroll = EnrollService.Enroll(
                    UUID.randomUUID().toString(),
                    TelegramService.TelegramModType.Bot,
                    null,
                    telegramMod.title,
                    telegramMod.description,
                    null,
                    null,
                    telegramMod.username,
                    null,
                    null,
                    Date().time,
                    request.chatId,
                    request.update.message().from().nick(),
                    false,
                    null
                )
                val createEnroll = enrollService.addEnroll(enroll)
                if (!createEnroll) return
                recordMsgFactory.makeEnrollMsg(request.chatId, enroll)
            }
            else -> normalMsgFactory.makeReplyMsg(request.chatId, "nothing")
        }
        sendMessage.disableWebPagePreview(true)
        sendMessage.parseMode(ParseMode.HTML)
        botProvider.send(sendMessage)
    }

    private fun executeByCommand(request: RequestService.BotPrivateRequest) {
        // 获取命令内容
        val cmd = request.update.message().text().replaceFirst("/", "").replace("@${botProvider.username}", "")
        // 回执
        val sendMessage = when {
            cmd == "start" -> normalMsgFactory.makeStartMsg(request.chatId)
            cmd.startsWith("start ") -> executeBySuperCommand(request)
            cmd == "enroll" -> normalMsgFactory.makeReplyMsg(request.chatId, cmd)
            cmd == "update" || cmd == "mine" -> mineMsgFactory.makeListFirstPageMsg(request.update.message().from())
            cmd == "setting" -> normalMsgFactory.makeReplyMsg(request.chatId, "only-group")
            cmd == "help" -> normalMsgFactory.makeReplyMsg(request.chatId, "help-private")
            cmd == "cancel" -> {
                awaitStatusService.clearAwaitStatus(request.chatId)
                normalMsgFactory.makeReplyMsg(request.chatId, "cancel")
            }
            else -> normalMsgFactory.makeReplyMsg(request.chatId, "can-not-understand")
        }
        sendMessage.disableWebPagePreview(true)
        sendMessage.parseMode(ParseMode.HTML)
        botProvider.send(sendMessage)
    }

    private fun executeBySuperCommand(request: RequestService.BotPrivateRequest): SendMessage {
        return try {
            val recordUUID = request.update.message().text().replaceFirst("/start ", "")
            val record = recordService.getRecord(recordUUID)!!
            recordMsgFactory.makeRecordMsg(request.chatId, record)
        } catch (e: Throwable) {
            normalMsgFactory.makeReplyMsg(request.chatId, "start")
        }
    }

    private fun executeByButton(request: RequestService.BotPrivateRequest) {
        val answer = AnswerCallbackQuery(request.update.callbackQuery().id())
        botProvider.send(answer)

        val callbackData = request.update.callbackQuery().data()
        when {
            callbackData.startsWith("enroll") || callbackData.startsWith("enroll-class") -> {
                enrollExecute.executeByEnrollButton(EnrollExecute.Type.Enroll, request)
            }
            callbackData.startsWith("update") || callbackData.startsWith("record-class") -> {
                recordExecute.executeByRecordButton(request)
            }
            callbackData.startsWith("page") -> {
                val callback = callbackData.replace("page:", "").split("&")
                val keywords = callback[0]
                val pageIndex = callback[1].toInt()
                val msg = listMsgFactory.makeListNextPageMsg(request.chatId, request.messageId!!, keywords, pageIndex)
                botProvider.send(msg)
            }
            callbackData.startsWith("mine") -> {
                val pageIndex = callbackData.replace("mine:", "").toInt()
                val user = request.update.callbackQuery().from()
                val msg = mineMsgFactory.makeListNextPageMsg(user, request.messageId!!, pageIndex)
                botProvider.send(msg)
            }
            callbackData.startsWith("feedback") -> {
                awaitStatusService.setAwaitStatus(request.chatId, AwaitStatusService.Await(request.messageId!!, callbackData))
                val msg = normalMsgFactory.makeReplyMsg(request.chatId, "feedback-start")
                botProvider.send(msg)
            }
        }
    }

    suspend fun executeByStatus(type: EnrollExecute.Type, request: RequestService.BotRequest) {
        val statusCallbackData = awaitStatusService.getAwaitStatus(request.chatId!!)!!.callbackData
        when {
            statusCallbackData.startsWith("feedback:") ->  {
                val recordUUID = statusCallbackData.replace("feedback:", "")
                val record = recordService.getRecord(recordUUID)!!
                val user = request.update.message().from()
                val content = request.update.message().text()
                val feedback = Feedback(record, user, content)
                requestService.emit(feedback)
                // 清除状态
                awaitStatusService.clearAwaitStatus(request.chatId!!)
                val msg = normalMsgFactory.makeReplyMsg(request.chatId!!, "feedback-finish")
                botProvider.send(msg)
            }
        }
    }

    private fun footprint(user: User) {
        try {
            userService.footprint(user.id().toLong())
        } catch (e: Throwable) {
            logger.warn("记录足迹失败：(${e::class.java})[${e.message}]")
        }
    }

}
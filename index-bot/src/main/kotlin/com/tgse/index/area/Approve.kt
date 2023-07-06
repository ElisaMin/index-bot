package com.tgse.index.area

import com.pengrad.telegrambot.model.request.ParseMode
import com.pengrad.telegrambot.request.AnswerCallbackQuery
import com.pengrad.telegrambot.request.SendMessage
import com.tgse.index.area.execute.BlacklistExecute
import com.tgse.index.area.execute.EnrollExecute
import com.tgse.index.area.msgFactory.NormalMsgFactory
import com.tgse.index.area.msgFactory.RecordMsgFactory
import com.tgse.index.domain.repository.nick
import com.tgse.index.domain.service.*
import com.tgse.index.infrastructure.provider.BotProvider
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.util.*

@Service
class Approve(
    private val enrollExecute: EnrollExecute,
    private val blacklistExecute: BlacklistExecute,
    private val botProvider: BotProvider,
    private val requestService: RequestService,
    private val banListService: BanListService,
    private val enrollService: EnrollService,
    private val recordService: RecordService,
    private val userService: UserService,
    private val awaitStatusService: AwaitStatusService,
    private val normalMsgFactory: NormalMsgFactory,
    private val recordMsgFactory: RecordMsgFactory,
    @Value("\${group.approve.id}")
    private val approveGroupChatId: Long,
    @Value("\${secretary.autoDeleteMsgCycle}")
    private val autoDeleteMsgCycle: Long
) {
    private val logger = LoggerFactory.getLogger(Approve::class.java)

    private val commandRegex = """^/(\w+)@(\w+bot)\s*(.+)?""".toRegex()

    init {
        subscribeUpdate()
        subscribeFeedback()
        subscribeSubmitEnroll()
        subscribeApproveEnroll()
        subscribeDeleteRecord()
    }

    private fun subscribeUpdate() {
        requestService.approves.subscribe(
            { request ->
                try {
                    // 回执
                    val commandRegexResult =
                        if (request.update.message()?.text() == null) null
                        else commandRegex.find(request.update.message().text())
                    when {
                        request.update.callbackQuery() != null -> {
                            botProvider.sendTyping(request.chatId)
                            executeByButton(request)
                        }
                        awaitStatusService.getAwaitStatus(request.chatId) != null -> {
                            botProvider.sendTyping(request.chatId)
                            enrollExecute.executeByStatus(EnrollExecute.Type.Approve, request)
                        }
                        commandRegexResult != null -> {
                            val botUsername = commandRegexResult.groupValues[2]
                            val command = commandRegexResult.groupValues[1]
                            val parameter = commandRegexResult.groupValues[3].ifEmpty { null }

                            if (botUsername != botProvider.username) return@subscribe
                            botProvider.sendTyping(request.chatId)
                            executeByCommand(request.chatId, command, parameter)
                        }
                    }
                } catch (e: Throwable) {
                    botProvider.sendErrorMessage(e)
                    e.printStackTrace()
                }
            },
            { throwable ->
                throwable.printStackTrace()
                logger.error("Approve.error")
                botProvider.sendErrorMessage(throwable)
            },
            {
                logger.error("Approve.complete")
            }
        )
    }

    private fun subscribeFeedback() {
        requestService.feedbackObservable.subscribe(
            { (record, user, content) ->
                try {
                    val recordMsg = recordMsgFactory.makeFeedbackMsg(approveGroupChatId, record)
                    botProvider.send(recordMsg)
                    val feedbackMsg = SendMessage(approveGroupChatId, "ID：${user.id()}\n用户：${user.nick()}\n反馈：$content")
                    botProvider.send(feedbackMsg)
                } catch (e: Throwable) {
                    botProvider.sendErrorMessage(e)
                    e.printStackTrace()
                }
            },
            { throwable ->
                throwable.printStackTrace()
                logger.error("Approve.error")
                botProvider.sendErrorMessage(throwable)
            },
            {
                logger.error("Approve.complete")
            }
        )
    }

    private fun subscribeSubmitEnroll() {
        enrollService.submitEnrollObservable.subscribe(
            { enroll ->
                val msg = recordMsgFactory.makeApproveMsg(approveGroupChatId, enroll)
                botProvider.send(msg)
            },
            { throwable ->
                throwable.printStackTrace()
                logger.error("subscribeSubmitEnroll.error")
                botProvider.sendErrorMessage(throwable)
            },
            {
                logger.error("subscribeSubmitEnroll.complete")
            }
        )
    }

    private fun subscribeApproveEnroll() {
        enrollService.submitApproveObservable.subscribe(
            { (enroll, manager, isPassed) ->
                val msg = recordMsgFactory.makeApproveResultMsg(approveGroupChatId, enroll, manager, isPassed)
                val msgResponse = botProvider.send(msg)
                if (isPassed) return@subscribe
                val editMsg = normalMsgFactory.makeClearMarkupMsg(approveGroupChatId, msgResponse.message().messageId())
                botProvider.sendDelay(editMsg, autoDeleteMsgCycle * 1000)
            },
            { throwable ->
                throwable.printStackTrace()
                logger.error("subscribeApproveEnroll.error")
                botProvider.sendErrorMessage(throwable)
            },
            {
                logger.error("subscribeApproveEnroll.complete")
            }
        )
    }

    private fun subscribeDeleteRecord() {
        recordService.deleteRecordObservable.subscribe(
            { next ->
                try {
                    val msg = normalMsgFactory.makeRemoveRecordReplyMsg(
                        approveGroupChatId,
                        next.second.nick(),
                        next.first.title
                    )
                    botProvider.send(msg)
                } catch (e: Throwable) {
                    botProvider.sendErrorMessage(e)
                    e.printStackTrace()
                }
            },
            { throwable ->
                throwable.printStackTrace()
                logger.error("Approve.error")
                botProvider.sendErrorMessage(throwable)
            },
            {
                logger.error("Approve.complete")
            }
        )
    }

    private fun executeByCommand(chatId: Long, command: String, parameter: String?) {
        // 回执
        val sendMessage = when (command) {
            "start", "enroll", "update", "setting", "help" -> normalMsgFactory.makeReplyMsg(chatId, "disable")
            "list" -> normalMsgFactory.makeReplyMsg(chatId, "disable")
            "mine" -> normalMsgFactory.makeReplyMsg(chatId, "only-private")
            "ban" -> {
                if (parameter == null || """^[-\d]\d*$""".toRegex().find(parameter) == null) {
                    normalMsgFactory.makeReplyMsg(chatId, "ban-parameter")
                } else {
                    val ban = BanListService.Ban(UUID.randomUUID().toString(), parameter.toLong(), Date().time)
                    banListService.add(ban)
                    normalMsgFactory.makeBanReplyMsg(chatId, "ban-success", parameter)
                }
            }
            "unban" -> {
                if (parameter == null || """^[-\d]\d*$""".toRegex().find(parameter) == null) {
                    normalMsgFactory.makeReplyMsg(chatId, "unban-parameter")
                } else {
                    val ban = banListService.get(parameter.toLong())
                    if (ban == null) {
                        normalMsgFactory.makeReplyMsg(chatId, "unban-no-need")
                    } else {
                        banListService.delete(ban.uuid)
                        normalMsgFactory.makeBanReplyMsg(chatId, "unban-success", parameter)
                    }
                }
            }
            else -> normalMsgFactory.makeReplyMsg(chatId, "can-not-understand")
        }
        sendMessage.disableWebPagePreview(true)
        sendMessage.parseMode(ParseMode.HTML)
        botProvider.send(sendMessage)
    }

    private fun executeByButton(request: RequestService.BotApproveRequest) {
        val answer = AnswerCallbackQuery(request.update.callbackQuery().id())
        botProvider.send(answer)

        val callbackData = request.update.callbackQuery().data()
        when {
            callbackData.startsWith("approve") || callbackData.startsWith("enroll-class") -> {
                enrollExecute.executeByEnrollButton(EnrollExecute.Type.Approve, request)
            }
            callbackData.startsWith("blacklist") -> {
                blacklistExecute.executeByBlacklistButton(request)
            }
            callbackData.startsWith("remove") -> {
                val manager = request.update.callbackQuery().from()
                val recordUUID = callbackData.replace("remove:", "")
                recordService.deleteRecord(recordUUID, manager)
                val msg = normalMsgFactory.makeClearMarkupMsg(request.chatId, request.messageId!!)
                botProvider.send(msg)
            }
        }
    }

    @Scheduled(zone = "Asia/Shanghai", cron = "0 0 8 * * ?")
    private fun statisticsDaily() {
        val (userCount, dailyIncrease, dailyActive) = userService.statistics()
        val recordCount = recordService.count()

        val msg = normalMsgFactory.makeStatisticsDailyReplyMsg(
            approveGroupChatId,
            dailyIncrease,
            dailyActive,
            userCount,
            recordCount
        )
        botProvider.send(msg)
    }

}
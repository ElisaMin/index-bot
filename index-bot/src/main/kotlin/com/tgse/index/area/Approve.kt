package com.tgse.index.area


import com.tgse.index.area.execute.BlacklistExecute
import com.tgse.index.area.execute.EnrollExecute
import com.tgse.index.area.msgFactory.NormalMsgFactory
import com.tgse.index.area.msgFactory.NormalMsgFactory.Companion.SendMessage
import com.tgse.index.area.msgFactory.NormalMsgFactory.Companion.disableWebPagePreview
import com.tgse.index.area.msgFactory.NormalMsgFactory.Companion.parseMode
import com.tgse.index.area.msgFactory.RecordMsgFactory
import com.tgse.index.domain.repository.nick
import com.tgse.index.domain.service.*
import com.tgse.index.infrastructure.provider.BotLifecycle
import com.tgse.index.infrastructure.provider.BotProvider
import com.tgse.index.infrastructure.provider.ElasticSearchScope
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery
import org.telegram.telegrambots.meta.api.methods.ParseMode
import org.telegram.telegrambots.meta.api.objects.*
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
    private val scope:ElasticSearchScope,
    @Value("\${group.approve.id}")
    private val approveGroupChatId: Long,
    @Value("\${secretary.autoDeleteMsgCycle}")
    private val autoDeleteMsgCycle: Long
): BotLifecycle() {
    private val logger = LoggerFactory.getLogger(Approve::class.java)

    private val commandRegex = """^/(\w+)@(\w+bot)\s*(.+)?""".toRegex()

    init {
        makeCoroutine {
            requestService.blockRequest<RequestService.BotApproveRequest>(::handle)
        }
        makeCoroutine {
            requestService.blockFeedbacks(::handle)
        }
        enrollService.subscribeEnrolls(::handle)
        enrollService.subscribeApproves(::handleApprove)
        recordService.subscribeDeletes(::handleDelete)
    }

    private suspend inline fun handle(request: RequestService.BotApproveRequest) {
        runCatching {
            val commandRegexResult =
                if (request.update.message?.text == null) null
                else commandRegex.find(request.update.message.text)
            when {
                request.update.callbackQuery != null -> {
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

                    if (botUsername != botProvider.username) return
                    botProvider.sendTyping(request.chatId)
                    executeByCommand(request.chatId, command, parameter)
                }
            }
        }.onFailure { e->
            logger.error("Approve",e)
            botProvider.sendErrorMessage(e)
        }
    }
    private suspend inline fun handle(feedback: Feedback) {
        val (record, user, content) = feedback
        runCatching {
            val recordMsg = recordMsgFactory.makeFeedbackMsg(approveGroupChatId, record)
            botProvider.send(recordMsg)
            val feedbackMsg = SendMessage(approveGroupChatId, "ID：${user.id}\n用户：${user.nick()}\n反馈：$content")
            botProvider.send(feedbackMsg)
        }.onFailure {e ->
            logger.error("Approve",e)
            botProvider.sendErrorMessage(e)
        }
    }
    private suspend inline fun handle(enroll:EnrollService.Enroll) {
        runCatching {
            val msg = recordMsgFactory.makeApproveMsg(approveGroupChatId, enroll)
            botProvider.send(msg)
        }.onFailure { e ->
            logger.error("subscribeSubmitEnroll.error", e)
            botProvider.sendErrorMessage(e)
        }
    }

    private suspend inline fun handleApprove(approve:Triple<EnrollService.Enroll, User, Boolean>) {
        val (enroll, user, isPassed) = approve
        runCatching {
            val msg = recordMsgFactory.makeApproveResultMsg(approveGroupChatId, enroll, user, isPassed)
            val msgResponse = botProvider.send(msg)
            if (!isPassed) {
                val editMsg = normalMsgFactory.makeClearMarkupMsg(approveGroupChatId, msgResponse.messageId)
                botProvider.sendDelay(editMsg, autoDeleteMsgCycle * 1000)
            }
        }.onFailure { e ->
            logger.error("subscribeApproveEnroll.error", e)
            botProvider.sendErrorMessage(e)
        }
    }

    private suspend inline fun handleDelete(next:Pair<RecordService.Record,User>) {
        runCatching {
            val msg = normalMsgFactory.makeRemoveRecordReplyMsg(
                approveGroupChatId,
                next.second.nick(),
                next.first.title
            )
            botProvider.send(msg)
        }.onFailure { e ->
            logger.error("Approve.error", e)
            botProvider.sendErrorMessage(e)
        }
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
        val answer = AnswerCallbackQuery(request.update.callbackQuery.id)
        botProvider.send(answer)

        val callbackData = request.update.callbackQuery.data
        when {
            callbackData.startsWith("approve") || callbackData.startsWith("enroll-class") -> {
                enrollExecute.executeByEnrollButton(EnrollExecute.Type.Approve, request)
            }
            callbackData.startsWith("blacklist") -> {
                blacklistExecute.executeByBlacklistButton(request)
            }
            callbackData.startsWith("remove") -> {
                val manager = request.update.callbackQuery.from
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
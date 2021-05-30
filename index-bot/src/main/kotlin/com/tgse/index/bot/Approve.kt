package com.tgse.index.bot

import com.pengrad.telegrambot.model.request.ParseMode
import com.pengrad.telegrambot.request.AnswerCallbackQuery
import com.pengrad.telegrambot.request.SendMessage
import com.tgse.index.bot.execute.BlacklistExecute
import com.tgse.index.bot.execute.EnrollExecute
import com.tgse.index.datasource.*
import com.tgse.index.msgFactory.NormalMsgFactory
import com.tgse.index.msgFactory.RecordMsgFactory
import com.tgse.index.provider.BotProvider
import com.tgse.index.provider.WatershedProvider
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Service
class Approve(
    private val enrollExecute: EnrollExecute,
    private val blacklistExecute: BlacklistExecute,
    private val botProvider: BotProvider,
    private val watershedProvider: WatershedProvider,
    private val enrollElastic: EnrollElastic,
    private val recordElastic: RecordElastic,
    private val userElastic: UserElastic,
    private val awaitStatus: AwaitStatus,
    private val normalMsgFactory: NormalMsgFactory,
    private val recordMsgFactory: RecordMsgFactory,
    @Value("\${group.approve.id}")
    private val approveGroupChatId: Long,
    @Value("\${secretary.autoDeleteMsgCycle}")
    private val autoDeleteMsgCycle: Long
) {
    private val logger = LoggerFactory.getLogger(Approve::class.java)

    init {
        subscribeUpdate()
        subscribeFeedback()
        subscribeSubmitEnroll()
        subscribeApproveEnroll()
        subscribeDeleteRecord()
    }

    private fun subscribeUpdate() {
        watershedProvider.requestObservable.subscribe(
            { request ->
                try {
                    if (request !is WatershedProvider.BotApproveRequest) return@subscribe
                    // 回执
                    when {
                        request.update.callbackQuery() != null -> {
                            botProvider.sendTyping(request.chatId)
                            executeByButton(request)
                        }
                        awaitStatus.getAwaitStatus(request.chatId) != null -> {
                            botProvider.sendTyping(request.chatId)
                            enrollExecute.executeByStatus(EnrollExecute.Type.Approve, request)
                        }
                        request.update.message().text().startsWith("/") && request.update.message().text()
                            .endsWith("@${botProvider.username}") -> {
                            botProvider.sendTyping(request.chatId)
                            executeByCommand(request)
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
        watershedProvider.feedbackObservable.subscribe(
            { (record, user, content) ->
                try {
                    val recordMsg = recordMsgFactory.makeFeedbackMsg(approveGroupChatId, record)
                    botProvider.send(recordMsg)
                    val feedbackMsg = SendMessage(approveGroupChatId, "用户：${user.nick()}\n反馈：$content")
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
        enrollElastic.submitEnrollObservable.subscribe(
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
        enrollElastic.submitApproveObservable.subscribe(
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
        recordElastic.deleteRecordObservable.subscribe(
            { next ->
                try {
                    val msg = normalMsgFactory.makeRemoveRecordReplyMsg(approveGroupChatId, next.second.nick(), next.first.title)
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

    private fun executeByCommand(request: WatershedProvider.BotApproveRequest) {
        // 获取命令内容
        val cmd = request.update.message().text().replaceFirst("/", "").replace("@${botProvider.username}", "")
        // 回执
        val sendMessage = when (cmd) {
            "start", "enroll", "update", "setting", "help" -> normalMsgFactory.makeReplyMsg(request.chatId, "disable")
            "list" -> normalMsgFactory.makeReplyMsg(request.chatId, "disable")
            "mine" -> normalMsgFactory.makeReplyMsg(request.chatId, "only-private")
            else -> normalMsgFactory.makeReplyMsg(request.chatId, "can-not-understand")
        }
        sendMessage.disableWebPagePreview(true)
        sendMessage.parseMode(ParseMode.HTML)
        botProvider.send(sendMessage)
    }

    private fun executeByButton(request: WatershedProvider.BotApproveRequest) {
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
                recordElastic.deleteRecord(recordUUID, manager)
                val msg = normalMsgFactory.makeClearMarkupMsg(request.chatId,request.messageId!!)
                botProvider.send(msg)
            }
        }
    }

    //    @Scheduled(zone = "Asia/Shanghai", cron = "0 0 8 * * ?")
//    @Scheduled(zone = "Asia/Shanghai", cron = "0 39 15 * * ?")
//    private fun statisticsDaily() {
//        val countOfUser = userElastic.count()
//        val dailyIncreaseOfUser = userElastic.dailyIncrease()
//        val dailyActiveOfUser = userElastic.dailyActive()
////        val countOfUser = userElastic.count()
//        val countOfRecord = recordElastic.count()
//
//        val msg = msgFactory.makeStatisticsDailyReplyMsg(
//            approveGroupChatId,
//            dailyIncreaseOfUser,
//            dailyActiveOfUser,
//            countOfUser,
//            countOfRecord
//        )
//        botProvider.send(msg)
//    }

}
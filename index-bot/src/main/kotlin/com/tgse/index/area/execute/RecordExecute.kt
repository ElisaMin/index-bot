package com.tgse.index.area.execute

import com.tgse.index.MismatchException
import com.tgse.index.area.msgFactory.NormalMsgFactory
import com.tgse.index.area.msgFactory.NormalMsgFactory.Companion.disableWebPagePreview
import com.tgse.index.area.msgFactory.RecordMsgFactory
import com.tgse.index.infrastructure.provider.BotProvider
import com.tgse.index.domain.service.*
import org.springframework.stereotype.Component

@Component
class RecordExecute(
    private val blacklistExecute: BlacklistExecute,
    private val botProvider: BotProvider,
    private val normalMsgFactory: NormalMsgFactory,
    private val recordMsgFactory: RecordMsgFactory,
    private val enrollService: EnrollService,
    private val recordService: RecordService,
    private val telegramService: TelegramService,
    private val blackListService: BlackListService,
    private val awaitStatusService: AwaitStatusService
) {

    fun executeByRecordButton(request: RequestService.BotRequest) {
        val user = request.update.callbackQuery.from
        val callbackData = request.update.callbackQuery.data
        val callbackDataVal = callbackData.replace("update:", "").replace("record-class:", "").split("&")
        val field = callbackDataVal[0]
        val record = recordService.getRecord(callbackDataVal[1])!!
        when {
            // 通过按钮修改收录申请信息
            callbackData.startsWith("record-class:") -> {
                // 修改数据
                val newRecord = record.copy(classification = field)
                recordService.updateRecord(newRecord)
                // 删除上一条消息
                botProvider.sendDeleteMessage(request.chatId!!, request.messageId!!)
                // 回执新消息
                val msg = recordMsgFactory.makeRecordMsg(request.chatId!!, newRecord)
                botProvider.send(msg)
            }
            // 通过文字修改收录申链接
            field == "link" -> {
                if (record.type == TelegramService.TelegramModType.Group) {
                    val msg = normalMsgFactory.makeReplyMsg(request.chatId!!, "update-link-group")
                    botProvider.send(msg)
                    return
                }
                awaitStatusService.setAwaitStatus(request.chatId!!, AwaitStatusService.Await(request.messageId!!, callbackData))
                val msg = normalMsgFactory.makeReplyMsg(request.chatId!!, "update-$field").disableWebPagePreview(true)
                botProvider.send(msg)
            }
            // 通过文字修改收录申请信息
            arrayOf("title", "about", "tags").contains(field) -> {
                awaitStatusService.setAwaitStatus(request.chatId!!, AwaitStatusService.Await(request.messageId!!, callbackData))
                val msg = normalMsgFactory.makeReplyMsg(request.chatId!!, "update-$field")
                botProvider.send(msg)
            }
            // 通过按钮修改收录申请信息
            field == "record-class" -> {
                // 删除上一条消息
                botProvider.sendDeleteMessage(request.chatId!!, request.messageId!!)
                // 回执新消息
                val msg = recordMsgFactory.makeRecordChangeClassificationMsg(request.chatId!!, record)
                botProvider.send(msg)
            }
            // 移除收录
            field == "remove" -> {
                // 删除上一条消息
                botProvider.sendDeleteMessage(request.chatId!!, request.messageId!!)
                recordService.deleteRecord(record.uuid, user)
                // 回执新消息
                val msg = normalMsgFactory.makeRemoveRecordReplyMsg(request.chatId!!, record.title)
                botProvider.send(msg)
            }
        }
    }

    fun executeByStatus(request: RequestService.BotRequest) {
        val statusCallbackData = awaitStatusService.getAwaitStatus(request.chatId!!)!!.callbackData
        val callbackDataVal = statusCallbackData.replace("update:", "").split("&")
        val field = callbackDataVal[0]
        val uuid = callbackDataVal[1]
        val msgContent = request.update.message.text
        try {
            val record = recordService.getRecord(uuid)!!
            val newRecord = when (field) {
                "link" -> {
                    // 获取收录内容
                    val username = request.update.message.text.replaceFirst("@", "").replaceFirst("https://t.me/", "")
                    if (record.username == username) throw MismatchException("链接未发生改变")
                    val telegramMod = telegramService.getTelegramMod(username)
                    // 收录对象黑名单检测
                    val recordBlack = blackListService.get(username)
                    if (recordBlack != null && telegramMod != null) {
                        blacklistExecute.notify(request.chatId!!, telegramMod)
                        return
                    }
                    // 检测是否已有提交或已收录
                    val enrollExist = enrollService.getSubmittedEnrollByUsername(username)
                    val recordExist = recordService.getRecordByUsername(username)
                    if (enrollExist != null || recordExist != null) {
                        val msg = normalMsgFactory.makeReplyMsg(request.chatId!!, "exist")
                        botProvider.send(msg)
                        return
                    }
                    val type = when (telegramMod) {
                        is TelegramService.TelegramGroup -> TelegramService.TelegramModType.Group
                        is TelegramService.TelegramChannel -> TelegramService.TelegramModType.Channel
                        is TelegramService.TelegramBot -> TelegramService.TelegramModType.Bot
                        else -> throw MismatchException("链接不存在")
                    }
                    if(record.type != type) throw  throw MismatchException("类型不匹配")
                    record.copy(username = username)
                }
                "title" -> {
                    if (msgContent.length > 26) throw MismatchException("标题太长，修改失败")
                    record.copy(title = msgContent)
                }
                "about" -> {
                    record.copy(description = msgContent)
                }
                "tags" -> {
                    val tags = mutableListOf<String>()
                    """(?<=#)[^\s#]+""".toRegex().findAll(msgContent).forEach {
                        val tag = "#${it.value}"
                        if (!tags.contains(tag))
                            tags.add(tag)
                    }
                    record.copy(tags = tags)
                }
                else -> throw RuntimeException("record request")
            }
            recordService.updateRecord(newRecord)
            // 清除状态
            awaitStatusService.applyAwaitStatus(request.chatId!!)
            // 回执新消息
            val msg = recordMsgFactory.makeRecordMsg(request.chatId!!, newRecord)
            botProvider.send(msg)
        } catch (e: Throwable) {
            when (e) {
                is MismatchException -> {
                    val msg = normalMsgFactory.makeExceptionMsg(request.chatId!!, e)
                    botProvider.send(msg)
                }
                else -> throw  e
            }
        }
    }

}
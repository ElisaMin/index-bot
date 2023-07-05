package com.tgse.index.area.msgFactory


import com.tgse.index.infrastructure.provider.BotProvider
import com.tgse.index.domain.service.EnrollService
import com.tgse.index.domain.service.RecordService
import com.tgse.index.domain.service.ReplyService
import com.tgse.index.domain.service.TelegramService
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import java.math.RoundingMode
import java.text.DecimalFormat

abstract class BaseMsgFactory(
    protected open val replyService: ReplyService,
    protected open val botProvider: BotProvider
) {

    /**
     * {
     *         return SendMessage(
     *             chatId,
     *             replyService.messages[replyType]!!.replace("\\{bot.username\\}".toRegex(), botProvider.username)
     *         ).disableWebPagePreview(false)
     *     }
     *
     */
    fun makeReplyMsg(chatId: Long, replyType: String): SendMessage = SendMessage().apply {
        this.chatId = chatId.toString()
        text = replyService.messages[replyType]!!.replace("\\{bot.username}".toRegex(), botProvider.username)
        disableWebPagePreview = false
    }

    protected fun makeRecordDetail(record: RecordService.Record): String {
        val link = if (record.username != null) "https://t.me/${record.username}" else record.link
        val detailSB = StringBuffer()
        detailSB.append("<b>标题</b>： <a href=\"$link\">${record.title}</a>\n")
        detailSB.append("<b>标签</b>： ${if (record.tags == null) "暂无" else record.tags.joinToString(" ")}\n")
        detailSB.append("<b>分类</b>： ${record.classification ?: "暂无"}\n")
        detailSB.append("<b>简介</b>：\n")
        val description = if (record.description == null) ""
        else record.description.replace("<", "&lt;").replace(">", "&gt;") + "\n"
        detailSB.append(description)
        return detailSB.toString()
    }

    protected fun makeRecordDetail(enroll: EnrollService.Enroll): String {
        val link = if (enroll.username != null) "https://t.me/${enroll.username}" else enroll.link
        val detailSB = StringBuffer()
        detailSB.append("<b>标题</b>： <a href=\"$link\">${enroll.title}</a>\n")
        detailSB.append("<b>标签</b>： ${if (enroll.tags == null) "暂无" else enroll.tags.joinToString(" ")}\n")
        detailSB.append("<b>分类</b>： ${enroll.classification ?: "暂无"}\n")
        detailSB.append("<b>简介</b>：\n")
        val description = if (enroll.description == null) ""
        else enroll.description.replace("<", "&lt;").replace(">", "&gt;") + "\n"
        detailSB.append(description)
        return detailSB.toString()
    }

    /**
     * 整理列表内容
     */
    protected fun generateRecordItem(record: RecordService.Record): String {
        // 频道或群组图标
        val icon = when (record.type) {
            TelegramService.TelegramModType.Group -> "\uD83D\uDC65"
            TelegramService.TelegramModType.Channel -> "\uD83D\uDCE2"
            TelegramService.TelegramModType.Bot -> "\uD83E\uDD16"
            else -> "❓"
        }
        // 成员数量
        val members = when (record.type) {
            TelegramService.TelegramModType.Group -> getMemberUnit(record.members!!)
            TelegramService.TelegramModType.Channel -> getMemberUnit(record.members!!)
            else -> ""
        }
        // 名称及地址
        val title = record.title.replace("<".toRegex(), "&lt;").replace(">".toRegex(), "&gt;")
        val display = "<a href='https://t.me/${botProvider.username}?start=${record.uuid}'>${title}</a>\n"
        // 最终
        return "$icon $members | $display"
    }

    /**
     * 整理列表内容
     */
    protected fun generateListItem(record: RecordService.Record): String {
        // 频道或群组图标
        val icon = when (record.type) {
            TelegramService.TelegramModType.Group -> "\uD83D\uDC65"
            TelegramService.TelegramModType.Channel -> "\uD83D\uDCE2"
            TelegramService.TelegramModType.Bot -> "\uD83E\uDD16"
            else -> "❓"
        }
        val operate = "<a href='https://t.me/${botProvider.username}?start=${record.uuid}'>${icon}</a>"
        // 成员数量
        val members = when (record.type) {
            TelegramService.TelegramModType.Group -> getMemberUnit(record.members!!)
            TelegramService.TelegramModType.Channel -> getMemberUnit(record.members!!)
            else -> ""
        }
        // 名称及地址
        val title = record.title.replace("<".toRegex(), "&lt;").replace(">".toRegex(), "&gt;")
        val display =
            if (record.username != null)
                "<a href='https://t.me/${record.username}'>${title}</a>\n"
            else
                "<a href='${record.link}'>${title}</a>\n"
        // 最终
        return "$operate $members | $display"
    }

    /**
     * 整理列表内容
     */
    protected fun generateEnrollItem(enroll: EnrollService.Enroll): String {
        // 频道或群组图标
        val icon = "⏳"
        // 成员数量
        val members = when (enroll.type) {
            TelegramService.TelegramModType.Group -> getMemberUnit(enroll.members!!)
            TelegramService.TelegramModType.Channel -> getMemberUnit(enroll.members!!)
            else -> ""
        }
        // 名称及地址
        val title = enroll.title.replace("<".toRegex(), "&lt;").replace(">".toRegex(), "&gt;")
        val display = "${title}\n"
        // 最终
        return "$icon $members | $display"
    }

    /**
     * 为数字增加单位
     */
    protected fun getMemberUnit(count: Long): String {
        var size = count.toDouble()
        // 数值过大增加单位
        val unit = when {
            count > 1000000 -> {
                size = count / 1000000.0
                "M"
            }
            count > 1000 -> {
                size = count / 1000.0
                "K"
            }
            else -> ""
        }
        // 保留1位小数
        val formatter = DecimalFormat()
        formatter.maximumFractionDigits = 1
        formatter.groupingSize = 0
        formatter.roundingMode = RoundingMode.FLOOR
        return formatter.format(size) + unit
    }

}
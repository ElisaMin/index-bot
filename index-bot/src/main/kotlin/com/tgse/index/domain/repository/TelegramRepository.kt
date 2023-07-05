package com.tgse.index.domain.repository


import com.tgse.index.domain.service.TelegramService
import org.telegram.telegrambots.meta.api.objects.User

interface TelegramRepository {

    /**
     * 公开群组、频道、机器人
     */
    fun getTelegramMod(username: String): TelegramService.TelegramMod?

    /**
     * 群组
     */
    fun getTelegramMod(id: Long): TelegramService.TelegramGroup?

}

fun User.nick(): String {
    return "$firstName$lastName"
}
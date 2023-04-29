package com.tgse.index.domain.service

import com.tgse.index.domain.repository.RecordRepository
import com.tgse.index.domain.repository.TelegramRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service

@Service
class SyncMembersService(
    private val recordRepository: RecordRepository,
    private val telegramRepository: TelegramRepository
) {

    private val logger = LoggerFactory.getLogger(this::class.java)

    @Scheduled(cron = "0 0 6 * * ?")
    fun doing() {
        logger.info("开始 ----> 更新成员数量")
        val size = 100
        var cursor = 0
        var counter = 0
        while (true) {
            val (records, total) = recordRepository.getAllRecords(cursor, size)
            cursor += size
            if (records.size == 0) break

            records.forEach { record ->
                when (record.type) {
                    TelegramService.TelegramModType.Bot, TelegramService.TelegramModType.Person -> return@forEach
                    else -> {}
                }

                 val mode = when {
                    record.chatId != null -> {
                        telegramRepository.getTelegramMod(record.chatId)
                    }
                    record.username != null -> {
                        telegramRepository.getTelegramMod(record.username)
                    }
                    else -> null
                } ?: return@forEach

                val newRecord = when (mode) {
                    is TelegramService.TelegramGroup -> record.copy(members = mode.members)
                    is TelegramService.TelegramChannel -> record.copy(members = mode.members)
                    else -> return@forEach
                }

                recordRepository.updateRecord(newRecord)
                counter++
            }
        }
        logger.info("结束 ----> 更新成员数量：已更新${counter}条数据")
    }

}
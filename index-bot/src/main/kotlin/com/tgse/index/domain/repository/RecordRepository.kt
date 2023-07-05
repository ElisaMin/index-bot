package com.tgse.index.domain.repository


import com.tgse.index.domain.service.RecordService
import org.telegram.telegrambots.meta.api.objects.User

interface RecordRepository {

    suspend fun getAllRecords(from: Int, size: Int): Pair<MutableList<RecordService.Record>, Long>

    suspend fun searchRecordsByClassification(classification: String, from: Int, size: Int): Pair<MutableList<RecordService.Record>, Long>
    suspend fun searchRecordsByKeyword(keyword: String, from: Int, size: Int): Pair<MutableList<RecordService.Record>, Long>
    suspend fun searchRecordsByCreator(user: User, from: Int, size: Int): Pair<MutableList<RecordService.Record>, Long>

    suspend fun getRecordByUsername(username: String): RecordService.Record?
    suspend fun getRecordByChatId(chatId: Long): RecordService.Record?

    suspend fun addRecord(record: RecordService.Record): Boolean
    suspend fun updateRecord(record: RecordService.Record)
    suspend fun deleteRecord(uuid: String, manager: User)
    suspend fun getRecord(uuid: String): RecordService.Record?

    suspend fun count(): Long

}
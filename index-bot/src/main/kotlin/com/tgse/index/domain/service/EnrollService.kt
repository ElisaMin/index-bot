package com.tgse.index.domain.service

import com.tgse.index.domain.repository.EnrollRepository
import com.tgse.index.infrastructure.provider.ElasticSearchScope
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import org.springframework.stereotype.Service
import org.telegram.telegrambots.meta.api.objects.User

@Service
class EnrollService(
    private val enrollRepository: EnrollRepository,
    private val scope:ElasticSearchScope
) {

    data class Enroll(
        val uuid: String,
        val type: TelegramService.TelegramModType,
        val chatId: Long?,
        val title: String,
        val description: String?,
        /**
         * 包含#
         * 如：#apple #iphone
         */
        val tags: Collection<String>?,
        val classification: String?,
        val username: String?,
        val link: String?,
        val members: Long?,
        val createTime: Long,
        val createUser: Long,
        val createUserNick: String,
        val isSubmit: Boolean,
        val approve: Boolean?
    )

    private val submits = MutableSharedFlow<Enroll>()
    private val submitApproves = MutableSharedFlow<Triple<Enroll, User, Boolean>>()
    fun subscribeEnrolls(onSubmit:FlowCollector<Enroll>) = scope.launch {
        submits.collect(onSubmit)
    }
    fun subscribeApproves(onApprove:FlowCollector<Triple<Enroll, User, Boolean>>) = scope.launch {
        submitApproves.collect(onApprove)
    }

    fun searchEnrolls(user: User, from: Int, size: Int): Pair<MutableList<Enroll>, Long> {
        return enrollRepository.searchEnrolls(user, from, size)
    }

    fun addEnroll(enroll: Enroll): Boolean {
        return enrollRepository.addEnroll(enroll)
    }

    fun updateEnroll(enroll: Enroll): Boolean {
        return enrollRepository.updateEnroll(enroll)
    }

    fun deleteEnroll(uuid: String) {
        enrollRepository.deleteEnroll(uuid)
    }

    fun getEnroll(uuid: String): Enroll? {
        return enrollRepository.getEnroll(uuid)
    }

    fun submitEnroll(uuid: String) {
        val enroll = getEnroll(uuid)!!
        if (enroll.isSubmit) return
        val newEnroll = enroll.copy(isSubmit = true)
        updateEnroll(newEnroll)
        scope.launch {
            submits.emit(enroll)
        }
    }

    fun approveEnroll(uuid: String, manager: User, isPassed: Boolean) {
        val enroll = getEnroll(uuid)!!
        val newEnroll = enroll.copy(approve = isPassed)
        updateEnroll(newEnroll)
        val triple = Triple(newEnroll, manager, isPassed)
        scope.launch {
            submitApproves.emit(triple)
        }
    }

    fun getSubmittedEnrollByUsername(username: String): Enroll? {
        return enrollRepository.getSubmittedEnroll(username)
    }

    fun getSubmittedEnrollByChatId(chatId: Long): Enroll? {
        return enrollRepository.getSubmittedEnroll(chatId)
    }

}
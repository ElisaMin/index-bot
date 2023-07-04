package com.tgse.index.domain.service

import com.pengrad.telegrambot.model.User
import com.tgse.index.domain.repository.EnrollRepository
import com.tgse.index.infrastructure.provider.ElasticSearchScope
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.subjects.BehaviorSubject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import org.springframework.stereotype.Service

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

    val submits = MutableSharedFlow<Enroll>()
     val submitApproves = MutableSharedFlow<Triple<Enroll, User, Boolean>>()

    fun subscribeApproves(onApprove:(Triple<Enroll, User, Boolean>)->Unit) = scope.launch {
        submitApproves.collect(onApprove)
    }

    private val submitApproveSubject = BehaviorSubject.create<Triple<Enroll, User, Boolean>>()
    val submitApproveObservable: Observable<Triple<Enroll, User, Boolean>> = submitApproveSubject.distinct()

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
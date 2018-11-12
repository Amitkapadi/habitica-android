package com.habitrpg.android.habitica.ui.viewmodels

import android.os.Bundle
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.habitrpg.android.habitica.components.AppComponent
import com.habitrpg.android.habitica.data.SocialRepository
import com.habitrpg.android.habitica.data.UserRepository
import com.habitrpg.android.habitica.extensions.*
import com.habitrpg.android.habitica.helpers.RxErrorHandler
import com.habitrpg.android.habitica.models.social.ChatMessage
import com.habitrpg.android.habitica.models.social.Group
import com.habitrpg.android.habitica.ui.activities.MainActivity
import com.habitrpg.android.habitica.ui.views.HabiticaSnackbar
import io.reactivex.BackpressureStrategy
import io.reactivex.Flowable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.functions.Consumer
import io.reactivex.subjects.BehaviorSubject
import io.realm.RealmResults
import java.util.HashMap
import javax.inject.Inject


enum class GroupViewType(internal val order: String) {
    PARTY("party"),
    GUILD("guild"),
    TAVERN("tavern")
}

open class GroupViewModel: BaseViewModel() {

    @Inject
    lateinit var socialRepository: SocialRepository

    var groupViewType: GroupViewType? = null

    private val group: MutableLiveData<Group?> by lazy {
        loadGroupFromLocal()
        MutableLiveData<Group?>()
    }

    protected val groupIDSubject = BehaviorSubject.create<Optional<String>>()
    var gotNewMessages: Boolean = false

    override fun inject(component: AppComponent) {
        component.inject(this)
    }

    override fun onCleared() {
        socialRepository.close()
        super.onCleared()
    }

    fun setGroupID(groupID: String) {
        groupIDSubject.onNext(groupID.asOptional())
    }

    fun getGroupData(): LiveData<Group?> = group

    private fun loadGroupFromLocal() {
        disposable.add(groupIDSubject.toFlowable(BackpressureStrategy.LATEST)
                .filterOptionalDoOnEmpty { group.value = null }
                .flatMap { socialRepository.getGroup(it) }
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(Consumer { group.value = it }, RxErrorHandler.handleEmptyError()))
    }

    fun getChatMessages(): Flowable<RealmResults<ChatMessage>> {
        return groupIDSubject.toFlowable(BackpressureStrategy.BUFFER)
                .filterMapEmpty()
                .flatMap { socialRepository.getGroupChat(it) }
    }

    fun retrieveGroup(function: (() -> Unit)?) {
        disposable.add(socialRepository.retrieveGroup("party")
                .filter { groupViewType == GroupViewType.PARTY }
                .flatMap { group1 ->
                    socialRepository.retrieveGroupMembers(group1.id, true)
                }
                .doOnComplete { function?.invoke() }
                .subscribe(Consumer { }, RxErrorHandler.handleEmptyError()))
    }

    fun inviteToGroup(inviteData: HashMap<String, Any>) {
        disposable.add(socialRepository.inviteToGroup(group.value?.id ?: "", inviteData)
                .subscribe(Consumer { }, RxErrorHandler.handleEmptyError()))
    }

    fun updateGroup(bundle: Bundle?) {
        if (group.value == null) {
            return
        }
        disposable.add(socialRepository.updateGroup(group.value, bundle?.getString("name"),
                bundle?.getString("description"),
                bundle?.getString("leader"),
                bundle?.getString("privacy"))
                .subscribe(Consumer { }, RxErrorHandler.handleEmptyError()))
    }

    fun leaveGroup(function: () -> Unit) {
        disposable.add(socialRepository.leaveGroup(this.group.value?.id ?: "")
                .flatMap { userRepository.retrieveUser(false, true) }
                .subscribe(Consumer {
                    function()
                }, RxErrorHandler.handleEmptyError()))
    }

    fun joinGroup(groupID: String) {
        disposable.add(socialRepository.joinGroup(groupID).subscribe(Consumer { }, RxErrorHandler.handleEmptyError()))
    }

    fun rejectGroupInvite(groupID: String) {
        disposable.add(socialRepository.rejectGroupInvite(groupID).subscribe(Consumer { }, RxErrorHandler.handleEmptyError()))
    }

    fun markMessagesSeen() {
        groupIDSubject.value?.value.notNull {
            if (groupViewType != GroupViewType.TAVERN && it.isNotEmpty() && gotNewMessages) {
                socialRepository.markMessagesSeen(it)
            }
        }
    }

    fun likeMessage(message: ChatMessage) {
        disposable.add(socialRepository.likeMessage(message).subscribe(Consumer { }, RxErrorHandler.handleEmptyError()))
    }

    fun flagMessage(chatMessage: ChatMessage, function: () -> Unit) {
        disposable.add(socialRepository.flagMessage(chatMessage)
                .subscribe(Consumer {
                    function()
                }, RxErrorHandler.handleEmptyError()))
    }

    fun deleteMessage(chatMessage: ChatMessage) {
        disposable.add(socialRepository.deleteMessage(chatMessage).subscribe(Consumer { }, RxErrorHandler.handleEmptyError()))
    }

    fun postGroupChat(chatText: String, onComplete: () -> Unit?) {
        groupIDSubject.value?.value.notNull {
            disposable.add(socialRepository.postGroupChat(it, chatText).subscribe(Consumer {
                onComplete()
            }, RxErrorHandler.handleEmptyError()))
        }
    }

    fun retrieveGroupChat(onComplete: () -> Unit) {
        groupIDSubject.value?.value.notNull {
            disposable.add(socialRepository.retrieveGroupChat(it).subscribe(Consumer {
                onComplete()
            }, RxErrorHandler.handleEmptyError()))
        }
    }
}
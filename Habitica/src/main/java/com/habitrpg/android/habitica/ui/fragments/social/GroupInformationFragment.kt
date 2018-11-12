package com.habitrpg.android.habitica.ui.fragments.social

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.net.toUri
import com.habitrpg.android.habitica.R
import com.habitrpg.android.habitica.components.AppComponent
import com.habitrpg.android.habitica.data.SocialRepository
import com.habitrpg.android.habitica.data.UserRepository
import com.habitrpg.android.habitica.extensions.backgroundCompat
import com.habitrpg.android.habitica.extensions.notNull
import com.habitrpg.android.habitica.helpers.RxErrorHandler
import com.habitrpg.android.habitica.models.invitations.PartyInvite
import com.habitrpg.android.habitica.models.members.Member
import com.habitrpg.android.habitica.models.social.Group
import com.habitrpg.android.habitica.models.user.User
import com.habitrpg.android.habitica.ui.activities.MainActivity
import com.habitrpg.android.habitica.ui.fragments.BaseFragment
import com.habitrpg.android.habitica.ui.helpers.DataBindingUtils
import com.habitrpg.android.habitica.ui.helpers.MarkdownParser
import com.habitrpg.android.habitica.ui.views.HabiticaSnackbar
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.functions.Consumer
import kotlinx.android.synthetic.main.fragment_group_info.*
import javax.inject.Inject


class GroupInformationFragment : BaseFragment() {

    @Inject
    lateinit var socialRepository: SocialRepository
    @Inject
    lateinit var userRepository: UserRepository

    var group: Group? = null
    set(value) {
        field = value
        updateGroup(value)
    }
    private var user: User? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? =
            inflater.inflate(R.layout.fragment_group_info, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)


        refreshLayout?.setOnRefreshListener { this.refresh() }

        if (user != null) {
            setUser(user)
        } else {
            compositeSubscription.add(userRepository.getUser().subscribe(Consumer {
                user = it
                setUser(user)
            }, RxErrorHandler.handleEmptyError()))
        }

        updateGroup(group)

        buttonPartyInviteAccept.setOnClickListener { _ ->
            val userId = user?.invitations?.party?.id
            if (userId != null) {
                socialRepository.joinGroup(userId)
                        .doOnNext { setInvitation(null) }
                        .flatMap { userRepository.retrieveUser(false) }
                        .flatMap { socialRepository.retrieveGroup("party") }
                        .flatMap<List<Member>> { group1 -> socialRepository.retrieveGroupMembers(group1.id, true) }
                        .subscribe(Consumer {  }, RxErrorHandler.handleEmptyError())
            }
        }

        buttonPartyInviteReject.setOnClickListener { _ ->
            val userId = user?.invitations?.party?.id
            if (userId != null) {
                socialRepository.rejectGroupInvite(userId)
                        .subscribe(Consumer { setInvitation(null) }, RxErrorHandler.handleEmptyError())
            }
        }

        username_textview.setOnClickListener { _ ->
            val clipboard = context?.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val clip = ClipData.newPlainText(context?.getString(R.string.username), user?.username)
            clipboard?.primaryClip = clip
            val activity = activity as? MainActivity
            if (activity != null) {
                HabiticaSnackbar.showSnackbar(activity.floatingMenuWrapper, getString(R.string.username_copied), HabiticaSnackbar.SnackbarDisplayType.NORMAL)
            }
        }

        craetePartyButton.setOnClickListener {
            val browserIntent = Intent(Intent.ACTION_VIEW, "https://habitica.com/party".toUri())
            startActivity(browserIntent)
        }

        context.notNull { context ->
            DataBindingUtils.loadImage("timeTravelersShop_background_fall") {
                val aspectRatio = it.width / it.height.toFloat()
                val height = context.resources.getDimension(R.dimen.shop_height).toInt()
                val width = Math.round(height * aspectRatio)
                val drawable = BitmapDrawable(context.resources, Bitmap.createScaledBitmap(it, width, height, false))
                drawable.tileModeX = Shader.TileMode.REPEAT
                Observable.just(drawable)
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(Consumer {
                            no_party_background.backgroundCompat = it
                        }, RxErrorHandler.handleEmptyError())
            }
        }

        groupDescriptionView.movementMethod = LinkMovementMethod.getInstance()
        groupSummaryView.movementMethod = LinkMovementMethod.getInstance()
    }

    private fun refresh() {
        if (group != null) {
            compositeSubscription.add(socialRepository.retrieveGroup(group?.id ?: "").subscribe(Consumer {}, RxErrorHandler.handleEmptyError()))
        } else {
            compositeSubscription.add(userRepository.retrieveUser(false, forced = true)
                    .filter { it.hasParty() }
                    .flatMap { socialRepository.retrieveGroup("party") }
                    .flatMap<List<Member>> { group1 -> socialRepository.retrieveGroupMembers(group1.id, true) }
                    .doOnComplete { refreshLayout.isRefreshing = false }
                    .subscribe(Consumer {  }, RxErrorHandler.handleEmptyError()))
        }
    }

    private fun setUser(user: User?) {
        if (group == null && user?.invitations?.party?.id != null) {
            setInvitation(user.invitations?.party)
        } else {
            setInvitation(null)
        }
        username_textview.text = user?.formattedUsername
    }

    private fun setInvitation(invitation: PartyInvite?) {
        invitationWrapper.visibility = if (invitation == null) View.GONE else View.VISIBLE
    }

    override fun onDestroy() {
        userRepository.close()
        socialRepository.close()
        super.onDestroy()
    }

    override fun injectFragment(component: AppComponent) {
        component.inject(this)
    }

    private fun updateGroup(group: Group?) {
        if (noPartyWrapper == null) {
            return
        }

        val hasGroup = group != null
        val groupItemVisibility = if (hasGroup) View.VISIBLE else View.GONE
        noPartyWrapper.visibility = if (hasGroup) View.GONE else View.VISIBLE
        groupNameView.visibility = groupItemVisibility
        groupDescriptionView.visibility = groupItemVisibility
        groupDescriptionWrapper.visibility = groupItemVisibility

        groupDescriptionView.text = MarkdownParser.parseMarkdown(group?.description)
        groupSummaryView.text = MarkdownParser.parseMarkdown(group?.summary)
        gemCountWrapper.visibility = if (group?.balance != null && group.balance > 0) View.VISIBLE else View.GONE
        gemCountTextView.text = (group?.balance ?: 0 * 4.0).toInt().toString()
    }

    companion object {

        fun newInstance(group: Group?, user: User?): GroupInformationFragment {
            val args = Bundle()

            val fragment = GroupInformationFragment()
            fragment.arguments = args
            fragment.group = group
            fragment.user = user
            return fragment
        }
    }

}

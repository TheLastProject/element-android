/*
 * Copyright (c) 2022 New Vector Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package im.vector.app.features.home.room.detail.composer

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.Spannable
import android.text.format.DateUtils
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.annotation.RequiresApi
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.core.text.buildSpannedString
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import com.airbnb.mvrx.activityViewModel
import com.airbnb.mvrx.withState
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.vanniktech.emoji.EmojiPopup
import dagger.hilt.android.AndroidEntryPoint
import im.vector.app.R
import im.vector.app.core.error.fatalError
import im.vector.app.core.extensions.registerStartForActivityResult
import im.vector.app.core.extensions.showKeyboard
import im.vector.app.core.glide.GlideApp
import im.vector.app.core.platform.VectorBaseFragment
import im.vector.app.core.platform.lifecycleAwareLazy
import im.vector.app.core.platform.showOptimizedSnackbar
import im.vector.app.core.resources.BuildMeta
import im.vector.app.core.utils.DimensionConverter
import im.vector.app.core.utils.checkPermissions
import im.vector.app.core.utils.onPermissionDeniedDialog
import im.vector.app.core.utils.registerForPermissionsResult
import im.vector.app.databinding.FragmentComposerBinding
import im.vector.app.features.VectorFeatures
import im.vector.app.features.attachments.AttachmentTypeSelectorView
import im.vector.app.features.attachments.AttachmentsHelper
import im.vector.app.features.attachments.ContactAttachment
import im.vector.app.features.attachments.ShareIntentHandler
import im.vector.app.features.attachments.preview.AttachmentsPreviewActivity
import im.vector.app.features.attachments.preview.AttachmentsPreviewArgs
import im.vector.app.features.attachments.toGroupedContentAttachmentData
import im.vector.app.features.command.Command
import im.vector.app.features.command.ParsedCommand
import im.vector.app.features.home.AvatarRenderer
import im.vector.app.features.home.room.detail.AutoCompleter
import im.vector.app.features.home.room.detail.RoomDetailAction
import im.vector.app.features.home.room.detail.TimelineViewModel
import im.vector.app.features.home.room.detail.composer.voice.VoiceMessageRecorderView
import im.vector.app.features.home.room.detail.timeline.action.MessageSharedActionViewModel
import im.vector.app.features.home.room.detail.timeline.helper.MatrixItemColorProvider
import im.vector.app.features.home.room.detail.timeline.image.buildImageContentRendererData
import im.vector.app.features.home.room.detail.upgrade.MigrateRoomBottomSheet
import im.vector.app.features.html.EventHtmlRenderer
import im.vector.app.features.html.PillImageSpan
import im.vector.app.features.html.PillsPostProcessor
import im.vector.app.features.location.LocationSharingMode
import im.vector.app.features.media.ImageContentRenderer
import im.vector.app.features.poll.PollMode
import im.vector.app.features.settings.VectorPreferences
import im.vector.app.features.share.SharedData
import im.vector.app.features.voice.VoiceFailure
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import org.commonmark.parser.Parser
import org.matrix.android.sdk.api.session.Session
import org.matrix.android.sdk.api.session.content.ContentAttachmentData
import org.matrix.android.sdk.api.session.room.model.message.MessageAudioContent
import org.matrix.android.sdk.api.session.room.model.message.MessageBeaconInfoContent
import org.matrix.android.sdk.api.session.room.model.message.MessageContent
import org.matrix.android.sdk.api.session.room.model.message.MessageFormat
import org.matrix.android.sdk.api.session.room.model.message.MessagePollContent
import org.matrix.android.sdk.api.session.room.model.message.MessageTextContent
import org.matrix.android.sdk.api.session.room.timeline.TimelineEvent
import org.matrix.android.sdk.api.session.room.timeline.getLastMessageContent
import org.matrix.android.sdk.api.util.MatrixItem
import org.matrix.android.sdk.api.util.toMatrixItem
import reactivecircus.flowbinding.android.view.focusChanges
import reactivecircus.flowbinding.android.widget.textChanges
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class MessageComposerFragment : VectorBaseFragment<FragmentComposerBinding>(), AttachmentsHelper.Callback, AttachmentTypeSelectorView.Callback {

    companion object {
        private const val ircPattern = " (IRC)"
    }

    @Inject lateinit var autoCompleterFactory: AutoCompleter.Factory
    @Inject lateinit var avatarRenderer: AvatarRenderer
    @Inject lateinit var matrixItemColorProvider: MatrixItemColorProvider
    @Inject lateinit var eventHtmlRenderer: EventHtmlRenderer
    @Inject lateinit var dimensionConverter: DimensionConverter
    @Inject lateinit var imageContentRenderer: ImageContentRenderer
    @Inject lateinit var shareIntentHandler: ShareIntentHandler
    @Inject lateinit var pillsPostProcessorFactory: PillsPostProcessor.Factory
    @Inject lateinit var vectorPreferences: VectorPreferences
    @Inject lateinit var vectorFeatures: VectorFeatures
    @Inject lateinit var buildMeta: BuildMeta
    @Inject lateinit var session: Session

    private val roomId: String get() = withState(timelineViewModel) { it.roomId }

    private val autoCompleter: AutoCompleter by lazy {
        autoCompleterFactory.create(roomId, isThreadTimeLine())
    }

    private val pillsPostProcessor by lazy {
        pillsPostProcessorFactory.create(roomId)
    }

    private val emojiPopup: EmojiPopup by lifecycleAwareLazy {
        createEmojiPopup()
    }

    private val glideRequests by lazy {
        GlideApp.with(this)
    }

    private val isEmojiKeyboardVisible: Boolean
        get() = vectorPreferences.showEmojiKeyboard()

    private var lockSendButton = false

    private lateinit var attachmentsHelper: AttachmentsHelper
    private lateinit var attachmentTypeSelector: AttachmentTypeSelectorView

    private val timelineViewModel: TimelineViewModel by activityViewModel()
    private val messageComposerViewModel: MessageComposerViewModel by activityViewModel()
    private lateinit var sharedActionViewModel: MessageSharedActionViewModel

    override fun getBinding(inflater: LayoutInflater, container: ViewGroup?): FragmentComposerBinding {
        return FragmentComposerBinding.inflate(inflater, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        sharedActionViewModel = activityViewModelProvider.get(MessageSharedActionViewModel::class.java)

        attachmentsHelper = AttachmentsHelper(requireContext(), this, buildMeta).register()

        setupComposer()
        setupEmojiButton()

        messageComposerViewModel.observeViewEvents {
            when (it) {
                is MessageComposerViewEvents.JoinRoomCommandSuccess -> handleJoinedToAnotherRoom(it)
                is MessageComposerViewEvents.SlashCommandConfirmationRequest -> handleSlashCommandConfirmationRequest(it)
                is MessageComposerViewEvents.SendMessageResult -> renderSendMessageResult(it)
                is MessageComposerViewEvents.ShowMessage -> showSnackWithMessage(it.message)
                is MessageComposerViewEvents.ShowRoomUpgradeDialog -> handleShowRoomUpgradeDialog(it)
                is MessageComposerViewEvents.AnimateSendButtonVisibility -> handleSendButtonVisibilityChanged(it)
                is MessageComposerViewEvents.OpenRoomMemberProfile -> openRoomMemberProfile(it.userId)
                is MessageComposerViewEvents.VoicePlaybackOrRecordingFailure -> {
                    if (it.throwable is VoiceFailure.UnableToRecord) {
                        onCannotRecord()
                    }
                    showErrorInSnackbar(it.throwable)
                }
                is MessageComposerViewEvents.InsertUserDisplayName -> insertUserDisplayNameInTextEditor(it.userId)
            }
        }

        messageComposerViewModel.onEach(MessageComposerViewState::sendMode, MessageComposerViewState::canSendMessage) { mode, canSend ->
            if (!canSend.boolean()) {
                return@onEach
            }
            when (mode) {
                is SendMode.Regular -> renderRegularMode(mode.text.toString())
                is SendMode.Edit -> renderSpecialMode(mode.timelineEvent, R.drawable.ic_edit, R.string.edit, mode.text.toString())
                is SendMode.Quote -> renderSpecialMode(mode.timelineEvent, R.drawable.ic_quote, R.string.action_quote, mode.text.toString())
                is SendMode.Reply -> renderSpecialMode(mode.timelineEvent, R.drawable.ic_reply, R.string.reply, mode.text.toString())
                is SendMode.Voice -> renderVoiceMessageMode(mode.text)
            }
        }

        if (savedInstanceState != null) {
            handleShareData()
        }
    }

    override fun onPause() {
        super.onPause()

        if (withState(messageComposerViewModel) { it.isVoiceRecording } && requireActivity().isChangingConfigurations) {
            // we're rotating, maintain any active recordings
        } else {
            messageComposerViewModel.handle(MessageComposerAction.OnEntersBackground(views.composerLayout.text.toString()))
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()

        autoCompleter.clear()
        messageComposerViewModel.endAllVoiceActions()
    }

    override fun invalidate() = withState(timelineViewModel, messageComposerViewModel) { mainState, messageComposerState ->
        if (mainState.tombstoneEvent != null) return@withState

        views.root.isInvisible = !messageComposerState.isComposerVisible
        views.composerLayout.views.sendButton.isInvisible = !messageComposerState.isSendButtonVisible
    }

    private fun setupComposer() {
        val composerEditText = views.composerLayout.views.composerEditText
        composerEditText.setHint(R.string.room_message_placeholder)

        autoCompleter.setup(composerEditText)

        observerUserTyping()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            composerEditText.setUseIncognitoKeyboard(vectorPreferences.useIncognitoKeyboard())
        }
        composerEditText.setSendMessageWithEnter(vectorPreferences.sendMessageWithEnter())

        composerEditText.setOnEditorActionListener { v, actionId, keyEvent ->
            val imeActionId = actionId and EditorInfo.IME_MASK_ACTION
            val isSendAction = EditorInfo.IME_ACTION_DONE == imeActionId || EditorInfo.IME_ACTION_SEND == imeActionId
            // Add external keyboard functionality (to send messages)
            val externalKeyboardPressedEnter = null != keyEvent &&
                    !keyEvent.isShiftPressed &&
                    keyEvent.keyCode == KeyEvent.KEYCODE_ENTER &&
                    resources.configuration.keyboard != Configuration.KEYBOARD_NOKEYS
            if (isSendAction || externalKeyboardPressedEnter) {
                sendTextMessage(v.text)
                true
            } else false
        }

        views.composerLayout.views.composerEmojiButton.isVisible = vectorPreferences.showEmojiKeyboard()

        val showKeyboard = withState(timelineViewModel) { it.showKeyboardWhenPresented }
        if (isThreadTimeLine() && showKeyboard) {
            // Show keyboard when the user started a thread
            views.composerLayout.views.composerEditText.showKeyboard(andRequestFocus = true)
        }
        views.composerLayout.callback = object : MessageComposerView.Callback {
            override fun onAddAttachment() {
                if (!::attachmentTypeSelector.isInitialized) {
                    attachmentTypeSelector = AttachmentTypeSelectorView(vectorBaseActivity, vectorBaseActivity.layoutInflater, this@MessageComposerFragment)
                    attachmentTypeSelector.setAttachmentVisibility(
                            AttachmentTypeSelectorView.Type.LOCATION,
                            vectorFeatures.isLocationSharingEnabled(),
                    )
                    attachmentTypeSelector.setAttachmentVisibility(
                            AttachmentTypeSelectorView.Type.POLL, !isThreadTimeLine()
                    )
                    attachmentTypeSelector.setAttachmentVisibility(
                            AttachmentTypeSelectorView.Type.VOICE_BROADCAST,
                            vectorFeatures.isVoiceBroadcastEnabled(), // TODO check user permission
                    )
                }
                attachmentTypeSelector.show(views.composerLayout.views.attachmentButton)
            }

            override fun onExpandOrCompactChange() {
                views.composerLayout.views.composerEmojiButton.isVisible = isEmojiKeyboardVisible
            }

            override fun onSendMessage(text: CharSequence) {
                sendTextMessage(text)
            }

            override fun onCloseRelatedMessage() {
                messageComposerViewModel.handle(MessageComposerAction.EnterRegularMode(false))
            }

            override fun onRichContentSelected(contentUri: Uri): Boolean {
                return sendUri(contentUri)
            }

            override fun onTextChanged(text: CharSequence) {
                messageComposerViewModel.handle(MessageComposerAction.OnTextChanged(text))
            }
        }
    }

    private fun sendTextMessage(text: CharSequence) {
        if (lockSendButton) {
            Timber.w("Send button is locked")
            return
        }
        if (text.isNotBlank()) {
            // We collapse ASAP, if not there will be a slight annoying delay
            views.composerLayout.collapse(true)
            lockSendButton = true
            messageComposerViewModel.handle(MessageComposerAction.SendMessage(text, vectorPreferences.isMarkdownEnabled()))
            emojiPopup.dismiss()
        }
    }

    private fun sendUri(uri: Uri): Boolean {
        val shareIntent = Intent(Intent.ACTION_SEND, uri)
        val isHandled = shareIntentHandler.handleIncomingShareIntent(shareIntent, ::onContentAttachmentsReady, onPlainText = {
            fatalError("Should not happen as we're generating a File based share Intent", vectorPreferences.failFast())
        })
        if (!isHandled) {
            Toast.makeText(requireContext(), R.string.error_handling_incoming_share, Toast.LENGTH_SHORT).show()
        }
        return isHandled
    }

    private fun renderRegularMode(content: String) {
        autoCompleter.exitSpecialMode()
        views.composerLayout.collapse()
        views.composerLayout.setTextIfDifferent(content)
        views.composerLayout.views.sendButton.contentDescription = getString(R.string.action_send)
    }

    private fun renderSpecialMode(
            event: TimelineEvent,
            @DrawableRes iconRes: Int,
            @StringRes descriptionRes: Int,
            defaultContent: String
    ) {
        autoCompleter.enterSpecialMode()
        // switch to expanded bar
        views.composerLayout.views.composerRelatedMessageTitle.apply {
            text = event.senderInfo.disambiguatedDisplayName
            setTextColor(matrixItemColorProvider.getColor(MatrixItem.UserItem(event.root.senderId ?: "@")))
        }

        val messageContent: MessageContent? = event.getLastMessageContent()
        val nonFormattedBody = when (messageContent) {
            is MessageAudioContent -> getAudioContentBodyText(messageContent)
            is MessagePollContent -> messageContent.getBestPollCreationInfo()?.question?.getBestQuestion()
            is MessageBeaconInfoContent -> getString(R.string.live_location_description)
            else -> messageContent?.body.orEmpty()
        }
        var formattedBody: CharSequence? = null
        if (messageContent is MessageTextContent && messageContent.format == MessageFormat.FORMAT_MATRIX_HTML) {
            val parser = Parser.builder().build()
            val document = parser.parse(messageContent.formattedBody ?: messageContent.body)
            formattedBody = eventHtmlRenderer.render(document, pillsPostProcessor)
        }
        views.composerLayout.views.composerRelatedMessageContent.text = (formattedBody ?: nonFormattedBody)

        // Image Event
        val data = event.buildImageContentRendererData(dimensionConverter.dpToPx(66))
        val isImageVisible = if (data != null) {
            imageContentRenderer.render(data, ImageContentRenderer.Mode.THUMBNAIL, views.composerLayout.views.composerRelatedMessageImage)
            true
        } else {
            imageContentRenderer.clear(views.composerLayout.views.composerRelatedMessageImage)
            false
        }

        views.composerLayout.views.composerRelatedMessageImage.isVisible = isImageVisible

        views.composerLayout.setTextIfDifferent(defaultContent)

        views.composerLayout.views.composerRelatedMessageActionIcon.setImageDrawable(ContextCompat.getDrawable(requireContext(), iconRes))
        views.composerLayout.views.sendButton.contentDescription = getString(descriptionRes)

        avatarRenderer.render(event.senderInfo.toMatrixItem(), views.composerLayout.views.composerRelatedMessageAvatar)

        views.composerLayout.expand {
            if (isAdded) {
                // need to do it here also when not using quick reply
                focusComposerAndShowKeyboard()
                views.composerLayout.views.composerRelatedMessageImage.isVisible = isImageVisible
            }
        }
        focusComposerAndShowKeyboard()
    }

    private fun observerUserTyping() {
        if (isThreadTimeLine()) return
        views.composerLayout.views.composerEditText.textChanges()
                .skipInitialValue()
                .debounce(300)
                .map { it.isNotEmpty() }
                .onEach {
                    Timber.d("Typing: User is typing: $it")
                    messageComposerViewModel.handle(MessageComposerAction.UserIsTyping(it))
                }
                .launchIn(viewLifecycleOwner.lifecycleScope)

        views.composerLayout.views.composerEditText.focusChanges()
                .onEach {
                    timelineViewModel.handle(RoomDetailAction.ComposerFocusChange(it))
                }
                .launchIn(viewLifecycleOwner.lifecycleScope)
    }

    private fun focusComposerAndShowKeyboard() {
        if (views.composerLayout.isVisible) {
            views.composerLayout.views.composerEditText.showKeyboard(andRequestFocus = true)
        }
    }

    private fun handleSendButtonVisibilityChanged(event: MessageComposerViewEvents.AnimateSendButtonVisibility) {
        if (event.isVisible) {
            views.root.views.sendButton.alpha = 0f
            views.root.views.sendButton.isVisible = true
            views.root.views.sendButton.animate().alpha(1f).setDuration(150).start()
        } else {
            views.root.views.sendButton.isInvisible = true
        }
    }

    private fun renderVoiceMessageMode(content: String) {
        ContentAttachmentData.fromJsonString(content)?.let { audioAttachmentData ->
            messageComposerViewModel.handle(MessageComposerAction.InitializeVoiceRecorder(audioAttachmentData))
        }
    }

    private fun getAudioContentBodyText(messageContent: MessageAudioContent): String {
        val formattedDuration = DateUtils.formatElapsedTime(((messageContent.audioInfo?.duration ?: 0) / 1000).toLong())
        return if (messageContent.voiceMessageIndicator != null) {
            getString(R.string.voice_message_reply_content, formattedDuration)
        } else {
            getString(R.string.audio_message_reply_content, messageContent.body, formattedDuration)
        }
    }

    private fun createEmojiPopup(): EmojiPopup {
        return EmojiPopup(
                rootView = views.root,
                keyboardAnimationStyle = R.style.emoji_fade_animation_style,
                onEmojiPopupShownListener = {
                    views.composerLayout.views.composerEmojiButton.apply {
                        contentDescription = getString(R.string.a11y_close_emoji_picker)
                        setImageResource(R.drawable.ic_keyboard)
                    }
                },
                onEmojiPopupDismissListener = lifecycleAwareDismissAction {
                    views.composerLayout.views.composerEmojiButton.apply {
                        contentDescription = getString(R.string.a11y_open_emoji_picker)
                        setImageResource(R.drawable.ic_insert_emoji)
                    }
                },
                editText = views.composerLayout.views.composerEditText
        )
    }

    /**
     *  Ensure dismiss actions only trigger when the fragment is in the started state.
     *  EmojiPopup by default dismisses onViewDetachedFromWindow, this can cause race conditions with onDestroyView.
     */
    private fun lifecycleAwareDismissAction(action: () -> Unit): () -> Unit {
        return {
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                action()
            }
        }
    }

    private fun setupEmojiButton() {
        views.composerLayout.views.composerEmojiButton.debouncedClicks {
            emojiPopup.toggle()
        }
    }

    private fun onCannotRecord() {
        // Update the UI, cancel the animation
        messageComposerViewModel.handle(MessageComposerAction.OnVoiceRecordingUiStateChanged(VoiceMessageRecorderView.RecordingUiState.Idle))
    }

    private fun handleJoinedToAnotherRoom(action: MessageComposerViewEvents.JoinRoomCommandSuccess) {
        views.composerLayout.setTextIfDifferent("")
        lockSendButton = false
        navigator.openRoom(vectorBaseActivity, action.roomId)
    }

    private fun handleSlashCommandConfirmationRequest(action: MessageComposerViewEvents.SlashCommandConfirmationRequest) {
        when (action.parsedCommand) {
            is ParsedCommand.UnignoreUser -> promptUnignoreUser(action.parsedCommand)
            else -> TODO("Add case for ${action.parsedCommand.javaClass.simpleName}")
        }
        lockSendButton = false
    }

    private fun promptUnignoreUser(command: ParsedCommand.UnignoreUser) {
        MaterialAlertDialogBuilder(requireActivity())
                .setTitle(R.string.room_participants_action_unignore_title)
                .setMessage(getString(R.string.settings_unignore_user, command.userId))
                .setPositiveButton(R.string.unignore) { _, _ ->
                    messageComposerViewModel.handle(MessageComposerAction.SlashCommandConfirmed(command))
                }
                .setNegativeButton(R.string.action_cancel, null)
                .show()
    }

    private fun renderSendMessageResult(sendMessageResult: MessageComposerViewEvents.SendMessageResult) {
        when (sendMessageResult) {
            is MessageComposerViewEvents.SlashCommandLoading -> {
                showLoading(null)
            }
            is MessageComposerViewEvents.SlashCommandError -> {
                displayCommandError(getString(R.string.command_problem_with_parameters, sendMessageResult.command.command))
            }
            is MessageComposerViewEvents.SlashCommandUnknown -> {
                displayCommandError(getString(R.string.unrecognized_command, sendMessageResult.command))
            }
            is MessageComposerViewEvents.SlashCommandResultOk -> {
                handleSlashCommandResultOk(sendMessageResult.parsedCommand)
            }
            is MessageComposerViewEvents.SlashCommandResultError -> {
                dismissLoadingDialog()
                displayCommandError(errorFormatter.toHumanReadable(sendMessageResult.throwable))
            }
            is MessageComposerViewEvents.SlashCommandNotImplemented -> {
                displayCommandError(getString(R.string.not_implemented))
            }
            is MessageComposerViewEvents.SlashCommandNotSupportedInThreads -> {
                displayCommandError(getString(R.string.command_not_supported_in_threads, sendMessageResult.command.command))
            }
        }

        lockSendButton = false
    }

    private fun handleSlashCommandResultOk(parsedCommand: ParsedCommand) {
        dismissLoadingDialog()
        views.composerLayout.setTextIfDifferent("")
        when (parsedCommand) {
            is ParsedCommand.DevTools -> {
                navigator.openDevTools(requireContext(), roomId)
            }
            is ParsedCommand.SetMarkdown -> {
                showSnackWithMessage(getString(if (parsedCommand.enable) R.string.markdown_has_been_enabled else R.string.markdown_has_been_disabled))
            }
            else -> Unit
        }
    }

    private fun displayCommandError(message: String) {
        MaterialAlertDialogBuilder(requireActivity())
                .setTitle(R.string.command_error)
                .setMessage(message)
                .setPositiveButton(R.string.ok, null)
                .show()
    }

    private fun showSnackWithMessage(message: String) {
        view?.showOptimizedSnackbar(message)
    }

    private fun handleShowRoomUpgradeDialog(roomDetailViewEvents: MessageComposerViewEvents.ShowRoomUpgradeDialog) {
        val tag = MigrateRoomBottomSheet::javaClass.name
        val roomId = withState(timelineViewModel) { it.roomId }
        MigrateRoomBottomSheet.newInstance(roomId, roomDetailViewEvents.newVersion)
                .show(parentFragmentManager, tag)
    }

    private fun openRoomMemberProfile(userId: String) {
        navigator.openRoomMemberProfile(userId = userId, roomId = roomId, context = requireActivity())
    }

    private val contentAttachmentActivityResultLauncher = registerStartForActivityResult { activityResult ->
        val data = activityResult.data ?: return@registerStartForActivityResult
        if (activityResult.resultCode == Activity.RESULT_OK) {
            val sendData = AttachmentsPreviewActivity.getOutput(data)
            val keepOriginalSize = AttachmentsPreviewActivity.getKeepOriginalSize(data)
            timelineViewModel.handle(RoomDetailAction.SendMedia(sendData, !keepOriginalSize))
        }
    }

    // AttachmentsHelper.Callback
    override fun onContentAttachmentsReady(attachments: List<ContentAttachmentData>) {
        val grouped = attachments.toGroupedContentAttachmentData()
        if (grouped.notPreviewables.isNotEmpty()) {
            // Send the not previewable attachments right now (?)
            timelineViewModel.handle(RoomDetailAction.SendMedia(grouped.notPreviewables, false))
        }
        if (grouped.previewables.isNotEmpty()) {
            val intent = AttachmentsPreviewActivity.newIntent(requireContext(), AttachmentsPreviewArgs(grouped.previewables))
            contentAttachmentActivityResultLauncher.launch(intent)
        }
    }

    override fun onContactAttachmentReady(contactAttachment: ContactAttachment) {
        val formattedContact = contactAttachment.toHumanReadable()
        messageComposerViewModel.handle(MessageComposerAction.SendMessage(formattedContact, false))
    }

    override fun onAttachmentError(throwable: Throwable) {
        showFailure(throwable)
    }

    // AttachmentTypeSelectorView.Callback
    private val typeSelectedActivityResultLauncher = registerForPermissionsResult { allGranted, deniedPermanently ->
        if (allGranted) {
            val pendingType = attachmentsHelper.pendingType
            if (pendingType != null) {
                attachmentsHelper.pendingType = null
                launchAttachmentProcess(pendingType)
            }
        } else {
            if (deniedPermanently) {
                activity?.onPermissionDeniedDialog(R.string.denied_permission_generic)
            }
            cleanUpAfterPermissionNotGranted()
        }
    }

    private fun launchAttachmentProcess(type: AttachmentTypeSelectorView.Type) {
        when (type) {
            AttachmentTypeSelectorView.Type.CAMERA -> attachmentsHelper.openCamera(
                    activity = requireActivity(),
                    vectorPreferences = vectorPreferences,
                    cameraActivityResultLauncher = attachmentCameraActivityResultLauncher,
                    cameraVideoActivityResultLauncher = attachmentCameraVideoActivityResultLauncher
            )
            AttachmentTypeSelectorView.Type.FILE -> attachmentsHelper.selectFile(attachmentFileActivityResultLauncher)
            AttachmentTypeSelectorView.Type.GALLERY -> attachmentsHelper.selectGallery(attachmentMediaActivityResultLauncher)
            AttachmentTypeSelectorView.Type.CONTACT -> attachmentsHelper.selectContact(attachmentContactActivityResultLauncher)
            AttachmentTypeSelectorView.Type.STICKER -> timelineViewModel.handle(RoomDetailAction.SelectStickerAttachment)
            AttachmentTypeSelectorView.Type.POLL -> navigator.openCreatePoll(requireContext(), roomId, null, PollMode.CREATE)
            AttachmentTypeSelectorView.Type.LOCATION -> {
                navigator
                        .openLocationSharing(
                                context = requireContext(),
                                roomId = roomId,
                                mode = LocationSharingMode.STATIC_SHARING,
                                initialLocationData = null,
                                locationOwnerId = session.myUserId
                        )
            }
            AttachmentTypeSelectorView.Type.VOICE_BROADCAST -> timelineViewModel.handle(RoomDetailAction.StartVoiceBroadcast)
        }
    }

    override fun onTypeSelected(type: AttachmentTypeSelectorView.Type) {
        if (checkPermissions(type.permissions, requireActivity(), typeSelectedActivityResultLauncher)) {
            launchAttachmentProcess(type)
        } else {
            attachmentsHelper.pendingType = type
        }
    }

    private val attachmentFileActivityResultLauncher = registerStartForActivityResult {
        if (it.resultCode == Activity.RESULT_OK) {
            attachmentsHelper.onFileResult(it.data)
        }
    }

    private val attachmentContactActivityResultLauncher = registerStartForActivityResult {
        if (it.resultCode == Activity.RESULT_OK) {
            attachmentsHelper.onContactResult(it.data)
        }
    }

    private val attachmentMediaActivityResultLauncher = registerStartForActivityResult {
        if (it.resultCode == Activity.RESULT_OK) {
            attachmentsHelper.onMediaResult(it.data)
        }
    }

    private val attachmentCameraActivityResultLauncher = registerStartForActivityResult {
        if (it.resultCode == Activity.RESULT_OK) {
            attachmentsHelper.onCameraResult()
        }
    }

    private val attachmentCameraVideoActivityResultLauncher = registerStartForActivityResult {
        if (it.resultCode == Activity.RESULT_OK) {
            attachmentsHelper.onCameraVideoResult()
        }
    }

    private fun cleanUpAfterPermissionNotGranted() {
        // Reset all pending data
        timelineViewModel.pendingAction = null
        attachmentsHelper.pendingType = null
    }

    private fun handleShareData() {
        when (val sharedData = withState(timelineViewModel) { it.sharedData }) {
            is SharedData.Text -> {
                messageComposerViewModel.handle(MessageComposerAction.OnTextChanged(sharedData.text))
                messageComposerViewModel.handle(MessageComposerAction.EnterRegularMode(fromSharing = true))
            }
            is SharedData.Attachments -> {
                // open share edition
                onContentAttachmentsReady(sharedData.attachmentData)
            }
            null -> Timber.v("No share data to process")
        }
    }

    @SuppressLint("SetTextI18n")
    private fun insertUserDisplayNameInTextEditor(userId: String) {
        val startToCompose = views.composerLayout.text.isNullOrBlank()

        if (startToCompose &&
                userId == session.myUserId) {
            // Empty composer, current user: start an emote
            views.composerLayout.views.composerEditText.setText("${Command.EMOTE.command} ")
            views.composerLayout.views.composerEditText.setSelection(Command.EMOTE.command.length + 1)
        } else {
            val roomMember = timelineViewModel.getMember(userId)
            val displayName = sanitizeDisplayName(roomMember?.displayName ?: userId)
            val pill = buildSpannedString {
                append(displayName)
                setSpan(
                        PillImageSpan(
                                glideRequests,
                                avatarRenderer,
                                requireContext(),
                                MatrixItem.UserItem(userId, displayName, roomMember?.avatarUrl)
                        )
                                .also { it.bind(views.composerLayout.views.composerEditText) },
                        0,
                        displayName.length,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                append(if (startToCompose) ": " else " ")
            }
            if (startToCompose) {
                if (displayName.startsWith("/")) {
                    // Ensure displayName will not be interpreted as a Slash command
                    views.composerLayout.views.composerEditText.append("\\")
                }
                views.composerLayout.views.composerEditText.append(pill)
            } else {
                views.composerLayout.views.composerEditText.text?.insert(views.composerLayout.views.composerEditText.selectionStart, pill)
            }
        }
        focusComposerAndShowKeyboard()
    }

    /**
     * Sanitize the display name.
     *
     * @param displayName the display name to sanitize
     * @return the sanitized display name
     */
    private fun sanitizeDisplayName(displayName: String): String {
        if (displayName.endsWith(ircPattern)) {
            return displayName.substring(0, displayName.length - ircPattern.length)
        }

        return displayName
    }

    /**
     * Returns the root thread event if we are in a thread room, otherwise returns null.
     */
    fun getRootThreadEventId(): String? = withState(timelineViewModel) { it.rootThreadEventId }

    /**
     * Returns true if the current room is a Thread room, false otherwise.
     */
    private fun isThreadTimeLine(): Boolean = withState(timelineViewModel) { it.isThreadTimeline() }

    /** Set whether the keyboard should disable personalized learning. */
    @RequiresApi(Build.VERSION_CODES.O)
    private fun EditText.setUseIncognitoKeyboard(useIncognitoKeyboard: Boolean) {
        imeOptions = if (useIncognitoKeyboard) {
            imeOptions or EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
        } else {
            imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING.inv()
        }
    }

    /** Set whether enter should send the message or add a new line. */
    private fun EditText.setSendMessageWithEnter(sendMessageWithEnter: Boolean) {
        if (sendMessageWithEnter) {
            inputType = inputType and EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE.inv()
            imeOptions = imeOptions or EditorInfo.IME_ACTION_SEND
        } else {
            inputType = inputType or EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE
            imeOptions = imeOptions and EditorInfo.IME_ACTION_SEND.inv()
        }
    }
}

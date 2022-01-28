/*
 * Copyright 2019 New Vector Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package im.vector.app.core.epoxy.bottomsheet

import android.text.method.MovementMethod
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import com.airbnb.epoxy.EpoxyAttribute
import com.airbnb.epoxy.EpoxyModelClass
import com.bumptech.glide.request.RequestOptions
import im.vector.app.R
import im.vector.app.core.epoxy.ClickListener
import im.vector.app.core.epoxy.VectorEpoxyHolder
import im.vector.app.core.epoxy.VectorEpoxyModel
import im.vector.app.core.epoxy.onClick
import im.vector.app.core.extensions.setTextOrHide
import im.vector.app.core.glide.GlideApp
import im.vector.app.features.displayname.getBestName
import im.vector.app.features.home.AvatarRenderer
import im.vector.app.features.home.room.detail.timeline.helper.LocationPinProvider
import im.vector.app.features.home.room.detail.timeline.item.BindingOptions
import im.vector.app.features.home.room.detail.timeline.tools.findPillsAndProcess
import im.vector.app.features.location.INITIAL_MAP_ZOOM_IN_TIMELINE
import im.vector.app.features.location.LocationData
import im.vector.app.features.location.getStaticMapUrl
import im.vector.app.features.media.ImageContentRenderer
import im.vector.lib.core.utils.epoxy.charsequence.EpoxyCharSequence
import org.matrix.android.sdk.api.util.MatrixItem

/**
 * A message preview for bottom sheet.
 */
@EpoxyModelClass(layout = R.layout.item_bottom_sheet_message_preview)
abstract class BottomSheetMessagePreviewItem : VectorEpoxyModel<BottomSheetMessagePreviewItem.Holder>() {

    @EpoxyAttribute
    lateinit var avatarRenderer: AvatarRenderer

    @EpoxyAttribute
    lateinit var matrixItem: MatrixItem

    @EpoxyAttribute
    lateinit var body: EpoxyCharSequence

    @EpoxyAttribute
    var bindingOptions: BindingOptions? = null

    @EpoxyAttribute
    var bodyDetails: EpoxyCharSequence? = null

    @EpoxyAttribute
    var imageContentRenderer: ImageContentRenderer? = null

    @EpoxyAttribute
    var data: ImageContentRenderer.Data? = null

    @EpoxyAttribute
    var time: String? = null

    @EpoxyAttribute
    var locationData: LocationData? = null

    @EpoxyAttribute
    var locationPinProvider: LocationPinProvider? = null

    @EpoxyAttribute
    var movementMethod: MovementMethod? = null

    @EpoxyAttribute(EpoxyAttribute.Option.DoNotHash)
    var userClicked: ClickListener? = null

    @EpoxyAttribute
    var mapWidth: Int = 1200

    @EpoxyAttribute
    var mapHeight: Int = 800

    override fun bind(holder: Holder) {
        super.bind(holder)
        avatarRenderer.render(matrixItem, holder.avatar)
        holder.avatar.onClick(userClicked)
        holder.sender.onClick(userClicked)
        holder.sender.setTextOrHide(matrixItem.getBestName())
        data?.let {
            imageContentRenderer?.render(it, ImageContentRenderer.Mode.THUMBNAIL, holder.imagePreview)
        }
        holder.imagePreview.isVisible = data != null
        holder.body.movementMethod = movementMethod
        holder.body.text = body.charSequence
        holder.bodyDetails.setTextOrHide(bodyDetails?.charSequence)
        body.charSequence.findPillsAndProcess(coroutineScope) { it.bind(holder.body) }
        holder.timestamp.setTextOrHide(time)

        if (locationData == null) {
            holder.body.isVisible = true
            holder.mapViewContainer.isVisible = false
        } else {
            holder.body.isVisible = false
            holder.mapViewContainer.isVisible = true
            GlideApp.with(holder.staticMapImageView)
                    .load(getStaticMapUrl(locationData!!.latitude, locationData!!.longitude, INITIAL_MAP_ZOOM_IN_TIMELINE, mapWidth, mapHeight))
                    .apply(RequestOptions.centerCropTransform())
                    .into(holder.staticMapImageView)

            locationPinProvider?.create(matrixItem.id) { pinDrawable ->
                GlideApp.with(holder.staticMapPinImageView)
                        .load(pinDrawable)
                        .into(holder.staticMapPinImageView)
            }
        }
    }

    override fun unbind(holder: Holder) {
        imageContentRenderer?.clear(holder.imagePreview)
        super.unbind(holder)
    }

    class Holder : VectorEpoxyHolder() {
        val avatar by bind<ImageView>(R.id.bottom_sheet_message_preview_avatar)
        val sender by bind<TextView>(R.id.bottom_sheet_message_preview_sender)
        val body by bind<TextView>(R.id.bottom_sheet_message_preview_body)
        val bodyDetails by bind<TextView>(R.id.bottom_sheet_message_preview_body_details)
        val timestamp by bind<TextView>(R.id.bottom_sheet_message_preview_timestamp)
        val imagePreview by bind<ImageView>(R.id.bottom_sheet_message_preview_image)
        val mapViewContainer by bind<FrameLayout>(R.id.mapViewContainer)
        val staticMapImageView by bind<ImageView>(R.id.staticMapImageView)
        val staticMapPinImageView by bind<ImageView>(R.id.staticMapPinImageView)
    }
}

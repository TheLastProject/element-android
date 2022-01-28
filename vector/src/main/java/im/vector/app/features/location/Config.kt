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

package im.vector.app.features.location

import im.vector.app.BuildConfig

const val MAP_STYLE_URL = "https://api.maptiler.com/maps/streets/style.json?key=${BuildConfig.mapTilerKey}"
private const val STATIC_MAP_IMAGE_URL = "https://api.maptiler.com/maps/basic/static/"

const val INITIAL_MAP_ZOOM_IN_PREVIEW = 15.0
const val INITIAL_MAP_ZOOM_IN_TIMELINE = 17.0
const val MIN_TIME_TO_UPDATE_LOCATION_MILLIS = 5 * 1_000L // every 5 seconds
const val MIN_DISTANCE_TO_UPDATE_LOCATION_METERS = 10f

fun getStaticMapUrl(latitude: Double,
                    longitude: Double,
                    zoom: Double,
                    width: Int,
                    height: Int): String {
    return buildString {
        append(STATIC_MAP_IMAGE_URL)
        append(longitude)
        append(",")
        append(latitude)
        append(",")
        append(zoom)
        append("/")
        append(width)
        append("x")
        append(height)
        append(".png?key=")
        append(BuildConfig.mapTilerKey)
        append("&attribution=bottomleft")
    }
}

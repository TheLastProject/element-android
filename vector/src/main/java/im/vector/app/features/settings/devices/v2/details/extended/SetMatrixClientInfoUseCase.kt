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

package im.vector.app.features.settings.devices.v2.details.extended

import MATRIX_CLIENT_INFO_KEY_PREFIX
import im.vector.app.core.di.ActiveSessionHolder
import org.matrix.android.sdk.api.session.events.model.toContent
import javax.inject.Inject

/**
 * This use case sets the account data event containing extended client info.
 */
class SetMatrixClientInfoUseCase @Inject constructor(
        private val activeSessionHolder: ActiveSessionHolder,
) {

    // TODO add unit tests
    suspend fun execute(clientInfo: MatrixClientInfoContent): Result<Unit> = runCatching {
        activeSessionHolder.getSafeActiveSession()
                ?.let { session ->
                    val deviceId = session.sessionParams.deviceId
                    if (deviceId != null) {
                        val type = MATRIX_CLIENT_INFO_KEY_PREFIX + deviceId
                        session.accountDataService()
                                .updateUserAccountData(type, clientInfo.toContent())
                    }
                }
    }
}

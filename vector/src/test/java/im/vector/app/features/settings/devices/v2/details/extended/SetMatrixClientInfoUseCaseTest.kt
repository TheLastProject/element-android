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
import im.vector.app.test.fakes.FakeActiveSessionHolder
import kotlinx.coroutines.test.runTest
import org.amshove.kluent.shouldBe
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeInstanceOf
import org.junit.Test
import org.matrix.android.sdk.api.session.events.model.toContent

private const val A_DEVICE_ID = "device-id"

class SetMatrixClientInfoUseCaseTest {

    private val fakeActiveSessionHolder = FakeActiveSessionHolder()

    private val setMatrixClientInfoUseCase = SetMatrixClientInfoUseCase(
            activeSessionHolder = fakeActiveSessionHolder.instance
    )

    @Test
    fun `given client info and no error when setting the info then account data is correctly updated`() = runTest {
        // Given
        val type = MATRIX_CLIENT_INFO_KEY_PREFIX + A_DEVICE_ID
        val clientInfo = givenClientInfo()
        val content = clientInfo.toContent()
        fakeActiveSessionHolder.fakeSession
                .givenSessionId(A_DEVICE_ID)
        fakeActiveSessionHolder.fakeSession
                .fakeSessionAccountDataService
                .givenUpdateUserAccountDataEventSucceeds()

        // When
        val result = setMatrixClientInfoUseCase.execute(clientInfo)

        // Then
        result.isSuccess shouldBe true
        fakeActiveSessionHolder.fakeSession
                .fakeSessionAccountDataService
                .verifyUpdateUserAccountDataEventSucceeds(type, content)
    }

    @Test
    fun `given client info and error during update when setting the info then result is failure`() = runTest {
        // Given
        val type = MATRIX_CLIENT_INFO_KEY_PREFIX + A_DEVICE_ID
        val clientInfo = givenClientInfo()
        val content = clientInfo.toContent()
        fakeActiveSessionHolder.fakeSession
                .givenSessionId(A_DEVICE_ID)
        val error = Exception()
        fakeActiveSessionHolder.fakeSession
                .fakeSessionAccountDataService
                .givenUpdateUserAccountDataEventFailsWithError(error)

        // When
        val result = setMatrixClientInfoUseCase.execute(clientInfo)

        // Then
        result.isFailure shouldBe true
        result.exceptionOrNull() shouldBeEqualTo error
        fakeActiveSessionHolder.fakeSession
                .fakeSessionAccountDataService
                .verifyUpdateUserAccountDataEventSucceeds(type, content)
    }

    @Test
    fun `given client info and null device id when setting the info then result is failure`() = runTest {
        // Given
        val type = MATRIX_CLIENT_INFO_KEY_PREFIX + A_DEVICE_ID
        val clientInfo = givenClientInfo()
        val content = clientInfo.toContent()
        fakeActiveSessionHolder.fakeSession
                .givenSessionId(null)

        // When
        val result = setMatrixClientInfoUseCase.execute(clientInfo)

        // Then
        result.isFailure shouldBe true
        result.exceptionOrNull() shouldBeInstanceOf NoDeviceIdError::class
        fakeActiveSessionHolder.fakeSession
                .fakeSessionAccountDataService
                .verifyUpdateUserAccountDataEventSucceeds(type, content, inverse = true)
    }

    private fun givenClientInfo() = MatrixClientInfoContent(
            name = "name",
            version = "version",
            url = null,
    )
}

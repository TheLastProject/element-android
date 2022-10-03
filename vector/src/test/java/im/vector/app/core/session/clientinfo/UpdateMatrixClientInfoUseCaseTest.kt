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

package im.vector.app.core.session.clientinfo

import im.vector.app.core.resources.BuildMeta
import im.vector.app.test.fakes.FakeAppNameProvider
import im.vector.app.test.fakes.FakeSession
import im.vector.app.test.testDispatcher
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

private const val AN_APP_NAME_1 = "app_name_1"
private const val AN_APP_NAME_2 = "app_name_2"
private const val A_VERSION_NAME_1 = "version_name_1"
private const val A_VERSION_NAME_2 = "version_name_2"
private const val A_SESSION_ID = "session-id"

class UpdateMatrixClientInfoUseCaseTest {

    private val fakeSession = FakeSession()
    private val fakeAppNameProvider = FakeAppNameProvider()
    private val fakeBuildMeta = mockk<BuildMeta>()
    private val getMatrixClientInfoUseCase = mockk<GetMatrixClientInfoUseCase>()
    private val setMatrixClientInfoUseCase = mockk<SetMatrixClientInfoUseCase>()

    private val updateMatrixClientInfoUseCase = UpdateMatrixClientInfoUseCase(
            appNameProvider = fakeAppNameProvider,
            buildMeta = fakeBuildMeta,
            getMatrixClientInfoUseCase = getMatrixClientInfoUseCase,
            setMatrixClientInfoUseCase = setMatrixClientInfoUseCase,
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `given current client info is different than the stored one when trying to update then new client info is set`() = runTest {
        // Given
        givenCurrentAppName(AN_APP_NAME_1)
        givenCurrentVersionName(A_VERSION_NAME_1)
        givenStoredClientInfo(AN_APP_NAME_2, A_VERSION_NAME_2)
        givenSetClientInfoSucceeds()
        val expectedClientInfoToSet = MatrixClientInfoContent(
                name = AN_APP_NAME_1,
                version = A_VERSION_NAME_1,
        )

        // When
        updateMatrixClientInfoUseCase.execute(fakeSession)

        // Then
        coVerify { setMatrixClientInfoUseCase.execute(fakeSession, match { it == expectedClientInfoToSet }) }
    }

    @Test
    fun `given current client info is equal to the stored one when trying to update then nothing is done`() = runTest {
        // Given
        givenCurrentAppName(AN_APP_NAME_1)
        givenCurrentVersionName(A_VERSION_NAME_1)
        givenStoredClientInfo(AN_APP_NAME_1, A_VERSION_NAME_1)

        // When
        updateMatrixClientInfoUseCase.execute(fakeSession)

        // Then
        coVerify(inverse = true) { setMatrixClientInfoUseCase.execute(fakeSession, any()) }
    }

    @Test
    fun `given no session id for current session when trying to update then nothing is done`() = runTest {
        // Given
        givenCurrentAppName(AN_APP_NAME_1)
        givenCurrentVersionName(A_VERSION_NAME_1)
        fakeSession.givenSessionId(null)

        // When
        updateMatrixClientInfoUseCase.execute(fakeSession)

        // Then
        coVerify(inverse = true) { setMatrixClientInfoUseCase.execute(fakeSession, any()) }
    }

    private fun givenCurrentAppName(appName: String) {
        fakeAppNameProvider.givenAppName(appName)
    }

    private fun givenCurrentVersionName(versionName: String) {
        every { fakeBuildMeta.versionName } returns versionName
    }

    private fun givenStoredClientInfo(appName: String, versionName: String) {
        fakeSession.givenSessionId(A_SESSION_ID)
        every { getMatrixClientInfoUseCase.execute(fakeSession, A_SESSION_ID) } returns MatrixClientInfoContent(
                name = appName,
                version = versionName,
        )
    }

    private fun givenSetClientInfoSucceeds() {
        coEvery { setMatrixClientInfoUseCase.execute(any(), any()) } returns Result.success(Unit)
    }
}

package org.simple.clinic.remoteconfig

import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigFetchThrottledException
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.whenever
import io.reactivex.Completable
import junitparams.JUnitParamsRunner
import junitparams.Parameters
import org.junit.Test
import org.junit.runner.RunWith
import org.simple.clinic.crash.CrashReporter
import java.net.SocketTimeoutException

@RunWith(JUnitParamsRunner::class)
class RemoteConfigSyncTest {

  @Test
  @Parameters(method = "errors expected during remote config sync")
  fun `all errors should be swallowed`(error: Throwable) {
    val crashReporter = mock<CrashReporter>()
    val configReader = mock<ConfigReader>()

    whenever(configReader.update()).thenReturn(Completable.error(error))

    val configSync = RemoteConfigSync(crashReporter, configReader)
    configSync.sync()
        .test()
        .await()
        .assertNoErrors()
  }

  @Suppress("unused")
  fun `errors expected during remote config sync`(): List<Throwable> {
    return listOf(
        AssertionError("You shall not pass"),
        SocketTimeoutException(),
        mock<FirebaseRemoteConfigFetchThrottledException>()
    )
  }
}

package org.simple.clinic

import com.google.common.truth.Truth.assertThat
import io.reactivex.subjects.PublishSubject
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.simple.clinic.analytics.Analytics
import org.simple.clinic.analytics.MockReporter
import org.simple.clinic.widgets.UiEvent

class ReportAnalyticsEventsTest {

  private data class UiEvent1(val prop: String) : UiEvent
  private data class UiEvent2(val prop: Int) : UiEvent

  private val uiEvents = PublishSubject.create<UiEvent>()

  private val reporter = MockReporter()

  private lateinit var controller: ReportAnalyticsEvents

  private val forwardedEvents = mutableListOf<UiEvent>()

  @Before
  fun setUp() {
    Analytics.addReporter(reporter)
    controller = ReportAnalyticsEvents()
    uiEvents.compose(controller).subscribe { forwardedEvents.add(it) }
  }

  @Test
  fun `whenever ui events are emitted, their class name must be emitted to the reporters`() {
    uiEvents.onNext(UiEvent1("1"))
    uiEvents.onNext(UiEvent2(2))
    uiEvents.onNext(UiEvent1("3"))

    val expected = listOf<Pair<String, Map<String, Any>>>(
        "UserInteraction" to mapOf("name" to "UiEvent1"),
        "UserInteraction" to mapOf("name" to "UiEvent2"),
        "UserInteraction" to mapOf("name" to "UiEvent1")
    )

    assertThat(reporter.receivedEvents).isEqualTo(expected)
  }

  @Test
  fun `whenever ui events are emitted, the events must be forwarded to the controller`() {
    uiEvents.onNext(UiEvent1("1"))
    uiEvents.onNext(UiEvent2(2))
    uiEvents.onNext(UiEvent1("3"))

    assertThat(forwardedEvents).isEqualTo(listOf(UiEvent1("1"), UiEvent2(2), UiEvent1("3")))
  }

  @After
  fun tearDown() {
    forwardedEvents.clear()
    reporter.clearReceivedEvents()
    Analytics.clearReporters()
  }
}

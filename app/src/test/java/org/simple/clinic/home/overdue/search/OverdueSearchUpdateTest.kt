package org.simple.clinic.home.overdue.search

import com.spotify.mobius.test.NextMatchers.hasEffects
import com.spotify.mobius.test.NextMatchers.hasModel
import com.spotify.mobius.test.NextMatchers.hasNoEffects
import com.spotify.mobius.test.NextMatchers.hasNoModel
import com.spotify.mobius.test.UpdateSpec
import com.spotify.mobius.test.UpdateSpec.assertThatNext
import org.junit.Test
import org.simple.clinic.home.overdue.search.OverdueSearchQueryValidator.Result.Valid

class OverdueSearchUpdateTest {

  private val updateSpec = UpdateSpec(OverdueSearchUpdate())
  private val defaultModel = OverdueSearchModel.create()

  @Test
  fun `when overdue search history is loaded, then update the model`() {
    val searchHistory = setOf(
        "Babri",
        "Ramesh"
    )

    updateSpec
        .given(defaultModel)
        .whenEvent(OverdueSearchHistoryLoaded(searchHistory))
        .then(assertThatNext(
            hasModel(defaultModel.overdueSearchHistoryLoaded(searchHistory)),
            hasNoEffects()
        ))
  }

  @Test
  fun `when overdue search query is changed, then validate the search query`() {
    val searchQuery = "Babri"
    updateSpec
        .given(defaultModel)
        .whenEvent(OverdueSearchQueryChanged(searchQuery))
        .then(assertThatNext(
            hasModel(defaultModel.overdueSearchQueryChanged(searchQuery)),
            hasEffects(ValidateOverdueSearchQuery(searchQuery))
        ))
  }

  @Test
  fun `when search query is validated and is valid, then update model and add query to search history`() {
    val searchQuery = "Babri"
    updateSpec
        .given(defaultModel)
        .whenEvent(OverdueSearchQueryValidated(Valid(searchQuery)))
        .then(assertThatNext(
            hasNoModel(),
            hasEffects(AddQueryToOverdueSearchHistory(searchQuery))
        ))
  }
}
package org.simple.clinic.search

import android.content.Context
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.util.AttributeSet
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.RelativeLayout
import com.jakewharton.rxbinding2.view.RxView
import com.jakewharton.rxbinding2.widget.RxTextView
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers.mainThread
import io.reactivex.rxkotlin.ofType
import io.reactivex.schedulers.Schedulers.io
import kotterknife.bindView
import org.simple.clinic.R
import org.simple.clinic.activity.TheActivity
import org.simple.clinic.newentry.PatientEntryScreen
import org.simple.clinic.patient.PatientSearchResult
import org.simple.clinic.router.screen.ActivityResult
import org.simple.clinic.router.screen.ScreenRouter
import org.simple.clinic.summary.PatientSummaryCaller.SEARCH
import org.simple.clinic.summary.PatientSummaryScreen
import org.simple.clinic.widgets.showKeyboard
import java.util.UUID
import javax.inject.Inject

class PatientSearchScreen(context: Context, attrs: AttributeSet) : RelativeLayout(context, attrs) {

  companion object {
    val KEY = PatientSearchScreenKey()
    const val REQCODE_AGE = 1
  }

  @Inject
  lateinit var screenRouter: ScreenRouter

  @Inject
  lateinit var activity: TheActivity

  @Inject
  lateinit var controller: PatientSearchScreenController

  private val backButton by bindView<ImageButton>(R.id.patientsearch_back)
  private val fullNameEditText by bindView<EditText>(R.id.patientsearch_fullname)
  private val ageFilterButton by bindView<Button>(R.id.patientsearch_age_filter_button)
  private val newPatientButton by bindView<Button>(R.id.patientsearch_new_patient)
  private val searchButton by bindView<View>(R.id.patientsearch_search)
  private val patientRecyclerView by bindView<RecyclerView>(R.id.patientsearch_recyclerview)
  private val resultsAdapter = PatientSearchResultsAdapter()

  override fun onFinishInflate() {
    super.onFinishInflate()
    if (isInEditMode) {
      return
    }
    TheActivity.component.inject(this)

    // TODO: this will be replaced by ACTION_NEXT once DOB and Age fields are also added.
    fullNameEditText.setOnEditorActionListener { _, _, _ ->
      // Swallow IME key presses because a search is triggered on every text change automatically.
      // Without this, the keyboard gets dismissed
      true
    }

    fullNameEditText.showKeyboard()

    patientRecyclerView.adapter = resultsAdapter
    patientRecyclerView.layoutManager = LinearLayoutManager(context)

    backButton.setOnClickListener {
      screenRouter.pop()
    }

    Observable
        .mergeArray(
            fullnameChanges(),
            ageFilterToolbarButtonClicks(),
            ageFilterTextChanges(),
            searchClicks(),
            newPatientButtonClicks(),
            searchResultClicks())
        .observeOn(io())
        .compose(controller)
        .observeOn(mainThread())
        .takeUntil(RxView.detaches(this))
        .subscribe { uiChange -> uiChange(this) }
  }

  private fun fullnameChanges() = RxTextView.textChanges(fullNameEditText)
      .map(CharSequence::toString)
      .map(::SearchQueryNameChanged)

  private fun ageFilterToolbarButtonClicks() = RxView.clicks(ageFilterButton)
      .map { SearchQueryAgeFilterClicked() }

  private fun ageFilterTextChanges() = screenRouter.streamScreenResults()
      .ofType<ActivityResult>()
      .filter { it.requestCode == REQCODE_AGE && it.succeeded() }
      .map { PatientSearchAgeFilterSheet.extractResult(it.data!!) }
      .startWith(SearchQueryAgeChanged(""))

  private fun newPatientButtonClicks() = RxView.clicks(newPatientButton)
      .map { CreateNewPatientClicked() }

  private fun searchClicks() =
      RxView
          .clicks(searchButton)
          .map { SearchClicked() }

  private fun searchResultClicks() = resultsAdapter.itemClicks

  fun showCreatePatientButton(shouldBeShown: Boolean) {
    if (shouldBeShown) {
      newPatientButton.visibility = View.VISIBLE
    } else {
      newPatientButton.visibility = View.GONE
    }
  }

  fun updatePatientSearchResults(patients: List<PatientSearchResult>) {
    resultsAdapter.updateAndNotifyChanges(patients)
  }

  fun openAgeFilterSheet(ageText: String) {
    activity.startActivityForResult(PatientSearchAgeFilterSheet.intent(context, ageText), REQCODE_AGE)
  }

  fun openPatientSummaryScreen(patientUuid: UUID) {
    screenRouter.push(PatientSummaryScreen.KEY(patientUuid, SEARCH))
  }

  fun openPersonalDetailsEntryScreen() {
    screenRouter.push(PatientEntryScreen.KEY)
  }
}

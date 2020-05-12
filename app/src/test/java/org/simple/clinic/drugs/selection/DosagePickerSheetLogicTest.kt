package org.simple.clinic.drugs.selection

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import com.nhaarman.mockitokotlin2.whenever
import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.rxkotlin.ofType
import io.reactivex.subjects.PublishSubject
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.simple.clinic.TestData
import org.simple.clinic.drugs.PrescriptionRepository
import org.simple.clinic.drugs.selection.dosage.DosageListItem
import org.simple.clinic.drugs.selection.dosage.DosagePickerEffect
import org.simple.clinic.drugs.selection.dosage.DosagePickerEffectHandler
import org.simple.clinic.drugs.selection.dosage.DosagePickerEvent
import org.simple.clinic.drugs.selection.dosage.DosagePickerInit
import org.simple.clinic.drugs.selection.dosage.DosagePickerModel
import org.simple.clinic.drugs.selection.dosage.DosagePickerUi
import org.simple.clinic.drugs.selection.dosage.DosagePickerUiActions
import org.simple.clinic.drugs.selection.dosage.DosagePickerUiRenderer
import org.simple.clinic.drugs.selection.dosage.DosagePickerUpdate
import org.simple.clinic.drugs.selection.dosage.DosageSelected
import org.simple.clinic.drugs.selection.dosage.NoneSelected
import org.simple.clinic.facility.FacilityRepository
import org.simple.clinic.protocol.ProtocolRepository
import org.simple.clinic.user.UserSession
import org.simple.clinic.util.None
import org.simple.clinic.util.Optional
import org.simple.clinic.util.RxErrorsRule
import org.simple.clinic.util.scheduler.TrampolineSchedulersProvider
import org.simple.clinic.util.toOptional
import org.simple.clinic.widgets.UiEvent
import org.simple.mobius.migration.MobiusTestFixture
import java.util.UUID

class DosagePickerSheetLogicTest {

  @get:Rule
  val rxErrorsRule = RxErrorsRule()

  private val protocolRepository = mock<ProtocolRepository>()
  private val userSession = mock<UserSession>()
  private val ui = mock<DosagePickerUi>()
  private val facilityRepository = mock<FacilityRepository>()
  private val prescriptionRepository = mock<PrescriptionRepository>()
  private val uiEvents = PublishSubject.create<UiEvent>()
  private val protocolUuid = UUID.fromString("812aa343-40dd-495c-97b7-9e058442f4f9")
  private val user = TestData.loggedInUser(uuid = UUID.fromString("4c1da47b-3a01-4e5b-afd2-2ca544495cae"))
  private val currentFacility = TestData.facility(
      uuid = UUID.fromString("105d4904-8d34-43e9-b208-edffd22a628f"),
      protocolUuid = protocolUuid
  )
  private val drugName = "Amlodipine"
  private val patientUuid = UUID.fromString("c4df02bd-d9d7-4120-9ff7-41f1e35aa8dd")

  private val uiRenderer = DosagePickerUiRenderer(ui)
  private val uiActions = mock<DosagePickerUiActions>()
  private val dosagePickerEffectHandler = DosagePickerEffectHandler(
      userSession = userSession,
      facilityRepository = facilityRepository,
      protocolRepository = protocolRepository,
      prescriptionRepository = prescriptionRepository,
      schedulers = TrampolineSchedulersProvider(),
      uiActions = uiActions
  )

  private lateinit var testFixture: MobiusTestFixture<DosagePickerModel, DosagePickerEvent, DosagePickerEffect>

  @After
  fun tearDown() {
    testFixture.dispose()
  }

  @Test
  fun `when sheet is created, list of dosages for that drug should be displayed`() {
    val protocolDrug1 = TestData.protocolDrug(uuid = UUID.fromString("22751f47-4a4a-4459-bb2b-e137ec47757e"), name = drugName, dosage = "5 mg")
    val protocolDrug2 = TestData.protocolDrug(uuid = UUID.fromString("29cf2315-d2b5-4a7f-b19a-34f998b82513"), name = drugName, dosage = "10 mg")

    whenever(protocolRepository.drugsByNameOrDefault(drugName, protocolUuid)).thenReturn(Observable.just(listOf(protocolDrug1, protocolDrug2)))

    setupController()

    verify(ui).populateDosageList(listOf(
        DosageListItem.WithDosage(protocolDrug1),
        DosageListItem.WithDosage(protocolDrug2),
        DosageListItem.WithoutDosage
    ))
    verifyNoMoreInteractions(ui)
    verifyZeroInteractions(uiActions)
  }

  @Test
  fun `when a dosage is selected, it should be saved as prescription`() {
    val drugName = "Amlodipine"
    val dosageSelected = TestData.protocolDrug(uuid = UUID.fromString("8e5d4f3a-aa57-4f1c-957b-1994a2c3f4a1"), name = drugName, dosage = "5 mg")

    whenever(protocolRepository.drugsByNameOrDefault(drugName, protocolUuid)).thenReturn(Observable.never())
    whenever(prescriptionRepository.savePrescription(patientUuid, dosageSelected, currentFacility)).thenReturn(Completable.complete())

    setupController()

    uiEvents.onNext(DosageSelected(dosageSelected))

    verify(prescriptionRepository).savePrescription(patientUuid, dosageSelected, currentFacility)
    verify(prescriptionRepository, never()).softDeletePrescription(any())
    verify(uiActions).close()
    verifyNoMoreInteractions(uiActions)
    verifyZeroInteractions(ui)
  }

  @Test
  fun `when a dosage is selected and a prescription exists for that drug, existing prescription should get deleted and new prescription should be saved`() {
    val drugName = "Amlodipine"
    val dosageSelected = TestData.protocolDrug(uuid = UUID.fromString("e3b78829-70fa-4165-90a0-cc0be2158e1e"), name = drugName, dosage = "5 mg")
    val existingPrescription = TestData.prescription(uuid = UUID.fromString("aaac79dc-ea26-4a52-b9e9-e26d3920371c"), name = drugName, dosage = "10 mg")

    whenever(protocolRepository.drugsByNameOrDefault(drugName, protocolUuid)).thenReturn(Observable.never())
    whenever(prescriptionRepository.savePrescription(patientUuid, dosageSelected, currentFacility)).thenReturn(Completable.complete())
    whenever(prescriptionRepository.softDeletePrescription(existingPrescription.uuid)).thenReturn(Completable.complete())

    setupController(existingPrescriptionUuid = existingPrescription.uuid.toOptional())

    uiEvents.onNext(DosageSelected(dosageSelected))

    verify(prescriptionRepository).softDeletePrescription(existingPrescription.uuid)
    verify(prescriptionRepository, times(1)).savePrescription(patientUuid, dosageSelected, currentFacility)
    verify(uiActions).close()
    verifyNoMoreInteractions(uiActions)
    verifyZeroInteractions(ui)
  }

  @Test
  fun `when none is selected, the existing prescription should be soft deleted`() {
    val drugName = "Amlodipine"
    val existingPrescription = TestData.prescription(uuid = UUID.fromString("8b18bd44-e163-4b94-96b2-07107f220847"), name = drugName, dosage = "10 mg")

    whenever(protocolRepository.drugsByNameOrDefault(drugName, protocolUuid)).thenReturn(Observable.never())
    whenever(prescriptionRepository.softDeletePrescription(existingPrescription.uuid)).thenReturn(Completable.complete())

    setupController(existingPrescriptionUuid = existingPrescription.uuid.toOptional())

    uiEvents.onNext(NoneSelected)

    verify(prescriptionRepository).softDeletePrescription(existingPrescription.uuid)
    verify(prescriptionRepository, never()).savePrescription(any(), any(), any())
    verify(uiActions).close()
    verifyNoMoreInteractions(uiActions)
    verifyZeroInteractions(ui)
  }

  private fun setupController(
      existingPrescriptionUuid: Optional<UUID> = None
  ) {
    whenever(userSession.loggedInUserImmediate()).thenReturn(user)
    whenever(facilityRepository.currentFacilityImmediate(user)).thenReturn(currentFacility)

    testFixture = MobiusTestFixture(
        events = uiEvents.ofType(),
        defaultModel = DosagePickerModel.create(
            patientUuid = patientUuid,
            drugName = drugName,
            existingPrescriptionUuid = existingPrescriptionUuid.toNullable()
        ),
        init = DosagePickerInit(),
        update = DosagePickerUpdate(),
        effectHandler = dosagePickerEffectHandler.build(),
        modelUpdateListener = uiRenderer::render
    )
    testFixture.start()
  }
}
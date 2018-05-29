package org.resolvetosavelives.red.newentry.search

import android.arch.persistence.room.Room
import android.support.test.InstrumentationRegistry
import android.support.test.runner.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.resolvetosavelives.red.AppDatabase
import org.threeten.bp.LocalDate

@RunWith(AndroidJUnit4::class)
class PatientRepositoryTest {

  lateinit var patientRepository: PatientRepository
  lateinit var database: AppDatabase

  @Before
  fun setUp() {
    val context = InstrumentationRegistry.getTargetContext()
    database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
    patientRepository = PatientRepository(database)
  }

  @Test
  fun createAnOngoingPatientEntry_thenSaveItCompletely() {
    val ongoingAddress = OngoingPatientEntry.Address("Noida", "Gautam Buddha Nagar", "Uttar Pradesh")
    val ongoingPersonalDetails = OngoingPatientEntry.PersonalDetails("Ashok Kumar", "09-10-1942", null, Gender.MALE)

    val personalDetailsOnlyEntry = OngoingPatientEntry(personalDetails = ongoingPersonalDetails)

    patientRepository.saveOngoingEntry(personalDetailsOnlyEntry)
        .andThen(patientRepository.ongoingEntry())
        .map { ongoingEntry -> ongoingEntry.copy(address = ongoingAddress) }
        .flatMapCompletable { withAddress -> patientRepository.saveOngoingEntry(withAddress) }
        .andThen(patientRepository.saveOngoingEntryAsPatient())
        .subscribe()

    val search1 = patientRepository.searchPatients("lakshman")
        .blockingFirst()

    assertThat(search1).isEmpty()

    val search2 = patientRepository.searchPatients("ashok")
        .blockingFirst()

    assertThat(search2).isNotEmpty()
  }

  @Test
  fun createAnOngoingPatientEntry_withNullDateOfBirth_andAgeFilledIn_thenSaveItCompletely() {
    val ongoingAddress = OngoingPatientEntry.Address("Noida", "Gautam Buddha Nagar", "Uttar Pradesh")
    val ongoingPersonalDetails = OngoingPatientEntry.PersonalDetails("Ashok Kumar", null, "42", Gender.MALE)

    val personalDetailsOnlyEntry = OngoingPatientEntry(personalDetails = ongoingPersonalDetails)

    patientRepository.saveOngoingEntry(personalDetailsOnlyEntry)
        .andThen(patientRepository.ongoingEntry())
        .map { ongoingEntry -> ongoingEntry.copy(address = ongoingAddress) }
        .flatMapCompletable { withAddress -> patientRepository.saveOngoingEntry(withAddress) }
        .andThen(patientRepository.saveOngoingEntryAsPatient())
        .subscribe()

    val search1 = patientRepository.searchPatients("lakshman")
        .blockingFirst()

    assertThat(search1).isEmpty()

    val search2 = patientRepository.searchPatients("ashok")
        .blockingFirst()

    val patient = search2[0]
    assertThat(patient.fullName).isEqualTo("Ashok Kumar")
    assertThat(patient.dateOfBirth).isNull()
    assertThat(patient.ageWhenCreated).isEqualTo(42)
  }

  @Test
  fun createAnOngoingPatientEntry_withNullDateOfBirth_andNullAgeWhenCreated_thenFail() {
    val ongoingAddress = OngoingPatientEntry.Address("Noida", "Gautam Buddha Nagar", "Uttar Pradesh")
    val ongoingPersonalDetails = OngoingPatientEntry.PersonalDetails("Ashok Kumar", null, null, Gender.MALE)

    val ongoingPatientEntry = OngoingPatientEntry(personalDetails = ongoingPersonalDetails, address = ongoingAddress)

    patientRepository.saveOngoingEntry(ongoingPatientEntry)
        .andThen(patientRepository.saveOngoingEntryAsPatient())
        .test()
        .assertError(AssertionError::class.java)
  }

  @Test
  fun patientWithAddressSearch_shouldReturn_correctlyCombinedObject() {
    val ongoingAddress = OngoingPatientEntry.Address("Arambol", "Arambol", "Goa")
    val ongoingPersonalDetails = OngoingPatientEntry.PersonalDetails("Asha Kumar", "15-08-1947", null, Gender.FEMALE)

    val personalDetailsOnlyEntry = OngoingPatientEntry(personalDetails = ongoingPersonalDetails)

    patientRepository.saveOngoingEntry(personalDetailsOnlyEntry)
        .andThen(patientRepository.ongoingEntry())
        .map { ongoingEntry -> ongoingEntry.copy(address = ongoingAddress) }
        .flatMapCompletable { withAddress -> patientRepository.saveOngoingEntry(withAddress) }
        .andThen(patientRepository.saveOngoingEntryAsPatient())
        .subscribe()

    val results = patientRepository.searchPatientsWithAddresses("kumar")
        .blockingFirst()

    val patientWithAddress = results[0]

    assertThat(patientWithAddress.fullName).isEqualTo("Asha Kumar")
    assertThat(patientWithAddress.gender).isEqualTo(Gender.FEMALE)
    assertThat(patientWithAddress.dateOfBirth).isEqualTo(LocalDate.parse("1947-08-15"))
    assertThat(patientWithAddress.createdAt).isGreaterThan(patientWithAddress.address.createdAt)
    assertThat(patientWithAddress.address.colonyOrVillage).isEqualTo("Arambol")
    assertThat(patientWithAddress.address.state).isEqualTo("Goa")
    assertThat(patientWithAddress.address.syncStatus).isEqualTo(SyncStatus.PENDING)
  }

  @After
  fun tearDown() {
    database.clearAllTables()
    database.close()
  }
}

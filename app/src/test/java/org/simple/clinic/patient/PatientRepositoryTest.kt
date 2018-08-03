package org.simple.clinic.patient

import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.argThat
import com.nhaarman.mockito_kotlin.eq
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.never
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.whenever
import io.reactivex.Flowable
import io.reactivex.Single
import junitparams.JUnitParamsRunner
import junitparams.Parameters
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.simple.clinic.AppDatabase
import org.simple.clinic.newentry.DateOfBirthFormatValidator
import org.simple.clinic.patient.sync.PatientPayload
import org.simple.clinic.patient.sync.PatientPhoneNumberPayload
import org.threeten.bp.Instant
import java.util.UUID

@RunWith(JUnitParamsRunner::class)
class PatientRepositoryTest {

  private lateinit var repository: PatientRepository
  private lateinit var database: AppDatabase
  private lateinit var mockPatientSearchResultDao: PatientSearchResult.RoomDao

  private val mockPatientDao = mock<Patient.RoomDao>()
  private val mockPatientAddressDao = mock<PatientAddress.RoomDao>()
  private val mockPatientPhoneNumberDao = mock<PatientPhoneNumber.RoomDao>()
  private val mockFuzzyPatientSearchDao = mock<PatientSearchResult.FuzzyPatientSearchDao>()
  private val dobValidator = mock<DateOfBirthFormatValidator>()

  @Before
  fun setUp() {
    database = mock()
    mockPatientSearchResultDao = mock()
    repository = PatientRepository(database, dobValidator)
  }

  @Test
  @Parameters(value = [
    "Name, Name, Name",
    "Name Surname, Name Surname, NameSurname",
    "Name Surname, Name   Surname , NameSurname",
    "Old Name, Name-Surname, NameSurname",
    "Name, Name.Middle-Surname, NameMiddleSurname"
  ])
  fun `when merging patients with server records, update the searchable name of the patient to the full name stripped of all spaces and punctuation`(
      localFullName: String,
      remoteFullName: String,
      expectedSearchableName: String
  ) {
    whenever(database.patientDao()).thenReturn(mockPatientDao)
    whenever(database.addressDao()).thenReturn(mockPatientAddressDao)

    val patientUUID = UUID.randomUUID()
    val addressUUID = UUID.randomUUID()

    val localPatientCopy = PatientMocker.patient(uuid = patientUUID, fullName = localFullName, addressUuid = addressUUID, syncStatus = SyncStatus.DONE)
    whenever(mockPatientDao.getOne(patientUUID)).thenReturn(localPatientCopy)

    val serverAddress = PatientMocker.address(addressUUID).toPayload()
    val serverPatientWithoutPhone = PatientPayload(
        uuid = patientUUID,
        fullName = remoteFullName,
        gender = mock(),
        dateOfBirth = mock(),
        age = 0,
        ageUpdatedAt = mock(),
        status = mock(),
        createdAt = mock(),
        updatedAt = mock(),
        address = serverAddress,
        phoneNumbers = null)

    repository.mergeWithLocalData(listOf(serverPatientWithoutPhone)).blockingAwait()

    verify(mockPatientDao).save(argThat<List<Patient>> { first().searchableName == expectedSearchableName })
  }

  @Test
  @Parameters(value = [
    "Name, Name",
    "Name   Surname, NameSurname",
    "Name Middle.Surname, NameMiddleSurname",
    "Name \tSurname, NameSurname"
  ])
  fun `when searching for patients without age bound, strip the search query of any whitespace or punctuation`(
      query: String,
      expectedSearchQuery: String
  ) {
    whenever(mockFuzzyPatientSearchDao.searchForPatientsWithNameLike(any())).thenReturn(Single.just(emptyList()))
    whenever(mockPatientSearchResultDao.search(any())).thenReturn(Flowable.just(emptyList()))
    whenever(database.patientSearchDao()).thenReturn(mockPatientSearchResultDao)
    whenever(database.fuzzyPatientSearchDao()).thenReturn(mockFuzzyPatientSearchDao)
    whenever(database.addressDao()).thenReturn(mockPatientAddressDao)

    repository.searchPatientsAndPhoneNumbers(query).blockingFirst()

    verify(mockPatientSearchResultDao).search(eq(expectedSearchQuery))
  }

  @Test
  fun `when searching for patients with age bound, strip the search query of any whitespace or punctuation`() {
    whenever(mockPatientSearchResultDao.search(any(), any(), any())).thenReturn(Flowable.just(emptyList()))
    whenever(mockFuzzyPatientSearchDao.searchForPatientsWithNameLikeAndAgeWithin(any(), any(), any())).thenReturn(Single.just(emptyList()))
    whenever(database.patientSearchDao()).thenReturn(mockPatientSearchResultDao)
    whenever(database.fuzzyPatientSearchDao()).thenReturn(mockFuzzyPatientSearchDao)
    whenever(database.addressDao()).thenReturn(mockPatientAddressDao)

    repository.searchPatientsAndPhoneNumbers("Name   Surname", 40).blockingFirst()

    verify(mockPatientSearchResultDao).search(eq("NameSurname"), any(), any())
  }

  @Test
  @Parameters(value = [
    "123, 123",
    "123 456, 123456",
    "234 \t1, 2341"
  ])
  fun `when searching for query with phone number, do not remove any of the digits`(
      query: String,
      expectedSearchQuery: String
  ) {
    whenever(mockPatientSearchResultDao.search(any())).thenReturn(Flowable.just(emptyList()))
    whenever(database.addressDao()).thenReturn(mockPatientAddressDao)
    whenever(database.patientSearchDao()).thenReturn(mockPatientSearchResultDao)

    repository.searchPatientsAndPhoneNumbers(query).blockingFirst()

    verify(mockPatientSearchResultDao).search(eq(expectedSearchQuery))
  }

  @Test
  fun `when searching for query with phone number, do not query the fuzzy search dao`() {
    whenever(mockPatientSearchResultDao.search(any())).thenReturn(Flowable.just(emptyList()))
    whenever(mockFuzzyPatientSearchDao.searchForPatientsWithNameLike(any())).thenReturn(Single.just(emptyList()))
    whenever(database.addressDao()).thenReturn(mockPatientAddressDao)
    whenever(database.patientSearchDao()).thenReturn(mockPatientSearchResultDao)
    whenever(database.fuzzyPatientSearchDao()).thenReturn(mockFuzzyPatientSearchDao)

    repository.searchPatientsAndPhoneNumbers("123").blockingFirst()

    verify(mockFuzzyPatientSearchDao, never()).searchForPatientsWithNameLike(any())
    verify(mockFuzzyPatientSearchDao, never()).searchForPatientsWithNameLikeAndAgeWithin(any(), any(), any())
  }

  @Test
  fun `when the fuzzy patient search returns results, they must be at the head of the final results list`() {
    val patientSearchResultTemplate = PatientSearchResult(
        uuid = UUID.randomUUID(),
        fullName = "",
        gender = Gender.FEMALE,
        dateOfBirth = null,
        age = null,
        status = PatientStatus.ACTIVE,
        createdAt = Instant.now(),
        updatedAt = Instant.now(),
        syncStatus = SyncStatus.DONE,
        address = PatientAddress(UUID.randomUUID(), null, "", "", null, createdAt = Instant.now(), updatedAt = Instant.now()),
        phoneUuid = null,
        phoneNumber = null,
        phoneType = null,
        phoneActive = null,
        phoneCreatedAt = null,
        phoneUpdatedAt = null
    )

    val actualResults = listOf(patientSearchResultTemplate.copy(uuid = UUID.randomUUID()), patientSearchResultTemplate.copy(UUID.randomUUID()))
    val fuzzyResults = listOf(patientSearchResultTemplate.copy(uuid = UUID.randomUUID()))
    whenever(mockPatientSearchResultDao.search(any())).thenReturn(Flowable.just(actualResults))
    whenever(mockFuzzyPatientSearchDao.searchForPatientsWithNameLike(any())).thenReturn(Single.just(fuzzyResults))
    whenever(database.patientSearchDao()).thenReturn(mockPatientSearchResultDao)
    whenever(database.fuzzyPatientSearchDao()).thenReturn(mockFuzzyPatientSearchDao)

    repository.searchPatientsAndPhoneNumbers("test")
        .firstOrError()
        .test()
        .assertValue(fuzzyResults + actualResults)
  }

  @Test
  fun `when the fuzzy patient search returns results, they must not contain any duplicates`() {
    val patientSearchResultTemplate = PatientSearchResult(
        uuid = UUID.randomUUID(),
        fullName = "",
        gender = Gender.FEMALE,
        dateOfBirth = null,
        age = null,
        status = PatientStatus.ACTIVE,
        createdAt = Instant.now(),
        updatedAt = Instant.now(),
        syncStatus = SyncStatus.DONE,
        address = PatientAddress(UUID.randomUUID(), null, "", "", null, createdAt = Instant.now(), updatedAt = Instant.now()),
        phoneUuid = null,
        phoneNumber = null,
        phoneType = null,
        phoneActive = null,
        phoneCreatedAt = null,
        phoneUpdatedAt = null
    )

    val actualResults = listOf(patientSearchResultTemplate.copy(uuid = UUID.randomUUID()), patientSearchResultTemplate.copy(UUID.randomUUID()))
    val fuzzyResults = listOf(patientSearchResultTemplate.copy(uuid = UUID.randomUUID()), actualResults[0])

    val expected = listOf(fuzzyResults[0], fuzzyResults[1], actualResults[1])
    whenever(mockPatientSearchResultDao.search(any())).thenReturn(Flowable.just(actualResults))
    whenever(mockFuzzyPatientSearchDao.searchForPatientsWithNameLike(any())).thenReturn(Single.just(fuzzyResults))
    whenever(database.patientSearchDao()).thenReturn(mockPatientSearchResultDao)
    whenever(database.fuzzyPatientSearchDao()).thenReturn(mockFuzzyPatientSearchDao)

    repository.searchPatientsAndPhoneNumbers("test")
        .firstOrError()
        .test()
        .assertValue(expected)
  }

  @Test
  @Parameters(value = [
    "PENDING, false",
    "IN_FLIGHT, false",
    "INVALID, true",
    "DONE, true"])
  fun `when merging patients with server records, ignore records that already exist locally and are syncing or pending-sync`(
      syncStatusOfLocalCopy: SyncStatus,
      serverRecordExpectedToBeSaved: Boolean
  ) {
    whenever(database.patientDao()).thenReturn(mockPatientDao)
    whenever(database.addressDao()).thenReturn(mockPatientAddressDao)

    val patientUuid = UUID.randomUUID()
    val addressUuid = UUID.randomUUID()

    val localPatientCopy = PatientMocker.patient(uuid = patientUuid, addressUuid = addressUuid, syncStatus = syncStatusOfLocalCopy)
    whenever(mockPatientDao.getOne(patientUuid)).thenReturn(localPatientCopy)

    val serverAddress = PatientMocker.address(uuid = addressUuid).toPayload()
    val serverPatientWithoutPhone = PatientPayload(
        uuid = patientUuid,
        fullName = "name",
        gender = mock(),
        dateOfBirth = mock(),
        age = 0,
        ageUpdatedAt = mock(),
        status = mock(),
        createdAt = mock(),
        updatedAt = mock(),
        address = serverAddress,
        phoneNumbers = null)

    repository.mergeWithLocalData(listOf(serverPatientWithoutPhone)).blockingAwait()

    if (serverRecordExpectedToBeSaved) {
      verify(mockPatientDao).save(argThat<List<Patient>> { isNotEmpty() })
      verify(mockPatientAddressDao).save(argThat<List<PatientAddress>> { isNotEmpty() })

    } else {
      verify(mockPatientDao).save(argThat<List<Patient>> { isEmpty() })
      verify(mockPatientAddressDao).save(argThat<List<PatientAddress>> { isEmpty() })
    }
  }

  @Test
  @Parameters(value = [
    "PENDING, false",
    "IN_FLIGHT, false",
    "INVALID, true",
    "DONE, true"])
  fun `that already exist locally and are syncing or pending-sync`(
      syncStatusOfLocalCopy: SyncStatus,
      serverRecordExpectedToBeSaved: Boolean
  ) {
    whenever(database.patientDao()).thenReturn(mockPatientDao)
    whenever(database.addressDao()).thenReturn(mockPatientAddressDao)
    whenever(database.phoneNumberDao()).thenReturn(mockPatientPhoneNumberDao)

    val patientUuid = UUID.randomUUID()
    val addressUuid = UUID.randomUUID()

    val localPatientCopy = PatientMocker.patient(uuid = patientUuid, addressUuid = addressUuid, syncStatus = syncStatusOfLocalCopy)
    whenever(mockPatientDao.getOne(patientUuid)).thenReturn(localPatientCopy)

    val serverAddress = PatientMocker.address(uuid = addressUuid).toPayload()
    val serverPatientWithPhone = PatientPayload(
        uuid = patientUuid,
        fullName = "name",
        gender = mock(),
        dateOfBirth = mock(),
        age = 0,
        ageUpdatedAt = mock(),
        status = mock(),
        createdAt = mock(),
        updatedAt = mock(),

        address = serverAddress,
        phoneNumbers = listOf(PatientPhoneNumberPayload(UUID.randomUUID(), "1232", mock(), false, mock(), mock())))

    repository.mergeWithLocalData(listOf(serverPatientWithPhone)).blockingAwait()

    if (serverRecordExpectedToBeSaved) {
      verify(mockPatientAddressDao).save(argThat<List<PatientAddress>> { isNotEmpty() })
      verify(mockPatientDao).save(argThat<List<Patient>> { isNotEmpty() })
      verify(mockPatientPhoneNumberDao).save(argThat { isNotEmpty() })

    } else {
      verify(mockPatientAddressDao).save(argThat<List<PatientAddress>> { isEmpty() })
      verify(mockPatientDao).save(argThat<List<Patient>> { isEmpty() })
      verify(mockPatientPhoneNumberDao, never()).save(any())
    }
  }
}

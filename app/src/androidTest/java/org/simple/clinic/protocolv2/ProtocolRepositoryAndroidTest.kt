package org.simple.clinic.protocolv2

import android.support.test.runner.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.reactivex.Single
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.simple.clinic.AppDatabase
import org.simple.clinic.AuthenticationRule
import org.simple.clinic.TestClinicApp
import org.simple.clinic.TestData
import org.simple.clinic.facility.FacilityRepository
import org.simple.clinic.facility.FacilitySyncApiV1
import org.simple.clinic.user.UserSession
import java.util.UUID
import javax.inject.Inject

@RunWith(AndroidJUnit4::class)
class ProtocolRepositoryAndroidTest {

  @Inject
  lateinit var database: AppDatabase

  @Inject
  lateinit var protocolRepository: ProtocolRepository

  @Inject
  lateinit var facilityRepository: FacilityRepository

  @Inject
  lateinit var userSession: UserSession

  @Inject
  lateinit var configProvider: Single<ProtocolConfig>

  @Inject
  lateinit var testData: TestData

  @Inject
  lateinit var facilitySyncApi: FacilitySyncApiV1

  @get:Rule
  val authenticationRule = AuthenticationRule()

  @Before
  fun setUp() {
    TestClinicApp.appComponent().inject(this)
  }

  // TODO: Make protocolUuid non-null in FacilityPayload when the feature is enabled and this test can be removed.
  @Test
  fun protocol_ID_in_payload_should_not_be_empty_when_feature_is_enabled() {
    val config = configProvider.blockingGet()
    if (config.isProtocolDrugSyncEnabled) {
      val facilities = facilitySyncApi.pull(recordsToPull = 100, lastPullToken = null).blockingGet()
      val facilityWithoutProtocolId = facilities.payloads.find { it.protocolUuid == null }
      assertThat(facilityWithoutProtocolId).isNull()
    }
  }

  // TODO: Remove user auth when this test is removed.
  @Test
  fun when_feature_is_disabled_then_default_drugs_should_be_returned() {
    val config = configProvider.blockingGet()
    if (config.isProtocolDrugSyncEnabled.not()) {
      val randomProtocolId = UUID.randomUUID()
      val drugs = protocolRepository.drugsForProtocol(randomProtocolId).blockingFirst()
      assertThat(drugs).isEqualTo(protocolRepository.defaultProtocolDrugs())
    }
  }

  @Test
  fun when_protocols_are_not_present_in_database_then_default_drugs_should_be_returned() {
    database.clearAllTables()

    val randomProtocolId = UUID.randomUUID()
    val drugs = protocolRepository.drugsForProtocol(randomProtocolId).blockingFirst()
    assertThat(drugs).isEqualTo(protocolRepository.defaultProtocolDrugs())
  }

  @Test
  fun drugs_are_present_but_feature_is_disabled_then_default_drugs_should_be_returned() {
    val config = configProvider.blockingGet()
    if (config.isProtocolDrugSyncEnabled.not()) {
      val protocol1 = testData.protocol()
      database.protocolDao().save(listOf(protocol1))

      val drug1 = testData.protocolDrug(protocolUuid = protocol1.uuid)
      database.protocolDrugDao().save(listOf(drug1))

      val drugs = protocolRepository.drugsForProtocol(protocol1.uuid).blockingFirst()
      assertThat(drugs).isEqualTo(protocolRepository.defaultProtocolDrugs())
    }
  }

  @Test
  fun when_drugs_are_not_present_for_a_specific_protocol_then_default_values_should_be_returned() {
    database.clearAllTables()

    val protocol1 = testData.protocol()
    val protocol2 = testData.protocol()
    database.protocolDao().save(listOf(protocol1, protocol2))

    val drug1 = testData.protocolDrug(protocolUuid = protocol1.uuid)
    val drug2 = testData.protocolDrug(protocolUuid = protocol1.uuid)
    database.protocolDrugDao().save(listOf(drug1, drug2))

    val drugsForProtocol2 = protocolRepository.drugsForProtocol(protocol2.uuid).blockingFirst()
    assertThat(drugsForProtocol2).isEqualTo(protocolRepository.defaultProtocolDrugs())
  }

  @Test
  fun when_protocols_are_present_in_database_then_they_should_be_returned() {
    val config = configProvider.blockingGet()

    if (config.isProtocolDrugSyncEnabled) {
      database.clearAllTables()

      val currentProtocolId = UUID.randomUUID()

      val protocol1 = testData.protocol(uuid = currentProtocolId)
      val protocol2 = testData.protocol()
      database.protocolDao().save(listOf(protocol1, protocol2))

      val drug1 = testData.protocolDrug(name = "Amlodipine", protocolUuid = protocol1.uuid)
      val drug2 = testData.protocolDrug(name = "Telmisartan", protocolUuid = protocol1.uuid)
      val drug3 = testData.protocolDrug(name = "Amlodipine", protocolUuid = protocol2.uuid)
      database.protocolDrugDao().save(listOf(drug1, drug2, drug3))

      val drugsForCurrentProtocol = protocolRepository.drugsForProtocol(currentProtocolId).blockingFirst()
      assertThat(drugsForCurrentProtocol).containsAllOf(
          ProtocolDrugAndDosages(drugName = "Amlodipine", drugs = listOf(drug1)),
          ProtocolDrugAndDosages(drugName = "Telmisartan", drugs = listOf(drug2)))
      assertThat(drugsForCurrentProtocol).doesNotContain(drug3)
      assertThat(drugsForCurrentProtocol).hasSize(2)
    }
  }

  @Test
  fun protocols_drugs_should_be_grouped_by_names() {
    val config = configProvider.blockingGet()

    if (config.isProtocolDrugSyncEnabled) {
      database.clearAllTables()

      val protocol1 = testData.protocol()
      val protocol2 = testData.protocol()
      val protocols = listOf(protocol1, protocol2)
      database.protocolDao().save(protocols)

      val amlodipine5mg = testData.protocolDrug(name = "Amlodipine", dosage = "5mg", protocolUuid = protocol1.uuid)
      val amlodipine10mg = testData.protocolDrug(name = "Amlodipine", dosage = "10mg", protocolUuid = protocol1.uuid)
      val telmisartan40mg = testData.protocolDrug(name = "Telmisartan", dosage = "40mg", protocolUuid = protocol1.uuid)
      val telmisartan80mg = testData.protocolDrug(name = "Telmisartan", dosage = "80mg", protocolUuid = protocol2.uuid)
      database.protocolDrugDao().save(listOf(amlodipine5mg, amlodipine10mg, telmisartan40mg, telmisartan80mg))

      val drugsForProtocol1 = protocolRepository.drugsForProtocol(protocol1.uuid).blockingFirst()
      assertThat(drugsForProtocol1).containsAllOf(
          ProtocolDrugAndDosages(drugName = "Amlodipine", drugs = listOf(amlodipine5mg, amlodipine10mg)),
          ProtocolDrugAndDosages(drugName = "Telmisartan", drugs = listOf(telmisartan40mg)))
    }
  }
}

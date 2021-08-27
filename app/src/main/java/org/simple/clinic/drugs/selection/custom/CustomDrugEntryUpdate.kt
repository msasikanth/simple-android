package org.simple.clinic.drugs.selection.custom

import com.spotify.mobius.Next
import com.spotify.mobius.Next.next
import com.spotify.mobius.Next.noChange
import com.spotify.mobius.Update
import org.simple.clinic.drugs.PrescribedDrug
import org.simple.clinic.drugs.search.Drug
import org.simple.clinic.drugs.search.DrugFrequency
import org.simple.clinic.mobius.dispatch
import org.simple.clinic.mobius.next
import java.util.UUID

class CustomDrugEntryUpdate : Update<CustomDrugEntryModel, CustomDrugEntryEvent, CustomDrugEntryEffect> {
  override fun update(
      model: CustomDrugEntryModel,
      event: CustomDrugEntryEvent
  ): Next<CustomDrugEntryModel, CustomDrugEntryEffect> {
    return when (event) {
      is DosageEdited -> next(model.dosageEdited(event.dosage))
      is DosageFocusChanged -> next(model.dosageFocusChanged(event.hasFocus))
      is EditFrequencyClicked -> dispatch(ShowEditFrequencyDialog(model.frequency))
      is FrequencyEdited -> next(model.frequencyEdited(event.frequency), SetDrugFrequency(event.frequency))
      is AddMedicineButtonClicked -> createOrUpdatePrescriptionEntry(model, event.patientUuid)
      is CustomDrugSaved, ExistingDrugRemoved -> dispatch(CloseSheetAndGoToEditMedicineScreen)
      is PrescribedDrugFetched -> prescriptionFetched(model, event.prescription)
      is RemoveDrugButtonClicked -> {
        val update = model.openAs as OpenAs.Update
        dispatch(RemoveDrugFromPrescription(update.prescribedDrugUuid))
      }
      is DrugFetched -> drugFetched(model, event.drug)
      is DrugFrequencyChoiceItemsLoaded -> noChange()
    }
  }

  private fun drugFetched(
      model: CustomDrugEntryModel,
      drug: Drug
  ): Next<CustomDrugEntryModel, CustomDrugEntryEffect> {
    val updatedModel = model.drugNameLoaded(drug.name).dosageEdited(drug.dosage).frequencyEdited(drug.frequency).rxNormCodeEdited(drug.rxNormCode)

    return next(updatedModel, SetDrugFrequency(drug.frequency), SetDrugDosage(drug.dosage))
  }

  private fun prescriptionFetched(
      model: CustomDrugEntryModel,
      prescription: PrescribedDrug
  ): Next<CustomDrugEntryModel, CustomDrugEntryEffect> {
    val frequency = DrugFrequency.fromMedicineFrequency(prescription.frequency)

    val updatedModel = model.drugNameLoaded(prescription.name).dosageEdited(prescription.dosage).frequencyEdited(frequency).rxNormCodeEdited(prescription.rxNormCode)

    return next(updatedModel, SetDrugFrequency(frequency), SetDrugDosage(prescription.dosage))
  }

  private fun createOrUpdatePrescriptionEntry(
      model: CustomDrugEntryModel,
      patientUuid: UUID
  ): Next<CustomDrugEntryModel, CustomDrugEntryEffect> {
    return when (model.openAs) {
      is OpenAs.New.FromDrugList -> dispatch(SaveCustomDrugToPrescription(patientUuid, model.drugName!!, model.dosage, model.rxNormCode, model.frequency))
      is OpenAs.New.FromDrugName -> dispatch(SaveCustomDrugToPrescription(patientUuid, model.openAs.drugName, model.dosage, null, model.frequency))
      is OpenAs.Update -> dispatch(UpdatePrescription(patientUuid, model.openAs.prescribedDrugUuid, model.drugName!!, model.dosage, model.rxNormCode, model.frequency))
    }
  }
}
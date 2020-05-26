package org.simple.clinic.editpatient.deletepatient

import com.spotify.mobius.Next
import com.spotify.mobius.Next.noChange
import com.spotify.mobius.Update
import org.simple.clinic.mobius.dispatch
import org.simple.clinic.patient.DeletedReason

class DeletePatientUpdate : Update<DeletePatientModel, DeletePatientEvent, DeletePatientEffect> {
  override fun update(model: DeletePatientModel, event: DeletePatientEvent): Next<DeletePatientModel, DeletePatientEffect> {
    return when (event) {
      is PatientDeleteReasonClicked -> patientDeleteReasonClicked(model, event)
      PatientDeleted -> noChange()
      PatientMarkedAsDead -> noChange()
    }
  }

  private fun patientDeleteReasonClicked(
      model: DeletePatientModel,
      event: PatientDeleteReasonClicked
  ): Next<DeletePatientModel, DeletePatientEffect> {
    val effect = when(event.patientDeleteReason) {
      PatientDeleteReason.Duplicate -> ShowConfirmDeleteDialog(model.patientName!!, DeletedReason.Duplicate)
      PatientDeleteReason.AccidentalRegistration -> ShowConfirmDeleteDialog(model.patientName!!, DeletedReason.AccidentalRegistration)
      PatientDeleteReason.Died -> ShowConfirmDiedDialog(model.patientName!!)
    }

    return dispatch(effect)
  }
}
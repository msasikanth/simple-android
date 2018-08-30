package org.simple.clinic.overdue.communication

import io.reactivex.Completable
import io.reactivex.Single
import org.simple.clinic.patient.SyncStatus
import org.simple.clinic.patient.canBeOverriddenByServerCopy
import org.simple.clinic.sync.SynceableRepository
import java.util.UUID
import javax.inject.Inject

class CommunicationRepository @Inject constructor(val dao: Communication.RoomDao) : SynceableRepository<Communication, CommunicationPayload> {

  fun save(communications: List<Communication>): Completable {
    return Completable.fromAction {
      dao.save(communications)
    }
  }

  override fun pendingSyncRecords(): Single<List<Communication>> {
    return dao.withSyncStatus(SyncStatus.PENDING).firstOrError()
  }

  override fun setSyncStatus(from: SyncStatus, to: SyncStatus): Completable {
    return Completable.fromAction { dao.updateSyncStatus(from, to) }
  }

  override fun setSyncStatus(ids: List<UUID>, to: SyncStatus): Completable {
    if (ids.isEmpty()) {
      throw AssertionError()
    }
    return Completable.fromAction { dao.updateSyncStatus(ids, to) }
  }

  override fun mergeWithLocalData(payloads: List<CommunicationPayload>): Completable {
    val newOrUpdatedCommunications = payloads
        .filter { payload: CommunicationPayload ->
          val localCopy = dao.getOne(payload.id)
          localCopy?.syncStatus.canBeOverriddenByServerCopy()
        }
        .map { toDatabaseModel(it, SyncStatus.DONE) }
        .toList()

    return Completable.fromAction { dao.save(newOrUpdatedCommunications) }
  }

  private fun toDatabaseModel(payload: CommunicationPayload, syncStatus: SyncStatus): Communication {
    return payload.run {
      Communication(
          id = id,
          appointmentId = appointmentId,
          userId = userId,
          type = type,
          result = result,
          syncStatus = syncStatus,
          createdAt = createdAt,
          updatedAt = updatedAt)
    }
  }
}

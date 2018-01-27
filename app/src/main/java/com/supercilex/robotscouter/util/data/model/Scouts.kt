package com.supercilex.robotscouter.util.data.model

import android.arch.lifecycle.LiveData
import com.firebase.ui.firestore.ObservableSnapshotArray
import com.google.android.gms.tasks.Continuation
import com.google.android.gms.tasks.OnCompleteListener
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.SetOptions
import com.supercilex.robotscouter.R
import com.supercilex.robotscouter.RobotScouter
import com.supercilex.robotscouter.data.model.Scout
import com.supercilex.robotscouter.data.model.Team
import com.supercilex.robotscouter.data.model.TemplateType
import com.supercilex.robotscouter.util.AsyncTaskExecutor
import com.supercilex.robotscouter.util.FIRESTORE_METRICS
import com.supercilex.robotscouter.util.FIRESTORE_NAME
import com.supercilex.robotscouter.util.FIRESTORE_SCOUTS
import com.supercilex.robotscouter.util.FIRESTORE_TIMESTAMP
import com.supercilex.robotscouter.util.data.QueuedDeletion
import com.supercilex.robotscouter.util.data.firestoreBatch
import com.supercilex.robotscouter.util.data.observeOnDataChanged
import com.supercilex.robotscouter.util.data.observeOnce
import com.supercilex.robotscouter.util.data.scoutParser
import com.supercilex.robotscouter.util.defaultTemplates
import com.supercilex.robotscouter.util.doAsync
import com.supercilex.robotscouter.util.log
import com.supercilex.robotscouter.util.logAddScout
import com.supercilex.robotscouter.util.logFailures
import org.jetbrains.anko.longToast
import java.util.Date
import kotlin.math.abs

fun Team.getScoutsRef() = ref.collection(FIRESTORE_SCOUTS)

fun Team.getScoutsQuery(direction: Query.Direction = Query.Direction.ASCENDING): Query =
        FIRESTORE_TIMESTAMP.let {
            getScoutsRef().whereGreaterThanOrEqualTo(it, Date(0)).orderBy(it, direction)
        }

fun Team.getScoutMetricsRef(id: String) = getScoutsRef().document(id).collection(FIRESTORE_METRICS)

fun Team.addScout(
        overrideId: String?,
        existingScouts: LiveData<ObservableSnapshotArray<Scout>>
): String {
    val templateId = overrideId ?: templateId
    val scoutRef = getScoutsRef().document()

    logAddScout(id, templateId)
    scoutRef.log().set(Scout(scoutRef.id, templateId)).logFailures()

    val errorIgnorer: (Exception) -> Boolean = {
        scoutRef.log().delete().logFailures()
        RobotScouter.longToast(R.string.scout_add_template_not_cached_error)
        it is FirebaseFirestoreException
                && it.code == FirebaseFirestoreException.Code.UNAVAILABLE
    }
    (TemplateType.coerce(templateId)?.let { type ->
        defaultTemplates.document(type.id.toString()).log().get().continueWith(
                AsyncTaskExecutor, Continuation<DocumentSnapshot, String?> {
            val scout = scoutParser.parseSnapshot(it.result)

            firestoreBatch {
                scout.metrics.forEach {
                    set(getScoutMetricsRef(scoutRef.id).document(it.ref.id).log(), it)
                }
            }.logFailures()

            scout.name
        })
    } ?: run {
        getTemplateRef(templateId).collection(FIRESTORE_METRICS).log().get().continueWithTask(
                AsyncTaskExecutor, Continuation<QuerySnapshot, Task<Void>> {
            firestoreBatch {
                it.result.documents.associate { it.id to it.data }.forEach {
                    set(getScoutMetricsRef(scoutRef.id).document(it.key).log(), it.value)
                }
            }
        }).logFailures(errorIgnorer)

        getTemplateRef(templateId).log().get().continueWith {
            if (it.result.exists()) {
                scoutParser.parseSnapshot(it.result).name
            } else {
                null
            }
        }
    }).logFailures(errorIgnorer).addOnCompleteListener(AsyncTaskExecutor, OnCompleteListener {
        val templateName = it.result!! // Blow up if we failed so as not to wastefully query for scouts
        val nExistingTemplates = Tasks.await(existingScouts.observeOnDataChanged().observeOnce {
            doAsync {
                it.map { it.templateId }.groupBy { it }[templateId]!!.size
            }
        })

        scoutRef.log().update(FIRESTORE_NAME, "$templateName $nExistingTemplates").logFailures()
    })

    return scoutRef.id
}

fun Team.trashScout(id: String) = updateScoutDate(id) { -abs(it) }

fun Team.untrashScout(id: String) = updateScoutDate(id) { abs(it) }

private fun Team.updateScoutDate(id: String, update: (Long) -> Long) {
    getScoutsRef().document(id).log().get().continueWithTask(
            AsyncTaskExecutor, Continuation<DocumentSnapshot, Task<Void>> {
        val snapshot = it.result
        val oppositeDate = Date(update(snapshot.getDate(FIRESTORE_TIMESTAMP).time))
        firestoreBatch {
            this.update(snapshot.reference.log(), FIRESTORE_TIMESTAMP, oppositeDate)
            if (oppositeDate.time > 0) {
                this.update(userDeletionQueue.log(), id, FieldValue.delete())
            } else {
                set(userDeletionQueue.log(),
                    QueuedDeletion.Scout(id, this@updateScoutDate.id).data,
                    SetOptions.merge())
            }
        }
    }).logFailures()
}

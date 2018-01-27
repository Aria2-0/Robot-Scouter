package com.supercilex.robotscouter.util.data.model

import com.google.firebase.firestore.SetOptions
import com.supercilex.robotscouter.data.model.User
import com.supercilex.robotscouter.util.FIRESTORE_PREFS
import com.supercilex.robotscouter.util.deletionQueue
import com.supercilex.robotscouter.util.log
import com.supercilex.robotscouter.util.logFailures
import com.supercilex.robotscouter.util.uid
import com.supercilex.robotscouter.util.users

val userRef get() = getUserRef(uid!!)

val userPrefs get() = getUserPrefs(uid!!)

val userDeletionQueue get() = deletionQueue.document(uid!!)

fun User.add() {
    getUserRef(uid).log().set(this, SetOptions.merge()).logFailures()
}

private fun getUserRef(uid: String) = users.document(uid)

private fun getUserPrefs(uid: String) = getUserRef(uid).collection(FIRESTORE_PREFS)

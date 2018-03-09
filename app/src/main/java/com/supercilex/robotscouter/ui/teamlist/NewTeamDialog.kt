package com.supercilex.robotscouter.ui.teamlist

import android.os.Bundle
import android.support.design.widget.TextInputLayout
import android.support.v4.app.FragmentManager
import android.view.View
import android.widget.EditText
import com.supercilex.robotscouter.R
import com.supercilex.robotscouter.data.model.Team
import com.supercilex.robotscouter.util.data.getScoutBundle
import com.supercilex.robotscouter.util.ui.KeyboardDialogBase
import com.supercilex.robotscouter.util.ui.TeamSelectionListener
import kotterknife.bindView

class NewTeamDialog : KeyboardDialogBase() {
    private val inputLayout: TextInputLayout by bindView(R.id.name)
    override val lastEditText: EditText by bindView(R.id.team_number)

    override fun onCreateDialog(savedInstanceState: Bundle?) = createDialog(
            View.inflate(context, R.layout.dialog_new_team, null),
            R.string.scout_new_title,
            savedInstanceState
    )

    public override fun onAttemptDismiss(): Boolean {
        val teamNumber = try {
            lastEditText.text.toString().toLong()
        } catch (e: NumberFormatException) {
            inputLayout.error = getString(R.string.number_too_big_error)
            return false
        }
        (activity as TeamSelectionListener)
                .onTeamSelected(getScoutBundle(Team(teamNumber, ""), true))
        return true
    }

    companion object {
        private const val TAG = "NewTeamDialog"

        fun show(manager: FragmentManager) = NewTeamDialog().show(manager, TAG)
    }
}

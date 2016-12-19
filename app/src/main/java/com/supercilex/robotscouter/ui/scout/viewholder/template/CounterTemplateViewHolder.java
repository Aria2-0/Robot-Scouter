package com.supercilex.robotscouter.ui.scout.viewholder.template;

import android.support.v7.widget.SimpleItemAnimator;
import android.view.View;

import com.google.firebase.database.Query;
import com.supercilex.robotscouter.data.model.ScoutMetric;
import com.supercilex.robotscouter.ui.scout.viewholder.CounterViewHolder;

public class CounterTemplateViewHolder extends CounterViewHolder implements View.OnFocusChangeListener {
    public CounterTemplateViewHolder(View itemView) {
        super(itemView);
    }

    @Override
    public void bind(ScoutMetric<Integer> metric, Query query, SimpleItemAnimator animator) {
        super.bind(metric, query, animator);
        mName.setOnFocusChangeListener(this);
    }

    @Override
    public void onFocusChange(View v, boolean hasFocus) {
        if (!hasFocus) updateMetricName(mName.getText().toString());
    }
}

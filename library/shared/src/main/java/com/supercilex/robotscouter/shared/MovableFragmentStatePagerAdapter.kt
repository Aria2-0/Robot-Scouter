package com.supercilex.robotscouter.shared

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Parcelable
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentTransaction
import androidx.viewpager.widget.PagerAdapter

/**
 * A PagerAdapter that can withstand item reordering. See
 * https://issuetracker.google.com/issues/36956111.
 *
 * @see androidx.fragment.app.FragmentStatePagerAdapter
 */
abstract class MovableFragmentStatePagerAdapter(
        private val manager: FragmentManager
) : PagerAdapter() {
    private var currentTransaction: FragmentTransaction? = null
    private var currentPrimaryItem: Fragment? = null

    private val savedStates = LinkedHashMap<String, Fragment.SavedState?>()
    private val fragmentsToItemIds = LinkedHashMap<Fragment, String>()
    private val itemIdsToFragments = LinkedHashMap<String, Fragment>()
    private val unusedRestoredFragments = HashSet<Fragment>()

    /** @see androidx.fragment.app.FragmentStatePagerAdapter.getItem */
    abstract fun getItem(position: Int): Fragment

    /**
     * @return a unique identifier for the item at the given position.
     */
    abstract fun getItemId(position: Int): String

    /** @see androidx.fragment.app.FragmentStatePagerAdapter.startUpdate */
    override fun startUpdate(container: ViewGroup) {
        check(container.id != View.NO_ID) {
            "ViewPager with adapter $this requires a view id."
        }
    }

    /** @see androidx.fragment.app.FragmentStatePagerAdapter.instantiateItem */
    override fun instantiateItem(container: ViewGroup, position: Int): Any {
        val itemId = getItemId(position)

        val f = itemIdsToFragments[itemId]
        if (f != null) {
            unusedRestoredFragments.remove(f)
            return f
        }

        initTransaction()

        val fragment = getItem(position)
        fragmentsToItemIds[fragment] = itemId
        itemIdsToFragments[itemId] = fragment

        savedStates[itemId]?.let {
            fragment.setInitialSavedState(it)
        }
        fragment.setMenuVisibility(false)
        fragment.userVisibleHint = false

        checkNotNull(currentTransaction).add(container.id, fragment)

        return fragment
    }

    /** @see androidx.fragment.app.FragmentStatePagerAdapter.destroyItem */
    override fun destroyItem(container: ViewGroup, position: Int, fragment: Any) {
        (fragment as Fragment).destroy()
    }

    /** @see androidx.fragment.app.FragmentStatePagerAdapter.setPrimaryItem */
    override fun setPrimaryItem(container: ViewGroup, position: Int, fragment: Any) {
        fragment as Fragment
        if (fragment !== currentPrimaryItem) {
            currentPrimaryItem?.let {
                it.setMenuVisibility(false)
                it.userVisibleHint = false
            }

            fragment.setMenuVisibility(true)
            fragment.userVisibleHint = true
            currentPrimaryItem = fragment
        }
    }

    /** @see androidx.fragment.app.FragmentStatePagerAdapter.finishUpdate */
    override fun finishUpdate(container: ViewGroup) {
        for (fragment in unusedRestoredFragments) fragment.destroy()
        unusedRestoredFragments.clear()

        currentTransaction?.let {
            it.commitAllowingStateLoss()
            currentTransaction = null

            if (fragmentsToItemIds.isEmpty()) currentPrimaryItem = null
        }
    }

    /** @see androidx.fragment.app.FragmentStatePagerAdapter.isViewFromObject */
    override fun isViewFromObject(view: View, fragment: Any): Boolean =
            (fragment as Fragment).view === view

    /** @see androidx.fragment.app.FragmentStatePagerAdapter.saveState */
    override fun saveState(): Parcelable? = Bundle().apply {
        putStringArrayList(KEY_FRAGMENT_IDS, ArrayList(savedStates.keys))
        putParcelableArrayList(KEY_FRAGMENT_STATES, ArrayList(savedStates.values))

        for ((f, id) in fragmentsToItemIds.entries) {
            if (f.isAdded) manager.putFragment(this, "$KEY_FRAGMENT_STATE$id", f)
        }
    }

    /** @see androidx.fragment.app.FragmentStatePagerAdapter.restoreState */
    override fun restoreState(state: Parcelable?, loader: ClassLoader?) {
        if ((state as? Bundle)?.apply { classLoader = loader }?.isEmpty == false) {
            fragmentsToItemIds.clear()
            itemIdsToFragments.clear()
            unusedRestoredFragments.clear()
            savedStates.clear()

            val fragmentIds: List<String> = state.getStringArrayList(KEY_FRAGMENT_IDS).orEmpty()
            val fragmentStates: List<Fragment.SavedState> =
                    state.getParcelableArrayList<Fragment.SavedState>(KEY_FRAGMENT_STATES).orEmpty()

            for ((index, id) in fragmentIds.withIndex()) {
                savedStates[id] = fragmentStates[index]
            }

            for (key: String in state.keySet()) {
                if (key.startsWith(KEY_FRAGMENT_STATE)) {
                    val itemId = key.substring(KEY_FRAGMENT_STATE.length)

                    manager.getFragment(state, key)?.let {
                        it.setMenuVisibility(false)
                        fragmentsToItemIds[it] = itemId
                        itemIdsToFragments[itemId] = it
                    }
                }
            }

            unusedRestoredFragments.addAll(fragmentsToItemIds.keys)
        }
    }

    private fun Fragment.destroy() {
        initTransaction()

        val itemId = fragmentsToItemIds.remove(this)
        itemIdsToFragments.remove(itemId)
        if (itemId != null && isAdded) {
            savedStates[itemId] = manager.saveFragmentInstanceState(this)
        }

        checkNotNull(currentTransaction).remove(this)
    }

    private fun initTransaction() {
        if (currentTransaction == null) {
            // We commit the transaction later
            @SuppressLint("CommitTransaction")
            currentTransaction = manager.beginTransaction()
        }
    }

    private companion object {
        const val KEY_FRAGMENT_IDS = "fragment_keys_"
        const val KEY_FRAGMENT_STATES = "fragment_states_"
        const val KEY_FRAGMENT_STATE = "fragment_state_"
    }
}

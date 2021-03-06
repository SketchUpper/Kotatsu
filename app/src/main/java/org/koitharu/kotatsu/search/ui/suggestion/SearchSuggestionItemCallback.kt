package org.koitharu.kotatsu.search.ui.suggestion

import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import org.koitharu.kotatsu.search.ui.suggestion.adapter.SearchSuggestionAdapter
import org.koitharu.kotatsu.search.ui.suggestion.model.SearchSuggestionItem
import org.koitharu.kotatsu.utils.ext.getItem

class SearchSuggestionItemCallback(
	private val listener: SuggestionItemListener,
) : ItemTouchHelper.Callback() {

	private val movementFlags = makeMovementFlags(
		0,
		ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
	)

	override fun getMovementFlags(
		recyclerView: RecyclerView,
		viewHolder: RecyclerView.ViewHolder,
	): Int = if (viewHolder.itemViewType == SearchSuggestionAdapter.ITEM_TYPE_QUERY) {
		movementFlags
	} else {
		0
	}

	override fun onMove(
		recyclerView: RecyclerView,
		viewHolder: RecyclerView.ViewHolder,
		target: RecyclerView.ViewHolder,
	): Boolean = false

	override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
		val item = viewHolder.getItem<SearchSuggestionItem.RecentQuery>() ?: return
		listener.onRemoveQuery(item.query)
	}

	interface SuggestionItemListener {

		fun onRemoveQuery(query: String)
	}
}
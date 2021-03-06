package org.koitharu.kotatsu.search.ui.suggestion

import org.koitharu.kotatsu.core.model.Manga

interface SearchSuggestionListener {

	fun onMangaClick(manga: Manga)

	fun onQueryClick(query: String, submit: Boolean)

	fun onQueryChanged(query: String)

	fun onClearSearchHistory()
}
package org.koitharu.kotatsu.details.ui

import androidx.lifecycle.asFlow
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.plus
import org.koitharu.kotatsu.base.domain.MangaDataRepository
import org.koitharu.kotatsu.base.domain.MangaIntent
import org.koitharu.kotatsu.base.ui.BaseViewModel
import org.koitharu.kotatsu.core.exceptions.MangaNotFoundException
import org.koitharu.kotatsu.core.model.Manga
import org.koitharu.kotatsu.core.prefs.AppSettings
import org.koitharu.kotatsu.details.ui.model.toListItem
import org.koitharu.kotatsu.favourites.domain.FavouritesRepository
import org.koitharu.kotatsu.history.domain.ChapterExtra
import org.koitharu.kotatsu.history.domain.HistoryRepository
import org.koitharu.kotatsu.local.domain.LocalMangaRepository
import org.koitharu.kotatsu.tracker.domain.TrackingRepository
import org.koitharu.kotatsu.utils.SingleLiveEvent
import org.koitharu.kotatsu.utils.ext.mapToSet
import java.io.IOException

class DetailsViewModel(
	intent: MangaIntent,
	private val historyRepository: HistoryRepository,
	private val favouritesRepository: FavouritesRepository,
	private val localMangaRepository: LocalMangaRepository,
	private val trackingRepository: TrackingRepository,
	private val mangaDataRepository: MangaDataRepository,
	private val settings: AppSettings
) : BaseViewModel() {

	private val mangaData = MutableStateFlow<Manga?>(intent.manga)
	private val selectedBranch = MutableStateFlow<String?>(null)

	private val history = mangaData.mapNotNull { it?.id }
		.distinctUntilChanged()
		.flatMapLatest { mangaId ->
			historyRepository.observeOne(mangaId)
		}.stateIn(viewModelScope + Dispatchers.Default, SharingStarted.Eagerly, null)

	private val favourite = mangaData.mapNotNull { it?.id }
		.distinctUntilChanged()
		.flatMapLatest { mangaId ->
			favouritesRepository.observeCategoriesIds(mangaId).map { it.isNotEmpty() }
		}.stateIn(viewModelScope + Dispatchers.Default, SharingStarted.Eagerly, false)

	private val newChapters = mangaData.mapNotNull { it?.id }
		.distinctUntilChanged()
		.mapLatest { mangaId ->
			trackingRepository.getNewChaptersCount(mangaId)
		}.stateIn(viewModelScope + Dispatchers.Default, SharingStarted.Eagerly, 0)

	private val chaptersReversed = settings.observe()
		.filter { it == AppSettings.KEY_REVERSE_CHAPTERS }
		.map { settings.chaptersReverse }
		.onStart { emit(settings.chaptersReverse) }
		.stateIn(viewModelScope + Dispatchers.Default, SharingStarted.Eagerly, false)

	val manga = mangaData.filterNotNull()
		.asLiveData(viewModelScope.coroutineContext)
	val favouriteCategories = favourite
		.asLiveData(viewModelScope.coroutineContext)
	val newChaptersCount = newChapters
		.asLiveData(viewModelScope.coroutineContext)
	val readingHistory = history
		.asLiveData(viewModelScope.coroutineContext)
	val isChaptersReversed = chaptersReversed
		.asLiveData(viewModelScope.coroutineContext)

	val onMangaRemoved = SingleLiveEvent<Manga>()

	val branches = mangaData.map {
		it?.chapters?.mapToSet { x -> x.branch }?.sortedBy { x -> x }.orEmpty()
	}.asLiveData(viewModelScope.coroutineContext + Dispatchers.Default)

	val selectedBranchIndex = combine(
		branches.asFlow(),
		selectedBranch
	) { branches, selected ->
		branches.indexOf(selected)
	}.asLiveData(viewModelScope.coroutineContext + Dispatchers.Default)

	val chapters = combine(
		mangaData.map { it?.chapters.orEmpty() },
		history.map { it?.chapterId },
		newChapters,
		chaptersReversed,
		selectedBranch
	) { chapters, currentId, newCount, reversed, branch ->
		val currentIndex = chapters.indexOfFirst { it.id == currentId }
		val firstNewIndex = chapters.size - newCount
		val res = chapters.mapIndexed { index, chapter ->
			chapter.toListItem(
				when {
					index >= firstNewIndex -> ChapterExtra.NEW
					index == currentIndex -> ChapterExtra.CURRENT
					index < currentIndex -> ChapterExtra.READ
					else -> ChapterExtra.UNREAD
				}
			)
		}.filter { it.chapter.branch == branch }
		if (reversed) res.asReversed() else res
	}.asLiveData(viewModelScope.coroutineContext + Dispatchers.Default)

	init {
		launchLoadingJob(Dispatchers.Default) {
			var manga = mangaDataRepository.resolveIntent(intent)
				?: throw MangaNotFoundException("Cannot find manga")
			mangaData.value = manga
			manga = manga.source.repository.getDetails(manga)
			// find default branch
			val hist = historyRepository.getOne(manga)
			selectedBranch.value = if (hist != null) {
				manga.chapters?.find { it.id == hist.chapterId }?.branch
			} else {
				manga.chapters
					?.groupBy { it.branch }
					?.maxByOrNull { it.value.size }?.key
			}
			mangaData.value = manga
		}
	}

	fun deleteLocal(manga: Manga) {
		launchLoadingJob(Dispatchers.Default) {
			val original = localMangaRepository.getRemoteManga(manga)
			localMangaRepository.delete(manga) || throw IOException("Unable to delete file")
			runCatching {
				historyRepository.deleteOrSwap(manga, original)
			}
			onMangaRemoved.postCall(manga)
		}
	}

	fun setChaptersReversed(newValue: Boolean) {
		settings.chaptersReverse = newValue
	}

	fun setSelectedBranch(branch: String?) {
		selectedBranch.value = branch
	}
}
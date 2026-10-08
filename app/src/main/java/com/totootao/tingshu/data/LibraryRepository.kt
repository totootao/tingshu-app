package com.totootao.tingshu.data

import com.github.eprendre.tingshu.utils.Book
import com.github.eprendre.tingshu.utils.Episode
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.totootao.tingshu.data.db.FavoriteEntity
import com.totootao.tingshu.data.db.HistoryEntity
import com.totootao.tingshu.di.ServiceLocator
import kotlinx.coroutines.flow.Flow

/**
 * 书架(收藏)/播放历史 仓库
 */
object LibraryRepository {

    private val gson = Gson()
    private val episodeListType = object : TypeToken<List<Episode>>() {}.type

    private val favoriteDao get() = ServiceLocator.database.favoriteDao()
    private val historyDao get() = ServiceLocator.database.historyDao()

    val favoritesFlow: Flow<List<FavoriteEntity>> = favoriteDao.observeAll()
    val historyFlow: Flow<List<HistoryEntity>> = historyDao.observeRecent(200)

    fun toBook(e: FavoriteEntity): Book = Book(e.coverUrl, e.bookUrl, e.title, e.author, e.artist).apply {
        sourceId = e.sourceId
        intro = e.intro
        status = e.status
        currentEpisodeUrl = e.currentEpisodeUrl
        currentEpisodeName = e.currentEpisodeName
        currentEpisodePosition = e.currentEpisodePosition
        skipBeginning = e.skipBeginning
        skipEnd = e.skipEnd
        playSpeed = e.playSpeed
        playOrderType = e.playOrderType
        isEpisodesReversed = e.isEpisodesReversed
        hasFullEpisodes = e.hasFullEpisodes
        isCompleted = e.isCompleted
        isTransientEpisodes = e.isTransientEpisodes
        episodesUpdateTime = e.episodesUpdateTime
        volumeBoostLevel = e.volumeBoostLevel
        isShowBriefChapterTitle = e.isShowBriefChapterTitle
        episodeList = e.episodeListJson?.let {
            runCatching { gson.fromJson<List<Episode>>(it, episodeListType) }.getOrNull()
        }
    }

    fun toEntity(book: Book): FavoriteEntity = FavoriteEntity(
        sourceId = book.sourceId ?: "",
        bookUrl = book.bookUrl,
        title = book.title,
        author = book.author,
        artist = book.artist,
        coverUrl = book.coverUrl,
        intro = book.intro,
        status = book.status,
        currentEpisodeUrl = book.currentEpisodeUrl,
        currentEpisodeName = book.currentEpisodeName,
        currentEpisodePosition = book.currentEpisodePosition,
        skipBeginning = book.skipBeginning,
        skipEnd = book.skipEnd,
        playSpeed = book.playSpeed,
        playOrderType = book.playOrderType,
        isEpisodesReversed = book.isEpisodesReversed,
        episodeListJson = book.episodeList?.let { gson.toJson(it) },
        hasFullEpisodes = book.hasFullEpisodes,
        isCompleted = book.isCompleted,
        isTransientEpisodes = book.isTransientEpisodes,
        episodesUpdateTime = book.episodesUpdateTime,
        volumeBoostLevel = book.volumeBoostLevel,
        isShowBriefChapterTitle = book.isShowBriefChapterTitle
    )

    suspend fun isFavorite(sourceId: String, bookUrl: String): Boolean =
        favoriteDao.get(sourceId, bookUrl) != null

    suspend fun addFavorite(book: Book) {
        val exist = favoriteDao.get(book.sourceId ?: "", book.bookUrl)
        val merged = if (exist != null) {
            // 保留旧记录的播放进度
            toBook(exist).apply { copyFrom(book) }
        } else book
        favoriteDao.upsert(toEntity(merged))
    }

    suspend fun removeFavorite(sourceId: String, bookUrl: String) {
        favoriteDao.deleteByKey(sourceId, bookUrl)
    }

    suspend fun getFavorite(sourceId: String, bookUrl: String): Book? =
        favoriteDao.get(sourceId, bookUrl)?.let { toBook(it) }

    /**
     * 保存播放进度(播放位置变化时调用)
     */
    suspend fun saveProgress(book: Book, episodeUrl: String?, episodeName: String?, position: Long) {
        val exist = favoriteDao.get(book.sourceId ?: "", book.bookUrl) ?: return
        favoriteDao.upsert(
            exist.copy(
                currentEpisodeUrl = episodeUrl,
                currentEpisodeName = episodeName,
                currentEpisodePosition = position
            )
        )
    }

    /**
     * 更新章节列表缓存
     */
    suspend fun updateEpisodes(book: Book, episodes: List<Episode>, hasFullEpisodes: Boolean) {
        val exist = favoriteDao.get(book.sourceId ?: "", book.bookUrl) ?: return
        favoriteDao.upsert(
            exist.copy(
                episodeListJson = gson.toJson(episodes),
                hasFullEpisodes = hasFullEpisodes,
                episodesUpdateTime = System.currentTimeMillis()
            )
        )
    }

    suspend fun updateBookFields(book: Book) {
        val exist = favoriteDao.get(book.sourceId ?: "", book.bookUrl) ?: return
        favoriteDao.upsert(toEntity(book))
    }

    suspend fun addHistory(book: Book, episode: Episode, position: Long) {
        historyDao.insert(
            HistoryEntity(
                sourceId = book.sourceId ?: "",
                bookUrl = book.bookUrl,
                title = book.title,
                coverUrl = book.coverUrl,
                episodeTitle = episode.title,
                episodeUrl = episode.url,
                position = position
            )
        )
    }

    suspend fun clearHistory() = historyDao.clear()
}

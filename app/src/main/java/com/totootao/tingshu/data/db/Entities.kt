package com.totootao.tingshu.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 书架(收藏)。主键 = sourceId + bookUrl
 */
@Entity(tableName = "favorites", primaryKeys = ["sourceId", "bookUrl"])
data class FavoriteEntity(
    val sourceId: String,
    val bookUrl: String,
    val title: String,
    val author: String = "",
    val artist: String = "",
    val coverUrl: String = "",
    val intro: String = "",
    val status: String = "",
    var currentEpisodeUrl: String? = null,
    var currentEpisodeName: String? = null,
    var currentEpisodePosition: Long = 0,
    var skipBeginning: Long = 0,
    var skipEnd: Long = 0,
    var playSpeed: Float = 1f,
    var playOrderType: Int = 0,
    var isEpisodesReversed: Boolean = false,
    var episodeListJson: String? = null,
    var hasFullEpisodes: Boolean = false,
    var isCompleted: Boolean = false,
    var isTransientEpisodes: Boolean = false,
    var episodesUpdateTime: Long = 0,
    var volumeBoostLevel: Int = 0,
    var isShowBriefChapterTitle: Boolean = false,
    val addedTime: Long = System.currentTimeMillis()
)

@Entity(tableName = "history")
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sourceId: String,
    val bookUrl: String,
    val title: String,
    val coverUrl: String,
    val episodeTitle: String,
    val episodeUrl: String,
    val position: Long,
    val playedAt: Long = System.currentTimeMillis()
)

/**
 * 外置源订阅
 */
@Entity(tableName = "external_sources")
data class ExternalSourceEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val url: String,
    val entryPackage: String,
    var version: Int = 0,
    val downloadUrl: String = "",
    var updateMsg: String = "",
    var supportUrl: String = "",
    var enabled: Boolean = true,
    var lastUpdated: Long = 0
)

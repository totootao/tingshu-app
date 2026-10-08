package com.totootao.tingshu.playback

import android.content.Intent
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/**
 * 前台播放服务(媒体会话)。支持通知栏控制、锁屏控制。
 */
class PlayerService : MediaSessionService() {

    companion object {
        /** 底层 ExoPlayer 引用,供 PlaybackManager 设置 skipSilence 等 ExoPlayer 专有属性 */
        @Volatile
        var activePlayer: ExoPlayer? = null
    }

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val player: ExoPlayer = PlaybackManager.buildPlayer(this)
        activePlayer = player
        mediaSession = MediaSession.Builder(this, player)
            .setCallback(object : MediaSession.Callback {
                override fun onAddMediaItems(
                    mediaSession: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    mediaItems: MutableList<MediaItem>
                ): ListenableFuture<MutableList<MediaItem>> {
                    // 保留原始 uri(含 tingshu-pending 待解析协议)
                    val resolved = mediaItems.map { item ->
                        val uri = item.requestMetadata.mediaUri
                            ?: item.localConfiguration?.uri
                            ?: Uri.EMPTY
                        item.buildUpon().setUri(uri).build()
                    }
                    return Futures.immediateFuture(resolved as MutableList<MediaItem>)
                }
            })
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        activePlayer = null
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}

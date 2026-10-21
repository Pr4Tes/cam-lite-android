@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package com.camlite.android

import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.rtsp.RtspMediaSource
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

class CameraSession(context: Context, private val camera: CameraConfig,
    private val profile: PerformanceProfile = PerformanceProfile.NORMAL,
    onDoubleTap: () -> Unit = {}, private val onAspectRatio: (Double) -> Unit = {}, onLongPress: () -> Unit) {
    val cell = FrameLayout(context).apply { setBackgroundColor(Color.BLACK) }
    private val video = PlayerView(context).apply {
        useController = false
        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
        setShutterBackgroundColor(Color.BLACK)
        setKeepContentOnPlayerReset(false)
        isClickable = true
    }
    private val status = TextView(context).apply {
        setTextColor(Color.LTGRAY); textSize = 14f; gravity = Gravity.CENTER
        setBackgroundColor(Color.BLACK)
    }
    private val handler = Handler(Looper.getMainLooper())
    private val streamUri = camera.uri()
    private val mediaItem = MediaItem.fromUri(streamUri)
    @Volatile private var player: ExoPlayer? = null
    private var listener: Player.Listener? = null
    @Volatile private var lastVideoFrameMs = 0L
    private var active = false
    private var rendered = true
    private var networks = emptyList<LocalNetwork>()
    private var readySince = 0L
    private var bufferingSince = 0L
    private var decoderFailures = 0
    private val recovery = RecoveryLoop(
        schedule = { delay, task -> val runnable = Runnable { task() }; handler.postDelayed(runnable, delay); { handler.removeCallbacks(runnable); Unit } },
        connect = { key -> connect(key) }, release = { bufferingSince = 0L; readySince = 0L; releasePlayer() },
        offline = { status.text = "Aguardando conexão"; status.visibility = View.VISIBLE }
    )
    private val watchdog = object : Runnable {
        override fun run() {
            if (!active) return
            val now = SystemClock.elapsedRealtime()
            // Transport timeout handles missing RTP. Never compare pixels or frames of static scenes.
            if (bufferingSince != 0L && now - bufferingSince > 25000) retry("Reconectando...")
            if (rendered && player?.isPlaying == true && readySince != 0L &&
                now - maxOf(readySince, lastVideoFrameMs) > 25000) retry("Vídeo sem frames.\nReconectando...")
            if (readySince != 0L && now - readySince > 30000) { recovery.stable(); decoderFailures = 0 }
            handler.postDelayed(this, 5000)
        }
    }
    init {
        val gesture = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent) = true
            override fun onDoubleTap(e: MotionEvent): Boolean { onDoubleTap(); return true }
            override fun onLongPress(e: MotionEvent) { onLongPress() }
        })
        val touch = View.OnTouchListener { _, event -> gesture.onTouchEvent(event); true }
        video.setOnTouchListener(touch)
        status.setOnTouchListener(touch)
        cell.addView(video, FrameLayout.LayoutParams(-1, -1))
        cell.addView(status, FrameLayout.LayoutParams(-1, -1))
        status.text = "Aguardando..."
    }
    fun start(delayMs: Long) {
        if (active) return
        active = true
        recovery.start(delayMs)
        handler.postDelayed(watchdog, 5000)
    }
    fun updateNetworks(available: List<LocalNetwork>) {
        networks = available
        val selected = available.maxByOrNull { it.score(streamUri.host.orEmpty()) }
        recovery.updateNetworks(available.map { it.network.networkHandle }.toSet(), selected?.network?.networkHandle)
    }
    fun setRenderingVisible(visible: Boolean) {
        if (visible == rendered) return
        if (visible && !rendered) lastVideoFrameMs = SystemClock.elapsedRealtime()
        rendered = visible
        // Detach the display surface without deselecting RTSP tracks or reopening sessions.
        video.player = if (visible) player else null
    }
    private fun connect(networkKey: Long) {
        if (!active) return
        val network = networks.firstOrNull { it.network.networkHandle == networkKey } ?: return
        releasePlayer()
        status.visibility = View.VISIBLE
        status.text = "Conectando..."
        bufferingSince = SystemClock.elapsedRealtime()
        readySince = 0L
        lastVideoFrameMs = 0L
        try {
            val selector = DefaultTrackSelector(cell.context).apply {
                parameters = buildUponParameters().setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true).build()
            }
            val renderers = DefaultRenderersFactory(cell.context).setMediaCodecSelector { mime, secure, tunnel ->
                MediaCodecSelector.DEFAULT.getDecoderInfos(mime, secure, tunnel).sortedByDescending { it.hardwareAccelerated }
            }
            val p = ExoPlayer.Builder(cell.context, renderers).setTrackSelector(selector)
                .setLoadControl(DefaultLoadControl.Builder().setBufferDurationsMs(profile.minBufferMs, profile.maxBufferMs, 250, 500)
                    .setTargetBufferBytes(profile.targetBytes).setPrioritizeTimeOverSizeThresholds(false).build()).build()
            player = p
            video.player = if (rendered) p else null
            p.volume = 0f
            val currentListener = object : Player.Listener {
                override fun onVideoSizeChanged(videoSize: VideoSize) {
                    if (player !== p || !active || videoSize.height <= 0 || videoSize.width <= 0) return
                    val ratio = videoSize.width.toDouble() * videoSize.pixelWidthHeightRatio / videoSize.height
                    onAspectRatio(if (videoSize.unappliedRotationDegrees % 180 != 0) 1 / ratio else ratio)
                }
                override fun onPlaybackStateChanged(state: Int) {
                    if (player !== p || !active) return
                    when (state) {
                        Player.STATE_IDLE -> Unit
                        Player.STATE_READY -> { bufferingSince = 0L; readySince = SystemClock.elapsedRealtime() }
                        Player.STATE_BUFFERING -> { readySince = 0L; if (bufferingSince == 0L) bufferingSince = SystemClock.elapsedRealtime() }
                        Player.STATE_ENDED -> retry("Reconectando...")
                    }
                }
                override fun onRenderedFirstFrame() {
                    if (player === p && active) status.visibility = View.GONE
                }
                override fun onPlayerError(error: PlaybackException) {
                    if (player !== p || !active) return
                    val decoderError = error.errorCode in 4000..4999
                    if (decoderError) decoderFailures++
                    retry(if (decoderError) "Decoder indisponível. Use substream H.264\nou reduza o layout. Reconectando..." else "Reconectando...",
                        if (decoderError) if (decoderFailures >= 2) 30000 else 5000 else 0)
                }
            }
            listener = currentListener
            p.addListener(currentListener)
            // Timestamp only, no pixel inspection: static scenes still deliver video frames.
            p.setVideoFrameMetadataListener { _, _, _, _ -> if (player === p) lastVideoFrameMs = SystemClock.elapsedRealtime() }
            val source = RtspMediaSource.Factory().setForceUseRtpTcp(true).setTimeoutMs(12000)
                .setSocketFactory(network.network.socketFactory)
                .createMediaSource(mediaItem)
            p.setMediaSource(source)
            p.prepare()
            p.playWhenReady = true
        } catch (_: Exception) { retry("Não foi possível iniciar o vídeo.\nReconectando...") }
    }
    private fun retry(message: String, minimumDelayMs: Long = 0) {
        if (!active) return
        bufferingSince = 0L; readySince = 0L
        status.text = message; status.visibility = View.VISIBLE
        recovery.fail(minimumDelayMs)
    }
    private fun releasePlayer() {
        val old = player
        player = null
        video.player = null
        listener?.let { old?.removeListener(it) }; listener = null
        old?.release()
    }
    fun stop() {
        active = false
        recovery.stop()
        handler.removeCallbacksAndMessages(null)
        releasePlayer()
    }
}

package com.myvault.app.data.quran.memorization

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.MediaPlayer
import android.media.AudioRecord
import android.net.Uri
import android.os.SystemClock
import java.io.File
import java.io.RandomAccessFile
import kotlin.math.max

data class QuranMemorizationRecording(
    val file: File,
    val uri: Uri,
    val durationMs: Long,
    val surahNumber: Int,
    val ayahNumber: Int,
    val verseKey: String,
    val createdAt: Long,
)

class QuranMemorizationRecorder(
    context: Context,
) {
    private companion object {
        const val SampleRate = 16_000
        const val ChannelCount = 1
        const val BitsPerSample = 16
        const val WavHeaderSize = 44
    }

    private val appContext = context.applicationContext
    private var recorder: AudioRecord? = null
    private var recorderThread: Thread? = null
    private var player: MediaPlayer? = null
    private var outputFile: File? = null
    private var recordingStartedAtMs = 0L
    private var accumulatedRecordingMs = 0L
    @Volatile private var recordingActive = false
    @Volatile private var recordedAudioBytes = 0L
    private var paused = false

    fun start(surahNumber: Int, ayahNumber: Int): File {
        release()
        val directory = File(appContext.cacheDir, "quran_memorisation_recordings")
        directory.mkdirs()
        val createdAt = System.currentTimeMillis()
        val file = File(directory, "quran_${surahNumber}_${ayahNumber}_$createdAt.wav")
        val minBuffer = AudioRecord.getMinBufferSize(
            SampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        val bufferSize = max(minBuffer, SampleRate * ChannelCount * (BitsPerSample / 8))
        @Suppress("MissingPermission")
        val audioRecord = AudioRecord.Builder()
            .setAudioSource(android.media.MediaRecorder.AudioSource.MIC)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(SampleRate)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(bufferSize)
            .build()
        if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
            audioRecord.release()
            error("Microphone recorder could not be prepared.")
        }

        file.outputStream().use { stream ->
            stream.write(ByteArray(WavHeaderSize))
        }
        recordedAudioBytes = 0L
        recordingActive = true
        paused = false
        audioRecord.startRecording()
        recorderThread = Thread(
            {
                writeWavAudio(audioRecord, file, bufferSize)
            },
            "QuranMemorizationRecorder",
        ).apply { start() }
        recorder = audioRecord
        outputFile = file
        recordingStartedAtMs = SystemClock.elapsedRealtime()
        accumulatedRecordingMs = 0L
        return file
    }

    fun pause() {
        recorder ?: return
        if (paused) return
        accumulatedRecordingMs += SystemClock.elapsedRealtime() - recordingStartedAtMs
        paused = true
    }

    fun resume() {
        recorder ?: return
        if (!paused) return
        recordingStartedAtMs = SystemClock.elapsedRealtime()
        paused = false
    }

    fun elapsedMs(): Long {
        val liveMs = if (recorder != null && !paused) {
            SystemClock.elapsedRealtime() - recordingStartedAtMs
        } else {
            0L
        }
        return accumulatedRecordingMs + liveMs
    }

    fun stop(surahNumber: Int, ayahNumber: Int): QuranMemorizationRecording? {
        val file = outputFile ?: return null
        val duration = elapsedMs()
        stopActiveRecorder()
        outputFile = null
        paused = false
        return QuranMemorizationRecording(
            file = file,
            uri = Uri.fromFile(file),
            durationMs = duration,
            surahNumber = surahNumber,
            ayahNumber = ayahNumber,
            verseKey = "$surahNumber:$ayahNumber",
            createdAt = System.currentTimeMillis(),
        )
    }

    fun play(recording: QuranMemorizationRecording, onCompleted: () -> Unit, onError: () -> Unit) {
        stopPlayback()
        val mediaPlayer = MediaPlayer()
        player = mediaPlayer
        runCatching {
            mediaPlayer.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            mediaPlayer.setDataSource(recording.file.absolutePath)
            mediaPlayer.setOnCompletionListener {
                stopPlayback()
                onCompleted()
            }
            mediaPlayer.setOnErrorListener { _, _, _ ->
                stopPlayback()
                onError()
                true
            }
            mediaPlayer.prepare()
            mediaPlayer.start()
        }.onFailure {
            stopPlayback()
            onError()
        }
    }

    fun stopPlayback() {
        player?.runCatching {
            if (isPlaying) stop()
            reset()
            release()
        }
        player = null
    }

    fun isPlaying(): Boolean = player?.isPlaying == true

    private fun writeWavAudio(audioRecord: AudioRecord, file: File, bufferSize: Int) {
        val buffer = ByteArray(bufferSize)
        runCatching {
            file.outputStream().use { stream ->
                stream.channel.position(WavHeaderSize.toLong())
                while (recordingActive) {
                    if (paused) {
                        Thread.sleep(20L)
                        continue
                    }
                    val read = audioRecord.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        stream.write(buffer, 0, read)
                        recordedAudioBytes += read.toLong()
                    }
                }
            }
        }
    }

    private fun stopActiveRecorder() {
        recordingActive = false
        recorderThread?.runCatching { join(800L) }
        recorderThread = null
        recorder?.runCatching { stop() }
        recorder?.runCatching { release() }
        recorder = null
        outputFile?.let { file ->
            if (file.exists() && file.length() >= WavHeaderSize) {
                writeWavHeader(file, recordedAudioBytes)
            }
        }
    }

    private fun writeWavHeader(file: File, audioBytes: Long) {
        RandomAccessFile(file, "rw").use { wav ->
            wav.seek(0)
            wav.writeBytes("RIFF")
            wav.writeIntLittleEndian((36L + audioBytes).coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
            wav.writeBytes("WAVE")
            wav.writeBytes("fmt ")
            wav.writeIntLittleEndian(16)
            wav.writeShortLittleEndian(1)
            wav.writeShortLittleEndian(ChannelCount)
            wav.writeIntLittleEndian(SampleRate)
            wav.writeIntLittleEndian(SampleRate * ChannelCount * (BitsPerSample / 8))
            wav.writeShortLittleEndian(ChannelCount * (BitsPerSample / 8))
            wav.writeShortLittleEndian(BitsPerSample)
            wav.writeBytes("data")
            wav.writeIntLittleEndian(audioBytes.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
        }
    }

    private fun RandomAccessFile.writeIntLittleEndian(value: Int) {
        write(value and 0xff)
        write((value shr 8) and 0xff)
        write((value shr 16) and 0xff)
        write((value shr 24) and 0xff)
    }

    private fun RandomAccessFile.writeShortLittleEndian(value: Int) {
        write(value and 0xff)
        write((value shr 8) and 0xff)
    }

    fun release() {
        stopPlayback()
        stopActiveRecorder()
        outputFile = null
        paused = false
    }
}

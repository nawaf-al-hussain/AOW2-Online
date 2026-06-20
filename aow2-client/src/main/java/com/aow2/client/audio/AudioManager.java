package com.aow2.client.audio;

import javafx.scene.media.AudioClip;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages game audio: music, sound effects, ambient sounds.
 * Uses JavaFX MediaPlayer for music and AudioClip for SFX.
 * <p>
 * Audio resources are loaded from the classpath under /audio/.
 * Music tracks are streamed via MediaPlayer; SFX are pre-loaded as AudioClip.
 * <p>
 * REF: MASTER_DOCUMENTATION.md Section 7 - Audio System
 */
public final class AudioManager {

    private static final Logger LOG = LoggerFactory.getLogger(AudioManager.class);

    /** Base path for audio resources on the classpath. */
    private static final String AUDIO_BASE_PATH = "/audio/";

    /** Base path for music resources. */
    private static final String MUSIC_BASE_PATH = AUDIO_BASE_PATH + "music/";

    /** Base path for SFX resources. */
    private static final String SFX_BASE_PATH = AUDIO_BASE_PATH + "sfx/";

    /** Current music volume (0.0 to 1.0). */
    private double musicVolume;

    /** Current SFX volume (0.0 to 1.0). */
    private double sfxVolume;

    /** Currently playing music MediaPlayer. */
    private MediaPlayer currentMusicPlayer;

    /** The music player for playlist support and crossfading. */
    private final MusicPlayer musicPlayer;

    /** Cache of pre-loaded sound effects. */
    private final Map<String, AudioClip> sfxCache;

    /** Whether audio is muted. */
    private boolean muted;

    /**
     * Constructs an AudioManager with default volume levels.
     */
    public AudioManager() {
        this.musicVolume = 0.5;
        this.sfxVolume = 0.7;
        this.musicPlayer = new MusicPlayer(this);
        this.sfxCache = new ConcurrentHashMap<>();
        this.muted = false;
        LOG.info("AudioManager initialized (music={}, sfx={})", musicVolume, sfxVolume);
    }

    /**
     * Play background music by track name.
     * The track file should be located at /audio/music/{trackName}.mp3.
     * If music is already playing, it will be stopped first.
     *
     * @param trackName the music track name (without extension)
     */
    public void playMusic(String trackName) {
        if (muted) {
            LOG.debug("Audio muted, not playing music: {}", trackName);
            return;
        }

        stopMusic();

        String resourcePath = MUSIC_BASE_PATH + trackName + ".mp3";
        URL resource = getClass().getResource(resourcePath);
        if (resource == null) {
            LOG.warn("Music track not found: {}", resourcePath);
            return;
        }

        Media media = new Media(resource.toExternalForm());
        currentMusicPlayer = new MediaPlayer(media);
        currentMusicPlayer.setVolume(musicVolume);
        currentMusicPlayer.setCycleCount(MediaPlayer.INDEFINITE);
        currentMusicPlayer.play();

        LOG.info("Playing music: {}", trackName);
    }

    /**
     * Play a sound effect by name.
     * SFX files should be located at /audio/sfx/{sfxName}.wav.
     * Sound effects are cached after first load for faster subsequent playback.
     *
     * @param sfxName the sound effect name (without extension)
     */
    public void playSFX(String sfxName) {
        if (muted) {
            LOG.debug("Audio muted, not playing SFX: {}", sfxName);
            return;
        }

        // Check if already cached
        AudioClip clip = sfxCache.get(sfxName);
        if (clip != null) {
            clip.setVolume(sfxVolume);
            clip.play();
            LOG.debug("Playing SFX: {}", sfxName);
            return;
        }

        // Load the SFX resource — check for null BEFORE calling computeIfAbsent,
        // as ConcurrentHashMap.computeIfAbsent() cannot return null (throws NPE)
        String resourcePath = SFX_BASE_PATH + sfxName + ".wav";
        URL resource = getClass().getResource(resourcePath);
        if (resource == null) {
            LOG.warn("SFX not found: {}", resourcePath);
            return;
        }

        AudioClip newClip = new AudioClip(resource.toExternalForm());
        sfxCache.put(sfxName, newClip);
        newClip.setVolume(sfxVolume);
        newClip.play();
        LOG.debug("Playing SFX: {}", sfxName);
    }

    /**
     * Stop all audio including music and SFX.
     */
    public void stopAll() {
        stopMusic();
        sfxCache.values().forEach(AudioClip::stop);
        LOG.info("All audio stopped");
    }

    /**
     * Stop the currently playing music.
     */
    public void stopMusic() {
        if (currentMusicPlayer != null) {
            currentMusicPlayer.stop();
            currentMusicPlayer.dispose();
            currentMusicPlayer = null;
        }
    }

    /**
     * Set music volume (0.0 - 1.0).
     *
     * @param volume the music volume level
     */
    public void setMusicVolume(double volume) {
        this.musicVolume = Math.clamp(volume, 0.0, 1.0);
        if (currentMusicPlayer != null) {
            currentMusicPlayer.setVolume(this.musicVolume);
        }
        musicPlayer.setVolume(this.musicVolume);
        LOG.debug("Music volume set to {}", this.musicVolume);
    }

    /**
     * Set SFX volume (0.0 - 1.0).
     *
     * @param volume the SFX volume level
     */
    public void setSfxVolume(double volume) {
        this.sfxVolume = Math.clamp(volume, 0.0, 1.0);
        LOG.debug("SFX volume set to {}", this.sfxVolume);
    }

    /**
     * Get the current music volume.
     *
     * @return music volume (0.0 - 1.0)
     */
    public double getMusicVolume() {
        return musicVolume;
    }

    /**
     * Get the current SFX volume.
     *
     * @return SFX volume (0.0 - 1.0)
     */
    public double getSfxVolume() {
        return sfxVolume;
    }

    /**
     * Set whether all audio is muted.
     *
     * @param muted true to mute all audio
     */
    public void setMuted(boolean muted) {
        this.muted = muted;
        if (muted) {
            stopAll();
        }
        LOG.info("Audio {}", muted ? "muted" : "unmuted");
    }

    /**
     * Check if audio is currently muted.
     *
     * @return true if muted
     */
    public boolean isMuted() {
        return muted;
    }

    /**
     * Get the music player for playlist support.
     *
     * @return the music player
     */
    public MusicPlayer getMusicPlayer() {
        return musicPlayer;
    }

    /**
     * Get the currently active music MediaPlayer, or null if none.
     *
     * @return the current MediaPlayer, or null
     */
    public MediaPlayer getCurrentMusicPlayer() {
        return currentMusicPlayer;
    }

    /**
     * Pre-load a sound effect into the cache.
     *
     * @param sfxName the SFX name to pre-load
     */
    public void preloadSFX(String sfxName) {
        String resourcePath = SFX_BASE_PATH + sfxName + ".wav";
        URL resource = getClass().getResource(resourcePath);
        if (resource != null) {
            sfxCache.putIfAbsent(sfxName, new AudioClip(resource.toExternalForm()));
            LOG.debug("Pre-loaded SFX: {}", sfxName);
        } else {
            LOG.warn("Cannot pre-load SFX, not found: {}", resourcePath);
        }
    }
}

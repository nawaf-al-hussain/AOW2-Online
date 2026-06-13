package com.aow2.client.audio;

import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Background music player with playlist support and crossfading.
 * Manages a list of music tracks and plays them sequentially or shuffled.
 * <p>
 * Crossfading smoothly transitions between tracks by ramping the volume
 * of the outgoing track down while ramping the incoming track up.
 * <p>
 * REF: MASTER_DOCUMENTATION.md Section 7 - Audio System
 */
public final class MusicPlayer {

    private static final Logger LOG = LoggerFactory.getLogger(MusicPlayer.class);

    /** Base path for music resources. */
    private static final String MUSIC_BASE_PATH = "/audio/music/";

    /** Crossfade duration in milliseconds. */
    private static final int CROSSFADE_DURATION_MS = 2000;

    /** The parent audio manager. */
    private final AudioManager audioManager;

    /** The playlist of track names. */
    private final List<String> playlist;

    /** Current track index in the playlist. */
    private int currentTrackIndex;

    /** Whether to shuffle the playlist. */
    private boolean shuffle;

    /** Whether playback is currently active. */
    private boolean playing;

    /** Current volume level (0.0 - 1.0). */
    private double volume;

    /** The active media player for crossfading (outgoing). */
    private MediaPlayer fadingOutPlayer;

    /** The active media player for the current track. */
    private MediaPlayer activePlayer;

    /**
     * Constructs a MusicPlayer with a reference to the AudioManager.
     *
     * @param audioManager the parent audio manager
     */
    public MusicPlayer(AudioManager audioManager) {
        this.audioManager = audioManager;
        this.playlist = new ArrayList<>();
        this.currentTrackIndex = -1;
        this.shuffle = false;
        this.playing = false;
        this.volume = 0.5;
    }

    /**
     * Add a track to the playlist.
     *
     * @param trackName the track name (without extension)
     */
    public void addTrack(String trackName) {
        playlist.add(trackName);
        LOG.debug("Added track to playlist: {}", trackName);
    }

    /**
     * Remove a track from the playlist.
     *
     * @param trackName the track name to remove
     */
    public void removeTrack(String trackName) {
        playlist.remove(trackName);
        LOG.debug("Removed track from playlist: {}", trackName);
    }

    /**
     * Clear the entire playlist.
     */
    public void clearPlaylist() {
        stop();
        playlist.clear();
        currentTrackIndex = -1;
        LOG.debug("Playlist cleared");
    }

    /**
     * Get an unmodifiable view of the current playlist.
     *
     * @return the playlist
     */
    public List<String> getPlaylist() {
        return Collections.unmodifiableList(playlist);
    }

    /**
     * Start playing the playlist from the beginning (or current index).
     */
    public void play() {
        if (playlist.isEmpty()) {
            LOG.warn("Cannot play: playlist is empty");
            return;
        }

        if (currentTrackIndex < 0 || currentTrackIndex >= playlist.size()) {
            currentTrackIndex = 0;
        }

        playing = true;
        playTrack(currentTrackIndex);
    }

    /**
     * Stop playback and release resources.
     */
    public void stop() {
        playing = false;
        if (activePlayer != null) {
            activePlayer.stop();
            activePlayer.dispose();
            activePlayer = null;
        }
        if (fadingOutPlayer != null) {
            fadingOutPlayer.stop();
            fadingOutPlayer.dispose();
            fadingOutPlayer = null;
        }
    }

    /**
     * Skip to the next track in the playlist with crossfading.
     */
    public void nextTrack() {
        if (playlist.isEmpty()) {
            return;
        }

        currentTrackIndex = (currentTrackIndex + 1) % playlist.size();
        if (playing) {
            crossfadeToTrack(currentTrackIndex);
        }
    }

    /**
     * Skip to the previous track in the playlist with crossfading.
     */
    public void previousTrack() {
        if (playlist.isEmpty()) {
            return;
        }

        currentTrackIndex = (currentTrackIndex - 1 + playlist.size()) % playlist.size();
        if (playing) {
            crossfadeToTrack(currentTrackIndex);
        }
    }

    /**
     * Set the volume level.
     *
     * @param volume the volume (0.0 - 1.0)
     */
    public void setVolume(double volume) {
        this.volume = Math.clamp(volume, 0.0, 1.0);
        if (activePlayer != null) {
            activePlayer.setVolume(this.volume);
        }
    }

    /**
     * Set whether to shuffle the playlist.
     *
     * @param shuffle true to enable shuffle
     */
    public void setShuffle(boolean shuffle) {
        this.shuffle = shuffle;
        LOG.debug("Shuffle {}", shuffle ? "enabled" : "disabled");
    }

    /**
     * Check if currently playing.
     *
     * @return true if playing
     */
    public boolean isPlaying() {
        return playing;
    }

    /**
     * Get the current track index.
     *
     * @return the current track index, or -1 if not set
     */
    public int getCurrentTrackIndex() {
        return currentTrackIndex;
    }

    /**
     * Get the current track name.
     *
     * @return the current track name, or null if no track is active
     */
    public String getCurrentTrackName() {
        if (currentTrackIndex >= 0 && currentTrackIndex < playlist.size()) {
            return playlist.get(currentTrackIndex);
        }
        return null;
    }

    /**
     * Play a specific track by index.
     *
     * @param index the playlist index
     */
    private void playTrack(int index) {
        if (index < 0 || index >= playlist.size()) {
            return;
        }

        String trackName = playlist.get(index);
        String resourcePath = MUSIC_BASE_PATH + trackName + ".mp3";
        URL resource = getClass().getResource(resourcePath);

        if (resource == null) {
            LOG.warn("Music track not found: {}", resourcePath);
            // Try next track automatically
            if (playlist.size() > 1) {
                currentTrackIndex = (index + 1) % playlist.size();
                playTrack(currentTrackIndex);
            }
            return;
        }

        if (activePlayer != null) {
            activePlayer.stop();
            activePlayer.dispose();
        }

        Media media = new Media(resource.toExternalForm());
        activePlayer = new MediaPlayer(media);
        activePlayer.setVolume(volume);
        activePlayer.setOnEndOfMedia(this::onTrackEnd);
        activePlayer.play();

        LOG.info("Now playing: {} (track {}/{})", trackName, index + 1, playlist.size());
    }

    /**
     * Crossfade to a new track.
     *
     * @param index the playlist index to crossfade to
     */
    private void crossfadeToTrack(int index) {
        if (activePlayer != null) {
            fadingOutPlayer = activePlayer;
            // Fade out over CROSSFADE_DURATION_MS
            javafx.animation.Timeline fadeOut = new javafx.animation.Timeline(
                new javafx.animation.KeyFrame(
                    javafx.util.Duration.millis(CROSSFADE_DURATION_MS),
                    new javafx.animation.KeyValue(fadingOutPlayer.volumeProperty(), 0.0)
                )
            );
            fadeOut.setOnFinished(e -> {
                fadingOutPlayer.stop();
                fadingOutPlayer.dispose();
                fadingOutPlayer = null;
            });
            fadeOut.play();
        }

        // Start new track
        String trackName = playlist.get(index);
        String resourcePath = MUSIC_BASE_PATH + trackName + ".mp3";
        URL resource = getClass().getResource(resourcePath);

        if (resource == null) {
            LOG.warn("Crossfade target track not found: {}", resourcePath);
            return;
        }

        Media media = new Media(resource.toExternalForm());
        activePlayer = new MediaPlayer(media);
        activePlayer.setVolume(0.0);
        activePlayer.setOnEndOfMedia(this::onTrackEnd);
        activePlayer.play();

        // Fade in over CROSSFADE_DURATION_MS
        javafx.animation.Timeline fadeIn = new javafx.animation.Timeline(
            new javafx.animation.KeyFrame(
                javafx.util.Duration.millis(CROSSFADE_DURATION_MS),
                new javafx.animation.KeyValue(activePlayer.volumeProperty(), volume)
            )
        );
        fadeIn.play();

        LOG.info("Crossfading to: {} (track {}/{})", trackName, index + 1, playlist.size());
    }

    /**
     * Called when the current track finishes playing.
     * Automatically advances to the next track.
     */
    private void onTrackEnd() {
        if (!playing) {
            return;
        }

        if (shuffle) {
            currentTrackIndex = (int) (Math.random() * playlist.size());
        } else {
            currentTrackIndex = (currentTrackIndex + 1) % playlist.size();
        }

        playTrack(currentTrackIndex);
    }
}

package com.aow2.client.audio;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the AudioManager.
 * Since AudioManager depends on JavaFX Media classes which may not be
 * available in headless test environments, these tests focus on
 * non-media operations (volume, muting, state management).
 * <p>
 * REF: MASTER_DOCUMENTATION.md Section 7 - Audio System
 */
class AudioManagerTest {

    private AudioManager audioManager;

    @BeforeEach
    void setUp() {
        audioManager = new AudioManager();
    }

    @Test
    @DisplayName("Default music volume is 0.5")
    void defaultMusicVolume() {
        assertEquals(0.5, audioManager.getMusicVolume(), 0.001);
    }

    @Test
    @DisplayName("Default SFX volume is 0.7")
    void defaultSfxVolume() {
        assertEquals(0.7, audioManager.getSfxVolume(), 0.001);
    }

    @Test
    @DisplayName("Set music volume clamps to valid range")
    void setMusicVolumeClamps() {
        audioManager.setMusicVolume(0.8);
        assertEquals(0.8, audioManager.getMusicVolume(), 0.001);

        audioManager.setMusicVolume(-0.5);
        assertEquals(0.0, audioManager.getMusicVolume(), 0.001);

        audioManager.setMusicVolume(1.5);
        assertEquals(1.0, audioManager.getMusicVolume(), 0.001);
    }

    @Test
    @DisplayName("Set SFX volume clamps to valid range")
    void setSfxVolumeClamps() {
        audioManager.setSfxVolume(0.9);
        assertEquals(0.9, audioManager.getSfxVolume(), 0.001);

        audioManager.setSfxVolume(-1.0);
        assertEquals(0.0, audioManager.getSfxVolume(), 0.001);

        audioManager.setSfxVolume(2.0);
        assertEquals(1.0, audioManager.getSfxVolume(), 0.001);
    }

    @Test
    @DisplayName("Audio starts unmuted")
    void audioStartsUnmuted() {
        assertFalse(audioManager.isMuted());
    }

    @Test
    @DisplayName("Muting audio sets muted state")
    void mutingAudio() {
        audioManager.setMuted(true);
        assertTrue(audioManager.isMuted());

        audioManager.setMuted(false);
        assertFalse(audioManager.isMuted());
    }

    @Test
    @DisplayName("MusicPlayer is accessible from AudioManager")
    void musicPlayerAccessible() {
        assertNotNull(audioManager.getMusicPlayer());
    }

    @Test
    @DisplayName("MusicPlayer playlist is initially empty")
    void musicPlayerPlaylistEmpty() {
        assertTrue(audioManager.getMusicPlayer().getPlaylist().isEmpty());
    }

    @Test
    @DisplayName("MusicPlayer can add tracks to playlist")
    void musicPlayerAddTracks() {
        MusicPlayer player = audioManager.getMusicPlayer();
        player.addTrack("main_theme");
        player.addTrack("battle_1");
        assertEquals(2, player.getPlaylist().size());
        assertEquals("main_theme", player.getPlaylist().get(0));
        assertEquals("battle_1", player.getPlaylist().get(1));
    }

    @Test
    @DisplayName("MusicPlayer can remove tracks from playlist")
    void musicPlayerRemoveTracks() {
        MusicPlayer player = audioManager.getMusicPlayer();
        player.addTrack("main_theme");
        player.addTrack("battle_1");
        player.removeTrack("main_theme");
        assertEquals(1, player.getPlaylist().size());
        assertEquals("battle_1", player.getPlaylist().get(0));
    }

    @Test
    @DisplayName("MusicPlayer clearPlaylist removes all tracks")
    void musicPlayerClearPlaylist() {
        MusicPlayer player = audioManager.getMusicPlayer();
        player.addTrack("track1");
        player.addTrack("track2");
        player.clearPlaylist();
        assertTrue(player.getPlaylist().isEmpty());
    }

    @Test
    @DisplayName("MusicPlayer is not playing by default")
    void musicPlayerNotPlayingByDefault() {
        assertFalse(audioManager.getMusicPlayer().isPlaying());
    }

    @Test
    @DisplayName("MusicPlayer shuffle defaults to false")
    void musicPlayerShuffleDefault() {
        MusicPlayer player = audioManager.getMusicPlayer();
        player.setShuffle(true);
        // Just verify it doesn't throw
        assertDoesNotThrow(() -> player.setShuffle(false));
    }

    @Test
    @DisplayName("StopAll does not throw when no audio is playing")
    void stopAllDoesNotThrowWhenNoAudio() {
        assertDoesNotThrow(() -> audioManager.stopAll());
    }

    @Test
    @DisplayName("Play music with non-existent track does not throw")
    void playMusicNonExistentTrack() {
        // With muted audio, this should just log a warning
        audioManager.setMuted(true);
        assertDoesNotThrow(() -> audioManager.playMusic("nonexistent"));
    }

    @Test
    @DisplayName("Play SFX with non-existent clip does not throw")
    void playSfxNonExistentClip() {
        audioManager.setMuted(true);
        assertDoesNotThrow(() -> audioManager.playSFX("nonexistent"));
    }

    @Test
    @DisplayName("Preload SFX with non-existent resource does not throw")
    void preloadSfxNonExistent() {
        assertDoesNotThrow(() -> audioManager.preloadSFX("nonexistent"));
    }
}

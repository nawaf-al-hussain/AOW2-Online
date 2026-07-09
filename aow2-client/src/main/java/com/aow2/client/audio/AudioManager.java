package com.aow2.client.audio;

import javafx.scene.media.AudioClip;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages game audio: music, sound effects, ambient sounds.
 * Uses JavaFX MediaPlayer for music and AudioClip for SFX.
 * <p>
 * SOUND INTEGRATION: SFX files are OGG/Vorbis format decoded from the iOS v2.2
 * build of Art of War 2. The AudioManager maps game events to the original
 * iOS SFX filenames and supports random-variant selection for sounds that have
 * multiple versions (e.g., 6 select clicks, 7 heavy explosions, 5 screams).
 * <p>
 * REF: docs/RE/external_versions/ipa_ios_v2.2/DECODED_ASSETS.md §3
 */
public final class AudioManager {

    private static final Logger LOG = LoggerFactory.getLogger(AudioManager.class);

    private static final String AUDIO_BASE_PATH = "/audio/";
    private static final String MUSIC_BASE_PATH = AUDIO_BASE_PATH + "music/";
    private static final String SFX_BASE_PATH = AUDIO_BASE_PATH + "sfx/";

    private double musicVolume;
    private double sfxVolume;
    private MediaPlayer currentMusicPlayer;
    private final MusicPlayer musicPlayer;
    private final Map<String, AudioClip> sfxCache;
    private boolean muted;

    /** RNG for random-variant SFX selection. */
    private final Random rng = new Random();

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

    // =========================================================================
    // Music
    // =========================================================================

    /**
     * Play background music by track name.
     * The track file should be located at /audio/music/{trackName}.ogg.
     *
     * @param trackName the music track name (without extension)
     */
    public void playMusic(String trackName) {
        if (muted) {
            LOG.debug("Audio muted, not playing music: {}", trackName);
            return;
        }

        stopMusic();

        String resourcePath = MUSIC_BASE_PATH + trackName + ".ogg";
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
     * Stop the currently playing music.
     */
    public void stopMusic() {
        if (currentMusicPlayer != null) {
            currentMusicPlayer.stop();
            currentMusicPlayer.dispose();
            currentMusicPlayer = null;
        }
    }

    // =========================================================================
    // Sound Effects — original iOS SFX with random-variant selection
    // =========================================================================

    /**
     * Plays a random variant of a multi-variant SFX.
     * e.g., playSFXVariant("select", 6) plays select_1 through select_6 at random.
     *
     * @param baseName the base SFX name without the _N suffix (e.g., "select")
     * @param maxVariant the number of available variants (e.g., 6)
     */
    public void playSFXVariant(String baseName, int maxVariant) {
        if (maxVariant <= 1) {
            playSFX(baseName + "_1");
        } else {
            playSFX(baseName + "_" + (1 + rng.nextInt(maxVariant)));
        }
    }

    /**
     * Play a sound effect by exact name.
     * SFX files are located at /audio/sfx/{sfxName}.ogg.
     *
     * @param sfxName the sound effect name (without extension), e.g., "sniper_1"
     */
    public void playSFX(String sfxName) {
        if (muted) {
            LOG.debug("Audio muted, not playing SFX: {}", sfxName);
            return;
        }

        AudioClip clip = sfxCache.get(sfxName);
        if (clip != null) {
            clip.setVolume(sfxVolume);
            clip.play();
            return;
        }

        String resourcePath = SFX_BASE_PATH + sfxName + ".ogg";
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
     * Pre-load a sound effect into the cache.
     *
     * @param sfxName the SFX name to pre-load (without extension)
     */
    public void preloadSFX(String sfxName) {
        String resourcePath = SFX_BASE_PATH + sfxName + ".ogg";
        URL resource = getClass().getResource(resourcePath);
        if (resource != null) {
            sfxCache.putIfAbsent(sfxName, new AudioClip(resource.toExternalForm()));
            LOG.debug("Pre-loaded SFX: {}", sfxName);
        } else {
            LOG.warn("Cannot pre-load SFX, not found: {}", resourcePath);
        }
    }

    // =========================================================================
    // High-level SFX methods — map game events to the correct iOS SFX
    // =========================================================================

    /** Unit selected by player. 6 variants. */
    public void playSelect() { playSFXVariant("select", 6); }

    /** Unit acknowledges move/attack order. 4 variants. */
    public void playAffirmative() { playSFXVariant("affirmative", 4); }

    /** Quiet affirmative (for large groups). 7 variants + _11. */
    public void playAffirmativeLow() { playSFXVariant("affirmative_l", 7); }

    /** "Attack!" voice command. */
    public void playAttack() { playSFX("attack_1"); }

    /** Infantry death scream. 5 variants. */
    public void playScream() { playSFXVariant("scream", 5); }

    /** UI click. */
    public void playClick() { playSFX("click_1"); }

    /** Menu open. */
    public void playMenuOpen() { playSFX("menu_open_1"); }

    /** Menu close / error. */
    public void playMenuClose() { playSFX("menu_close_1"); }

    /** Construction started. */
    public void playBuild() { playSFX("build_1"); }

    /** Construction completed. */
    public void playBuildingReady() { playSFX("building_ready_1"); }

    /** Research completed. */
    public void playResearchComplete() { playSFX("research_complete_1"); }

    /** Credits received. */
    public void playMoney() { playSFX("money_1"); }

    /** Light explosion (infantry death, light vehicle). 5 variants. */
    public void playExplosionLight() { playSFXVariant("explode_light", 5); }

    /** Heavy explosion (tank destruction). 7 variants. */
    public void playExplosionHeavy() { playSFXVariant("explode_heavy", 7); }

    /** Building destruction. 2 variants. */
    public void playExplosionBuilding() { playSFXVariant("explode_bld", 2); }

    /** Machine gun fire (light). 6 variants. */
    public void playMachineLight() { playSFXVariant("machine_light", 6); }

    /** Machine gun fire (medium). 3 variants. */
    public void playMachineMed() { playSFXVariant("machine_med", 3); }

    /** Sniper rifle fire. 3 variants. */
    public void playSniper() { playSFXVariant("sniper", 3); }

    /** Light tank cannon. 4 variants. */
    public void playTankLight() { playSFXVariant("tank_light", 4); }

    /** Heavy tank cannon. 4 variants. */
    public void playTankHeavy() { playSFXVariant("tank_heavy", 4); }

    /** Siege tank cannon. 3 variants. */
    public void playTankSiege() { playSFXVariant("tank_siege", 3); }

    /** Rocket launcher fire. 3 variants. */
    public void playRocket() { playSFXVariant("rocket_light", 3); }

    /** Flamethrower. */
    public void playFlamethrower() { playSFX("flamethrower_1"); }

    /**
     * Plays the appropriate weapon SFX based on the unit's weapon type.
     *
     * @param weaponType the weapon type from UnitStats
     */
    public void playWeaponSound(com.aow2.common.model.WeaponType weaponType) {
        if (weaponType == null) return;
        switch (weaponType) {
            case BULLET -> playMachineLight();
            case MACHINE_GUN -> playMachineMed();
            case SNIPER_RIFLE -> playSniper();
            case ROCKET -> playRocket();
            case ARTILLERY -> playTankSiege();
            case FLAME -> playFlamethrower();
            case NONE -> { /* silent */ }
        }
    }

    /**
     * Plays the appropriate explosion SFX based on the entity type that was destroyed.
     *
     * @param isVehicle true if the destroyed entity was a vehicle
     * @param isBuilding true if the destroyed entity was a building
     */
    public void playExplosionFor(boolean isVehicle, boolean isBuilding) {
        if (isBuilding) {
            playExplosionBuilding();
        } else if (isVehicle) {
            playExplosionHeavy();
        } else {
            playExplosionLight();
        }
    }

    // =========================================================================
    // Volume / mute control
    // =========================================================================

    public void stopAll() {
        stopMusic();
        sfxCache.values().forEach(AudioClip::stop);
        LOG.info("All audio stopped");
    }

    public void setMusicVolume(double volume) {
        this.musicVolume = Math.clamp(volume, 0.0, 1.0);
        if (currentMusicPlayer != null) {
            currentMusicPlayer.setVolume(this.musicVolume);
        }
        musicPlayer.setVolume(this.musicVolume);
    }

    public void setSfxVolume(double volume) {
        this.sfxVolume = Math.clamp(volume, 0.0, 1.0);
    }

    public double getMusicVolume() { return musicVolume; }
    public double getSfxVolume() { return sfxVolume; }

    public void setMuted(boolean muted) {
        this.muted = muted;
        if (muted) stopAll();
        LOG.info("Audio {}", muted ? "muted" : "unmuted");
    }

    public boolean isMuted() { return muted; }

    public MusicPlayer getMusicPlayer() { return musicPlayer; }

    public MediaPlayer getCurrentMusicPlayer() { return currentMusicPlayer; }
}

package com.aow2.client.scene;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.Text;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Asset test scene for validating the decoded iOS sprite and audio assets.
 * <p>
 * This scene loads a sample of decoded sprites (from the iOS v2.2 build) and
 * OGG SFX files, displaying them in a grid with play buttons for each sound.
 * It serves as an end-to-end validation that the asset pipeline works:
 * <ol>
 *   <li>Raw iOS assets (binary-packed i0 containers, WAV audio) are decoded
 *       by scripts/decode_ios_sprites.py and scripts/convert_ios_audio_to_ogg.py</li>
 *   <li>The decoded assets are copied into aow2-client/src/main/resources/</li>
 *   <li>This scene loads them via classpath getResourceAsStream() and displays them</li>
 * </ol>
 * <p>
 * If the sprites display correctly and the SFX play when buttons are clicked,
 * the asset pipeline is validated and the assets are ready for use in the
 * actual game scenes (GameScene, CampaignScene, etc.).
 * <p>
 * Usage: Add a "Asset Test" button to MainMenuScene that switches to this
 * scene, or invoke directly from AOW2App for testing.
 */
public class AssetTestScene {

    private static final Logger LOG = LoggerFactory.getLogger(AssetTestScene.class);

    /** Background color matching the military aesthetic. */
    private static final Color BG_COLOR = Color.rgb(15, 18, 22);

    /** The root pane for this scene. */
    private final StackPane root;

    /** Callback for returning to the main menu. */
    private Runnable backCallback;

    /** Cached audio clips for SFX playback. */
    private final Map<String, Clip> sfxCache = new HashMap<>();

    /** Button style matching MainMenuScene. */
    private static final String BTN_STYLE = "-fx-background-color: rgb(40, 45, 35); "
        + "-fx-text-fill: rgb(210, 200, 160); "
        + "-fx-border-color: rgb(100, 110, 70); -fx-border-width: 1; "
        + "-fx-font-size: 13px; -fx-font-weight: bold; "
        + "-fx-padding: 6 20 6 20; -fx-cursor: hand;";

    /** Button hover style. */
    private static final String BTN_HOVER_STYLE = "-fx-background-color: rgb(60, 70, 45); "
        + "-fx-text-fill: rgb(240, 230, 180); "
        + "-fx-border-color: rgb(140, 150, 90); -fx-border-width: 2; "
        + "-fx-font-size: 13px; -fx-font-weight: bold; "
        + "-fx-padding: 6 20 6 20; -fx-cursor: hand;";

    /** Sprites to display (filename + description). */
    private static final String[][] TEST_SPRITES = {
        {"en_000.png", "Title screen — Global Confederation globe"},
        {"en_001.png", "Terrain tile sheet — grass/dirt/water"},
        {"en_005.png", "Confederation (blue) sprite sheet — 15 sub-sprites"},
        {"en_006.png", "Rebels (red) sprite sheet — 12 sub-sprites"},
        {"en_016.png", "Explosion effect (RGBA with transparency)"},
        {"en_028.png", "UI elements sheet — panels, icons, landmass"},
        {"en_031.png", "Water texture (RGBA, animated variant 1)"},
    };

    /** SFX files to test (filename + description). */
    private static final String[][] TEST_SFX = {
        {"select_1.ogg", "UI: Unit select click"},
        {"affirmative_1.ogg", "UI: 'Acknowledged' voice response"},
        {"build_1.ogg", "UI: Construction start"},
        {"sniper_1.ogg", "Weapon: Sniper rifle fire"},
        {"tank_heavy_1.ogg", "Weapon: Heavy tank cannon"},
        {"explode_heavy_1.ogg", "Explosion: Heavy (tank destruction)"},
        {"scream_1.ogg", "Voice: Infantry death scream"},
        {"attack_1.ogg", "Voice: 'Attack!' command response"},
    };

    /**
     * Constructs a new AssetTestScene.
     */
    public AssetTestScene() {
        this.root = new StackPane();
        buildUI();
        LOG.info("AssetTestScene created");
    }

    /**
     * Sets the callback for returning to the main menu.
     *
     * @param callback the back callback
     */
    public void setBackCallback(Runnable callback) {
        this.backCallback = callback;
    }

    /**
     * Returns the root pane for adding to the FXGL game scene.
     *
     * @return the root StackPane
     */
    public StackPane getRoot() {
        return root;
    }

    /**
     * Builds the asset test UI.
     */
    private void buildUI() {
        root.setPrefSize(1280, 720);
        root.setStyle("-fx-background-color: rgb(15, 18, 22);");

        BorderPane borderPane = new BorderPane();
        borderPane.setPadding(new Insets(20));

        // Title
        Text title = new Text("Asset Pipeline Test — Decoded iOS Sprites & OGG Audio");
        title.setFont(Font.font("System", 24));
        title.setFill(Color.rgb(210, 200, 160));
        Text subtitle = new Text("Validates that decode_ios_sprites.py and convert_ios_audio_to_ogg.py produced loadable assets");
        subtitle.setFont(Font.font("System", 13));
        subtitle.setFill(Color.rgb(140, 150, 90));
        VBox header = new VBox(5, title, subtitle);
        header.setPadding(new Insets(0, 0, 20, 0));
        borderPane.setTop(header);

        // Center: two-column layout (sprites left, audio right)
        HBox center = new HBox(20);

        // Left column: sprite gallery
        VBox spriteCol = new VBox(10);
        Text spriteHeader = new Text("Decoded Sprites (loaded via classpath getResourceAsStream)");
        spriteHeader.setFont(Font.font("System", 16));
        spriteHeader.setFill(Color.rgb(210, 200, 160));
        spriteCol.getChildren().add(spriteHeader);

        TilePane spriteGrid = new TilePane();
        spriteGrid.setHgap(15);
        spriteGrid.setVgap(15);
        spriteGrid.setPrefColumns(2);

        for (String[] spriteInfo : TEST_SPRITES) {
            spriteGrid.getChildren().add(createSpriteCard(spriteInfo[0], spriteInfo[1]));
        }
        spriteCol.getChildren().add(spriteGrid);
        center.getChildren().add(spriteCol);

        // Right column: audio test buttons
        VBox audioCol = new VBox(10);
        audioCol.setPrefWidth(400);
        Text audioHeader = new Text("Converted OGG SFX (played via javax.sound.sampled)");
        audioHeader.setFont(Font.font("System", 16));
        audioHeader.setFill(Color.rgb(210, 200, 160));
        audioCol.getChildren().add(audioHeader);

        for (String[] sfxInfo : TEST_SFX) {
            audioCol.getChildren().add(createSfxButton(sfxInfo[0], sfxInfo[1]));
        }

        // Music test button
        audioCol.getChildren().add(new Label(" "));
        Button musicBtn = new Button("Play music.ogg (full track, ~2 min)");
        styleButton(musicBtn);
        musicBtn.setOnAction(e -> {
            LOG.info("Playing music.ogg");
            playSfx("/audio/music/music.ogg");
        });
        audioCol.getChildren().add(musicBtn);

        center.getChildren().add(audioCol);
        borderPane.setCenter(center);

        // Bottom: back button
        HBox footer = new HBox();
        footer.setAlignment(Pos.CENTER_RIGHT);
        footer.setPadding(new Insets(20, 0, 0, 0));
        Button backBtn = new Button("← Back to Main Menu");
        styleButton(backBtn);
        backBtn.setOnAction(e -> {
            LOG.info("Back button clicked");
            stopAllSfx();
            if (backCallback != null) {
                backCallback.run();
            }
        });
        footer.getChildren().add(backBtn);
        borderPane.setBottom(footer);

        root.getChildren().add(borderPane);
    }

    /**
     * Creates a card displaying a sprite image with its filename and description.
     */
    private VBox createSpriteCard(String filename, String description) {
        VBox card = new VBox(5);
        card.setPadding(new Insets(8));
        card.setStyle("-fx-background-color: rgb(25, 28, 22); "
            + "-fx-border-color: rgb(60, 65, 45); -fx-border-width: 1;");

        // Load the image from classpath
        String resourcePath = "/assets/sprites/entities/" + filename;
        InputStream is = getClass().getResourceAsStream(resourcePath);

        if (is != null) {
            Image image = new Image(is);
            ImageView view = new ImageView(image);
            // Scale to fit in a 180x180 box while preserving aspect ratio
            double scale = Math.min(180.0 / image.getWidth(), 180.0 / image.getHeight());
            scale = Math.min(scale, 1.0);  // don't upscale
            view.setFitWidth(image.getWidth() * scale);
            view.setFitHeight(image.getHeight() * scale);
            view.setPreserveRatio(true);
            card.getChildren().add(view);
            try {
                is.close();
            } catch (IOException e) {
                LOG.warn("Failed to close sprite input stream: {}", e.getMessage());
            }
        } else {
            Text notFound = new Text("NOT FOUND:\n" + filename);
            notFound.setFill(Color.RED);
            notFound.setFont(Font.font("System", 12));
            card.getChildren().add(notFound);
        }

        // Filename label
        Text nameText = new Text(filename);
        nameText.setFont(Font.font("System", 11));
        nameText.setFill(Color.rgb(180, 190, 150));

        // Description label
        Text descText = new Text(description);
        descText.setFont(Font.font("System", 10));
        descText.setFill(Color.rgb(140, 150, 90));
        descText.setWrappingWidth(180);

        card.getChildren().addAll(nameText, descText);
        return card;
    }

    /**
     * Creates a button that plays an OGG SFX file when clicked.
     */
    private HBox createSfxButton(String filename, String description) {
        HBox row = new HBox(10);
        row.setAlignment(Pos.CENTER_LEFT);

        Button playBtn = new Button("▶ " + filename);
        styleButton(playBtn);
        playBtn.setPrefWidth(200);
        playBtn.setOnAction(e -> {
            LOG.info("Playing SFX: {}", filename);
            playSfx("/audio/sfx/" + filename);
        });

        Text descText = new Text(description);
        descText.setFont(Font.font("System", 11));
        descText.setFill(Color.rgb(180, 190, 150));

        row.getChildren().addAll(playBtn, descText);
        return row;
    }

    /**
     * Applies the standard button styling and hover effects.
     */
    private void styleButton(Button btn) {
        btn.setStyle(BTN_STYLE);
        btn.setOnMouseEntered(e -> btn.setStyle(BTN_HOVER_STYLE));
        btn.setOnMouseExited(e -> btn.setStyle(BTN_STYLE));
    }

    /**
     * Plays an OGG audio file from the classpath.
     * <p>
     * Uses javax.sound.sampled (not JavaFX MediaPlayer) because the project's
     * AudioManager already uses this approach for SFX. The OGG format is
     * supported via Java's AudioSystem which delegates to the SPI layer.
     * <p>
     * If the OGG SPI is not on the classpath, this will fail with
     * UnsupportedAudioFileException. In that case, the test still validates
     * that the file exists on the classpath (the error message will confirm
     * this).
     *
     * @param resourcePath classpath resource path (e.g., "/audio/sfx/select_1.ogg")
     */
    private void playSfx(String resourcePath) {
        // Check cache first
        if (sfxCache.containsKey(resourcePath)) {
            Clip clip = sfxCache.get(resourcePath);
            if (clip.isRunning()) {
                clip.stop();
            }
            clip.setFramePosition(0);
            clip.start();
            return;
        }

        try (InputStream rawStream = getClass().getResourceAsStream(resourcePath)) {
            if (rawStream == null) {
                LOG.error("Audio resource not found on classpath: {}", resourcePath);
                return;
            }
            // BufferedInputStream is required for AudioSystem which marks/resets
            InputStream bufferedStream = new BufferedInputStream(rawStream);
            AudioInputStream audioStream = AudioSystem.getAudioInputStream(bufferedStream);
            Clip clip = AudioSystem.getClip();
            clip.open(audioStream);
            audioStream.close();
            sfxCache.put(resourcePath, clip);
            clip.start();
            LOG.info("Playing audio: {} ({} ms)", resourcePath, clip.getMicrosecondLength() / 1000);
        } catch (UnsupportedAudioFileException e) {
            LOG.error("OGG format not supported — add a Vorbis SPI (e.g., jorbis or tritonus) to the classpath. "
                + "Resource was found at: {}", resourcePath);
        } catch (LineUnavailableException e) {
            LOG.error("Audio line unavailable for: {} — {}", resourcePath, e.getMessage());
        } catch (IOException e) {
            LOG.error("IO error playing: {} — {}", resourcePath, e.getMessage());
        }
    }

    /**
     * Stops all currently playing SFX and releases audio resources.
     * Should be called when leaving the scene.
     */
    public void stopAllSfx() {
        for (Map.Entry<String, Clip> entry : sfxCache.entrySet()) {
            Clip clip = entry.getValue();
            if (clip.isRunning()) {
                clip.stop();
            }
            clip.close();
        }
        sfxCache.clear();
        LOG.info("All SFX stopped and audio resources released");
    }

    /**
     * Cleanup method called when the scene is disposed.
     */
    public void dispose() {
        stopAllSfx();
    }
}

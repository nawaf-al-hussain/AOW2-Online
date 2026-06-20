package com.aow2.client.scene;

import com.aow2.common.model.Faction;
import com.aow2.core.replay.ReplayFile;
import com.aow2.core.replay.ReplayPlayer;

import javafx.animation.AnimationTimer;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.Slider;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Replay viewer JavaFX scene for browsing and controlling replay playback.
 * <p>
 * Features:
 * - Load replay files (.aow2r) via FileChooser
 * - Transport controls: restart, step back, play/pause, step forward, fast forward
 * - Seek bar for scrubbing through replay ticks
 * - Speed controls (1x, 2x, 4x)
 * - Metadata display: map name, player factions, tick count, command count
 * - Dark theme with amber/gold accents matching the game aesthetic
 * <p>
 * This is a pure JavaFX scene (no FXGL dependency) intended for use as a utility scene.
 * <p>
 * REF: phases.md Phase 11 - replay playback UI
 */
public class ReplayViewerScene {

    private static final Logger LOG = LoggerFactory.getLogger(ReplayViewerScene.class);

    // --- Colors ---
    private static final String BG_DARK = "#1a1a2e";
    private static final String BG_PANEL = "#16213e";
    private static final String BG_HEADER = "#0f3460";
    private static final String ACCENT_GOLD = "#e2b714";
    private static final String ACCENT_AMBER = "#f0a500";
    private static final String TEXT_PRIMARY = "#e0d8c8";
    private static final String TEXT_SECONDARY = "#8a8478";
    private static final String TEXT_MUTED = "#5a5650";
    private static final String BORDER_COLOR = "#2a2a4a";
    private static final String BTN_BG = "#1e2a4a";
    private static final String BTN_BG_HOVER = "#2a3a5a";
    private static final String BTN_DANGER = "#4a1e2a";
    private static final String BTN_DANGER_HOVER = "#5a2a3a";

    private static final String FONT_FAMILY = "Consolas";

    // --- Button styles ---
    private static final String BTN_STYLE =
        "-fx-background-color: " + BTN_BG + "; "
        + "-fx-text-fill: " + TEXT_PRIMARY + "; "
        + "-fx-border-color: " + BORDER_COLOR + "; -fx-border-width: 1; "
        + "-fx-font-size: 13px; -fx-font-weight: bold; "
        + "-fx-padding: 6 14 6 14; -fx-cursor: hand; "
        + "-fx-font-family: " + FONT_FAMILY + ";";

    private static final String BTN_HOVER_STYLE =
        "-fx-background-color: " + BTN_BG_HOVER + "; "
        + "-fx-text-fill: #fff8e0; "
        + "-fx-border-color: " + ACCENT_GOLD + "; -fx-border-width: 1.5; "
        + "-fx-font-size: 13px; -fx-font-weight: bold; "
        + "-fx-padding: 6 14 6 14; -fx-cursor: hand; "
        + "-fx-font-family: " + FONT_FAMILY + ";";

    private static final String BTN_ACCENT_STYLE =
        "-fx-background-color: #3a3020; "
        + "-fx-text-fill: " + ACCENT_GOLD + "; "
        + "-fx-border-color: " + ACCENT_AMBER + "; -fx-border-width: 1.5; "
        + "-fx-font-size: 13px; -fx-font-weight: bold; "
        + "-fx-padding: 6 18 6 18; -fx-cursor: hand; "
        + "-fx-font-family: " + FONT_FAMILY + ";";

    private static final String BTN_ACCENT_HOVER_STYLE =
        "-fx-background-color: #4a4030; "
        + "-fx-text-fill: #fff0c0; "
        + "-fx-border-color: " + ACCENT_GOLD + "; -fx-border-width: 2; "
        + "-fx-font-size: 13px; -fx-font-weight: bold; "
        + "-fx-padding: 6 18 6 18; -fx-cursor: hand; "
        + "-fx-font-family: " + FONT_FAMILY + ";";

    private static final String BACK_BTN_STYLE =
        "-fx-background-color: " + BTN_DANGER + "; "
        + "-fx-text-fill: #e0b0b0; "
        + "-fx-border-color: #6a3a4a; -fx-border-width: 1; "
        + "-fx-font-size: 13px; -fx-font-weight: bold; "
        + "-fx-padding: 6 16 6 16; -fx-cursor: hand; "
        + "-fx-font-family: " + FONT_FAMILY + ";";

    private static final String BACK_BTN_HOVER_STYLE =
        "-fx-background-color: " + BTN_DANGER_HOVER + "; "
        + "-fx-text-fill: #f0d0d0; "
        + "-fx-border-color: #8a5a6a; -fx-border-width: 1.5; "
        + "-fx-font-size: 13px; -fx-font-weight: bold; "
        + "-fx-padding: 6 16 6 16; -fx-cursor: hand; "
        + "-fx-font-family: " + FONT_FAMILY + ";";

    // --- UI components ---
    private final ReplayPlayer player = new ReplayPlayer();
    private final StackPane root = new StackPane();

    private Label tickLabel;          // info bar tick label
    private Label tickViewerLabel;     // viewer area tick label
    private Label mapNameHeaderLabel;
    private Label mapNameViewerLabel;
    private Label factionsLabel;
    private Label commandCountLabel;
    private Label statusLabel;
    private Slider seekBar;
    private Label timeLabel;
    private ToggleButton playPauseBtn;
    private final ToggleGroup speedGroup = new ToggleGroup();
    private ToggleButton speed1xBtn;
    private ToggleButton speed2xBtn;
    private ToggleButton speed4xBtn;

    private AnimationTimer playbackTimer;
    private long lastFrameTime = 0;
    private long accumulator = 0;
    private int playbackSpeed = 1;

    /** Callback invoked when the Back button is pressed. */
    private final Consumer<String> onBack;

    /**
     * Private constructor — use {@link #createScene(Consumer)} factory method.
     *
     * @param onBack callback invoked when Back is pressed; receives the string "back"
     */
    private ReplayViewerScene(Consumer<String> onBack) {
        this.onBack = onBack;
    }

    /**
     * Creates a new ReplayViewerScene and returns a JavaFX {@link Scene}.
     *
     * @param onBack callback invoked when the user presses the Back button
     * @return a fully constructed JavaFX Scene
     */
    public static Scene createScene(Consumer<String> onBack) {
        ReplayViewerScene scene = new ReplayViewerScene(onBack);
        scene.buildUI();
        scene.startTimer();
        LOG.info("ReplayViewerScene created");
        return new Scene(scene.root, 1280, 720);
    }

    // =========================================================================
    // UI Construction
    // =========================================================================

    /**
     * Builds the complete UI layout.
     */
    private void buildUI() {
        root.setPrefSize(1280, 720);
        root.setStyle("-fx-background-color: " + BG_DARK + ";");

        BorderPane content = new BorderPane();
        content.setPadding(new Insets(0));

        content.setTop(buildHeader());
        content.setCenter(buildViewerArea());
        content.setBottom(buildControlsArea());

        root.getChildren().add(content);
    }

    // --- Header ---

    private HBox buildHeader() {
        HBox header = new HBox(16);
        header.setPadding(new Insets(12, 20, 12, 20));
        header.setAlignment(Pos.CENTER_LEFT);
        header.setStyle("-fx-background-color: " + BG_HEADER + "; "
            + "-fx-border-color: " + BORDER_COLOR + "; -fx-border-width: 0 0 1 0;");

        // Back button
        Button backBtn = createButton("\u2190 Back", BACK_BTN_STYLE, BACK_BTN_HOVER_STYLE);
        backBtn.setOnAction(e -> {
            player.pause();
            if (onBack != null) {
                onBack.accept("back");
            }
        });

        // Title
        Label titleLabel = new Label("REPLAY VIEWER");
        titleLabel.setFont(Font.font(FONT_FAMILY, 22));
        titleLabel.setTextFill(Color.web(ACCENT_GOLD));

        // Spacer
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // Map name display (right side of header)
        mapNameHeaderLabel = new Label("No replay loaded");
        mapNameHeaderLabel.setFont(Font.font(FONT_FAMILY, 13));
        mapNameHeaderLabel.setTextFill(Color.web(TEXT_SECONDARY));

        header.getChildren().addAll(backBtn, titleLabel, spacer, mapNameHeaderLabel);
        return header;
    }

    // --- Viewer area ---

    private StackPane buildViewerArea() {
        StackPane viewerPane = new StackPane();
        viewerPane.setStyle("-fx-background-color: " + BG_DARK + ";");
        VBox.setVgrow(viewerPane, Priority.ALWAYS);

        VBox placeholder = new VBox(20);
        placeholder.setAlignment(Pos.CENTER);
        placeholder.setPadding(new Insets(40));
        placeholder.setStyle("-fx-background-color: " + BG_PANEL + "; "
            + "-fx-border-color: " + BORDER_COLOR + "; -fx-border-width: 1; "
            + "-fx-border-radius: 4;");

        // Map name
        mapNameViewerLabel = new Label("No Replay Loaded");
        mapNameViewerLabel.setFont(Font.font(FONT_FAMILY, 24));
        mapNameViewerLabel.setTextFill(Color.web(ACCENT_GOLD));

        // Factions
        factionsLabel = new Label("Load a replay file to view details");
        factionsLabel.setFont(Font.font(FONT_FAMILY, 14));
        factionsLabel.setTextFill(Color.web(TEXT_SECONDARY));

        // Tick counter
        tickViewerLabel = new Label("Tick: 0 / 0");
        tickViewerLabel.setFont(Font.font(FONT_FAMILY, 16));
        tickViewerLabel.setTextFill(Color.web(TEXT_PRIMARY));

        // Command count
        commandCountLabel = new Label("Commands: 0");
        commandCountLabel.setFont(Font.font(FONT_FAMILY, 13));
        commandCountLabel.setTextFill(Color.web(TEXT_MUTED));

        // Status label
        statusLabel = new Label("\u25B6 Press Play or load a replay to begin");
        statusLabel.setFont(Font.font(FONT_FAMILY, 13));
        statusLabel.setTextFill(Color.web(ACCENT_AMBER));

        // Placeholder notice
        Label noticeLabel = new Label("Replay rendering not yet implemented \u2014 showing metadata");
        noticeLabel.setFont(Font.font(FONT_FAMILY, 11));
        noticeLabel.setTextFill(Color.web(TEXT_MUTED));

        placeholder.getChildren().addAll(
            mapNameViewerLabel,
            new Separator(),
            factionsLabel,
            tickViewerLabel,
            commandCountLabel,
            new Separator(),
            statusLabel,
            noticeLabel
        );

        viewerPane.getChildren().add(placeholder);
        return viewerPane;
    }

    // --- Controls area ---

    private VBox buildControlsArea() {
        VBox controlsBox = new VBox(8);
        controlsBox.setPadding(new Insets(12, 20, 16, 20));
        controlsBox.setStyle("-fx-background-color: " + BG_PANEL + "; "
            + "-fx-border-color: " + BORDER_COLOR + "; -fx-border-width: 1 0 0 0;");

        // Row 1: Transport controls + seek bar
        HBox transportRow = buildTransportRow();
        // Row 2: Info bar
        HBox infoRow = buildInfoRow();

        controlsBox.getChildren().addAll(transportRow, infoRow);
        return controlsBox;
    }

    private HBox buildTransportRow() {
        HBox row = new HBox(8);
        row.setAlignment(Pos.CENTER);

        // Transport buttons
        Button restartBtn = createButton("|\u25C4\u25C4", BTN_STYLE, BTN_HOVER_STYLE);
        restartBtn.setTooltip(createTooltip("Restart (seek to beginning and play)"));
        restartBtn.setOnAction(e -> handleRestart());

        Button stepBackBtn = createButton("\u25C4", BTN_STYLE, BTN_HOVER_STYLE);
        stepBackBtn.setTooltip(createTooltip("Step Back 100 ticks"));
        stepBackBtn.setOnAction(e -> handleStepBack());

        playPauseBtn = new ToggleButton("\u25B6");
        playPauseBtn.setToggleGroup(new ToggleGroup()); // standalone
        playPauseBtn.setSelected(false);
        styleButton(playPauseBtn, BTN_ACCENT_STYLE, BTN_ACCENT_HOVER_STYLE);
        playPauseBtn.setTooltip(createTooltip("Play / Pause"));
        playPauseBtn.selectedProperty().addListener((obs, oldVal, newVal) -> {
            playPauseBtn.setText(newVal ? "\u23F8" : "\u25B6");
        });
        playPauseBtn.setOnAction(e -> handlePlayPause());

        Button stepForwardBtn = createButton("\u25BA", BTN_STYLE, BTN_HOVER_STYLE);
        stepForwardBtn.setTooltip(createTooltip("Step Forward 1 tick"));
        stepForwardBtn.setOnAction(e -> handleStepForward());

        Button fastForwardBtn = createButton("\u25BA\u25BA|", BTN_STYLE, BTN_HOVER_STYLE);
        fastForwardBtn.setTooltip(createTooltip("Fast Forward 100 ticks"));
        fastForwardBtn.setOnAction(e -> handleFastForward());

        // Seek bar
        seekBar = new Slider(0, 100, 0);
        seekBar.setPrefWidth(400);
        seekBar.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(seekBar, Priority.ALWAYS);
        seekBar.setStyle("-fx-background-color: " + BG_DARK + "; "
            + "-fx-control-inner-background: " + BG_DARK + ";");
        seekBar.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (seekBar.isValueChanging()) {
                long targetTick = newVal.longValue();
                if (player.hasReplay()) {
                    targetTick = Math.min(targetTick, player.getTotalTicks());
                    targetTick = Math.max(0, targetTick);
                    player.seekTo(targetTick);
                    updateLabels();
                }
            }
        });

        // Time label (right of seek bar)
        timeLabel = new Label("00:00 / 00:00");
        timeLabel.setFont(Font.font(FONT_FAMILY, 12));
        timeLabel.setTextFill(Color.web(TEXT_SECONDARY));
        timeLabel.setMinWidth(120);
        timeLabel.setAlignment(Pos.CENTER_RIGHT);

        row.getChildren().addAll(
            restartBtn, stepBackBtn, playPauseBtn, stepForwardBtn, fastForwardBtn,
            seekBar, timeLabel
        );

        return row;
    }

    private HBox buildInfoRow() {
        HBox row = new HBox(12);
        row.setAlignment(Pos.CENTER_LEFT);

        // Tick display
        tickLabel = new Label("Tick: 0 / 0");
        tickLabel.setFont(Font.font(FONT_FAMILY, 12));
        tickLabel.setTextFill(Color.web(TEXT_SECONDARY));

        // Spacer
        Region spacer1 = new Region();
        HBox.setHgrow(spacer1, Priority.ALWAYS);

        // Speed controls
        Label speedLabel = new Label("Speed:");
        speedLabel.setFont(Font.font(FONT_FAMILY, 12));
        speedLabel.setTextFill(Color.web(TEXT_SECONDARY));

        speed1xBtn = new ToggleButton("1x");
        speed1xBtn.setToggleGroup(speedGroup);
        speed1xBtn.setSelected(true);
        styleToggleButton(speed1xBtn, BTN_STYLE, BTN_HOVER_STYLE);
        speed1xBtn.setOnAction(e -> setSpeed(1));

        speed2xBtn = new ToggleButton("2x");
        speed2xBtn.setToggleGroup(speedGroup);
        styleToggleButton(speed2xBtn, BTN_STYLE, BTN_HOVER_STYLE);
        speed2xBtn.setOnAction(e -> setSpeed(2));

        speed4xBtn = new ToggleButton("4x");
        speed4xBtn.setToggleGroup(speedGroup);
        styleToggleButton(speed4xBtn, BTN_STYLE, BTN_HOVER_STYLE);
        speed4xBtn.setOnAction(e -> setSpeed(4));

        // Spacer
        Region spacer2 = new Region();
        HBox.setHgrow(spacer2, Priority.ALWAYS);

        // Load replay button
        Button loadBtn = createButton("[ Load Replay ]", BTN_ACCENT_STYLE, BTN_ACCENT_HOVER_STYLE);
        loadBtn.setOnAction(e -> handleLoadReplay());

        row.getChildren().addAll(
            tickLabel, spacer1,
            speedLabel, speed1xBtn, speed2xBtn, speed4xBtn,
            spacer2, loadBtn
        );

        return row;
    }

    // =========================================================================
    // Playback Controls
    // =========================================================================

    /**
     * Starts the AnimationTimer for driving playback.
     */
    private void startTimer() {
        // Assume ~60 FPS, with 30 ticks/second at 1x speed.
        // At 1x speed: ~2 frames per tick (60fps / 30 ticks/s = 2 frames/tick)
        // We accumulate frame time and step when enough has passed.
        final long NANO_PER_TICK_1X = 1_000_000_000L / 30; // ~33.3ms per tick at 1x

        playbackTimer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                if (lastFrameTime == 0) {
                    lastFrameTime = now;
                    return;
                }

                long delta = now - lastFrameTime;
                lastFrameTime = now;

                if (player.isPlaying() && player.hasReplay()) {
                    // Accumulate time scaled by speed
                    accumulator += delta * playbackSpeed;

                    // Step forward for each tick's worth of time that has elapsed
                    while (accumulator >= NANO_PER_TICK_1X && player.isPlaying()) {
                        player.stepForward();
                        accumulator -= NANO_PER_TICK_1X;
                    }

                    // Clamp accumulator to avoid spiral of death
                    if (accumulator > NANO_PER_TICK_1X * 10) {
                        accumulator = NANO_PER_TICK_1X * 10;
                    }

                    updateLabels();

                    // Auto-pause at end
                    if (!player.isPlaying() && player.hasReplay()
                        && player.getCurrentTick() >= player.getTotalTicks()) {
                        playPauseBtn.setSelected(false);
                        statusLabel.setText("\u23F9 Playback complete");
                    }
                }
            }
        };
        playbackTimer.start();
    }

    private void handleRestart() {
        if (!player.hasReplay()) {
            statusLabel.setText("No replay loaded");
            return;
        }
        player.seekTo(0);
        player.play();
        playPauseBtn.setSelected(true);
        accumulator = 0;
        lastFrameTime = 0;
        statusLabel.setText("\u25B6 Playing from start");
        updateLabels();
    }

    private void handleStepBack() {
        if (!player.hasReplay()) {
            statusLabel.setText("No replay loaded");
            return;
        }
        long targetTick = Math.max(0, player.getCurrentTick() - 100);
        player.pause();
        playPauseBtn.setSelected(false);
        player.seekTo(targetTick);
        accumulator = 0;
        lastFrameTime = 0;
        statusLabel.setText("\u23ED Stepped back to tick " + targetTick);
        updateLabels();
    }

    private void handlePlayPause() {
        if (!player.hasReplay()) {
            statusLabel.setText("Load a replay first");
            playPauseBtn.setSelected(false);
            return;
        }

        if (playPauseBtn.isSelected()) {
            // Check if we're at the end
            if (player.getCurrentTick() >= player.getTotalTicks()) {
                // Restart from beginning
                player.seekTo(0);
            }
            player.resume();
            accumulator = 0;
            lastFrameTime = 0;
            statusLabel.setText("\u25B6 Playing at " + playbackSpeed + "x");
        } else {
            player.pause();
            statusLabel.setText("\u23F8 Paused at tick " + player.getCurrentTick());
        }
        updateLabels();
    }

    private void handleStepForward() {
        if (!player.hasReplay()) {
            statusLabel.setText("No replay loaded");
            return;
        }
        player.pause();
        playPauseBtn.setSelected(false);

        if (player.getCurrentTick() < player.getTotalTicks()) {
            // seekTo for single tick advance (stepForward() requires playing=true)
            player.seekTo(player.getCurrentTick() + 1);
            statusLabel.setText("\u23E9 Stepped to tick " + player.getCurrentTick());
        } else {
            statusLabel.setText("\u23F9 Already at end of replay");
        }
        accumulator = 0;
        lastFrameTime = 0;
        updateLabels();
    }

    private void handleFastForward() {
        if (!player.hasReplay()) {
            statusLabel.setText("No replay loaded");
            return;
        }
        long targetTick = Math.min(player.getTotalTicks(), player.getCurrentTick() + 100);
        player.pause();
        playPauseBtn.setSelected(false);
        player.seekTo(targetTick);
        accumulator = 0;
        lastFrameTime = 0;
        statusLabel.setText("\u23E9 Fast forwarded to tick " + targetTick);
        updateLabels();
    }

    private void handleLoadReplay() {
        // Pause any current playback
        player.pause();
        playPauseBtn.setSelected(false);

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select Replay File");
        fileChooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("AOW2 Replay Files", "*.aow2r")
        );
        fileChooser.setInitialDirectory(new File(System.getProperty("user.dir")));

        Stage stage = (Stage) root.getScene().getWindow();
        File selectedFile = fileChooser.showOpenDialog(stage);

        if (selectedFile != null) {
            Path filePath = selectedFile.toPath();
            statusLabel.setText("Loading replay...");
            updateLabels();

            // Load on a background thread to avoid blocking the UI
            Thread loadThread = new Thread(() -> {
                ReplayFile replayFile = ReplayPlayer.loadFromFile(filePath);

                javafx.application.Platform.runLater(() -> {
                    if (replayFile != null) {
                        player.loadReplay(replayFile);
                        seekBar.setMax(Math.max(1, replayFile.totalTicks()));
                        seekBar.setValue(0);
                        accumulator = 0;
                        lastFrameTime = 0;

                        // Update header map name
                        mapNameHeaderLabel.setText(replayFile.mapName());

                        // Update viewer area metadata
                        mapNameViewerLabel.setText(replayFile.mapName());
                        factionsLabel.setText(formatFactions(replayFile.playerFactions()));
                        commandCountLabel.setText("Commands: " + replayFile.commandCount()
                            + "  |  Players: " + replayFile.playerCount()
                            + "  |  Duration: " + formatDuration(replayFile.durationSeconds()));

                        statusLabel.setText("Loaded: " + selectedFile.getName()
                            + " (" + replayFile.commandCount() + " commands, "
                            + replayFile.totalTicks() + " ticks)");
                    } else {
                        statusLabel.setText("\u274C Failed to load replay: " + selectedFile.getName());
                    }
                    updateLabels();
                });
            }, "replay-loader");
            loadThread.setDaemon(true);
            loadThread.start();
        }
    }

    private void setSpeed(int speed) {
        this.playbackSpeed = speed;
        if (player.isPlaying()) {
            statusLabel.setText("\u25B6 Playing at " + speed + "x");
        }
    }

    // =========================================================================
    // UI Helpers
    // =========================================================================

    /**
     * Updates all dynamic labels and the seek bar to reflect current state.
     */
    private void updateLabels() {
        long currentTick = player.getCurrentTick();
        long totalTicks = player.getTotalTicks();

        // Tick labels
        String tickText = "Tick: " + currentTick + " / " + totalTicks;
        if (tickLabel != null) {
            tickLabel.setText(tickText);
        }
        if (tickViewerLabel != null) {
            tickViewerLabel.setText(tickText);
        }

        // Time label
        if (timeLabel != null) {
            long currentSec = currentTick / 30;
            long totalSec = totalTicks / 30;
            timeLabel.setText(formatDuration(currentSec) + " / " + formatDuration(totalSec));
        }

        // Seek bar (only update if not being dragged)
        if (seekBar != null && !seekBar.isValueChanging()) {
            seekBar.setValue(currentTick);
        }
    }

    /**
     * Formats a duration in seconds to MM:SS.
     */
    private static String formatDuration(long seconds) {
        long mins = seconds / 60;
        long secs = seconds % 60;
        return String.format("%02d:%02d", mins, secs);
    }

    /**
     * Formats an array of factions into a display string.
     */
    private static String formatFactions(Faction[] factions) {
        List<String> names = new ArrayList<>();
        for (int i = 0; i < factions.length; i++) {
            String displayName = formatFactionName(factions[i]);
            names.add("Player " + (i + 1) + " (" + displayName + ")");
        }
        return String.join("  vs  ", names);
    }

    /**
     * Formats a single faction enum into a user-friendly display name.
     */
    private static String formatFactionName(Faction faction) {
        if (faction == null) return "Unknown";
        return switch (faction) {
            case CONFEDERATION -> "Confederation";
            case RESISTANCE -> "Resistance";
            case NEUTRAL -> "Neutral";
        };
    }

    /**
     * Creates a styled button with hover effects.
     */
    private static Button createButton(String text, String style, String hoverStyle) {
        Button btn = new Button(text);
        styleButton(btn, style, hoverStyle);
        return btn;
    }

    /**
     * Applies style and hover effects to a button.
     */
    private static void styleButton(Button btn, String style, String hoverStyle) {
        btn.setStyle(style);
        btn.setOnMouseEntered(e -> btn.setStyle(hoverStyle));
        btn.setOnMouseExited(e -> btn.setStyle(style));
    }

    /**
     * Applies style and hover effects to a toggle button.
     */
    private static void styleToggleButton(ToggleButton btn, String style, String hoverStyle) {
        btn.setStyle(style);
        btn.setOnMouseEntered(e -> {
            if (!btn.isSelected()) {
                btn.setStyle(hoverStyle);
            }
        });
        btn.setOnMouseExited(e -> {
            if (!btn.isSelected()) {
                btn.setStyle(style);
            }
        });
        // Update style when selected state changes
        btn.selectedProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal) {
                btn.setStyle(BTN_ACCENT_STYLE);
            } else {
                btn.setStyle(style);
            }
        });
    }

    /**
     * Creates a simple tooltip.
     */
    private static javafx.scene.control.Tooltip createTooltip(String text) {
        javafx.scene.control.Tooltip tip = new javafx.scene.control.Tooltip(text);
        tip.setStyle("-fx-font-family: " + FONT_FAMILY + "; -fx-font-size: 11px; "
            + "-fx-background-color: " + BG_PANEL + "; "
            + "-fx-text-fill: " + TEXT_PRIMARY + "; "
            + "-fx-border-color: " + BORDER_COLOR + ";");
        return tip;
    }

    /**
     * Stops the animation timer. Call this when navigating away from this scene.
     */
    public void stop() {
        if (playbackTimer != null) {
            playbackTimer.stop();
            playbackTimer = null;
        }
        player.pause();
    }
}
package com.aow2.client.scene;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.effect.DropShadow;
import javafx.scene.effect.Glow;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.util.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main menu FXGL scene for Art of War 2: Online.
 * <p>
 * Features:
 * - Title "Art of War 2: Online"
 * - Buttons: Campaign, Skirmish, Multiplayer, Map Editor, Mods, Settings, Quit
 * - Styling with dark theme matching military aesthetic
 * - Background with subtle animation
 * <p>
 * REF: MASTER_DOCUMENTATION.md Section 7 - Main Menu UI
 */
public class MainMenuScene {

    private static final Logger LOG = LoggerFactory.getLogger(MainMenuScene.class);

    /** Background color for the military aesthetic. */
    private static final Color BG_COLOR = Color.rgb(15, 18, 22);

    /** Accent color (olive/military green). */
    private static final Color ACCENT_COLOR = Color.rgb(80, 100, 50);

    /** Button background color. */
    private static final String BTN_STYLE = "-fx-background-color: rgb(40, 45, 35); "
        + "-fx-text-fill: rgb(210, 200, 160); "
        + "-fx-border-color: rgb(100, 110, 70); -fx-border-width: 1; "
        + "-fx-font-size: 16px; -fx-font-weight: bold; "
        + "-fx-padding: 10 40 10 40; -fx-cursor: hand;";

    /** Button hover style. */
    private static final String BTN_HOVER_STYLE = "-fx-background-color: rgb(60, 70, 45); "
        + "-fx-text-fill: rgb(240, 230, 180); "
        + "-fx-border-color: rgb(140, 150, 90); -fx-border-width: 2; "
        + "-fx-font-size: 16px; -fx-font-weight: bold; "
        + "-fx-padding: 10 40 10 40; -fx-cursor: hand;";

    /** The root pane for this scene. */
    private final StackPane root;

    /** Callbacks for menu actions. */
    private MenuCallback callback;

    /** Background animation timeline. */
    private Timeline bgAnimation;

    /** Shared application version string. */
    private static final String VERSION = "v0.2.0-ALPHA";

    /**
     * Callback interface for menu button actions.
     */
    public interface MenuCallback {
        /**
         * Called when a menu button is clicked.
         *
         * @param action the action string
         */
        void onMenuAction(String action);
    }

    /**
     * Constructs a new MainMenuScene.
     */
    public MainMenuScene() {
        this.root = new StackPane();
        buildUI();
        startBackgroundAnimation();
        LOG.info("MainMenuScene created");
    }

    /**
     * Builds the main menu UI components.
     */
    private void buildUI() {
        root.setPrefSize(1280, 720);

        // Background layer with animated rectangles
        Pane bgLayer = createBackgroundLayer();

        // Content layer
        BorderPane content = new BorderPane();

        // Title section
        VBox titleBox = new VBox(8);
        titleBox.setAlignment(Pos.CENTER);
        titleBox.setPadding(new Insets(60, 0, 0, 0));

        Text title = new Text("ART OF WAR 2");
        title.setFont(Font.font("Consolas", 48));
        title.setFill(Color.rgb(210, 200, 160));
        title.setEffect(new DropShadow(10, Color.rgb(0, 0, 0, 0.7)));

        Text subtitle = new Text("ONLINE");
        subtitle.setFont(Font.font("Consolas", 28));
        subtitle.setFill(Color.rgb(140, 150, 90));
        subtitle.setEffect(new DropShadow(5, Color.rgb(0, 0, 0, 0.5)));

        Text version = new Text(VERSION);
        version.setFont(Font.font("Consolas", 12));
        version.setFill(Color.rgb(100, 100, 100));

        titleBox.getChildren().addAll(title, subtitle, version);
        content.setTop(titleBox);

        // Menu buttons — "Mods" positioned between "Map Editor" and "Settings"
        VBox buttonBox = new VBox(12);
        buttonBox.setAlignment(Pos.CENTER);
        buttonBox.setPadding(new Insets(0, 0, 80, 0));

        String[] buttonLabels = {"Campaign", "Skirmish", "Multiplayer", "Map Editor", "Mods", "Settings", "Quit"};
        String[] actions = {"campaign", "skirmish", "multiplayer", "map_editor", "mods", "settings", "quit"};

        for (int i = 0; i < buttonLabels.length; i++) {
            javafx.scene.control.Button btn = new javafx.scene.control.Button(buttonLabels[i]);
            btn.setStyle(BTN_STYLE);
            final String action = actions[i];
            btn.setOnAction(e -> {
                if (callback != null) {
                    callback.onMenuAction(action);
                }
                LOG.info("Menu: {}", action);
            });
            btn.setOnMouseEntered(e -> btn.setStyle(BTN_HOVER_STYLE));
            btn.setOnMouseExited(e -> btn.setStyle(BTN_STYLE));
            btn.setMaxWidth(250);
            buttonBox.getChildren().add(btn);
        }

        content.setCenter(buttonBox);
        content.setBottom(new HBox()); // Spacer

        // Footer
        Text footer = new Text("© 2024 Art of War 2: Online — Java 21 FXGL Recreation");
        footer.setFont(Font.font("Consolas", 10));
        footer.setFill(Color.rgb(80, 80, 80));
        HBox footerBox = new HBox(footer);
        footerBox.setAlignment(Pos.CENTER);
        footerBox.setPadding(new Insets(0, 0, 10, 0));
        content.setBottom(footerBox);

        root.getChildren().addAll(bgLayer, content);
    }

    /**
     * Creates the animated background layer with subtle moving rectangles.
     *
     * @return the background pane
     */
    private Pane createBackgroundLayer() {
        Pane bg = new Pane();
        bg.setPrefSize(1280, 720);
        bg.setStyle("-fx-background-color: rgb(15, 18, 22);");

        // Decorative grid lines
        for (int i = 0; i < 1280; i += 60) {
            Rectangle vLine = new Rectangle(i, 0, 1, 720);
            vLine.setFill(Color.rgb(30, 35, 28, 0.3));
            bg.getChildren().add(vLine);
        }
        for (int i = 0; i < 720; i += 60) {
            Rectangle hLine = new Rectangle(0, i, 1280, 1);
            hLine.setFill(Color.rgb(30, 35, 28, 0.3));
            bg.getChildren().add(hLine);
        }

        // Accent rectangles
        Rectangle accentTop = new Rectangle(0, 0, 1280, 3);
        accentTop.setFill(ACCENT_COLOR);
        bg.getChildren().add(accentTop);

        Rectangle accentBottom = new Rectangle(0, 717, 1280, 3);
        accentBottom.setFill(ACCENT_COLOR);
        bg.getChildren().add(accentBottom);

        return bg;
    }

    /**
     * Starts the subtle background animation.
     */
    private void startBackgroundAnimation() {
        Rectangle glowRect = new Rectangle(640 - 200, 360 - 100, 400, 200);
        glowRect.setFill(Color.rgb(80, 100, 50, 0.03));
        glowRect.setArcWidth(20);
        glowRect.setArcHeight(20);

        // Add glow rect behind content
        if (root.getChildren().size() > 1) {
            root.getChildren().add(1, glowRect);
        }

        bgAnimation = new Timeline(
            new KeyFrame(Duration.ZERO,
                new KeyValue(glowRect.opacityProperty(), 0.3)),
            new KeyFrame(Duration.seconds(3),
                new KeyValue(glowRect.opacityProperty(), 0.8)),
            new KeyFrame(Duration.seconds(6),
                new KeyValue(glowRect.opacityProperty(), 0.3))
        );
        bgAnimation.setCycleCount(Animation.INDEFINITE);
        bgAnimation.play();
    }

    /**
     * Gets the root pane for this scene.
     *
     * @return the root StackPane
     */
    public StackPane getRoot() {
        return root;
    }

    /**
     * Sets the menu callback.
     *
     * @param callback the callback
     */
    public void setCallback(MenuCallback callback) {
        this.callback = callback;
    }

    /**
     * Stops background animations when scene is no longer displayed.
     */
    public void dispose() {
        if (bgAnimation != null) {
            bgAnimation.stop();
        }
    }
}

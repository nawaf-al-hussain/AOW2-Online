package com.aow2.client.scene;

import com.aow2.core.campaign.CampaignEpisode;
import com.aow2.core.campaign.CampaignManager;
import com.aow2.core.engine.GameState;
import com.aow2.core.world.EntityManager;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Campaign selection and briefing screen.
 * <p>
 * Displays available campaign episodes and missions.
 * Each episode has a briefing text, difficulty rating, and mission list.
 * Players can select a mission, view the briefing, and start the mission.
 * <p>
 * REF: campaign_guide.md — Full campaign episode and mission data
 * REF: MASTER_DOCUMENTATION.md Section 7 - Campaign UI
 */
public class CampaignScene {

    private static final Logger LOG = LoggerFactory.getLogger(CampaignScene.class);

    /** Campaign episode data. */
    public record Episode(
        String title,
        String subtitle,
        String briefing,
        int missionCount,
        int difficulty,  // 1-5 stars
        String faction
    ) {}

    /** Available campaign episodes. REF: campaign_guide.md */
    // REF: campaign_guide.md — 29 total missions: 7 Ep1 + 7 Ep2 + 15 custom missions.
    // Episode 1 and Episode 2 are the original game episodes.
    // Custom Missions is a bonus section, not an "Episode 3".
    private static final List<Episode> EPISODES = List.of(
        new Episode(
            "Episode 1: Global Confederation",
            "The Confederation Strikes Back",
            "The Global Confederation launches a massive counter-offensive against the Resistance. "
                + "Command the Confederation forces through 7 missions to reclaim territory and crush the rebellion. "
                + "Begin with basic infantry and progress to heavy armor and advanced technology.",
            7, 2, "CONFEDERATION"
        ),
        new Episode(
            "Episode 2: Liberation of Peru",
            "Resistance Rising",
            "The Resistance fights to liberate Peru from Confederation occupation. "
                + "Use guerrilla tactics, sniper units, and captured equipment to overcome superior numbers. "
                + "Each mission unlocks new units and technologies for the Resistance arsenal.",
            7, 3, "RESISTANCE"
        ),
        new Episode(
            "Custom Missions",
            "Community Challenges",
            "A collection of 15 standalone custom missions with varying factions, objectives, and difficulty. "
                + "These missions can be played in any order and do not follow a sequential campaign storyline. "
                + "Difficulties range from beginner (1 star) to extreme (5 stars).",
            15, 3, "MIXED"
        )
    );

    /** The root pane for this scene. */
    private final StackPane root;

    /** Currently selected episode index. */
    private int selectedEpisode;

    /** Callback for scene navigation. */
    private SceneCallback callback;

    /** Campaign manager for checking mission state and save/load. */
    private CampaignManager campaignManager;

    /** Game state reference needed for saving. */
    private GameState gameState;

    /** Entity manager reference needed for saving. */
    private EntityManager entityManager;

    /**
     * Callback interface for scene transitions.
     */
    public interface SceneCallback {
        /**
         * Called when the player starts a campaign mission.
         *
         * @param episodeIndex the episode index
         * @param missionIndex the mission index within the episode
         */
        void onStartMission(int episodeIndex, int missionIndex);

        /**
         * Called when the player wants to go back to the main menu.
         */
        void onBack();
    }

    /**
     * Constructs a new CampaignScene.
     */
    public CampaignScene() {
        this.root = new StackPane();
        this.selectedEpisode = 0;
        buildUI();
        LOG.info("CampaignScene created");
    }

    /**
     * Build the campaign scene UI.
     */
    private void buildUI() {
        root.setPrefSize(1280, 720);
        root.setStyle("-fx-background-color: rgb(15, 18, 22);");

        BorderPane content = new BorderPane();
        content.setPadding(new Insets(20));

        // Title
        VBox titleBox = new VBox(5);
        titleBox.setAlignment(Pos.CENTER_LEFT);
        Label titleLabel = new Label("CAMPAIGN");
        titleLabel.setFont(Font.font("Consolas", 32));
        titleLabel.setTextFill(Color.rgb(210, 200, 160));
        Label subtitleLabel = new Label("Select an episode to begin");
        subtitleLabel.setFont(Font.font("Consolas", 14));
        subtitleLabel.setTextFill(Color.rgb(120, 120, 100));
        titleBox.getChildren().addAll(titleLabel, subtitleLabel);
        content.setTop(titleBox);

        // Episode list on the left
        VBox episodeList = new VBox(8);
        episodeList.setPadding(new Insets(10));
        episodeList.setPrefWidth(350);

        for (int i = 0; i < EPISODES.size(); i++) {
            Episode ep = EPISODES.get(i);
            VBox epCard = createEpisodeCard(ep, i);
            episodeList.getChildren().add(epCard);
        }

        ScrollPane episodeScroll = new ScrollPane(episodeList);
        episodeScroll.setFitToWidth(true);
        episodeScroll.setStyle("-fx-background: rgb(20, 23, 18); -fx-control-inner-background: rgb(20, 23, 18);");
        content.setLeft(episodeScroll);

        // Briefing panel on the right
        VBox briefingPanel = createBriefingPanel();
        content.setCenter(briefingPanel);

        // Bottom bar with back button and save/load slots
        HBox bottomBar = new HBox(10);
        bottomBar.setAlignment(Pos.CENTER_LEFT);
        bottomBar.setPadding(new Insets(5, 0, 0, 0));

        Button backButton = new Button("← Back to Menu");
        backButton.setStyle("-fx-background-color: rgb(40, 45, 35); "
            + "-fx-text-fill: rgb(210, 200, 160); -fx-border-color: rgb(100, 110, 70); "
            + "-fx-padding: 8 20 8 20; -fx-cursor: hand;");
        backButton.setOnAction(e -> {
            if (callback != null) {
                callback.onBack();
            }
        });
        bottomBar.getChildren().add(backButton);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        bottomBar.getChildren().add(spacer);

        // Save slot buttons
        Label saveLabel = new Label("Save/Load:");
        saveLabel.setFont(Font.font("Consolas", 12));
        saveLabel.setStyle("-fx-text-fill: rgb(120, 120, 100);");
        saveLabel.setAlignment(Pos.CENTER);
        bottomBar.getChildren().add(saveLabel);

        for (int slot = 0; slot < 3; slot++) {
            final int saveSlot = slot;
            HBox slotBox = new HBox(4);
            slotBox.setAlignment(Pos.CENTER);

            Button saveBtn = new Button("Save " + (slot + 1));
            saveBtn.setStyle("-fx-background-color: rgb(50, 60, 40); "
                + "-fx-text-fill: rgb(200, 200, 160); -fx-border-color: rgb(100, 110, 70); "
                + "-fx-font-size: 11px; -fx-padding: 6 12 6 12; -fx-cursor: hand;");
            saveBtn.setOnAction(e -> {
                if (campaignManager != null && gameState != null && entityManager != null) {
                    boolean ok = campaignManager.saveGame(saveSlot, gameState, entityManager);
                    LOG.info("Save to slot {}: {}", saveSlot + 1, ok ? "OK" : "FAILED");
                } else {
                    LOG.warn("Cannot save: campaignManager={}, gameState={}, entityManager={}",
                        campaignManager != null, gameState != null, entityManager != null);
                }
            });

            Button loadBtn = new Button("Load " + (slot + 1));
            loadBtn.setStyle("-fx-background-color: rgb(50, 50, 60); "
                + "-fx-text-fill: rgb(160, 180, 220); -fx-border-color: rgb(80, 90, 120); "
                + "-fx-font-size: 11px; -fx-padding: 6 12 6 12; -fx-cursor: hand;");
            loadBtn.setOnAction(e -> {
                if (campaignManager != null) {
                    boolean ok = campaignManager.loadGame(saveSlot);
                    LOG.info("Load from slot {}: {}", saveSlot + 1, ok ? "OK" : "NO SAVE");
                    if (ok) {
                        updateBriefing();
                    }
                }
            });

            Button delBtn = new Button("X");
            delBtn.setStyle("-fx-background-color: rgb(80, 35, 35); "
                + "-fx-text-fill: rgb(220, 160, 160); -fx-border-color: rgb(120, 60, 60); "
                + "-fx-font-size: 10px; -fx-padding: 6 8 6 8; -fx-cursor: hand;");
            delBtn.setOnAction(e -> {
                if (campaignManager != null) {
                    boolean ok = campaignManager.getSaveManager().deleteSave(saveSlot);
                    LOG.info("Delete slot {}: {}", saveSlot + 1, ok ? "OK" : "FAILED");
                }
            });

            slotBox.getChildren().addAll(saveBtn, loadBtn, delBtn);
            bottomBar.getChildren().add(slotBox);
        }

        content.setBottom(bottomBar);

        root.getChildren().add(content);
    }

    /**
     * Create an episode card for the episode list.
     *
     * @param episode the episode data
     * @param index   the episode index
     * @return the card VBox
     */
    private VBox createEpisodeCard(Episode episode, int index) {
        VBox card = new VBox(6);
        card.setPadding(new Insets(12));
        card.setStyle("-fx-background-color: rgb(25, 28, 22); "
            + "-fx-border-color: rgb(60, 65, 50); -fx-border-width: 1; "
            + "-fx-cursor: hand;");

        Label epTitle = new Label(episode.title());
        epTitle.setFont(Font.font("Consolas", 14));
        epTitle.setStyle("-fx-text-fill: rgb(210, 200, 160); -fx-font-weight: bold;");

        Label epFaction = new Label(episode.faction());
        epFaction.setFont(Font.font("Consolas", 11));
        epFaction.setStyle("-fx-text-fill: rgb(100, 110, 70);");

        // Difficulty stars
        String stars = "★".repeat(episode.difficulty()) + "☆".repeat(5 - episode.difficulty());
        Label diffLabel = new Label(stars);
        diffLabel.setFont(Font.font("Consolas", 12));
        diffLabel.setStyle("-fx-text-fill: rgb(200, 180, 80);");

        Label missionCount = new Label(episode.missionCount() + " missions");
        missionCount.setFont(Font.font("Consolas", 11));
        missionCount.setStyle("-fx-text-fill: rgb(120, 120, 100);");

        card.getChildren().addAll(epTitle, epFaction, diffLabel, missionCount);

        // Hover and click effects
        card.setOnMouseEntered(e -> card.setStyle("-fx-background-color: rgb(35, 40, 30); "
            + "-fx-border-color: rgb(100, 110, 70); -fx-border-width: 2; "
            + "-fx-cursor: hand;"));
        card.setOnMouseExited(e -> card.setStyle("-fx-background-color: rgb(25, 28, 22); "
            + "-fx-border-color: rgb(60, 65, 50); -fx-border-width: 1; "
            + "-fx-cursor: hand;"));
        card.setOnMouseClicked(e -> {
            selectedEpisode = index;
            updateBriefing();
            LOG.info("Selected episode: {}", episode.title());
        });

        return card;
    }

    /**
     * Create the briefing panel.
     *
     * @return the briefing VBox
     */
    private VBox createBriefingPanel() {
        VBox panel = new VBox(15);
        panel.setPadding(new Insets(20));
        panel.setId("briefing-panel");

        updateBriefing();

        return panel;
    }

    /**
     * Update the briefing panel with the currently selected episode.
     */
    private void updateBriefingPanel(VBox panel, Episode episode) {
        panel.getChildren().clear();

        Label briefingTitle = new Label(episode.title());
        briefingTitle.setFont(Font.font("Consolas", 24));
        briefingTitle.setStyle("-fx-text-fill: rgb(210, 200, 160);");

        Label briefingSub = new Label(episode.subtitle());
        briefingSub.setFont(Font.font("Consolas", 16));
        briefingSub.setStyle("-fx-text-fill: rgb(140, 150, 90);");

        TextFlow briefingText = new TextFlow();
        Text text = new Text(episode.briefing());
        text.setFont(Font.font("Consolas", 13));
        text.setFill(Color.rgb(180, 175, 145));
        briefingText.getChildren().add(text);
        briefingText.setMaxWidth(700);

        // Mission buttons generated dynamically based on episode mission count
        // Use FlowPane to handle varying mission counts (7 for episodes, 15 for custom)
        FlowPane missionButtons = new FlowPane(10, 8);
        missionButtons.setAlignment(Pos.CENTER_LEFT);
        missionButtons.setMaxWidth(700);
        CampaignEpisode campaignEpisode = toCampaignEpisode(selectedEpisode);
        for (int m = 0; m < episode.missionCount(); m++) {
            final int missionIndex = m;
            boolean completed = campaignManager != null
                && campaignManager.isMissionCompleted(campaignEpisode, missionIndex);
            boolean available = campaignManager == null
                || campaignManager.isMissionAvailable(campaignEpisode, missionIndex);

            Button missionBtn = new Button();
            if (completed) {
                missionBtn.setText("\u2713 Mission " + (missionIndex + 1) + " - Completed");
                missionBtn.setStyle("-fx-background-color: rgb(35, 80, 40); "
                    + "-fx-text-fill: rgb(140, 255, 140); -fx-border-color: rgb(80, 180, 80); "
                    + "-fx-font-size: 14px; -fx-font-weight: bold; "
                    + "-fx-padding: 10 30 10 30; -fx-cursor: hand;");
            } else if (available) {
                missionBtn.setText("Start Mission " + (missionIndex + 1));
                missionBtn.setStyle("-fx-background-color: rgb(60, 70, 45); "
                    + "-fx-text-fill: rgb(240, 230, 180); -fx-border-color: rgb(140, 150, 90); "
                    + "-fx-font-size: 14px; -fx-font-weight: bold; "
                    + "-fx-padding: 10 30 10 30; -fx-cursor: hand;");
            } else {
                missionBtn.setText("\uD83D\uDD12 Mission " + (missionIndex + 1) + " - Locked");
                missionBtn.setStyle("-fx-background-color: rgb(40, 40, 40); "
                    + "-fx-text-fill: rgb(90, 90, 90); -fx-border-color: rgb(60, 60, 60); "
                    + "-fx-font-size: 14px; -fx-font-weight: bold; "
                    + "-fx-padding: 10 30 10 30;");
            }

            final boolean isAvailable = available;
            missionBtn.setOnAction(e -> {
                if (isAvailable && callback != null) {
                    callback.onStartMission(selectedEpisode, missionIndex);
                }
            });
            missionButtons.getChildren().add(missionBtn);
        }

        panel.getChildren().addAll(briefingTitle, briefingSub, briefingText, missionButtons);
    }

    /**
     * Update the briefing display.
     */
    private void updateBriefing() {
        VBox panel = (VBox) root.lookup("#briefing-panel");
        if (panel != null && selectedEpisode < EPISODES.size()) {
            updateBriefingPanel(panel, EPISODES.get(selectedEpisode));
        }
    }

    /**
     * Get the root pane for this scene.
     *
     * @return the root StackPane
     */
    public StackPane getRoot() {
        return root;
    }

    /**
     * Set the scene callback.
     *
     * @param callback the callback
     */
    public void setCallback(SceneCallback callback) {
        this.callback = callback;
    }

    /**
     * Set the campaign manager for mission state tracking and save/load.
     *
     * @param campaignManager the campaign manager
     */
    public void setCampaignManager(CampaignManager campaignManager) {
        this.campaignManager = campaignManager;
    }

    /**
     * Set the game state reference needed for saving.
     *
     * @param gameState the current game state
     */
    public void setGameState(GameState gameState) {
        this.gameState = gameState;
    }

    /**
     * Set the entity manager reference needed for saving.
     *
     * @param entityManager the current entity manager
     */
    public void setEntityManager(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    /**
     * Map the UI episode index to a CampaignEpisode enum.
     *
     * @param episodeIndex the 0-based episode index
     * @return the corresponding CampaignEpisode
     */
    private static CampaignEpisode toCampaignEpisode(int episodeIndex) {
        return switch (episodeIndex) {
            case 0 -> CampaignEpisode.GLOBAL_CONFEDERATION;
            case 1 -> CampaignEpisode.LIBERATION_OF_PERU;
            default -> CampaignEpisode.CUSTOM_MISSIONS;
        };
    }
}

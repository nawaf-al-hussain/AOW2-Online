package com.aow2.client.ui;

import com.aow2.common.model.Faction;
import com.aow2.core.entity.Building;
import com.aow2.core.entity.Unit;
import com.aow2.core.world.EntityManager;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Set;

/**
 * Heads-Up Display overlay for the game scene.
 * <p>
 * Components:
 * - Resource display (credits amount) at top
 * - Selection info panel at bottom (shows selected unit/building stats)
 * - Production queue display
 * - Mini action panel (build, attack move, etc.)
 * <p>
 * Uses JavaFX StackPane overlay on top of game canvas.
 * REF: MASTER_DOCUMENTATION.md Section 4 - Economy (credits display)
 * REF: MASTER_DOCUMENTATION.md Section 7 - UI Controls
 */
public class HUD {

    private static final Logger LOG = LoggerFactory.getLogger(HUD.class);

    /** HUD background color. */
    private static final String PANEL_BG = "-fx-background-color: rgba(20, 20, 30, 0.85);";

    /** HUD border color. */
    private static final String PANEL_BORDER = "-fx-border-color: rgb(120, 100, 60); -fx-border-width: 1;";

    /** Text style. */
    private static final String TEXT_STYLE = "-fx-text-fill: rgb(220, 210, 180); -fx-font-size: 12px;";

    /** The root overlay pane. */
    private final StackPane root;

    /** Resource display labels. */
    private Label creditsLabel;
    private Label tickLabel;
    private Label unitCountLabel;

    /** Selection info labels. */
    private Label selectionNameLabel;
    private Label selectionHpLabel;
    private Label selectionInfoLabel;
    private Label selectionExtraLabel;

    /** Production queue labels — clickable for cancel. */
    private final Label[] productionSlots;

    /** The building ID whose production queue is currently displayed. */
    private int displayedBuildingId = -1;

    /** Action buttons. */
    private Button attackButton;
    private Button stopButton;
    private Button holdButton;
    private Button patrolButton;
    private Button buildButton;
    private Button upgradeButton;
    private Button produceButton;
    private Button researchButton;

    /** Current credits amount. */
    private int credits;

    /** Current tick. */
    private long currentTick;

    /** The entity manager. */
    private EntityManager entityManager;

    /** The player faction. */
    private Faction playerFaction;

    /** Action callback for button presses. */
    private ActionCallback actionCallback;

    /**
     * Callback interface for HUD action button presses.
     */
    @FunctionalInterface
    public interface ActionCallback {
        /**
         * Called when an action button is pressed.
         *
         * @param action the action type string
         */
        void onAction(String action);
    }

    /**
     * Constructs a new HUD.
     */
    public HUD() {
        this.root = new StackPane();
        this.productionSlots = new Label[5];
        this.credits = 0;
        this.currentTick = 0;
        this.playerFaction = Faction.CONFEDERATION;

        buildUI();
    }

    /**
     * Builds all HUD UI components.
     */
    private void buildUI() {
        // Top bar: resources
        HBox topBar = buildTopBar();

        // Bottom panel: selection info + actions
        VBox bottomPanel = buildBottomPanel();

        // Position top bar at top
        StackPane.setAlignment(topBar, Pos.TOP_LEFT);
        StackPane.setMargin(topBar, new Insets(8, 8, 0, 8));

        // Position bottom panel at bottom
        StackPane.setAlignment(bottomPanel, Pos.BOTTOM_CENTER);
        StackPane.setMargin(bottomPanel, new Insets(0, 8, 8, 8));

        root.getChildren().addAll(topBar, bottomPanel);
        root.setPickOnBounds(false);
    }

    /**
     * Builds the top resource bar.
     *
     * @return the top bar HBox
     */
    private HBox buildTopBar() {
        HBox topBar = new HBox(16);
        topBar.setStyle(PANEL_BG + PANEL_BORDER);
        topBar.setPadding(new Insets(6, 12, 6, 12));
        topBar.setAlignment(Pos.CENTER_LEFT);

        // Credits
        Label creditsTitle = new Label("Credits:");
        creditsTitle.setStyle(TEXT_STYLE + " -fx-font-weight: bold;");
        creditsLabel = new Label("0");
        creditsLabel.setStyle(TEXT_STYLE + " -fx-text-fill: rgb(255, 215, 0);");

        // Tick counter
        Label tickTitle = new Label("Tick:");
        tickTitle.setStyle(TEXT_STYLE);
        tickLabel = new Label("0");
        tickLabel.setStyle(TEXT_STYLE);

        // Unit count
        Label unitTitle = new Label("Units:");
        unitTitle.setStyle(TEXT_STYLE);
        unitCountLabel = new Label("0/50");
        unitCountLabel.setStyle(TEXT_STYLE);

        topBar.getChildren().addAll(
            creditsTitle, creditsLabel,
            createSpacer(),
            tickTitle, tickLabel,
            createSpacer(),
            unitTitle, unitCountLabel
        );

        return topBar;
    }

    /**
     * Builds the bottom panel with selection info and action buttons.
     *
     * @return the bottom panel VBox
     */
    private VBox buildBottomPanel() {
        VBox panel = new VBox(4);
        panel.setStyle(PANEL_BG + PANEL_BORDER);
        panel.setPadding(new Insets(8, 12, 8, 12));
        panel.setMaxWidth(600);

        // Selection info section
        HBox selectionRow = new HBox(12);
        selectionRow.setAlignment(Pos.CENTER_LEFT);

        selectionNameLabel = new Label("No Selection");
        selectionNameLabel.setStyle(TEXT_STYLE + " -fx-font-weight: bold; -fx-font-size: 14px;");

        selectionHpLabel = new Label("");
        selectionHpLabel.setStyle(TEXT_STYLE);

        selectionInfoLabel = new Label("");
        selectionInfoLabel.setStyle(TEXT_STYLE + " -fx-font-size: 11px;");

        selectionExtraLabel = new Label("");
        selectionExtraLabel.setStyle(TEXT_STYLE + " -fx-font-size: 10px; -fx-text-fill: rgb(180, 180, 160);");

        VBox infoBox = new VBox(2, selectionNameLabel, selectionHpLabel, selectionInfoLabel, selectionExtraLabel);
        selectionRow.getChildren().addAll(infoBox, createSpacer(), buildProductionQueue());

        panel.getChildren().add(selectionRow);

        // Action buttons row
        HBox actionRow = buildActionButtons();
        panel.getChildren().add(actionRow);

        return panel;
    }

    /**
     * Builds the production queue display.
     *
     * @return production queue VBox
     */
    private VBox buildProductionQueue() {
        VBox queueBox = new VBox(2);
        queueBox.setStyle(PANEL_BG);
        queueBox.setPadding(new Insets(4));

        Label queueTitle = new Label("Production:");
        queueTitle.setStyle(TEXT_STYLE + " -fx-font-size: 10px; -fx-font-weight: bold;");
        queueBox.getChildren().add(queueTitle);

        for (int i = 0; i < productionSlots.length; i++) {
            final int slotIndex = i;
            productionSlots[i] = new Label("-");
            productionSlots[i].setStyle(TEXT_STYLE + " -fx-font-size: 10px;");
            productionSlots[i].setPrefWidth(80);
            // FIX (M-NEW-23): Right-click on a queued item to cancel it.
            productionSlots[i].setOnMouseClicked(e -> {
                if (e.getButton() == MouseButton.SECONDARY && displayedBuildingId >= 0 && actionCallback != null) {
                    actionCallback.onAction("cancel_production:" + slotIndex);
                }
            });
            queueBox.getChildren().add(productionSlots[i]);
        }

        return queueBox;
    }

    /**
     * Builds the action buttons row.
     *
     * @return the action buttons HBox
     */
    private HBox buildActionButtons() {
        HBox row = new HBox(6);
        row.setPadding(new Insets(4, 0, 0, 0));
        row.setAlignment(Pos.CENTER_LEFT);

        String btnStyle = "-fx-background-color: rgb(60, 55, 40); -fx-text-fill: rgb(220, 210, 180); "
            + "-fx-border-color: rgb(120, 100, 60); -fx-border-width: 1; -fx-font-size: 11px; "
            + "-fx-padding: 4 10 4 10;";

        attackButton = new Button("Attack [A]");
        attackButton.setStyle(btnStyle);
        attackButton.setOnAction(e -> fireEvent("attack"));

        stopButton = new Button("Stop [S]");
        stopButton.setStyle(btnStyle);
        stopButton.setOnAction(e -> fireEvent("stop"));

        holdButton = new Button("Hold [H]");
        holdButton.setStyle(btnStyle);
        holdButton.setOnAction(e -> fireEvent("hold"));

        patrolButton = new Button("Patrol [P]");
        patrolButton.setStyle(btnStyle);
        patrolButton.setOnAction(e -> fireEvent("patrol"));

        buildButton = new Button("Build [B]");
        buildButton.setStyle(btnStyle);
        buildButton.setOnAction(e -> fireEvent("build"));

        upgradeButton = new Button("Upgrade [U]");
        upgradeButton.setStyle(btnStyle);
        upgradeButton.setOnAction(e -> fireEvent("upgrade"));

        // FIX (F-04): Produce button — opens the production dialog when a producing
        // building (Infantry Centre / Machine Factory / Barracks / Factory) is selected.
        produceButton = new Button("Produce [T]");
        produceButton.setStyle(btnStyle);
        produceButton.setOnAction(e -> fireEvent("produce"));

        // FIX (F-05): Research button — opens the research dialog when a tech centre
        // / laboratory is selected.
        researchButton = new Button("Research [R]");
        researchButton.setStyle(btnStyle);
        researchButton.setOnAction(e -> fireEvent("research"));

        row.getChildren().addAll(attackButton, stopButton, holdButton, patrolButton, buildButton, upgradeButton, produceButton, researchButton);
        return row;
    }

    /**
     * Fires an action event through the callback.
     *
     * @param action the action string
     */
    private void fireEvent(String action) {
        if (actionCallback != null) {
            actionCallback.onAction(action);
        }
        LOG.debug("HUD action: {}", action);
    }

    /**
     * Creates a horizontal spacer that grows to fill available space.
     *
     * @return the spacer region
     */
    private Region createSpacer() {
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        return spacer;
    }

    /**
     * Updates the HUD with current game state.
     *
     * @param credits    current credits
     * @param tick       current tick
     * @param selectedIds set of selected entity IDs
     */
    public void update(int credits, long tick, Set<Integer> selectedIds) {
        this.credits = credits;
        this.currentTick = tick;

        creditsLabel.setText(String.valueOf(credits));
        tickLabel.setText(String.valueOf(tick));

        // Update unit count
        if (entityManager != null) {
            int aliveUnits = entityManager.getAliveUnitsForPlayer(playerFaction).size();
            unitCountLabel.setText(aliveUnits + "/50");
        }

        // Update selection info
        updateSelectionInfo(selectedIds);

        // Update production queue display
        updateProductionQueue(selectedIds);
    }

    /**
     * Updates the selection info panel based on selected entities.
     *
     * @param selectedIds the set of selected entity IDs
     */
    private void updateSelectionInfo(Set<Integer> selectedIds) {
        if (selectedIds == null || selectedIds.isEmpty()) {
            selectionNameLabel.setText("No Selection");
            selectionHpLabel.setText("");
            selectionInfoLabel.setText("");
            selectionExtraLabel.setText("");
            return;
        }

        if (entityManager == null) {
            return;
        }

        // Single selection - show detailed info
        if (selectedIds.size() == 1) {
            int id = selectedIds.iterator().next();
            Unit unit = entityManager.getUnit(id);
            if (unit != null) {
                selectionNameLabel.setText(unit.getUnitType().displayName());
                selectionHpLabel.setText("HP: " + unit.getHp() + "/" + unit.getMaxHp());
                selectionInfoLabel.setText(String.format("DMG: %d | SPD: %d | RNG: %d | ARM: %d",
                    unit.getStats().damage(), unit.getStats().speed(),
                    unit.getStats().attackRange(), unit.getStats().armor()));
                selectionExtraLabel.setText(String.format("Rank: %d | XP: %d | %s",
                    unit.getRank(), unit.getExperience(), unit.getFaction()));
                return;
            }

            Building building = entityManager.getBuilding(id);
            if (building != null) {
                selectionNameLabel.setText(building.getBuildingType().displayName());
                selectionHpLabel.setText("HP: " + building.getHp() + "/" + building.getEffectiveMaxHp());
                selectionInfoLabel.setText(String.format("Armor: %d | Sight: %d | %s",
                    building.getStats().armor(), building.getStats().sightRange(),
                    building.isPowered() ? "Powered" : "Unpowered"));
                // FIX (Building Upgrade UI): Show upgrade level in the selection panel
                String extra = building.isUnderConstruction()
                    ? "Under Construction"
                    : "Lv " + building.getUpgradeLevel() + "/3 | " + building.getBuildingType().faction();
                selectionExtraLabel.setText(extra);
                return;
            }
        }

        // Multiple selection - show summary
        selectionNameLabel.setText(selectedIds.size() + " units selected");
        selectionHpLabel.setText("");
        selectionInfoLabel.setText("");
        selectionExtraLabel.setText("");
    }

    /**
     * Updates the production queue display for a selected building.
     *
     * @param selectedIds the set of selected entity IDs
     */
    private void updateProductionQueue(Set<Integer> selectedIds) {
        // Clear production slots
        for (Label slot : productionSlots) {
            slot.setText("-");
        }
        displayedBuildingId = -1;

        if (selectedIds == null || selectedIds.size() != 1 || entityManager == null) {
            return;
        }

        int id = selectedIds.iterator().next();
        displayedBuildingId = id;
        Building building = entityManager.getBuilding(id);
        if (building == null || !building.getBuildingType().producesUnits()) {
            return;
        }

        // Show current production
        if (building.getCurrentProduction() != null) {
            productionSlots[0].setText("▶ " + building.getCurrentProduction().displayName());
        }

        // Show queued items
        List<com.aow2.common.model.UnitType> queue = building.getProductionQueue();
        for (int i = 0; i < Math.min(queue.size(), productionSlots.length - 1); i++) {
            productionSlots[i + 1].setText("  " + queue.get(i).displayName());
        }
    }

    /**
     * Gets the root HUD pane for adding to the scene graph.
     *
     * @return the root StackPane
     */
    public StackPane getRoot() {
        return root;
    }

    /**
     * Sets the entity manager.
     *
     * @param entityManager the entity manager
     */
    public void setEntityManager(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    /**
     * Sets the player faction for display purposes.
     *
     * @param faction the player faction
     */
    public void setPlayerFaction(Faction faction) {
        this.playerFaction = faction;
    }

    /**
     * Sets the action callback for button presses.
     *
     * @param callback the action callback
     */
    public void setActionCallback(ActionCallback callback) {
        this.actionCallback = callback;
    }
}

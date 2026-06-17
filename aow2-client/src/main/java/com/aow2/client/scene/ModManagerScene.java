package com.aow2.client.scene;

import com.aow2.mod.GameDataRegistry;
import com.aow2.mod.ModInstaller;
import com.aow2.mod.ModManager;
import com.aow2.mod.ModManifest;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Desktop;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Mod management UI accessible from the Main Menu.
 * <p>
 * Features:
 * - List of discovered mods from the mods directory
 * - For each mod: name, version, author, description, enabled/disabled toggle
 * - Install button: opens file chooser for .zip mod files
 * - Uninstall button: removes mod directory
 * - Enable/Disable toggle: calls ModManager.enableMod()/disableMod()
 * - Refresh button: rescans mods directory
 * - Open Mods Folder button: opens the mods directory in file explorer
 * - Shows mod dependencies and conflicts
 * - Back button to return to main menu
 * - Dark military theme matching the rest of the UI
 * <p>
 * REF: phases.md Phase 10 - Mod Manager UI
 * REF: project_structure.md - mod system architecture
 */
public class ModManagerScene {

    private static final Logger LOG = LoggerFactory.getLogger(ModManagerScene.class);

    /** Default mods directory relative to working directory. */
    private static final String DEFAULT_MODS_DIR = "mods";

    /** Button style for primary actions. */
    private static final String BTN_STYLE = "-fx-background-color: rgb(40, 45, 35); "
        + "-fx-text-fill: rgb(210, 200, 160); "
        + "-fx-border-color: rgb(100, 110, 70); -fx-border-width: 1; "
        + "-fx-font-size: 13px; -fx-font-weight: bold; "
        + "-fx-padding: 6 16 6 16; -fx-cursor: hand; -fx-font-family: Consolas;";

    /** Button hover style. */
    private static final String BTN_HOVER_STYLE = "-fx-background-color: rgb(60, 70, 45); "
        + "-fx-text-fill: rgb(240, 230, 180); "
        + "-fx-border-color: rgb(140, 150, 90); -fx-border-width: 2; "
        + "-fx-font-size: 13px; -fx-font-weight: bold; "
        + "-fx-padding: 6 16 6 16; -fx-cursor: hand; -fx-font-family: Consolas;";

    /** Danger button style for destructive actions. */
    private static final String BTN_DANGER_STYLE = "-fx-background-color: rgb(60, 35, 35); "
        + "-fx-text-fill: rgb(210, 160, 160); "
        + "-fx-border-color: rgb(110, 70, 70); -fx-border-width: 1; "
        + "-fx-font-size: 13px; -fx-font-weight: bold; "
        + "-fx-padding: 6 16 6 16; -fx-cursor: hand; -fx-font-family: Consolas;";

    /** The root pane for this scene. */
    private final StackPane root;

    /** The mod manager instance. */
    private final ModManager modManager;

    /** The mod installer instance. */
    private final ModInstaller modInstaller;

    /** The mods directory path. */
    private final Path modsDirectory;

    /** Container for mod list entries. */
    private VBox modListContainer;

    /** Status label for feedback messages. */
    private Label statusLabel;

    /** Callback for navigation back to main menu. */
    private Runnable onBackCallback;

    /**
     * Constructs a new ModManagerScene with default mods directory.
     */
    public ModManagerScene() {
        this(Paths.get(DEFAULT_MODS_DIR));
    }

    /**
     * Constructs a new ModManagerScene with a specified mods directory.
     *
     * @param modsDirectory the path to the mods directory
     */
    public ModManagerScene(Path modsDirectory) {
        this.root = new StackPane();
        this.modsDirectory = modsDirectory;
        this.modManager = new ModManager(new GameDataRegistry());
        this.modInstaller = new ModInstaller(modsDirectory, modManager);

        buildUI();
        refreshMods();
        LOG.info("ModManagerScene created");
    }

    /**
     * Builds the mod manager UI.
     */
    private void buildUI() {
        root.setPrefSize(1280, 720);
        root.setStyle("-fx-background-color: rgb(15, 18, 22);");

        BorderPane content = new BorderPane();
        content.setPadding(new Insets(20));

        // --- Title bar ---
        VBox titleBox = new VBox(5);
        Label titleLabel = new Label("MOD MANAGER");
        titleLabel.setFont(Font.font("Consolas", 28));
        titleLabel.setTextFill(Color.rgb(210, 200, 160));

        statusLabel = new Label("");
        statusLabel.setFont(Font.font("Consolas", 12));
        statusLabel.setTextFill(Color.rgb(120, 130, 100));

        titleBox.getChildren().addAll(titleLabel, statusLabel);
        content.setTop(titleBox);

        // --- Toolbar ---
        HBox toolbar = new HBox(10);
        toolbar.setPadding(new Insets(10, 0, 10, 0));
        toolbar.setAlignment(Pos.CENTER_LEFT);

        Button installBtn = createStyledButton("Install Mod", BTN_STYLE);
        installBtn.setOnAction(e -> installModFromZip());

        Button refreshBtn = createStyledButton("Refresh", BTN_STYLE);
        refreshBtn.setOnAction(e -> refreshMods());

        Button openFolderBtn = createStyledButton("Open Mods Folder", BTN_STYLE);
        openFolderBtn.setOnAction(e -> openModsFolder());

        toolbar.getChildren().addAll(installBtn, refreshBtn, openFolderBtn);
        content.setTop(titleBox);

        // --- Mod list ---
        VBox listHeader = new VBox(toolbar);
        modListContainer = new VBox(6);
        modListContainer.setPadding(new Insets(10));

        ScrollPane scrollPane = new ScrollPane(modListContainer);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background-color: rgb(15, 18, 22); "
            + "-fx-border-color: rgb(40, 45, 35);");

        VBox centerContent = new VBox(10, toolbar, scrollPane);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);
        content.setCenter(centerContent);

        // --- Bottom bar ---
        HBox bottomBar = new HBox(10);
        bottomBar.setAlignment(Pos.BOTTOM_LEFT);
        Button backButton = new Button("← Back to Menu");
        backButton.setStyle("-fx-background-color: rgb(40, 45, 35); "
            + "-fx-text-fill: rgb(210, 200, 160); -fx-border-color: rgb(100, 110, 70); "
            + "-fx-padding: 8 20 8 20; -fx-cursor: hand;");
        backButton.setOnAction(e -> {
            if (onBackCallback != null) {
                onBackCallback.run();
            }
        });
        bottomBar.getChildren().add(backButton);
        content.setBottom(bottomBar);

        root.getChildren().add(content);
    }

    /**
     * Creates a styled button with hover effects.
     */
    private Button createStyledButton(String text, String style) {
        Button btn = new Button(text);
        btn.setStyle(style);
        btn.setOnMouseEntered(e -> btn.setStyle(BTN_HOVER_STYLE));
        btn.setOnMouseExited(e -> btn.setStyle(style));
        return btn;
    }

    /**
     * Refreshes the mod list by rescanning the mods directory.
     */
    private void refreshMods() {
        modListContainer.getChildren().clear();

        // Ensure mods directory exists
        try {
            Files.createDirectories(modsDirectory);
        } catch (Exception e) {
            setStatus("Could not create mods directory: " + e.getMessage(), true);
            return;
        }

        List<ModManifest> mods = modManager.discoverMods(modsDirectory);

        if (mods.isEmpty()) {
            Label emptyLabel = new Label("No mods found. Install a mod or place mod folders in the mods directory.");
            emptyLabel.setFont(Font.font("Consolas", 13));
            emptyLabel.setStyle("-fx-text-fill: rgb(120, 120, 100);");
            emptyLabel.setWrapText(true);
            modListContainer.getChildren().add(emptyLabel);
            setStatus("No mods discovered", false);
            return;
        }

        for (ModManifest mod : mods) {
            modListContainer.getChildren().add(createModEntry(mod));
        }

        setStatus(mods.size() + " mod(s) discovered", false);
    }

    /**
     * Creates a UI entry for a single mod.
     *
     * @param mod the mod manifest
     * @return the mod entry HBox
     */
    private HBox createModEntry(ModManifest mod) {
        HBox entry = new HBox(12);
        entry.setPadding(new Insets(10));
        entry.setStyle("-fx-background-color: rgb(22, 25, 20); "
            + "-fx-border-color: rgb(40, 45, 35); -fx-border-width: 1;");
        entry.setAlignment(Pos.CENTER_LEFT);

        // Left: mod info
        VBox infoBox = new VBox(4);
        infoBox.setPrefWidth(500);

        // Name and version
        HBox nameRow = new HBox(8);
        nameRow.setAlignment(Pos.CENTER_LEFT);
        Label nameLabel = new Label(mod.name());
        nameLabel.setFont(Font.font("Consolas", 14));
        nameLabel.setStyle("-fx-text-fill: rgb(210, 200, 160);");
        Label versionLabel = new Label("v" + mod.version());
        versionLabel.setFont(Font.font("Consolas", 11));
        versionLabel.setStyle("-fx-text-fill: rgb(100, 110, 70);");
        Label authorLabel = new Label("by " + mod.author());
        authorLabel.setFont(Font.font("Consolas", 11));
        authorLabel.setStyle("-fx-text-fill: rgb(100, 100, 80);");
        nameRow.getChildren().addAll(nameLabel, versionLabel, authorLabel);

        // Description
        Label descLabel = new Label(mod.description() != null ? mod.description() : "");
        descLabel.setFont(Font.font("Consolas", 11));
        descLabel.setStyle("-fx-text-fill: rgb(140, 135, 115);");
        descLabel.setWrapText(true);

        infoBox.getChildren().addAll(nameRow, descLabel);

        // Dependencies
        if (!mod.dependencies().isEmpty()) {
            Label depLabel = new Label("Dependencies: " + String.join(", ", mod.dependencies()));
            depLabel.setFont(Font.font("Consolas", 10));
            depLabel.setStyle("-fx-text-fill: rgb(100, 100, 80);");
            depLabel.setWrapText(true);
            infoBox.getChildren().add(depLabel);
        }

        // Game version compatibility
        if (mod.gameVersion() != null && !mod.gameVersion().isBlank()) {
            Label compatLabel = new Label("Game version: " + mod.gameVersion());
            compatLabel.setFont(Font.font("Consolas", 10));
            compatLabel.setStyle("-fx-text-fill: rgb(100, 100, 80);");
            infoBox.getChildren().add(compatLabel);
        }

        // Right: controls
        VBox controlsBox = new VBox(6);
        controlsBox.setAlignment(Pos.CENTER_RIGHT);

        // Enable/disable toggle
        boolean isEnabled = modManager.isModEnabled(mod.id());
        CheckBox enableCheck = new CheckBox("Enabled");
        enableCheck.setSelected(isEnabled);
        enableCheck.setStyle("-fx-text-fill: rgb(180, 175, 145); -fx-font-family: Consolas;");
        enableCheck.setOnAction(e -> {
            if (enableCheck.isSelected()) {
                boolean success = modManager.enableMod(mod.id());
                if (!success) {
                    enableCheck.setSelected(false);
                    setStatus("Cannot enable " + mod.name() + ": missing dependencies", true);
                } else {
                    setStatus("Enabled " + mod.name(), false);
                }
            } else {
                boolean success = modManager.disableMod(mod.id());
                if (!success) {
                    enableCheck.setSelected(true);
                    setStatus("Cannot disable " + mod.name() + ": other mods depend on it", true);
                } else {
                    setStatus("Disabled " + mod.name(), false);
                }
            }
        });

        // Uninstall button
        Button uninstallBtn = new Button("Uninstall");
        uninstallBtn.setStyle(BTN_DANGER_STYLE);
        uninstallBtn.setOnMouseEntered(e -> uninstallBtn.setStyle(
            "-fx-background-color: rgb(80, 40, 40); "
            + "-fx-text-fill: rgb(240, 180, 180); "
            + "-fx-border-color: rgb(150, 80, 80); -fx-border-width: 2; "
            + "-fx-font-size: 13px; -fx-font-weight: bold; "
            + "-fx-padding: 6 16 6 16; -fx-cursor: hand; -fx-font-family: Consolas;"));
        uninstallBtn.setOnMouseExited(e -> uninstallBtn.setStyle(BTN_DANGER_STYLE));
        uninstallBtn.setOnAction(e -> {
            boolean success = modInstaller.uninstallMod(mod.id());
            if (success) {
                setStatus("Uninstalled " + mod.name(), false);
                refreshMods();
            } else {
                setStatus("Failed to uninstall " + mod.name(), true);
            }
        });

        controlsBox.getChildren().addAll(enableCheck, uninstallBtn);

        entry.getChildren().addAll(infoBox, controlsBox);
        HBox.setHgrow(infoBox, Priority.ALWAYS);
        return entry;
    }

    /**
     * Opens a file chooser to select a .zip mod file for installation.
     */
    private void installModFromZip() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select Mod ZIP File");
        fileChooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("Mod ZIP Files", "*.zip"));
        fileChooser.setInitialDirectory(new File(System.getProperty("user.dir")));

        // Get the stage from the scene graph
        Stage stage = (Stage) root.getScene().getWindow();
        File selectedFile = fileChooser.showOpenDialog(stage);

        if (selectedFile != null) {
            Path zipPath = selectedFile.toPath();
            setStatus("Installing mod from " + zipPath.getFileName() + "...", false);

            boolean success = modInstaller.installFromZip(zipPath);
            if (success) {
                setStatus("Mod installed successfully", false);
                refreshMods();
            } else {
                setStatus("Failed to install mod. Check log for details.", true);
            }
        }
    }

    /**
     * Opens the mods directory in the system file explorer.
     */
    private void openModsFolder() {
        try {
            Files.createDirectories(modsDirectory);
            File modsDir = modsDirectory.toFile();
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(modsDir);
            } else {
                setStatus("Cannot open folder on this platform", true);
            }
        } catch (Exception e) {
            LOG.error("Failed to open mods folder", e);
            setStatus("Failed to open mods folder: " + e.getMessage(), true);
        }
    }

    /**
     * Sets the status label text.
     *
     * @param message the status message
     * @param isError whether this is an error message
     */
    private void setStatus(String message, boolean isError) {
        if (statusLabel != null) {
            statusLabel.setText(message);
            statusLabel.setStyle(isError
                ? "-fx-text-fill: rgb(200, 80, 80); -fx-font-family: Consolas; -fx-font-size: 12px;"
                : "-fx-text-fill: rgb(120, 130, 100); -fx-font-family: Consolas; -fx-font-size: 12px;");
        }
    }

    // --- Public API ---

    /**
     * Get the root pane for this scene.
     *
     * @return the root StackPane
     */
    public StackPane getRoot() {
        return root;
    }

    /**
     * Set the callback for navigating back to the main menu.
     *
     * @param callback the callback
     */
    public void setOnBackCallback(Runnable callback) {
        this.onBackCallback = callback;
    }

    /**
     * Returns the mod manager instance.
     *
     * @return the mod manager
     */
    public ModManager getModManager() {
        return modManager;
    }

    /**
     * Returns the mod installer instance.
     *
     * @return the mod installer
     */
    public ModInstaller getModInstaller() {
        return modInstaller;
    }
}

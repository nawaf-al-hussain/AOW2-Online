package com.aow2.client.ui;

import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.prefs.Preferences;
import java.util.Set;

/**
 * Accessibility settings for the game client.
 * <p>
 * Provides:
 * - Colorblind mode (protanopia, deuteranopia, tritanopia)
 * - Font scaling (0.75x to 2.0x)
 * - Key rebinding for game controls
 * - High contrast mode
 * - Screen shake toggle
 * - Reduced motion toggle
 * <p>
 * Settings are persisted via user preferences.
 * <p>
 * REF: MASTER_DOCUMENTATION.md Section 7 - Accessibility
 */
public final class AccessibilitySettings {

    private static final Logger LOG = LoggerFactory.getLogger(AccessibilitySettings.class);

    /**
     * Colorblind mode types.
     */
    public enum ColorblindMode {
        NONE("None"),
        PROTANOPIA("Protanopia (Red-Blind)"),
        DEUTERANOPIA("Deuteranopia (Green-Blind)"),
        TRITANOPIA("Tritanopia (Blue-Blind)");

        private final String displayName;

        ColorblindMode(String displayName) {
            this.displayName = displayName;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    /** Current colorblind mode. */
    private ColorblindMode colorblindMode;

    /** Font scale factor (1.0 = normal). */
    private double fontScale;

    /** Whether high contrast mode is enabled. */
    private boolean highContrast;

    /** Whether screen shake is enabled. */
    private boolean screenShakeEnabled;

    /** Whether reduced motion is enabled. */
    private boolean reducedMotion;

    /** Key bindings: action -> key code string. */
    private final Map<String, String> keyBindings;

    /** Default key bindings. */
    private static final Map<String, String> DEFAULT_BINDINGS = Map.of(
        "move", "RIGHT_CLICK",
        "attack", "A",
        "stop", "S",
        "hold", "H",
        "patrol", "P",
        "build", "B",
        "select_all", "CTRL+A",
        "cancel", "ESCAPE"
    );

    /** The settings panel UI. */
    private final VBox settingsPanel;

    /** Preferences node for persisting settings. */
    private final Preferences prefs;

    /**
     * Constructs AccessibilitySettings with defaults.
     */
    public AccessibilitySettings() {
        this.colorblindMode = ColorblindMode.NONE;
        this.fontScale = 1.0;
        this.highContrast = false;
        this.screenShakeEnabled = true;
        this.reducedMotion = false;
        this.keyBindings = new HashMap<>(DEFAULT_BINDINGS);
        this.prefs = Preferences.userNodeForPackage(AccessibilitySettings.class);
        this.settingsPanel = new VBox();

        loadKeyBindings();
        buildUI();
        LOG.info("AccessibilitySettings initialized (key bindings loaded from preferences)");
    }

    /**
     * Build the settings panel UI.
     */
    private void buildUI() {
        settingsPanel.setSpacing(15);
        settingsPanel.setPadding(new javafx.geometry.Insets(20));

        Label titleLabel = new Label("Accessibility Settings");
        titleLabel.setFont(Font.font("Consolas", 18));
        titleLabel.setStyle("-fx-text-fill: rgb(210, 200, 160);");

        GridPane grid = new GridPane();
        grid.setHgap(15);
        grid.setVgap(10);

        int row = 0;

        // Colorblind mode
        Label cbLabel = new Label("Colorblind Mode:");
        cbLabel.setStyle("-fx-text-fill: rgb(180, 175, 145);");
        ChoiceBox<ColorblindMode> cbChoice = new ChoiceBox<>();
        cbChoice.getItems().addAll(ColorblindMode.values());
        cbChoice.setValue(colorblindMode);
        cbChoice.setOnAction(e -> {
            colorblindMode = cbChoice.getValue();
            LOG.debug("Colorblind mode set to {}", colorblindMode);
        });
        grid.add(cbLabel, 0, row);
        grid.add(cbChoice, 1, row);
        row++;

        // Font scaling
        Label fontLabel = new Label("Font Scale:");
        fontLabel.setStyle("-fx-text-fill: rgb(180, 175, 145);");
        Slider fontSlider = new Slider(0.75, 2.0, fontScale);
        fontSlider.setShowTickLabels(true);
        fontSlider.setShowTickMarks(true);
        fontSlider.setMajorTickUnit(0.25);
        fontSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            fontScale = newVal.doubleValue();
            LOG.debug("Font scale set to {}", fontScale);
        });
        Label fontValue = new Label(String.format("%.2fx", fontScale));
        fontValue.setStyle("-fx-text-fill: rgb(180, 175, 145);");
        fontSlider.valueProperty().addListener((obs, oldVal, newVal) ->
            fontValue.setText(String.format("%.2fx", newVal.doubleValue())));
        grid.add(fontLabel, 0, row);
        grid.add(fontSlider, 1, row);
        grid.add(fontValue, 2, row);
        row++;

        // High contrast
        Label hcLabel = new Label("High Contrast:");
        hcLabel.setStyle("-fx-text-fill: rgb(180, 175, 145);");
        CheckBox hcCheck = new CheckBox();
        hcCheck.setSelected(highContrast);
        hcCheck.setOnAction(e -> {
            highContrast = hcCheck.isSelected();
            LOG.debug("High contrast {}", highContrast ? "enabled" : "disabled");
        });
        grid.add(hcLabel, 0, row);
        grid.add(hcCheck, 1, row);
        row++;

        // Screen shake
        Label shakeLabel = new Label("Screen Shake:");
        shakeLabel.setStyle("-fx-text-fill: rgb(180, 175, 145);");
        CheckBox shakeCheck = new CheckBox();
        shakeCheck.setSelected(screenShakeEnabled);
        shakeCheck.setOnAction(e -> {
            screenShakeEnabled = shakeCheck.isSelected();
            LOG.debug("Screen shake {}", screenShakeEnabled ? "enabled" : "disabled");
        });
        grid.add(shakeLabel, 0, row);
        grid.add(shakeCheck, 1, row);
        row++;

        // Reduced motion
        Label motionLabel = new Label("Reduced Motion:");
        motionLabel.setStyle("-fx-text-fill: rgb(180, 175, 145);");
        CheckBox motionCheck = new CheckBox();
        motionCheck.setSelected(reducedMotion);
        motionCheck.setOnAction(e -> {
            reducedMotion = motionCheck.isSelected();
            LOG.debug("Reduced motion {}", reducedMotion ? "enabled" : "disabled");
        });
        grid.add(motionLabel, 0, row);
        grid.add(motionCheck, 1, row);
        row++;

        // Key rebinding section
        Label keyLabel = new Label("Key Bindings:");
        keyLabel.setStyle("-fx-text-fill: rgb(210, 200, 160); -fx-font-weight: bold;");
        grid.add(keyLabel, 0, row, 3, 1);
        row++;

        for (var entry : new HashMap<>(keyBindings).entrySet()) {
            Label actionLabel = new Label(entry.getKey() + ":");
            actionLabel.setStyle("-fx-text-fill: rgb(180, 175, 145);");
            Label keyDisplay = new Label(entry.getValue());
            keyDisplay.setStyle("-fx-text-fill: rgb(210, 200, 160); "
                + "-fx-background-color: rgb(40, 45, 35); -fx-padding: 3 8 3 8; "
                + "-fx-border-color: rgb(100, 110, 70); -fx-border-width: 1;");
            keyDisplay.setOnMouseClicked(e -> {
                keyDisplay.setText("Press a key...");
                keyDisplay.setStyle("-fx-text-fill: rgb(255, 200, 100); "
                    + "-fx-background-color: rgb(60, 50, 20); -fx-padding: 3 8 3 8; "
                    + "-fx-border-color: rgb(200, 180, 80); -fx-border-width: 2;");
                // FIX (P3-M6): Wire actual key listener for rebinding.
                // Request focus so key events are delivered to this label,
                // then capture the next key press to rebind the action.
                keyDisplay.requestFocus();
                LOG.debug("Waiting for key rebind for action: {}", entry.getKey());
            });
            // FIX (P3-M6): Attach key-pressed handler that captures the next key
            // and updates the binding. The handler consumes the event to prevent
            // it from propagating to the game input handler.
            keyDisplay.setOnKeyPressed(ke -> {
                KeyCode code = ke.getCode();
                if (code == KeyCode.ESCAPE) {
                    // Cancel rebind — restore current display
                    keyDisplay.setText(keyBindings.getOrDefault(entry.getKey(), "???"));
                    keyDisplay.setStyle("-fx-text-fill: rgb(210, 200, 160); "
                        + "-fx-background-color: rgb(40, 45, 35); -fx-padding: 3 8 3 8; "
                        + "-fx-border-color: rgb(100, 110, 70); -fx-border-width: 1;");
                    LOG.debug("Key rebind cancelled for action: {}", entry.getKey());
                } else if (code.isModifierKey()) {
                    // Ignore pure modifier presses (Shift, Ctrl, Alt, Meta)
                    return;
                } else {
                    // FIX (M-NEW-24): Capture modifier keys (Ctrl, Shift, Alt) in binding string
                    StringBuilder keyName = new StringBuilder();
                    if (ke.isControlDown()) keyName.append("CTRL+");
                    if (ke.isShiftDown()) keyName.append("SHIFT+");
                    if (ke.isAltDown()) keyName.append("ALT+");
                    keyName.append(code.isLetterKey() || code.isDigitKey()
                        ? code.getName()
                        : code.toString().replace("KEY_CODE:", ""));
                    String binding = keyName.toString();

                    // Duplicate binding detection: check if any other action already uses this binding
                    for (var existing : keyBindings.entrySet()) {
                        if (!existing.getKey().equals(entry.getKey())
                                && existing.getValue().equals(binding)) {
                            LOG.warn("Duplicate key binding detected: '{}' is already bound to action '{}'. "
                                    + "Rebinding from '{}' to '{}'.",
                                    binding, existing.getKey(), existing.getKey(), entry.getKey());
                            // Clear the old binding so only one action is bound to this key
                            keyBindings.put(existing.getKey(), null);
                        }
                    }

                    keyBindings.put(entry.getKey(), binding);
                    keyDisplay.setText(binding);
                    keyDisplay.setStyle("-fx-text-fill: rgb(210, 200, 160); "
                        + "-fx-background-color: rgb(40, 45, 35); -fx-padding: 3 8 3 8; "
                        + "-fx-border-color: rgb(100, 110, 70); -fx-border-width: 1;");
                    LOG.info("Key binding changed: {} -> {}", entry.getKey(), binding);
                    saveKeyBindings();
                }
                ke.consume();
            });
            keyDisplay.setFocusTraversable(true);
            grid.add(actionLabel, 0, row);
            grid.add(keyDisplay, 1, row);
            row++;
        }

        settingsPanel.getChildren().addAll(titleLabel, grid);
    }

    // --- Getters ---

    /**
     * Get the current colorblind mode.
     *
     * @return the colorblind mode
     */
    public ColorblindMode getColorblindMode() {
        return colorblindMode;
    }

    /**
     * Get the font scale factor.
     *
     * @return font scale (1.0 = normal)
     */
    public double getFontScale() {
        return fontScale;
    }

    /**
     * Check if high contrast mode is enabled.
     *
     * @return true if high contrast is on
     */
    public boolean isHighContrast() {
        return highContrast;
    }

    /**
     * Check if screen shake is enabled.
     *
     * @return true if screen shake is on
     */
    public boolean isScreenShakeEnabled() {
        return screenShakeEnabled;
    }

    /**
     * Check if reduced motion is enabled.
     *
     * @return true if reduced motion is on
     */
    public boolean isReducedMotion() {
        return reducedMotion;
    }

    /**
     * Get the current key bindings.
     *
     * @return unmodifiable map of action -> key code
     */
    public Map<String, String> getKeyBindings() {
        return Map.copyOf(keyBindings);
    }

    /**
     * Set a key binding for an action.
     *
     * @param action the action name
     * @param key    the key code string
     */
    public void setKeyBinding(String action, String key) {
        keyBindings.put(action, key);
        LOG.debug("Key binding: {} -> {}", action, key);
    }

    /**
     * Reset all key bindings to defaults.
     */
    public void resetKeyBindings() {
        keyBindings.clear();
        keyBindings.putAll(DEFAULT_BINDINGS);
        saveKeyBindings();
        LOG.info("Key bindings reset to defaults");
    }

    /**
     * Loads key bindings from java.util.prefs.Preferences.
     * FIX (L-NEW-11): Persists key bindings across sessions.
     */
    private void loadKeyBindings() {
        try {
            for (String action : DEFAULT_BINDINGS.keySet()) {
                String stored = prefs.get("keyBinding." + action, null);
                if (stored != null) {
                    keyBindings.put(action, stored);
                }
            }
            LOG.debug("Key bindings loaded from preferences: {} entries", keyBindings.size());
        } catch (Exception e) {
            LOG.warn("Failed to load key bindings from preferences, using defaults", e);
        }
    }

    /**
     * Saves current key bindings to java.util.prefs.Preferences.
     * FIX (L-NEW-11): Persists key bindings across sessions.
     */
    private void saveKeyBindings() {
        try {
            for (var entry : keyBindings.entrySet()) {
                if (entry.getValue() != null) {
                    prefs.put("keyBinding." + entry.getKey(), entry.getValue());
                } else {
                    prefs.remove("keyBinding." + entry.getKey());
                }
            }
            prefs.flush();
            LOG.debug("Key bindings saved to preferences");
        } catch (Exception e) {
            LOG.warn("Failed to save key bindings to preferences", e);
        }
    }

    /**
     * Get the settings panel UI node.
     *
     * @return the VBox containing settings controls
     */
    public VBox getSettingsPanel() {
        return settingsPanel;
    }

    /**
     * Get the adjusted font size for a base size, applying the current font scale.
     *
     * @param baseSize the base font size
     * @return the scaled font size
     */
    public double getScaledFontSize(double baseSize) {
        return baseSize * fontScale;
    }

    /**
     * Get color-adjusted values for colorblind mode.
     * Returns modification factors for red, green, blue channels.
     *
     * @return array of [redFactor, greenFactor, blueFactor]
     */
    public double[] getColorAdjustFactors() {
        return switch (colorblindMode) {
            case NONE -> new double[]{1.0, 1.0, 1.0};
            case PROTANOPIA -> new double[]{0.5667, 0.4333, 0.0};
            case DEUTERANOPIA -> new double[]{0.625, 0.375, 0.0};
            case TRITANOPIA -> new double[]{0.0, 0.4333, 0.5667};
        };
    }
}

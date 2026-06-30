package com.aow2.client.input;

import com.aow2.common.model.Faction;
import com.aow2.common.model.GridPosition;
import com.aow2.core.entity.Building;
import com.aow2.core.entity.Unit;
import com.aow2.core.world.EntityManager;

import javafx.scene.input.MouseButton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Handles unit and building selection logic for the game.
 * <p>
 * Selection modes:
 * - Click to select single unit/building
 * - Drag box to select multiple units
 * - Shift+click to add to selection
 * - Ctrl+click to select all units of same type on screen
 * - Right-click for commands (move, attack, garrison)
 * - Max selection: 20 units
 * <p>
 * REF: MASTER_DOCUMENTATION.md Section 7 - Selection and Control Groups
 */
public class SelectionManager {

    private static final Logger LOG = LoggerFactory.getLogger(SelectionManager.class);

    /** Maximum number of units that can be selected at once. */
    public static final int MAX_SELECTION = 20;

    /** The currently selected entity IDs, preserving insertion order. */
    private final LinkedHashSet<Integer> selectedIds;

    /** The entity manager for entity lookups. */
    private EntityManager entityManager;

    /** The player's faction (only own units can be selected). */
    private Faction playerFaction;

    /** Whether a drag selection is in progress. */
    private boolean dragging;

    /** Start X of the drag selection box. */
    private double dragStartX;

    /** Start Y of the drag selection box. */
    private double dragStartY;

    /** Current X of the drag selection box. */
    private double dragCurrentX;

    /** Current Y of the drag selection box. */
    private double dragCurrentY;

    /** Minimum drag distance in pixels to count as a box select. */
    private static final double MIN_DRAG_DISTANCE = 5.0;

    /**
     * Constructs a new SelectionManager.
     */
    public SelectionManager() {
        this.selectedIds = new LinkedHashSet<>();
        this.playerFaction = Faction.CONFEDERATION;
        this.dragging = false;
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
     * Sets the player faction.
     *
     * @param faction the player faction
     */
    public void setPlayerFaction(Faction faction) {
        this.playerFaction = faction;
    }

    /**
     * Gets the set of currently selected entity IDs.
     *
     * @return unmodifiable set of selected entity IDs
     */
    public Set<Integer> getSelectedIds() {
        return Collections.unmodifiableSet(selectedIds);
    }

    /**
     * Returns whether any entities are currently selected.
     *
     * @return true if at least one entity is selected
     */
    public boolean hasSelection() {
        return !selectedIds.isEmpty();
    }

    /**
     * Returns the number of selected entities.
     *
     * @return selection count
     */
    public int selectionCount() {
        return selectedIds.size();
    }

    /**
     * Clears the current selection.
     */
    public void clearSelection() {
        selectedIds.clear();
    }

    /**
     * Selects units by their entity IDs, replacing the current selection.
     * Only IDs that correspond to alive units belonging to the player's faction
     * are actually added (invalid or dead IDs are silently skipped).
     * Selection is capped at {@link #MAX_SELECTION}.
     *
     * @param ids the entity IDs to select
     */
    public void selectUnitsByIds(List<Integer> ids) {
        selectedIds.clear();
        if (entityManager == null || ids == null) {
            return;
        }
        for (int id : ids) {
            if (selectedIds.size() >= MAX_SELECTION) {
                break;
            }
            Unit unit = entityManager.getUnit(id);
            if (unit != null && unit.isAlive() && unit.getFaction() == playerFaction) {
                selectedIds.add(id);
            }
        }
        LOG.debug("selectUnitsByIds: requested={}, actually selected={}", ids.size(), selectedIds.size());
    }

    /**
     * Starts a drag selection box at the given screen coordinates.
     *
     * @param screenX screen X coordinate
     * @param screenY screen Y coordinate
     */
    public void startDrag(double screenX, double screenY) {
        dragging = true;
        dragStartX = screenX;
        dragStartY = screenY;
        dragCurrentX = screenX;
        dragCurrentY = screenY;
    }

    /**
     * Updates the drag selection box endpoint.
     *
     * @param screenX current screen X
     * @param screenY current screen Y
     */
    public void updateDrag(double screenX, double screenY) {
        if (dragging) {
            dragCurrentX = screenX;
            dragCurrentY = screenY;
        }
    }

    /**
     * Ends the drag selection and selects units within the box.
     *
     * @param screenX   end screen X
     * @param screenY   end screen Y
     * @param shiftDown whether shift is held (add to selection)
     * @param gridConverter function to convert screen coords to grid coords: (sx, sy) -> int[gx, gy]
     */
    public void endDrag(double screenX, double screenY, boolean shiftDown,
                        java.util.function.BiFunction<Double, Double, int[]> gridConverter) {
        if (!dragging) {
            return;
        }
        dragging = false;
        dragCurrentX = screenX;
        dragCurrentY = screenY;

        // Check if the drag was large enough for box select
        double dx = Math.abs(dragCurrentX - dragStartX);
        double dy = Math.abs(dragCurrentY - dragStartY);
        if (dx < MIN_DRAG_DISTANCE && dy < MIN_DRAG_DISTANCE) {
            return; // Too small, treat as a click instead
        }

        if (!shiftDown) {
            selectedIds.clear();
        }

        // Select all own units within the drag box
        if (entityManager == null) {
            return;
        }

        // Determine grid bounds from screen box corners
        int[] topLeft = gridConverter.apply(
            Math.min(dragStartX, dragCurrentX), Math.min(dragStartY, dragCurrentY));
        int[] bottomRight = gridConverter.apply(
            Math.max(dragStartX, dragCurrentX), Math.max(dragStartY, dragCurrentY));

        int minGx = Math.min(topLeft[0], bottomRight[0]) - 1;
        int maxGx = Math.max(topLeft[0], bottomRight[0]) + 1;
        int minGy = Math.min(topLeft[1], bottomRight[1]) - 1;
        int maxGy = Math.max(topLeft[1], bottomRight[1]) + 1;

        for (Unit unit : entityManager.getAliveUnitsForPlayer(playerFaction)) {
            if (selectedIds.size() >= MAX_SELECTION) {
                break;
            }
            int ux = unit.getPosition().x();
            int uy = unit.getPosition().y();
            if (ux >= minGx && ux <= maxGx && uy >= minGy && uy <= maxGy) {
                selectedIds.add(unit.getId());
            }
        }

        LOG.debug("Box select: {} units selected", selectedIds.size());
    }

    /**
     * Handles a left-click selection at the given grid position.
     *
     * @param gx        grid X coordinate
     * @param gy        grid Y coordinate
     * @param shiftDown whether shift is held (add to selection)
     * @param ctrlDown  whether ctrl is held (select all of same type)
     */
    public void handleClick(int gx, int gy, boolean shiftDown, boolean ctrlDown) {
        if (entityManager == null) {
            return;
        }

        // Try to find a unit at this position
        Unit clickedUnit = entityManager.findUnitAt(new GridPosition(gx, gy));
        if (clickedUnit != null && clickedUnit.getFaction() == playerFaction) {
            if (ctrlDown) {
                // Select all units of the same type
                selectAllOfType(clickedUnit.getUnitType());
            } else if (shiftDown) {
                // Toggle this unit in selection
                if (selectedIds.contains(clickedUnit.getId())) {
                    selectedIds.remove(clickedUnit.getId());
                } else if (selectedIds.size() < MAX_SELECTION) {
                    selectedIds.add(clickedUnit.getId());
                }
            } else {
                // Single select
                selectedIds.clear();
                selectedIds.add(clickedUnit.getId());
            }
            LOG.debug("Selected unit: {} (total: {})", clickedUnit, selectedIds.size());
            return;
        }

        // Try to find a building at this position
        Building clickedBuilding = entityManager.findBuildingAt(new GridPosition(gx, gy));
        if (clickedBuilding != null && clickedBuilding.getFaction() == playerFaction) {
            if (!shiftDown) {
                selectedIds.clear();
            }
            selectedIds.add(clickedBuilding.getId());
            LOG.debug("Selected building: {} (total: {})", clickedBuilding, selectedIds.size());
            return;
        }

        // Click on empty space or enemy entity - deselect (unless shift held)
        if (!shiftDown) {
            selectedIds.clear();
        }
    }

    /**
     * Selects all alive units of the given type for the player's faction.
     *
     * @param unitType the unit type to select all of
     */
    private void selectAllOfType(com.aow2.common.model.UnitType unitType) {
        selectedIds.clear();
        for (Unit unit : entityManager.getAliveUnitsForPlayer(playerFaction)) {
            if (selectedIds.size() >= MAX_SELECTION) {
                break;
            }
            if (unit.getUnitType() == unitType) {
                selectedIds.add(unit.getId());
            }
        }
    }

    /**
     * Returns all currently selected units.
     *
     * @return list of selected units
     */
    public List<Unit> getSelectedUnits() {
        if (entityManager == null) {
            return List.of();
        }
        List<Unit> units = new ArrayList<>();
        for (int id : selectedIds) {
            Unit unit = entityManager.getUnit(id);
            if (unit != null && unit.isAlive()) {
                units.add(unit);
            }
        }
        return units;
    }

    /**
     * Returns all currently selected buildings.
     *
     * @return list of selected buildings
     */
    public List<Building> getSelectedBuildings() {
        if (entityManager == null) {
            return List.of();
        }
        List<Building> buildings = new ArrayList<>();
        for (int id : selectedIds) {
            Building building = entityManager.getBuilding(id);
            if (building != null && building.isAlive()) {
                buildings.add(building);
            }
        }
        return buildings;
    }

    /**
     * Returns whether a drag selection is currently in progress.
     *
     * @return true if dragging
     */
    public boolean isDragging() {
        return dragging;
    }

    /**
     * Gets the drag selection box coordinates.
     *
     * @return [startX, startY, currentX, currentY] or null if not dragging
     */
    public double[] getDragBox() {
        if (!dragging) {
            return null;
        }
        return new double[]{dragStartX, dragStartY, dragCurrentX, dragCurrentY};
    }

    /**
     * Gets the currently selected IDs as a mutable list.
     * Used by InputHandler for control group assignment.
     *
     * @return list of selected entity IDs
     */
    public List<Integer> getSelectedIdsList() {
        return new ArrayList<>(selectedIds);
    }

    /**
     * Removes dead entities from the current selection.
     * Should be called each frame.
     */
    public void cleanSelection() {
        if (entityManager == null) {
            return;
        }
        selectedIds.removeIf(id -> {
            Unit unit = entityManager.getUnit(id);
            if (unit != null) {
                return !unit.isAlive();
            }
            Building building = entityManager.getBuilding(id);
            if (building != null) {
                return !building.isAlive();
            }
            return true; // entity no longer exists
        });
    }

    /**
     * Cycles through unit types in the current selection.
     * <p>
     * Groups selected units by UnitType, then selects the next group in rotation.
     * Pressing Tab repeatedly cycles through all distinct unit types in the
     * original selection. When the cycle wraps around, the full selection is restored.
     * <p>
     * Standard RTS feature: in a mixed group (e.g. infantry + tanks), Tab lets
     * you quickly select just the infantry, then just the tanks, then back to all.
     */
    public void cycleUnitTypeInSelection() {
        if (entityManager == null || selectedIds.isEmpty()) {
            return;
        }

        // Group selected units by type
        Map<com.aow2.common.model.UnitType, List<Integer>> byType = new LinkedHashMap<>();
        List<Integer> buildingIds = new ArrayList<>();

        for (int id : selectedIds) {
            Unit unit = entityManager.getUnit(id);
            if (unit != null && unit.isAlive()) {
                byType.computeIfAbsent(unit.getUnitType(), k -> new ArrayList<>()).add(id);
            } else {
                Building building = entityManager.getBuilding(id);
                if (building != null && building.isAlive()) {
                    buildingIds.add(id);
                }
            }
        }

        if (byType.isEmpty()) {
            return;  // No units to cycle (only buildings selected)
        }

        // Determine current cycle index
        if (currentCycleIndex < 0 || currentCycleIndex >= byType.size()) {
            currentCycleIndex = 0;
        } else {
            currentCycleIndex = (currentCycleIndex + 1) % (byType.size() + 1);
        }

        // Select the appropriate group
        selectedIds.clear();
        if (currentCycleIndex < byType.size()) {
            // Select only the units of the current type
            List<List<Integer>> groups = new ArrayList<>(byType.values());
            for (int id : groups.get(currentCycleIndex)) {
                selectedIds.add(id);
            }
            LOG.debug("Tab: selected type group {} of {} ({} units)",
                currentCycleIndex + 1, byType.size(), selectedIds.size());
        } else {
            // Wrap around: restore full selection (all types + buildings)
            for (List<Integer> group : byType.values()) {
                for (int id : group) {
                    if (selectedIds.size() >= MAX_SELECTION) break;
                    selectedIds.add(id);
                }
            }
            for (int id : buildingIds) {
                if (selectedIds.size() >= MAX_SELECTION) break;
                selectedIds.add(id);
            }
            LOG.debug("Tab: restored full selection ({} entities)", selectedIds.size());
        }
    }

    /** Current index in the unit-type cycle (Tab key). -1 = not cycling. */
    private int currentCycleIndex = -1;
}

package com.aow2.client.render;

import com.aow2.common.model.BuildingType;
import com.aow2.common.model.Direction;
import com.aow2.common.model.Faction;
import com.aow2.common.model.UnitType;
import com.aow2.core.entity.Building;
import com.aow2.core.entity.Unit;
import com.aow2.core.world.EntityManager;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Set;

/**
 * Renders units and buildings on the isometric game scene.
 * Uses colored shapes as placeholders until sprite assets are available.
 * <p>
 * Rendering conventions:
 * - Units: colored circles (blue=CONFEDERATION, red=RESISTANCE)
 * - Buildings: colored rectangles with health bars
 * - Direction indicator: small arrow showing unit facing
 * - Health bars: above entities
 * - Selection highlight: yellow circle/ring for selected units
 * <p>
 * REF: MASTER_DOCUMENTATION.md Section 5 - Unit Encyclopedia (unit visual types)
 * REF: MASTER_DOCUMENTATION.md Section 6 - Building Encyclopedia (building sizes)
 */
public class EntityRenderer {

    private static final Logger LOG = LoggerFactory.getLogger(EntityRenderer.class);

    /** Radius of unit circles in pixels. */
    private static final double UNIT_RADIUS = 6.0;

    /** Width of building rectangles in pixels. */
    private static final double BUILDING_WIDTH = 24.0;

    /** Height of building rectangles in pixels. */
    private static final double BUILDING_HEIGHT = 16.0;

    /** Health bar width. */
    private static final double HEALTH_BAR_WIDTH = 20.0;

    /** Health bar height. */
    private static final double HEALTH_BAR_HEIGHT = 3.0;

    /** Health bar offset above entity. */
    private static final double HEALTH_BAR_OFFSET = 12.0;

    /** Direction arrow length. */
    private static final double ARROW_LENGTH = 8.0;

    /** Selection ring radius offset. */
    private static final double SELECTION_RING_OFFSET = 3.0;

    /** The isometric renderer for coordinate conversion. */
    private final IsometricRenderer isoRenderer;

    /** Set of currently selected entity IDs. */
    private Set<Integer> selectedEntityIds;

    /**
     * Constructs a new EntityRenderer.
     *
     * @param isoRenderer the isometric renderer for grid-to-screen coordinate conversion
     */
    public EntityRenderer(IsometricRenderer isoRenderer) {
        this.isoRenderer = isoRenderer;
        this.selectedEntityIds = Set.of();
    }

    /**
     * Sets the set of selected entity IDs for highlighting.
     *
     * @param selectedEntityIds the set of selected entity IDs
     */
    public void setSelectedEntityIds(Set<Integer> selectedEntityIds) {
        this.selectedEntityIds = selectedEntityIds;
    }

    /**
     * Renders all entities (buildings then units, for correct z-ordering).
     *
     * @param gc           the graphics context
     * @param entityManager the entity manager containing all entities
     * @param cameraOffsetX camera horizontal offset
     * @param cameraOffsetY camera vertical offset
     * @param zoom         zoom scale factor
     */
    public void render(GraphicsContext gc, EntityManager entityManager,
                       double cameraOffsetX, double cameraOffsetY, double zoom) {
        gc.save();
        gc.translate(cameraOffsetX, cameraOffsetY);
        gc.scale(zoom, zoom);

        // Render buildings first (they are behind units in isometric view)
        for (Building building : entityManager.getAllBuildings()) {
            if (building.isAlive()) {
                renderBuilding(gc, building);
            }
        }

        // Render units on top
        for (Unit unit : entityManager.getAllUnits()) {
            if (unit.isAlive()) {
                renderUnit(gc, unit);
            }
        }

        gc.restore();
    }

    /**
     * Renders a single unit as a colored circle with direction indicator and health bar.
     *
     * @param gc   graphics context
     * @param unit the unit to render
     */
    private void renderUnit(GraphicsContext gc, Unit unit) {
        double sx = isoRenderer.gridToScreenX(unit.getPosition().x(), unit.getPosition().y());
        double sy = isoRenderer.gridToScreenY(unit.getPosition().x(), unit.getPosition().y());

        Color factionColor = factionColor(unit.getFaction());
        boolean isSelected = selectedEntityIds.contains(unit.getId());

        // Selection highlight ring
        if (isSelected) {
            gc.setStroke(Color.YELLOW);
            gc.setLineWidth(2.0);
            gc.strokeOval(
                sx - UNIT_RADIUS - SELECTION_RING_OFFSET,
                sy - UNIT_RADIUS - SELECTION_RING_OFFSET,
                (UNIT_RADIUS + SELECTION_RING_OFFSET) * 2,
                (UNIT_RADIUS + SELECTION_RING_OFFSET) * 2
            );
        }

        // Unit body circle
        gc.setFill(factionColor);
        gc.fillOval(sx - UNIT_RADIUS, sy - UNIT_RADIUS, UNIT_RADIUS * 2, UNIT_RADIUS * 2);

        // Vehicle units get a slightly different shape indicator
        if (unit.isVehicle()) {
            gc.setStroke(factionColor.brighter());
            gc.setLineWidth(1.5);
            gc.strokeOval(sx - UNIT_RADIUS, sy - UNIT_RADIUS, UNIT_RADIUS * 2, UNIT_RADIUS * 2);
        }

        // Direction indicator arrow
        renderDirectionArrow(gc, sx, sy, unit.getStats().attackRange() > 1
            ? Direction.SOUTH : Direction.SOUTH);

        // Health bar (only show if damaged)
        if (unit.getHp() < unit.getMaxHp()) {
            renderHealthBar(gc, sx, sy - HEALTH_BAR_OFFSET, unit.getHp(), unit.getMaxHp(), factionColor);
        }

        // Rank indicator for ranked units
        if (unit.getRank() > 0) {
            gc.setFill(Color.GOLD);
            String rankStr = "*".repeat(unit.getRank());
            gc.fillText(rankStr, sx - 3, sy - UNIT_RADIUS - HEALTH_BAR_OFFSET - 2);
        }
    }

    /**
     * Renders a direction arrow from the unit center.
     *
     * @param gc       graphics context
     * @param cx       center x
     * @param cy       center y
     * @param facing   the direction the unit faces
     */
    private void renderDirectionArrow(GraphicsContext gc, double cx, double cy, Direction facing) {
        double angle = directionToAngle(facing);
        double endX = cx + Math.cos(angle) * (UNIT_RADIUS + ARROW_LENGTH);
        double endY = cy + Math.sin(angle) * (UNIT_RADIUS + ARROW_LENGTH);

        gc.setStroke(Color.WHITE);
        gc.setLineWidth(1.5);
        gc.strokeLine(cx + Math.cos(angle) * UNIT_RADIUS, cy + Math.sin(angle) * UNIT_RADIUS, endX, endY);

        // Arrowhead
        double headAngle1 = angle + Math.PI * 0.8;
        double headAngle2 = angle - Math.PI * 0.8;
        double headLen = 4.0;
        gc.strokeLine(endX, endY, endX + Math.cos(headAngle1) * headLen, endY + Math.sin(headAngle1) * headLen);
        gc.strokeLine(endX, endY, endX + Math.cos(headAngle2) * headLen, endY + Math.sin(headAngle2) * headLen);
    }

    /**
     * Converts a Direction enum to a screen angle in radians.
     * Isometric view: NORTH is up, EAST is right, etc.
     *
     * @param direction the facing direction
     * @return angle in radians
     */
    private double directionToAngle(Direction direction) {
        return switch (direction) {
            case NORTH     -> -Math.PI / 2;
            case NORTH_EAST -> -Math.PI / 4;
            case EAST      -> 0;
            case SOUTH_EAST -> Math.PI / 4;
            case SOUTH     -> Math.PI / 2;
            case SOUTH_WEST -> 3 * Math.PI / 4;
            case WEST      -> Math.PI;
            case NORTH_WEST -> -3 * Math.PI / 4;
        };
    }

    /**
     * Renders a single building as a colored rectangle with health bar.
     *
     * @param gc       graphics context
     * @param building the building to render
     */
    private void renderBuilding(GraphicsContext gc, Building building) {
        double sx = isoRenderer.gridToScreenX(building.getPosition().x(), building.getPosition().y());
        double sy = isoRenderer.gridToScreenY(building.getPosition().x(), building.getPosition().y());

        Color factionColor = factionColor(building.getFaction());
        boolean isSelected = selectedEntityIds.contains(building.getId());

        // Building size varies by type
        double w = BUILDING_WIDTH;
        double h = BUILDING_HEIGHT;
        if (building.getBuildingType().isHQ()) {
            w = BUILDING_WIDTH * 1.5;
            h = BUILDING_HEIGHT * 1.5;
        }

        double drawX = sx - w / 2;
        double drawY = sy - h / 2;

        // Selection highlight
        if (isSelected) {
            gc.setStroke(Color.YELLOW);
            gc.setLineWidth(2.5);
            gc.strokeRect(drawX - 3, drawY - 3, w + 6, h + 6);
        }

        // Building body
        gc.setFill(factionColor);
        gc.fillRect(drawX, drawY, w, h);
        gc.setStroke(factionColor.darker());
        gc.setLineWidth(1.0);
        gc.strokeRect(drawX, drawY, w, h);

        // Construction overlay (semi-transparent if under construction)
        if (building.isUnderConstruction()) {
            gc.setFill(Color.rgb(255, 255, 255, 0.4));
            gc.fillRect(drawX, drawY, w, h);

            // Construction progress text
            double progress = (double) building.getConstructionProgress() / building.getStats().buildTime();
            gc.setFill(Color.WHITE);
            gc.fillText(String.format("%.0f%%", progress * 100), drawX + 2, drawY + h / 2 + 4);
        }

        // Powered indicator for buildings that consume/produce power
        if (building.getBuildingType().producesPower() && building.isPowered()) {
            gc.setFill(Color.YELLOW);
            gc.fillOval(drawX + w - 5, drawY + 1, 4, 4);
        }

        // Health bar
        if (building.getHp() < building.getMaxHp()) {
            renderHealthBar(gc, sx, drawY - 4, building.getHp(), building.getMaxHp(), factionColor);
        }

        // Building label
        gc.setFill(Color.WHITE);
        gc.setFont(javafx.scene.text.Font.font(8));
        String label = buildingLabel(building.getBuildingType());
        gc.fillText(label, drawX, drawY + h + 10);
    }

    /**
     * Returns a short label string for a building type.
     *
     * @param type the building type
     * @return short label
     */
    private String buildingLabel(BuildingType type) {
        return switch (type) {
            case CONFED_COMMAND_CENTRE -> "CC";
            case CONFED_GENERATOR -> "Gen";
            case CONFED_INFANTRY_CENTRE -> "IC";
            case CONFED_MACHINE_FACTORY -> "MF";
            case CONFED_TECH_CENTRE -> "TC";
            case CONFED_BUNKER -> "Bnk";
            case CONFED_LOCATOR -> "Loc";
            case CONFED_ROCKET_LAUNCHER -> "RL";
            case REBEL_HEADQUARTERS -> "HQ";
            case REBEL_POWERPLANT -> "PP";
            case REBEL_BARRACKS -> "Bar";
            case REBEL_FACTORY -> "Fac";
            case REBEL_LABORATORY -> "Lab";
            case REBEL_BUNKER -> "Bnk";
            case REBEL_TOWER -> "Twr";
            case REBEL_WALL -> "Wall";
        };
    }

    /**
     * Renders a health bar above an entity.
     *
     * @param gc      graphics context
     * @param cx      center x of the entity
     * @param topY    y position for the top of the health bar
     * @param hp      current hit points
     * @param maxHp   maximum hit points
     * @param factionColor the faction color for the bar
     */
    private void renderHealthBar(GraphicsContext gc, double cx, double topY,
                                 int hp, int maxHp, Color factionColor) {
        double barX = cx - HEALTH_BAR_WIDTH / 2;
        double ratio = (double) hp / maxHp;

        // Background
        gc.setFill(Color.rgb(0, 0, 0, 0.6));
        gc.fillRect(barX, topY, HEALTH_BAR_WIDTH, HEALTH_BAR_HEIGHT);

        // Fill color based on health ratio
        Color barColor;
        if (ratio > 0.6) {
            barColor = Color.GREEN;
        } else if (ratio > 0.3) {
            barColor = Color.YELLOW;
        } else {
            barColor = Color.RED;
        }

        gc.setFill(barColor);
        gc.fillRect(barX, topY, HEALTH_BAR_WIDTH * ratio, HEALTH_BAR_HEIGHT);

        // Border
        gc.setStroke(Color.rgb(0, 0, 0, 0.8));
        gc.setLineWidth(0.5);
        gc.strokeRect(barX, topY, HEALTH_BAR_WIDTH, HEALTH_BAR_HEIGHT);
    }

    /**
     * Returns the JavaFX color for a faction.
     *
     * @param faction the faction
     * @return the associated color
     */
    public static Color factionColor(Faction faction) {
        return switch (faction) {
            case CONFEDERATION -> Color.rgb(50, 100, 220);   // blue
            case RESISTANCE    -> Color.rgb(200, 50, 50);    // red
            case NEUTRAL       -> Color.rgb(150, 150, 150);  // gray
        };
    }
}

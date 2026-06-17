package com.aow2.client.render;

import com.aow2.common.model.BuildingType;
import com.aow2.common.model.Direction;
import com.aow2.common.model.Faction;
import com.aow2.common.model.TerrainType;
import com.aow2.common.model.UnitCategory;
import com.aow2.common.model.UnitType;

import javafx.scene.SnapshotParameters;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import javafx.scene.transform.Transform;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Generates attractive procedural sprites using JavaFX Canvas API.
 * Creates detailed isometric-style sprites for units, buildings, and terrain
 * that look much better than the simple colored circle/rectangle placeholders.
 * <p>
 * Unit sprites: Draw actual unit shapes (infantry as soldier silhouettes,
 * tanks as vehicle shapes, mines as explosive devices) with faction colors
 * and 8-directional variants.
 * <p>
 * Building sprites: Draw isometric building shapes with appropriate details
 * like windows, doors, antennae, and faction-colored accents.
 * <p>
 * Terrain sprites: Draw textured diamond tiles with isometric perspective
 * and subtle shading for depth.
 * <p>
 * REF: MASTER_DOCUMENTATION.md Section 5 - Unit Encyclopedia (unit visual types)
 * REF: MASTER_DOCUMENTATION.md Section 6 - Building Encyclopedia (building sizes)
 * REF: MASTER_DOCUMENTATION.md Section 3.2 - Map System (isometric tile rendering)
 */
public class ProceduralSpriteGenerator {

    private static final Logger LOG = LoggerFactory.getLogger(ProceduralSpriteGenerator.class);

    /** Sprite size for unit sprites. */
    public static final int UNIT_SPRITE_SIZE = 32;

    /** Sprite size for building sprites. */
    public static final int BUILDING_SPRITE_SIZE = 48;

    /** Sprite size for terrain tiles (full diamond). */
    public static final int TERRAIN_SPRITE_WIDTH = 32;
    public static final int TERRAIN_SPRITE_HEIGHT = 20;

    /** Half-width of the isometric diamond for terrain sprites. */
    private static final int HALF_W = 15;

    /** Half-height of the isometric diamond for terrain sprites. */
    private static final int HALF_H = 10;

    /**
     * Generates a procedural unit sprite for the given unit type and direction.
     * The sprite includes a shadow, the unit shape, faction color, and
     * directional indicator appropriate for the unit category.
     *
     * @param unitType the unit type
     * @param direction the facing direction (8 directions)
     * @return the generated sprite image
     */
    public Image generateUnitSprite(UnitType unitType, Direction direction) {
        WritableImage image = new WritableImage(UNIT_SPRITE_SIZE, UNIT_SPRITE_SIZE);
        Canvas canvas = new Canvas(UNIT_SPRITE_SIZE, UNIT_SPRITE_SIZE);
        GraphicsContext gc = canvas.getGraphicsContext2D();

        double cx = UNIT_SPRITE_SIZE / 2.0;
        double cy = UNIT_SPRITE_SIZE / 2.0;

        // Faction colors
        Color primary = factionPrimaryColor(unitType.faction());
        Color secondary = factionSecondaryColor(unitType.faction());
        Color dark = primary.darker().darker();
        Color light = primary.brighter();

        // Draw ground shadow (ellipse at base)
        gc.setFill(Color.rgb(0, 0, 0, 0.25));
        gc.fillOval(cx - 8, cy + 6, 16, 6);

        if (unitType.category() == UnitCategory.INFANTRY) {
            drawInfantrySprite(gc, cx, cy, primary, secondary, dark, light, direction);
        } else if (unitType.category() == UnitCategory.VEHICLE || unitType.category() == UnitCategory.SPECIAL_MACHINERY) {
            drawVehicleSprite(gc, cx, cy, primary, secondary, dark, light, direction, unitType);
        } else if (unitType.category() == UnitCategory.MINE) {
            drawMineSprite(gc, cx, cy, primary, secondary, dark, light);
        }

        // Draw direction indicator (subtle arrow at top)
        drawDirectionIndicator(gc, cx, cy - 10, direction, Color.WHITE);

        snapshotCanvas(canvas, image);
        return image;
    }

    /**
     * Draws an infantry soldier sprite with a body, head, and weapon.
     * The figure faces the given direction with appropriate silhouette.
     */
    private void drawInfantrySprite(GraphicsContext gc, double cx, double cy,
                                     Color primary, Color secondary, Color dark,
                                     Color light, Direction direction) {
        // Body (torso)
        gc.setFill(primary);
        gc.fillRoundRect(cx - 4, cy - 4, 8, 10, 2, 2);

        // Head (circle)
        gc.setFill(secondary);
        gc.fillOval(cx - 3, cy - 9, 6, 6);

        // Helmet accent
        gc.setFill(dark);
        gc.fillArc(cx - 3, cy - 10, 6, 5, 0, 180);

        // Arms holding weapon
        double angle = directionToAngle(direction);
        double weaponDx = Math.cos(angle) * 6;
        double weaponDy = Math.sin(angle) * 4;

        // Left arm
        gc.setStroke(primary);
        gc.setLineWidth(2);
        gc.strokeLine(cx - 3, cy - 2, cx - 3 + weaponDx * 0.4, cy - 2 + weaponDy * 0.4);

        // Right arm
        gc.strokeLine(cx + 3, cy - 2, cx + 3 + weaponDx * 0.4, cy - 2 + weaponDy * 0.4);

        // Weapon line (rifle/grenade launcher)
        gc.setStroke(Color.rgb(80, 80, 80));
        gc.setLineWidth(1.5);
        gc.strokeLine(cx, cy - 3, cx + weaponDx, cy - 3 + weaponDy);

        // Legs
        gc.setStroke(dark);
        gc.setLineWidth(2);
        gc.strokeLine(cx - 2, cy + 5, cx - 3, cy + 9);
        gc.strokeLine(cx + 2, cy + 5, cx + 3, cy + 9);

        // Boots
        gc.setFill(Color.rgb(60, 50, 30));
        gc.fillRect(cx - 4, cy + 8, 3, 2);
        gc.fillRect(cx + 1, cy + 8, 3, 2);

        // Faction color shoulder pads
        gc.setFill(light);
        gc.fillRect(cx - 5, cy - 4, 2, 3);
        gc.fillRect(cx + 3, cy - 4, 2, 3);
    }

    /**
     * Draws a vehicle sprite (tank, artillery, etc.) with tracks, turret, and barrel.
     * The turret faces the given direction.
     */
    private void drawVehicleSprite(GraphicsContext gc, double cx, double cy,
                                    Color primary, Color secondary, Color dark,
                                    Color light, Direction direction, UnitType unitType) {
        // Tracks (two horizontal bars)
        gc.setFill(Color.rgb(50, 50, 50));
        gc.fillRoundRect(cx - 10, cy + 1, 20, 4, 2, 2);
        gc.fillRoundRect(cx - 10, cy + 5, 20, 4, 2, 2);

        // Track treads
        gc.setFill(Color.rgb(70, 70, 70));
        for (int i = 0; i < 5; i++) {
            gc.fillRect(cx - 9 + i * 4, cy + 1, 2, 2);
            gc.fillRect(cx - 9 + i * 4, cy + 6, 2, 2);
        }

        // Hull (main body)
        gc.setFill(primary);
        gc.fillRoundRect(cx - 8, cy - 4, 16, 7, 3, 2);

        // Hull highlight
        gc.setFill(light);
        gc.fillRoundRect(cx - 6, cy - 3, 12, 2, 1, 1);

        // Turret base
        gc.setFill(secondary);
        gc.fillOval(cx - 5, cy - 8, 10, 8);

        // Turret top highlight
        gc.setFill(light);
        gc.fillOval(cx - 3, cy - 7, 6, 3);

        // Barrel (points in facing direction)
        double angle = directionToAngle(direction);
        double barrelLen = unitType.isLargeUnit() ? 12 : 8;
        double barrelEndX = cx + Math.cos(angle) * barrelLen;
        double barrelEndY = cy - 5 + Math.sin(angle) * (barrelLen * 0.6);

        gc.setStroke(Color.rgb(60, 60, 60));
        gc.setLineWidth(2.5);
        gc.strokeLine(cx, cy - 5, barrelEndX, barrelEndY);

        // Barrel muzzle
        gc.setFill(Color.rgb(40, 40, 40));
        gc.fillOval(barrelEndX - 2, barrelEndY - 2, 4, 4);

        // Faction emblem on hull
        gc.setFill(Color.YELLOW);
        gc.fillOval(cx - 1.5, cy - 1.5, 3, 3);

        // Siege mode indicator for siege-capable units
        if (unitType.isSiegeCapable()) {
            gc.setFill(Color.rgb(255, 200, 0, 0.5));
            gc.fillOval(cx - 3, cy + 9, 6, 3);
        }
    }

    /**
     * Draws a mine sprite as a small explosive device with blinking indicator.
     */
    private void drawMineSprite(GraphicsContext gc, double cx, double cy,
                                 Color primary, Color secondary, Color dark, Color light) {
        // Mine body (dome shape)
        gc.setFill(primary);
        gc.fillOval(cx - 5, cy - 3, 10, 8);

        // Dome top
        gc.setFill(light);
        gc.fillOval(cx - 4, cy - 4, 8, 5);

        // Pressure plate (flat top)
        gc.setFill(dark);
        gc.fillRect(cx - 3, cy - 5, 6, 2);

        // Blinking indicator
        gc.setFill(Color.RED);
        gc.fillOval(cx - 1, cy - 7, 2, 2);

        // Spikes/antennae
        gc.setStroke(Color.rgb(100, 100, 100));
        gc.setLineWidth(1);
        gc.strokeLine(cx - 5, cy, cx - 7, cy - 3);
        gc.strokeLine(cx + 5, cy, cx + 7, cy - 3);
        gc.strokeLine(cx, cy + 4, cx, cy + 7);

        // Base ring
        gc.setStroke(dark);
        gc.setLineWidth(1.5);
        gc.strokeOval(cx - 6, cy + 2, 12, 4);
    }

    /**
     * Generates a procedural building sprite for the given building type and faction.
     * Buildings are rendered in isometric perspective with faction-colored accents.
     *
     * @param buildingType the building type
     * @param faction the owning faction
     * @return the generated sprite image
     */
    public Image generateBuildingSprite(BuildingType buildingType, Faction faction) {
        int size = buildingType.isHQ() ? BUILDING_SPRITE_SIZE + 16 : BUILDING_SPRITE_SIZE;
        WritableImage image = new WritableImage(size, size);
        Canvas canvas = new Canvas(size, size);
        GraphicsContext gc = canvas.getGraphicsContext2D();

        double cx = size / 2.0;
        double cy = size / 2.0;

        Color primary = factionPrimaryColor(faction);
        Color secondary = factionSecondaryColor(faction);
        Color dark = primary.darker().darker();
        Color light = primary.brighter();

        // Ground shadow
        gc.setFill(Color.rgb(0, 0, 0, 0.2));
        gc.fillOval(cx - 14, cy + 10, 28, 10);

        if (buildingType.isHQ()) {
            drawHQSprite(gc, cx, cy, primary, secondary, dark, light, faction);
        } else if (buildingType.producesPower()) {
            drawPowerBuildingSprite(gc, cx, cy, primary, secondary, dark, light);
        } else if (buildingType.producesUnits()) {
            drawProductionBuildingSprite(gc, cx, cy, primary, secondary, dark, light, buildingType);
        } else if (buildingType.researches()) {
            drawResearchBuildingSprite(gc, cx, cy, primary, secondary, dark, light);
        } else if (buildingType.isDefensive()) {
            drawDefensiveBuildingSprite(gc, cx, cy, primary, secondary, dark, light, buildingType);
        } else {
            drawGenericBuildingSprite(gc, cx, cy, primary, secondary, dark, light);
        }

        snapshotCanvas(canvas, image);
        return image;
    }

    /**
     * Draws the HQ/Command Centre sprite - a large building with dome and flag.
     */
    private void drawHQSprite(GraphicsContext gc, double cx, double cy,
                               Color primary, Color secondary, Color dark,
                               Color light, Faction faction) {
        // Main building body (isometric cube)
        drawIsometricBox(gc, cx, cy + 4, 18, 14, 12, primary, dark);

        // Dome on top
        gc.setFill(secondary);
        gc.fillOval(cx - 8, cy - 10, 16, 12);

        // Dome highlight
        gc.setFill(light);
        gc.fillOval(cx - 5, cy - 9, 6, 4);

        // Flag pole
        gc.setStroke(Color.rgb(120, 120, 120));
        gc.setLineWidth(1.5);
        gc.strokeLine(cx + 6, cy - 16, cx + 6, cy - 24);

        // Flag
        gc.setFill(primary.brighter());
        gc.fillPolygon(
            new double[]{cx + 6, cx + 14, cx + 6},
            new double[]{cy - 24, cy - 22, cy - 20},
            3
        );

        // Windows
        gc.setFill(Color.rgb(200, 220, 255));
        gc.fillRect(cx - 4, cy - 2, 3, 3);
        gc.fillRect(cx + 2, cy - 2, 3, 3);

        // Door
        gc.setFill(dark);
        gc.fillRect(cx - 2, cy + 2, 4, 5);

        // Power indicator
        gc.setFill(Color.YELLOW);
        gc.fillOval(cx - 1, cy - 6, 2, 2);
    }

    /**
     * Draws a power-generating building (Generator/Powerplant) with smokestacks.
     */
    private void drawPowerBuildingSprite(GraphicsContext gc, double cx, double cy,
                                          Color primary, Color secondary, Color dark, Color light) {
        // Building body
        drawIsometricBox(gc, cx, cy + 2, 14, 10, 10, primary, dark);

        // Smokestack
        gc.setFill(Color.rgb(100, 100, 100));
        gc.fillRect(cx + 4, cy - 12, 4, 14);

        // Stack top
        gc.setFill(Color.rgb(80, 80, 80));
        gc.fillRect(cx + 3, cy - 14, 6, 3);

        // Smoke puffs
        gc.setFill(Color.rgb(200, 200, 200, 0.4));
        gc.fillOval(cx + 3, cy - 18, 5, 4);
        gc.fillOval(cx + 5, cy - 22, 4, 3);

        // Lightning bolt symbol (power)
        gc.setFill(Color.YELLOW);
        double[] boltX = {cx - 3, cx + 1, cx - 1, cx + 3};
        double[] boltY = {cy - 5, cy - 1, cy - 1, cy + 3};
        gc.fillPolygon(boltX, boltY, 4);

        // Side highlight
        gc.setFill(light);
        gc.fillRect(cx - 6, cy - 4, 2, 6);
    }

    /**
     * Draws a unit production building (Barracks/Factory) with garage door.
     */
    private void drawProductionBuildingSprite(GraphicsContext gc, double cx, double cy,
                                               Color primary, Color secondary, Color dark,
                                               Color light, BuildingType type) {
        // Building body
        drawIsometricBox(gc, cx, cy + 2, 16, 10, 10, primary, dark);

        // Garage/production door
        gc.setFill(Color.rgb(60, 50, 40));
        gc.fillRect(cx - 5, cy - 2, 10, 7);

        // Door segments
        gc.setStroke(Color.rgb(80, 70, 50));
        gc.setLineWidth(0.5);
        gc.strokeLine(cx - 5, cy, cx + 5, cy);
        gc.strokeLine(cx - 5, cy + 2, cx + 5, cy + 2);
        gc.strokeLine(cx - 5, cy + 4, cx + 5, cy + 4);

        // If vehicle factory, add crane/hook
        if (type == BuildingType.CONFED_MACHINE_FACTORY || type == BuildingType.REBEL_FACTORY) {
            gc.setStroke(Color.rgb(120, 120, 120));
            gc.setLineWidth(1.5);
            gc.strokeLine(cx - 8, cy - 10, cx - 8, cy - 2);
            gc.strokeLine(cx - 8, cy - 10, cx + 4, cy - 10);
            gc.strokeLine(cx + 4, cy - 10, cx + 4, cy - 7);
        } else {
            // Barracks: add window
            gc.setFill(Color.rgb(200, 220, 255));
            gc.fillRect(cx - 3, cy - 5, 3, 3);
        }

        // Roof accent
        gc.setFill(secondary);
        gc.fillRect(cx - 8, cy - 8, 16, 2);

        // Faction emblem
        gc.setFill(light);
        gc.fillOval(cx - 1.5, cy - 7, 3, 3);
    }

    /**
     * Draws a research building (Tech Centre/Laboratory) with satellite dish.
     */
    private void drawResearchBuildingSprite(GraphicsContext gc, double cx, double cy,
                                             Color primary, Color secondary, Color dark, Color light) {
        // Building body
        drawIsometricBox(gc, cx, cy + 2, 14, 10, 10, primary, dark);

        // Satellite dish
        gc.setStroke(Color.rgb(150, 150, 150));
        gc.setLineWidth(1.5);
        gc.strokeLine(cx + 4, cy - 8, cx + 4, cy - 14);

        // Dish
        gc.setFill(Color.rgb(180, 200, 220));
        gc.fillArc(cx, cy - 18, 8, 8, -30, 240);

        // Building highlight
        gc.setFill(light);
        gc.fillRect(cx - 6, cy - 4, 2, 6);

        // Screen/display
        gc.setFill(Color.rgb(0, 180, 255));
        gc.fillRect(cx - 4, cy - 5, 5, 4);
        gc.setFill(Color.rgb(0, 220, 255));
        gc.fillRect(cx - 3, cy - 4, 3, 2);

        // Window
        gc.setFill(Color.rgb(200, 220, 255));
        gc.fillRect(cx + 2, cy - 4, 3, 3);
    }

    /**
     * Draws a defensive building (Bunker/Tower/Wall/Rocket Launcher) with battlements.
     */
    private void drawDefensiveBuildingSprite(GraphicsContext gc, double cx, double cy,
                                              Color primary, Color secondary, Color dark,
                                              Color light, BuildingType type) {
        if (type == BuildingType.REBEL_WALL) {
            drawWallSprite(gc, cx, cy, primary, dark);
            return;
        }

        // Building body
        drawIsometricBox(gc, cx, cy + 2, 12, 10, 10, primary, dark);

        // Battlements (crenellations)
        gc.setFill(secondary);
        for (int i = 0; i < 4; i++) {
            gc.fillRect(cx - 6 + i * 4, cy - 10, 2, 3);
        }

        // Firing slit
        gc.setFill(Color.rgb(30, 30, 30));
        gc.fillRect(cx - 3, cy - 4, 6, 2);

        // Muzzle flash hint (for rocket launcher / tower)
        if (type == BuildingType.CONFED_ROCKET_LAUNCHER || type == BuildingType.REBEL_TOWER) {
            gc.setFill(Color.rgb(100, 100, 100));
            gc.fillRect(cx - 1, cy - 7, 2, 4);
            gc.setFill(Color.rgb(255, 160, 0, 0.6));
            gc.fillOval(cx - 2, cy - 8, 4, 3);
        }

        // Highlight
        gc.setFill(light);
        gc.fillRect(cx - 5, cy - 2, 2, 5);
    }

    /**
     * Draws a wall sprite - a low, long barrier.
     */
    private void drawWallSprite(GraphicsContext gc, double cx, double cy, Color primary, Color dark) {
        // Wall body
        gc.setFill(primary);
        gc.fillRoundRect(cx - 8, cy - 4, 16, 10, 1, 1);

        // Top edge
        gc.setFill(primary.brighter());
        gc.fillRect(cx - 8, cy - 4, 16, 2);

        // Shadow side
        gc.setFill(dark);
        gc.fillRect(cx - 8, cy + 3, 16, 3);

        // Brick lines
        gc.setStroke(dark);
        gc.setLineWidth(0.5);
        gc.strokeLine(cx - 8, cy - 1, cx + 8, cy - 1);
        gc.strokeLine(cx - 8, cy + 1, cx + 8, cy + 1);
        gc.strokeLine(cx, cy - 4, cx, cy - 1);
        gc.strokeLine(cx - 4, cy - 1, cx - 4, cy + 1);
        gc.strokeLine(cx + 4, cy - 1, cx + 4, cy + 1);
    }

    /**
     * Draws a generic building sprite for building types without specific rendering.
     */
    private void drawGenericBuildingSprite(GraphicsContext gc, double cx, double cy,
                                            Color primary, Color secondary, Color dark, Color light) {
        drawIsometricBox(gc, cx, cy + 2, 14, 10, 10, primary, dark);

        // Window
        gc.setFill(Color.rgb(200, 220, 255));
        gc.fillRect(cx - 3, cy - 4, 3, 3);

        // Door
        gc.setFill(dark);
        gc.fillRect(cx - 1, cy - 1, 4, 5);

        // Roof accent
        gc.setFill(secondary);
        gc.fillRect(cx - 7, cy - 8, 14, 2);

        // Highlight
        gc.setFill(light);
        gc.fillRect(cx - 6, cy - 4, 2, 5);
    }

    /**
     * Draws an isometric 3D box shape.
     *
     * @param gc the graphics context
     * @param cx center x
     * @param cy center y (base)
     * @param halfW half-width of the box
     * @param halfD half-depth of the box
     * @param height height of the box
     * @param faceColor color for the front face
     * @param sideColor color for the side faces
     */
    private void drawIsometricBox(GraphicsContext gc, double cx, double cy,
                                   double halfW, double halfD, double height,
                                   Color faceColor, Color sideColor) {
        // Top face
        double[] topX = {cx, cx + halfW, cx, cx - halfW};
        double[] topY = {cy - height, cy - height + halfD * 0.5, cy - height + halfD, cy - height + halfD * 0.5};
        gc.setFill(faceColor.brighter());
        gc.fillPolygon(topX, topY, 4);
        gc.setStroke(faceColor.darker());
        gc.setLineWidth(0.5);
        gc.strokePolygon(topX, topY, 4);

        // Left face
        double[] leftX = {cx - halfW, cx, cx, cx - halfW};
        double[] leftY = {cy - height + halfD * 0.5, cy - height + halfD, cy + halfD, cy + halfD * 0.5};
        gc.setFill(sideColor);
        gc.fillPolygon(leftX, leftY, 4);
        gc.setStroke(sideColor.darker());
        gc.setLineWidth(0.5);
        gc.strokePolygon(leftX, leftY, 4);

        // Right face
        double[] rightX = {cx, cx + halfW, cx + halfW, cx};
        double[] rightY = {cy - height + halfD, cy - height + halfD * 0.5, cy + halfD * 0.5, cy + halfD};
        gc.setFill(faceColor);
        gc.fillPolygon(rightX, rightY, 4);
        gc.setStroke(faceColor.darker());
        gc.setLineWidth(0.5);
        gc.strokePolygon(rightX, rightY, 4);
    }

    /**
     * Generates a procedural terrain sprite for the given terrain type.
     * The sprite is a diamond shape matching the isometric tile dimensions
     * with textured patterns and shading.
     *
     * @param terrainType the terrain type
     * @return the generated sprite image
     */
    public Image generateTerrainSprite(TerrainType terrainType) {
        WritableImage image = new WritableImage(TERRAIN_SPRITE_WIDTH, TERRAIN_SPRITE_HEIGHT);
        Canvas canvas = new Canvas(TERRAIN_SPRITE_WIDTH, TERRAIN_SPRITE_HEIGHT);
        GraphicsContext gc = canvas.getGraphicsContext2D();

        double cx = HALF_W;
        double cy = HALF_H;

        Color baseColor = IsometricRenderer.terrainColor(terrainType);
        Color darkColor = baseColor.darker();
        Color lightColor = baseColor.brighter();

        // Diamond points
        double[] xPoints = {cx, cx + HALF_W, cx, cx - HALF_W};
        double[] yPoints = {cy - HALF_H, cy, cy + HALF_H, cy};

        // Base fill
        gc.setFill(baseColor);
        gc.fillPolygon(xPoints, yPoints, 4);

        // Terrain-specific textures
        drawTerrainTexture(gc, terrainType, cx, cy, baseColor, darkColor, lightColor);

        // Outline
        gc.setStroke(darkColor);
        gc.setLineWidth(0.5);
        gc.strokePolygon(xPoints, yPoints, 4);

        // Subtle top-left highlight
        gc.setStroke(Color.rgb(255, 255, 255, 0.15));
        gc.setLineWidth(1);
        gc.strokeLine(cx, cy - HALF_H, cx - HALF_W, cy);
        gc.strokeLine(cx - HALF_W, cy, cx, cy + HALF_H);

        snapshotCanvas(canvas, image);
        return image;
    }

    /**
     * Draws terrain-specific texture patterns within the diamond tile.
     */
    private void drawTerrainTexture(GraphicsContext gc, TerrainType terrain, double cx, double cy,
                                     Color base, Color dark, Color light) {
        switch (terrain) {
            case DEEP_WATER -> {
                // Wave lines
                gc.setStroke(Color.rgb(50, 80, 220, 0.5));
                gc.setLineWidth(0.8);
                for (int i = 0; i < 3; i++) {
                    double yOff = cy - 4 + i * 4;
                    gc.strokeLine(cx - 6 + i * 2, yOff, cx + 4 - i * 2, yOff);
                }
            }
            case SHALLOW_WATER -> {
                // Light wave lines
                gc.setStroke(Color.rgb(100, 170, 240, 0.4));
                gc.setLineWidth(0.6);
                gc.strokeLine(cx - 5, cy - 2, cx + 3, cy - 2);
                gc.strokeLine(cx - 3, cy + 2, cx + 5, cy + 2);
                // Foam dots
                gc.setFill(Color.rgb(255, 255, 255, 0.3));
                gc.fillOval(cx - 2, cy - 1, 2, 1);
            }
            case SAND -> {
                // Dot pattern for grainy texture
                gc.setFill(Color.rgb(220, 190, 130, 0.5));
                for (int i = 0; i < 5; i++) {
                    double px = cx - 6 + (i * 13 % 10);
                    double py = cy - 4 + (i * 7 % 8);
                    gc.fillOval(px, py, 1.5, 1.5);
                }
            }
            case GRASS -> {
                // Grass blades
                gc.setStroke(Color.rgb(50, 130, 0, 0.5));
                gc.setLineWidth(0.8);
                for (int i = 0; i < 4; i++) {
                    double bx = cx - 5 + i * 3;
                    double by = cy + 2 - (i % 2) * 3;
                    gc.strokeLine(bx, by, bx + 1, by - 3);
                }
            }
            case ROAD -> {
                // Center line
                gc.setStroke(Color.rgb(200, 200, 200, 0.5));
                gc.setLineWidth(1);
                gc.strokeLine(cx - HALF_W + 4, cy, cx + HALF_W - 4, cy);
                // Dashes
                gc.setStroke(Color.rgb(255, 255, 100, 0.3));
                gc.setLineWidth(0.5);
                gc.strokeLine(cx - 3, cy, cx, cy);
                gc.strokeLine(cx + 2, cy, cx + 5, cy);
            }
            case DIRT -> {
                // Pebbles
                gc.setFill(Color.rgb(140, 120, 80, 0.4));
                gc.fillOval(cx - 4, cy - 2, 2, 2);
                gc.fillOval(cx + 2, cy + 1, 2, 2);
                gc.fillOval(cx - 1, cy + 3, 1.5, 1.5);
            }
            case HILLS -> {
                // Contour lines
                gc.setStroke(Color.rgb(70, 110, 40, 0.4));
                gc.setLineWidth(0.6);
                gc.strokeOval(cx - 5, cy - 3, 6, 4);
                gc.strokeOval(cx - 3, cy - 1, 4, 3);
            }
            case FOREST -> {
                // Tree shapes
                gc.setFill(Color.rgb(20, 80, 20));
                gc.fillPolygon(new double[]{cx - 4, cx - 1, cx - 7}, new double[]{cy - 5, cy - 1, cy - 1}, 3);
                gc.fillPolygon(new double[]{cx + 2, cx + 5, cx + 8}, new double[]{cy - 3, cy - 6, cy - 1}, 3);
                gc.fillPolygon(new double[]{cx - 2, cx + 1, cx + 4}, new double[]{cy + 1, cy - 2, cy + 1}, 3);
                // Trunks
                gc.setFill(Color.rgb(100, 60, 20));
                gc.fillRect(cx - 2, cy - 1, 1, 2);
                gc.fillRect(cx + 4, cy - 1, 1, 2);
            }
            case BRIDGE -> {
                // Planks
                gc.setStroke(Color.rgb(160, 110, 60));
                gc.setLineWidth(0.8);
                for (int i = 0; i < 4; i++) {
                    double x1 = cx - HALF_W + 2 + i * 3;
                    gc.strokeLine(x1, cy - 4 + i * 2, x1 + 6, cy - 2 + i * 2);
                }
                // Rails
                gc.setStroke(Color.rgb(120, 80, 30));
                gc.setLineWidth(1);
                gc.strokeLine(cx - HALF_W + 3, cy - 5, cx + HALF_W - 3, cy - 3);
                gc.strokeLine(cx - HALF_W + 3, cy + 4, cx + HALF_W - 3, cy + 6);
            }
            case MOUNTAIN -> {
                // Mountain peak
                gc.setFill(Color.rgb(130, 100, 70, 0.5));
                gc.fillPolygon(new double[]{cx, cx - 7, cx + 7}, new double[]{cy - 7, cy + 2, cy + 2}, 3);
                // Snow cap
                gc.setFill(Color.rgb(240, 245, 255, 0.7));
                gc.fillPolygon(new double[]{cx, cx - 3, cx + 3}, new double[]{cy - 7, cy - 3, cy - 3}, 3);
            }
            case SWAMP -> {
                // Bubbles
                gc.setFill(Color.rgb(80, 130, 80, 0.4));
                gc.fillOval(cx - 4, cy - 2, 3, 2);
                gc.fillOval(cx + 2, cy + 1, 2, 1.5);
                // Dark patches
                gc.setFill(Color.rgb(30, 60, 30, 0.3));
                gc.fillOval(cx - 2, cy, 5, 3);
            }
            case SNOW -> {
                // Sparkle dots
                gc.setFill(Color.rgb(255, 255, 255, 0.6));
                gc.fillOval(cx - 5, cy - 3, 1.5, 1.5);
                gc.fillOval(cx + 3, cy - 1, 1, 1);
                gc.fillOval(cx - 1, cy + 2, 1.5, 1.5);
                gc.fillOval(cx + 5, cy + 3, 1, 1);
            }
            case ICE -> {
                // Crack lines
                gc.setStroke(Color.rgb(200, 240, 255, 0.5));
                gc.setLineWidth(0.5);
                gc.strokeLine(cx - 5, cy - 2, cx + 2, cy + 1);
                gc.strokeLine(cx + 2, cy + 1, cx + 6, cy - 1);
                gc.strokeLine(cx - 3, cy + 3, cx + 1, cy + 5);
            }
            case RUINS -> {
                // Broken walls
                gc.setFill(Color.rgb(60, 60, 60, 0.5));
                gc.fillRect(cx - 6, cy - 3, 3, 5);
                gc.fillRect(cx + 3, cy - 1, 2, 4);
                gc.fillRect(cx - 1, cy + 1, 4, 3);
                // Rubble dots
                gc.setFill(Color.rgb(80, 80, 80, 0.3));
                gc.fillOval(cx - 3, cy + 3, 2, 1.5);
            }
            case RESOURCE_DEPOSIT -> {
                // Crystal/gold shapes
                gc.setFill(Color.rgb(255, 220, 50, 0.6));
                gc.fillPolygon(new double[]{cx - 2, cx, cx + 2}, new double[]{cy - 4, cy - 7, cy - 4}, 3);
                gc.fillPolygon(new double[]{cx + 1, cx + 3, cx + 5}, new double[]{cy + 1, cy - 2, cy + 1}, 3);
                gc.fillOval(cx - 5, cy + 1, 3, 2);
                // Sparkle
                gc.setFill(Color.rgb(255, 255, 200, 0.8));
                gc.fillOval(cx - 1, cy - 5, 1, 1);
            }
        }
    }

    /**
     * Draws a small directional indicator arrow on the sprite.
     *
     * @param gc the graphics context
     * @param x center x for the indicator
     * @param y center y for the indicator
     * @param direction the facing direction
     * @param color the arrow color
     */
    private void drawDirectionIndicator(GraphicsContext gc, double x, double y,
                                         Direction direction, Color color) {
        double angle = directionToAngle(direction);
        double len = 4;
        double endX = x + Math.cos(angle) * len;
        double endY = y + Math.sin(angle) * len * 0.6;

        gc.setStroke(color);
        gc.setLineWidth(1.2);
        gc.strokeLine(x, y, endX, endY);

        // Arrowhead
        double headAngle1 = angle + Math.PI * 0.75;
        double headAngle2 = angle - Math.PI * 0.75;
        double headLen = 2.5;
        gc.strokeLine(endX, endY,
            endX + Math.cos(headAngle1) * headLen,
            endY + Math.sin(headAngle1) * headLen * 0.6);
        gc.strokeLine(endX, endY,
            endX + Math.cos(headAngle2) * headLen,
            endY + Math.sin(headAngle2) * headLen * 0.6);
    }

    /**
     * Converts a Direction enum to a screen angle in radians.
     * Isometric view: NORTH is up, EAST is right.
     *
     * @param direction the facing direction
     * @return angle in radians
     */
    private double directionToAngle(Direction direction) {
        return switch (direction) {
            case NORTH      -> -Math.PI / 2;
            case NORTH_EAST -> -Math.PI / 4;
            case EAST       -> 0;
            case SOUTH_EAST -> Math.PI / 4;
            case SOUTH      -> Math.PI / 2;
            case SOUTH_WEST -> 3 * Math.PI / 4;
            case WEST       -> Math.PI;
            case NORTH_WEST -> -3 * Math.PI / 4;
        };
    }

    /**
     * Returns the primary faction color for sprite rendering.
     *
     * @param faction the faction
     * @return primary color
     */
    private Color factionPrimaryColor(Faction faction) {
        return switch (faction) {
            case CONFEDERATION -> Color.rgb(50, 100, 220);
            case RESISTANCE    -> Color.rgb(200, 50, 50);
            case NEUTRAL       -> Color.rgb(150, 150, 150);
        };
    }

    /**
     * Returns the secondary (accent) faction color for sprite rendering.
     *
     * @param faction the faction
     * @return secondary color
     */
    private Color factionSecondaryColor(Faction faction) {
        return switch (faction) {
            case CONFEDERATION -> Color.rgb(80, 140, 240);
            case RESISTANCE    -> Color.rgb(230, 90, 70);
            case NEUTRAL       -> Color.rgb(180, 180, 180);
        };
    }

    /**
     * Takes a snapshot of the canvas content into the given WritableImage.
     *
     * @param canvas the source canvas
     * @param image the target image
     */
    private void snapshotCanvas(Canvas canvas, WritableImage image) {
        SnapshotParameters params = new SnapshotParameters();
        params.setTransform(Transform.scale(1, 1));
        canvas.snapshot(params, image);
    }
}

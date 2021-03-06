package com.zhsan.gamecomponents.maplayer;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.ui.WidgetGroup;
import com.zhsan.common.GlobalVariables;
import com.zhsan.common.Paths;
import com.zhsan.common.Point;
import com.zhsan.common.exception.FileReadException;
import com.zhsan.gamecomponents.common.GetKeyFocusWhenEntered;
import com.zhsan.gamecomponents.common.GetScrollFocusWhenEntered;
import com.zhsan.gamecomponents.common.textwidget.TextWidget;
import com.zhsan.gamecomponents.common.XmlHelper;
import com.zhsan.gamecomponents.contextmenu.ContextMenu;
import com.zhsan.gameobject.*;
import com.zhsan.screen.GameScreen;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by Peter on 19/3/2015.
 */
public class MainMapLayer extends WidgetGroup {

    public interface LocationSelectionListener {
        public void onLocationSelected(Point p);
    }

    private enum MoveStateX {
        IDLE, LEFT, RIGHT
    }
    private enum MoveStateY {
        IDLE, TOP, BOTTOM
    }
    private enum ZoomState {
        IDLE, IN, OUT
    }

    public static final String MAP_ROOT_PATH = Paths.RESOURCES + "Map" + File.separator;

    public static final String DATA_PATH = MAP_ROOT_PATH + "Data" + File.separator;

    private Map<String, Texture> mapTiles = new HashMap<>();

    private int mapZoomMin, mapZoomMax, mapScrollBoundary, mapMouseScrollFactor;
    private float mapScrollFactor;

    private GameScreen screen;
    private Vector2 mapCameraPosition;
    private MoveStateX moveStateX = MoveStateX.IDLE;
    private MoveStateY moveStateY = MoveStateY.IDLE;
    private ZoomState zoomState = ZoomState.IDLE;

    private TextWidget<Void> mapInfo;
    private int mapInfoMargin;
    private String mapInfoFormat;
    private Vector2 mousePosition = new Vector2();

    private Texture grid;

    private float captionSize;

    private List<MapLayer> mapLayers = new ArrayList<>();
    private TroopAnimationLayer troopAnimationLayer;
    private TileAnimationLayer tileAnimationLayer;
    private DamageLayer damageLayer;

    private LocationSelectionListener locationSelectionListener;
    private List<Point> locationSelectionCandidates;

    private void loadXml() {
        FileHandle f = Gdx.files.external(MAP_ROOT_PATH + "MapLayerData.xml");

        Document dom;
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        try {
            DocumentBuilder db = dbf.newDocumentBuilder();
            dom = db.parse(f.read());

            Node zoom = dom.getElementsByTagName("MapZoom").item(0);
            mapZoomMin = Integer.parseInt(XmlHelper.loadAttribute(zoom, "Min"));
            mapZoomMax = Integer.parseInt(XmlHelper.loadAttribute(zoom, "Max"));

            Node scroll = dom.getElementsByTagName("MapScroll").item(0);
            mapMouseScrollFactor = Integer.parseInt(XmlHelper.loadAttribute(scroll, "MouseFactor"));
            mapScrollBoundary = Integer.parseInt(XmlHelper.loadAttribute(scroll, "Boundary"));
            mapScrollFactor = Float.parseFloat(XmlHelper.loadAttribute(scroll, "Factor"));

            Node info = dom.getElementsByTagName("MapInfo").item(0);
            mapInfo = new TextWidget<>(TextWidget.Setting.fromXml(info));
            this.addActor(mapInfo);
            mapInfoMargin = Integer.parseInt(XmlHelper.loadAttribute(info, "BottomMargin"));
            mapInfoFormat = XmlHelper.loadAttribute(info, "TextFormat");

            captionSize = Float.parseFloat(XmlHelper.loadAttribute(dom.getElementsByTagName("Caption").item(0), "Size"));

        } catch (Exception e) {
            throw new FileReadException(MAP_ROOT_PATH + "MapLayerData.xml", e);
        }
    }

    public MainMapLayer(GameScreen screen) {
        this.screen = screen;

        // init myself
        loadXml();

        mapInfo.setX(0);
        mapInfo.setY(mapInfoMargin);
        mapInfo.setWidth(Gdx.graphics.getWidth());

        grid = new Texture(Gdx.files.external(DATA_PATH + "Grid.png"));

        GameMap map = screen.getScenario().getGameMap();
        Point mapCenter = screen.getScenario().getGameSurvey().getCameraPosition();
        this.mapCameraPosition = new Vector2(mapCenter.x * mapZoomMax, (map.getHeight() - 1 - mapCenter.y) * mapZoomMax);

        this.addListener(new InputEventListener());
        this.addListener(new GetScrollFocusWhenEntered(this));
        this.addListener(new GetKeyFocusWhenEntered(this));

        troopAnimationLayer = new TroopAnimationLayer();
        tileAnimationLayer = new TileAnimationLayer();
        damageLayer = new DamageLayer();
        mapLayers.add(new ArchitectureLayer(captionSize));
        mapLayers.add(new FacilityLayer());
        mapLayers.add(tileAnimationLayer);
        mapLayers.add(troopAnimationLayer);
        mapLayers.add(damageLayer);
        mapLayers.add(new HighlightLayer(screen.getScenario()));
    }

    public void resize(int width, int height) {
        mapInfo.setWidth(this.getWidth());
    }

    private Texture getMapTile(String mapName, String fileName) {
		// TODO async load tile images
        if (mapTiles.containsKey(fileName)) {
            return mapTiles.get(fileName);
        }
        Texture t = new Texture(Gdx.files.external(MAP_ROOT_PATH + mapName + File.separator + fileName + ".jpg"));
        mapTiles.put(fileName, t);
        return t;
    }

    private void updateSurveyCameraPosition() {
        screen.getScenario().getGameSurvey().setCameraPosition(
                new Point((int) (mapCameraPosition.x / mapZoomMax), (int) (screen.getScenario().getGameMap().getHeight() - mapCameraPosition.y / mapZoomMax))
        );
    }

    public void setMapCameraPosition(Point p) {
        this.mapCameraPosition = new Vector2(p.x * mapZoomMax, (screen.getScenario().getGameMap().getHeight() - 1 - p.y) * mapZoomMax);
        updateSurveyCameraPosition();
    }

    private void moveLeft() {
        mapCameraPosition.add(-mapScrollFactor * GlobalVariables.scrollSpeed /
                screen.getScenario().getGameMap().getZoom(), 0);
        updateSurveyCameraPosition();
    }

    private void moveRight() {
        mapCameraPosition.add(mapScrollFactor * GlobalVariables.scrollSpeed /
                screen.getScenario().getGameMap().getZoom(), 0);
        updateSurveyCameraPosition();
    }

    private void moveDown() {
        mapCameraPosition.add(0, -mapScrollFactor * GlobalVariables.scrollSpeed /
                screen.getScenario().getGameMap().getZoom());
        updateSurveyCameraPosition();
    }

    private void moveUp() {
        mapCameraPosition.add(0, mapScrollFactor * GlobalVariables.scrollSpeed /
                screen.getScenario().getGameMap().getZoom());
        updateSurveyCameraPosition();
    }

    private void adjustZoom(int amount) {
        if (screen.allowMapMove()) {
            int newZoom = screen.getScenario().getGameMap().getZoom();
            newZoom = MathUtils.clamp(newZoom + amount * mapMouseScrollFactor, mapZoomMin, mapZoomMax);
            screen.getScenario().getGameMap().setZoom(newZoom);
        }
    }

    public void startSelectingLocation(List<Point> candidates, LocationSelectionListener listener) {
        locationSelectionListener = listener;
        locationSelectionCandidates = candidates;
        mapLayers.forEach(l -> l.onStartSelectingLocation(candidates));
    }

    public void startSelectingLocation(Troop troop, LocationSelectionListener listener) {
        locationSelectionListener = listener;
        locationSelectionCandidates = null;
        mapLayers.forEach(l -> l.onStartSelectingLocation(troop));
    }

    public void addPendingTroopAnimation(TroopAnimationLayer.PendingTroopAnimation animation) {
        troopAnimationLayer.addPendingTroopAnimation(animation);
    }

    public void addTileAnimation(Point point, TroopAnimation animation) {
        tileAnimationLayer.showTileAnimation(point, animation);
    }

    public void showDamage(List<DamagePack> pack) {
        damageLayer.addDamagePack(pack);
    }

    public boolean isNoPendingTroopAnimations() {
        return troopAnimationLayer.isNoPendingTroopAnimations();
    }

    @Override
    public void act(float delta) {
        super.act(delta);

        GameMap map = screen.getScenario().getGameMap();

        if (moveStateX == MoveStateX.LEFT) {
            moveLeft();
        } else if (moveStateX == MoveStateX.RIGHT) {
            moveRight();
        }

        if (moveStateY == MoveStateY.BOTTOM) {
            moveDown();
        } else if (moveStateY == MoveStateY.TOP) {
            moveUp();
        }

        if (zoomState == ZoomState.IN) {
            adjustZoom(1);
        } else if (zoomState == ZoomState.OUT) {
            adjustZoom(-1);
        }

        mapCameraPosition.x = Math.max(getWidth() / 2, mapCameraPosition.x);
        mapCameraPosition.x = Math.min(map.getWidth() * mapZoomMax - getWidth() / 2, mapCameraPosition.x);

        mapCameraPosition.y = Math.max(getHeight() / 2, mapCameraPosition.y);
        mapCameraPosition.y = Math.min(map.getHeight() * mapZoomMax - getHeight() / 2, mapCameraPosition.y);

        updateSurveyCameraPosition();
    }

    private int mapDrawOffsetX, mapDrawOffsetY, imageLoX, imageLoY;
    private int xLo, xHi, yLo, yHi, zoom, offsetX, offsetY;

    public void draw(Batch batch, float parentAlpha) {
        // draw map tiles
        GameMap map = screen.getScenario().getGameMap();
        zoom = map.getZoom();

        int imageSize = map.getZoom() * map.getTileInEachImage();

        int noImagesX = MathUtils.ceil(this.getWidth() / imageSize);
        int noImagesY = MathUtils.ceil(this.getHeight() / imageSize);

        float scaledCameraPositionX = mapCameraPosition.x / mapZoomMax * zoom;
        float scaledCameraPositionY = mapCameraPosition.y / mapZoomMax * zoom;

        xLo = MathUtils.floor(mapCameraPosition.x / mapZoomMax / map.getTileInEachImage() - noImagesX / 2 - 1);
        xHi = MathUtils.ceil(mapCameraPosition.x / mapZoomMax / map.getTileInEachImage() + noImagesX / 2);
        yLo = MathUtils.floor(mapCameraPosition.y / mapZoomMax / map.getTileInEachImage() - noImagesY / 2 - 1);
        yHi = MathUtils.ceil(mapCameraPosition.y / mapZoomMax / map.getTileInEachImage() + noImagesY / 2);

        int startPointFromCameraX = (int)(scaledCameraPositionX - (imageSize * xLo));
        int startPointFromCameraY = (int)(scaledCameraPositionY - (imageSize * yLo));

        offsetX = (int) (startPointFromCameraX - this.getWidth() / 2);
        offsetY = (int) (startPointFromCameraY - this.getHeight() / 2);

        mapDrawOffsetX = offsetX;
        mapDrawOffsetY = offsetY;
        imageLoX = xLo;
        imageLoY = yLo;

        for (int y = yLo; y <= yHi; ++y) {
            for (int x = xLo; x <= xHi; ++x) {
                if (x < 0 || x >= map.getImageCount()) continue;
                if (y < 0 || y >= map.getImageCount()) continue;

                int px = (x - xLo) * imageSize - offsetX;
                int py = (y - yLo) * imageSize - offsetY;

                // map
                Texture texture = getMapTile(map.getFileName(), Integer.toString((map.getImageCount() - 1 - y) * map.getImageCount() + x));
                batch.draw(texture, px, py, imageSize, imageSize);

                // grid
                if (GlobalVariables.showGrid) {
                    for (int i = 0; i < map.getTileInEachImage(); ++i) {
                        for (int j = 0; j < map.getTileInEachImage(); ++j) {
                            int mx = x * map.getTileInEachImage() + j;
                            int my = map.getHeight() - 1 - (y * map.getTileInEachImage() + i);

                            if (mx >= 0 && mx < map.getWidth() && my >= 0 && my < map.getHeight()) {
                                if (map.getTerrainAt(mx, my).isPassableByAnyMilitaryKind(screen.getScenario())) {
                                    batch.draw(grid, px + j * zoom, py + i * zoom, zoom, zoom);
                                }
                            }
                        }
                    }
                }
            }
        }

        {
            // draw map info
            Point p = mouseOnMapPosition();
            int px = p.x;
            int py = p.y;

            TerrainDetail terrain = map.getTerrainAt(px, py);
            if (terrain != null) {
                String text = String.format(mapInfoFormat, terrain.getName(), px, py);
                mapInfo.setText(text);
            } else {
                mapInfo.setText("");
            }
        }

        String resPack = screen.getScenario().getGameSurvey().getResourcePackName();
        MapLayer.DrawingHelpers helpers = new MapLayer.DrawingHelpers() {
            @Override
            public boolean isMapLocationOnScreen(Point p) {
                return xLo * map.getTileInEachImage() <= p.x && p.x <= (xHi + 1) * map.getTileInEachImage() &&
                        yLo * map.getTileInEachImage() <= (map.getHeight() - p.y + 1) &&
                        (map.getHeight() - p.y + 1) <= (yHi + 1) * map.getTileInEachImage();
            }

            @Override
            public Point getPixelFromMapLocation(Point p) {
                return new Point((p.x - xLo * map.getTileInEachImage()) * zoom - offsetX,
                        ((map.getHeight() - 1 - p.y) - yLo * map.getTileInEachImage()) * zoom - offsetY);
            }
        };

        mapLayers.forEach(l -> l.draw(screen, resPack, helpers, zoom, batch, parentAlpha));

        // draw childrens
        super.draw(batch, parentAlpha);
    }

    public void dispose() {
        mapTiles.values().forEach(Texture::dispose);
        mapLayers.forEach(com.zhsan.gamecomponents.maplayer.MapLayer::dispose);
    }

    private Point mouseOnMapPosition() {
        GameMap map = screen.getScenario().getGameMap();

        int px = (int) (mousePosition.x + getX() + mapDrawOffsetX) / map.getZoom() + imageLoX * map.getTileInEachImage();
        int py = map.getHeight() - 1 - ((int) (mousePosition.y + getY() + mapDrawOffsetY) / map.getZoom() + imageLoY * map.getTileInEachImage());
        return new Point(px, py);
    }

    private class InputEventListener extends InputListener {

        @Override
        public void exit(InputEvent event, float x, float y, int pointer, Actor toActor) {
            moveStateX = MoveStateX.IDLE;
            moveStateY = MoveStateY.IDLE;
        }

        @Override
        public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
            mousePosition.set(x, y);

            Point pos = mouseOnMapPosition();
            Architecture a = screen.getScenario().getArchitectureAt(pos);
            Troop t = screen.getScenario().getTroopAt(pos);

             if (t != null) {
                if (button == Input.Buttons.LEFT) {
                    if (!screen.getDayRunner().isDayRunning() && t.getBelongedFaction() == screen.getScenario().getCurrentPlayer()) {
                        screen.showContextMenu(ContextMenu.MenuKindType.TROOP_LEFT_MENU, t, new Point(mousePosition));
                    }
                } else if (button == Input.Buttons.RIGHT) {
                    screen.showContextMenu(ContextMenu.MenuKindType.TROOP_RIGHT_MENU, t, new Point(mousePosition));
                }
                screen.hideArchitectureCommandFrame();
            } else if (a != null) {
                if (button == Input.Buttons.LEFT) {
                    screen.showArchitectureCcommandFrame(a);
                } else if (button == Input.Buttons.RIGHT) {
                    screen.showContextMenu(ContextMenu.MenuKindType.ARCHITECTURE_RIGHT_MENU, a, new Point(mousePosition));
                }
            } else {
                if (button == Input.Buttons.RIGHT) {
                    screen.showContextMenu(ContextMenu.MenuKindType.MAP_RIGHT_MENU, screen.getScenario(), new Point(mousePosition));
                }
                screen.hideArchitectureCommandFrame();
            }

            if (locationSelectionListener != null) {
                if (locationSelectionCandidates == null || locationSelectionCandidates.contains(pos)) {
                    locationSelectionListener.onLocationSelected(button == Input.Buttons.LEFT ? pos : null);
                    locationSelectionListener = null;
                    mapLayers.forEach(MapLayer::onEndSelectingLocation);
                }
            }

            return false;
        }

        @Override
        public boolean keyDown(InputEvent event, int keycode) {
            if (screen.allowMapMove()) {
                if (keycode == Input.Keys.MINUS) {
                    zoomState = ZoomState.OUT;
                } else if (keycode == Input.Keys.EQUALS) {
                    zoomState = ZoomState.IN;
                } else if (keycode == Input.Keys.W || keycode == Input.Keys.UP) {
                    moveStateY = MoveStateY.TOP;
                } else if (keycode == Input.Keys.A || keycode == Input.Keys.LEFT) {
                    moveStateX = MoveStateX.LEFT;
                } else if (keycode == Input.Keys.S || keycode == Input.Keys.DOWN) {
                    moveStateY = MoveStateY.BOTTOM;
                } else if (keycode == Input.Keys.D || keycode == Input.Keys.RIGHT) {
                    moveStateX = MoveStateX.RIGHT;
                }
            }
            if (keycode == Input.Keys.Q) {
                GlobalVariables.showGrid = !GlobalVariables.showGrid;
            }
            if (screen.allowRunDays()) {
                if (keycode == Input.Keys.NUM_1) {
                    screen.getDayRunner().runDays(1);
                } else if (keycode == Input.Keys.NUM_2) {
                    screen.getDayRunner().runDays(2);
                } else if (keycode == Input.Keys.NUM_3) {
                    screen.getDayRunner().runDays(3);
                } else if (keycode == Input.Keys.NUM_4) {
                    screen.getDayRunner().runDays(4);
                } else if (keycode == Input.Keys.NUM_5) {
                    screen.getDayRunner().runDays(5);
                } else if (keycode == Input.Keys.NUM_6) {
                    screen.getDayRunner().runDays(6);
                } else if (keycode == Input.Keys.NUM_7) {
                    screen.getDayRunner().runDays(7);
                } else if (keycode == Input.Keys.NUM_8) {
                    screen.getDayRunner().runDays(8);
                } else if (keycode == Input.Keys.NUM_9) {
                    screen.getDayRunner().runDays(9);
                } else if (keycode == Input.Keys.NUM_0) {
                    screen.getDayRunner().runDays(10);
                } else if (keycode == Input.Keys.F1) {
                    screen.getDayRunner().runDays(30);
                } else if (keycode == Input.Keys.F2) {
                    screen.getDayRunner().runDays(60);
                } else if (keycode == Input.Keys.F3) {
                    screen.getDayRunner().runDays(90);
                } else if (keycode == Input.Keys.SPACE) {
                    screen.getDayRunner().continueRunDays();
                }
            }

            return true;
        }

        @Override
        public boolean keyUp(InputEvent event, int keycode) {
            if (keycode == Input.Keys.MINUS) {
                zoomState = ZoomState.IDLE;
            } else if (keycode == Input.Keys.EQUALS) {
                zoomState = ZoomState.IDLE;
            } else if (keycode == Input.Keys.W || keycode == Input.Keys.UP) {
                moveStateY = MoveStateY.IDLE;
            } else if (keycode == Input.Keys.A || keycode == Input.Keys.LEFT) {
                moveStateX = MoveStateX.IDLE;
            } else if (keycode == Input.Keys.S || keycode == Input.Keys.DOWN) {
                moveStateY = MoveStateY.IDLE;
            } else if (keycode == Input.Keys.D || keycode == Input.Keys.RIGHT) {
                moveStateX = MoveStateX.IDLE;
            }

            return true;
        }

        @Override
        public boolean mouseMoved(InputEvent event, float x, float y) {
            handleMouseMoved(event, x, y);
            return true;
        }

        @Override
        public boolean scrolled(InputEvent event, float x, float y, int amount) {
            adjustZoom(-amount);
            return true;
        }
    }

    public void handleMouseMoved(InputEvent event, float x, float y) {
        mousePosition.set(x, y);

        // decide scroll
        moveStateX = MoveStateX.IDLE;
        moveStateY = MoveStateY.IDLE;

        if (screen.allowMapMove()) {
            if (x < mapScrollBoundary) {
                moveStateX = MoveStateX.LEFT;
            } else if (x > getWidth() - mapScrollBoundary) {
                moveStateX = MoveStateX.RIGHT;
            }
            if (y < mapScrollBoundary) {
                moveStateY = MoveStateY.BOTTOM;
            } else if (y > getHeight() - mapScrollBoundary) {
                moveStateY = MoveStateY.TOP;
            }
        }
    }

}

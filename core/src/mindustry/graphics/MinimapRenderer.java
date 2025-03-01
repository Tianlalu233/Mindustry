package mindustry.graphics;

import arc.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.math.geom.*;
import arc.scene.ui.layout.*;
import arc.struct.*;
import arc.util.*;
import arc.util.pooling.*;
import mindustry.entities.*;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import mindustry.io.*;
import mindustry.ui.*;
import mindustry.world.*;

import static mindustry.Vars.*;

public class MinimapRenderer{
    private static final float baseSize = 16f;
    private final Seq<Unit> units = new Seq<>();
    private Pixmap pixmap;
    private Texture texture;
    private TextureRegion region;
    private Rect rect = new Rect();
    private float zoom = 4;

    public MinimapRenderer(){
        Events.on(WorldLoadEvent.class, event -> {
            reset();
            updateAll();
        });

        Events.on(TileChangeEvent.class, event -> {
            //TODO don't update when the minimap is off?
            if(!ui.editor.isShown()){
                update(event.tile);
            }
        });

        Events.on(BuildTeamChangeEvent.class, event -> update(event.build.tile));
    }

    public Pixmap getPixmap(){
        return pixmap;
    }

    public @Nullable Texture getTexture(){
        return texture;
    }

    public void zoomBy(float amount){
        zoom += amount;
        setZoom(zoom);
    }

    public void setZoom(float amount){
        zoom = Mathf.clamp(amount, 1f, Math.min(world.width(), world.height()) / baseSize / 2f);
    }

    public float getZoom(){
        return zoom;
    }

    public void reset(){
        if(pixmap != null){
            pixmap.dispose();
            texture.dispose();
        }
        setZoom(4f);
        pixmap = new Pixmap(world.width(), world.height());
        texture = new Texture(pixmap);
        region = new TextureRegion(texture);
    }

    public void drawEntities(float x, float y, float w, float h, float scaling, boolean withLabels){
        if(!withLabels){
            updateUnitArray();
        }else{
            units.clear();
            Groups.unit.each(units::add);
        }

        float sz = baseSize * zoom;
        float dx = (Core.camera.position.x / tilesize);
        float dy = (Core.camera.position.y / tilesize);
        dx = Mathf.clamp(dx, sz, world.width() - sz);
        dy = Mathf.clamp(dy, sz, world.height() - sz);

        rect.set((dx - sz) * tilesize, (dy - sz) * tilesize, sz * 2 * tilesize, sz * 2 * tilesize);

        for(Unit unit : units){
            float rx = !withLabels ? (unit.x - rect.x) / rect.width * w : unit.x / (world.width() * tilesize) * w;
            float ry = !withLabels ? (unit.y - rect.y) / rect.width * h : unit.y / (world.height() * tilesize) * h;

            Draw.mixcol(unit.team().getUnitColor(), 1f);
            float scale = Scl.scl(1f) / 2f * scaling * 32f;
            var region = unit.icon();
            Draw.rect(region, x + rx, y + ry, scale, scale * (float)region.height / region.width, unit.rotation() - 90);
            Draw.reset();
        }

        if (state.hasSpawns()) {
            for(Tile tile : spawner.getSpawns()){
                float tileX = tile.x + 0.5f;
                float tileY = tile.y + 0.5f;
                float rx = !withLabels ? (tileX * tilesize - rect.x) / rect.width * w : tileX * w / world.width();
                float ry = !withLabels ? (tileY * tilesize - rect.y) / rect.width * h : tileY * h / world.height();
                float scale = Scl.scl(1f) * scaling * 8f;
                TextureRegion region = Core.atlas.find("editor-spawn");
                Draw.rect(region, x + rx, y + ry, scale, scale * (float)region.height / region.width);
            }
        }

        if(withLabels && net.active()){
            for(Player player : Groups.player){
                if(!player.dead()){
                    float rx = player.x / (world.width() * tilesize) * w;
                    float ry = player.y / (world.height() * tilesize) * h;

                    drawLabel(x + rx, y + ry, player.name, player.team().color);
                }
            }
        }

        Draw.reset();
    }

    public void drawEntities(float x, float y, float w, float h){
        drawEntities(x, y, w, h, 1f, true);
    }

    public @Nullable TextureRegion getRegion(){
        if(texture == null) return null;

        float sz = Mathf.clamp(baseSize * zoom, baseSize, Math.min(world.width(), world.height()));
        float dx = (Core.camera.position.x / tilesize);
        float dy = (Core.camera.position.y / tilesize);
        dx = Mathf.clamp(dx, sz, world.width() - sz);
        dy = Mathf.clamp(dy, sz, world.height() - sz);
        float invTexWidth = 1f / texture.width;
        float invTexHeight = 1f / texture.height;
        float x = dx - sz, y = world.height() - dy - sz, width = sz * 2, height = sz * 2;
        region.set(x * invTexWidth, y * invTexHeight, (x + width) * invTexWidth, (y + height) * invTexHeight);
        return region;
    }

    public void updateAll(){
        for(Tile tile : world.tiles){
            pixmap.set(tile.x, pixmap.height - 1 - tile.y, colorFor(tile));
        }
        texture.draw(pixmap);
    }

    public void update(Tile tile){
        if(world.isGenerating() || !state.isGame()) return;

        if(tile.build != null && tile.isCenter()){
            tile.getLinkedTiles(other -> {
                if(!other.isCenter()){
                    update(other);
                }
            });
        }

        int color = colorFor(tile);
        pixmap.set(tile.x, pixmap.height - 1 - tile.y, color);

        Pixmaps.drawPixel(texture, tile.x, pixmap.height - 1 - tile.y, color);
    }

    public void updateUnitArray(){
        float sz = baseSize * zoom;
        float dx = (Core.camera.position.x / tilesize);
        float dy = (Core.camera.position.y / tilesize);
        dx = Mathf.clamp(dx, sz, world.width() - sz);
        dy = Mathf.clamp(dy, sz, world.height() - sz);

        units.clear();
        Units.nearby((dx - sz) * tilesize, (dy - sz) * tilesize, sz * 2 * tilesize, sz * 2 * tilesize, units::add);
    }

    private int colorFor(Tile tile){
        if(tile == null) return 0;
        int bc = tile.block().minimapColor(tile);
        Color color = Tmp.c1.set(bc == 0 ? MapIO.colorFor(tile.block(), tile.floor(), tile.overlay(), tile.team()) : bc);
        color.mul(1f - Mathf.clamp(world.getDarkness(tile.x, tile.y) / 4f));

        return color.rgba();
    }

    public void drawLabel(float x, float y, String text, Color color){
        Font font = Fonts.outline;
        GlyphLayout l = Pools.obtain(GlyphLayout.class, GlyphLayout::new);
        boolean ints = font.usesIntegerPositions();
        font.getData().setScale(1 / 1.5f / Scl.scl(1f));
        font.setUseIntegerPositions(false);

        l.setText(font, text, color, 90f, Align.left, true);
        float yOffset = 20f;
        float margin = 3f;

        Draw.color(0f, 0f, 0f, 0.2f);
        Fill.rect(x, y + yOffset - l.height/2f, l.width + margin, l.height + margin);
        Draw.color();
        font.setColor(color);
        font.draw(text, x - l.width/2f, y + yOffset, 90f, Align.left, true);
        font.setUseIntegerPositions(ints);

        font.getData().setScale(1f);

        Pools.free(l);
    }
}

package com.zhsan.gameobject;

import com.badlogic.gdx.files.FileHandle;
import com.opencsv.CSVReader;
import com.opencsv.CSVWriter;
import com.zhsan.common.exception.FileReadException;
import com.zhsan.common.exception.FileWriteException;
import com.zhsan.gamecomponents.GlobalStrings;
import com.zhsan.lua.LuaAI;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStreamReader;

/**
 * Created by Peter on 4/8/2015.
 */
public final class TroopAnimation implements GameObject {

    public enum TroopAnimationKind {
        IDLE(1),
        MOVE(2),
        ATTACK(3),
        BE_ATTACKED(4),
        CAST(5),
        BE_CASTED(6),
        ROUTED(101);

        private final int id;
        TroopAnimationKind(int id) {
            this.id = id;
        }

        public int getId() {
            return id;
        }
    }

    public static final String SAVE_FILE = "TroopAnimation.csv";

    private final String name;
    private final String fileName;
    private final int frameCount, idleFrame, spriteSize;

    private final int id;
    private String aiTags = "";

    @Override
    @LuaAI.ExportToLua
    public int getId() {
        return id;
    }

    @Override
    @LuaAI.ExportToLua
    public String getAiTags() {
        return aiTags;
    }

    @Override
    @LuaAI.ExportToLua
    public GameObject setAiTags(String aiTags) {
        this.aiTags = aiTags;
        return this;
    }

    private TroopAnimation(int id, String name, String fileName, int frameCount, int idleFrame, int spriteSize) {
        this.id = id;
        this.name = name;
        this.fileName = fileName;
        this.frameCount = frameCount;
        this.idleFrame = idleFrame;
        this.spriteSize = spriteSize;
    }

    public static final GameObjectList<TroopAnimation> fromCSV(FileHandle root, @NotNull GameScenario scen) {
        GameObjectList<TroopAnimation> result = new GameObjectList<>();

        FileHandle f = root.child(SAVE_FILE);
        try (CSVReader reader = new CSVReader(new InputStreamReader(f.read(), "UTF-8"))) {
            String[] line;
            int index = 0;
            while ((line = reader.readNext()) != null) {
                index++;
                if (index == 1) continue; // skip first line.

                TroopAnimation t = new TroopAnimationBuilder()
                        .setId(Integer.parseInt(line[0]))
                        .setName(line[1])
                        .setFileName(line[2])
                        .setFrameCount(Integer.parseInt(line[3]))
                        .setIdleFrame(Integer.parseInt(line[4]))
                        .setSpriteSize(Integer.parseInt(line[5]))
                        .createTroopAnimation();

                result.add(t);
            }
        } catch (IOException e) {
            throw new FileReadException(f.path(), e);
        }

        return result;
    }

    public static final void toCSV(FileHandle root, GameObjectList<TroopAnimation> kinds) {
        FileHandle f = root.child(SAVE_FILE);
        try (CSVWriter writer = new CSVWriter(f.writer(false, "UTF-8"))) {
            writer.writeNext(GlobalStrings.getString(GlobalStrings.Keys.TROOP_ANIMATION_SAVE_HEADER).split(","));
            for (TroopAnimation detail : kinds) {
                writer.writeNext(new String[]{
                        String.valueOf(detail.getId()),
                        detail.name,
                        detail.fileName,
                        String.valueOf(detail.frameCount),
                        String.valueOf(detail.idleFrame),
                        String.valueOf(detail.spriteSize)
                });
            }
        } catch (IOException e) {
            throw new FileWriteException(f.path(), e);
        }

    }

    @Override
    public String getName() {
        return name;
    }

    public String getFileName() {
        return fileName;
    }

    public int getFrameCount() {
        return frameCount;
    }

    public int getIdleFrame() {
        return idleFrame;
    }

    public int getSpriteSize() {
        return spriteSize;
    }

    public static class TroopAnimationBuilder {
        private int id;
        private String name;
        private String fileName;
        private int frameCount;
        private int idleFrame;
        private int spriteSize;

        public TroopAnimationBuilder setId(int id) {
            this.id = id;
            return this;
        }

        public TroopAnimationBuilder setName(String name) {
            this.name = name;
            return this;
        }

        public TroopAnimationBuilder setFileName(String fileName) {
            this.fileName = fileName;
            return this;
        }

        public TroopAnimationBuilder setFrameCount(int frameCount) {
            this.frameCount = frameCount;
            return this;
        }

        public TroopAnimationBuilder setIdleFrame(int idleFrame) {
            this.idleFrame = idleFrame;
            return this;
        }

        public TroopAnimationBuilder setSpriteSize(int spriteSize) {
            this.spriteSize = spriteSize;
            return this;
        }

        public TroopAnimation createTroopAnimation() {
            return new TroopAnimation(id, name, fileName, frameCount, idleFrame, spriteSize);
        }
    }
}

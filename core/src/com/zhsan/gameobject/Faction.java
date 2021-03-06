package com.zhsan.gameobject;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.opencsv.CSVReader;
import com.opencsv.CSVWriter;
import com.zhsan.common.exception.FileReadException;
import com.zhsan.common.exception.FileWriteException;
import com.zhsan.gamecomponents.common.XmlHelper;
import com.zhsan.gamecomponents.GlobalStrings;
import com.zhsan.lua.LuaAI;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.RecursiveAction;

/**
 * Created by Peter on 24/5/2015.
 */
public class Faction implements GameObject {

    public static final String SAVE_FILE = "Faction.csv";

    private String name;

    private int leaderId;
    private Person leader;

    private GameScenario scenario;

    private Color color;

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

    private Faction(int id, GameScenario scen) {
        this.id = id;
        this.scenario = scen;
    }

    public static final GameObjectList<Faction> fromCSV(FileHandle root, @NotNull GameScenario scen) {
        GameObjectList<Faction> result = new GameObjectList<>();

        FileHandle f = root.child(Faction.SAVE_FILE);
        try (CSVReader reader = new CSVReader(new InputStreamReader(f.read(), "UTF-8"))) {
            String[] line;
            int index = 0;
            while ((line = reader.readNext()) != null) {
                index++;
                if (index == 1) continue; // skip first line.

                Faction t = new Faction(Integer.parseInt(line[0]), scen);
                t.setAiTags(line[1]);
                t.name = line[2];
                t.color = XmlHelper.loadColorFromXml(Integer.parseUnsignedInt(line[3]));
                t.leaderId = Integer.parseInt(line[4]);

                result.add(t);
            }

            return result;
        } catch (IOException e) {
            throw new FileReadException(f.path(), e);
        }
    }

    public static final GameObjectList<Faction> fromCSVQuick(FileHandle root) {
        GameObjectList<Faction> result = new GameObjectList<>();

        FileHandle f = root.child(Faction.SAVE_FILE);
        try (CSVReader reader = new CSVReader(new InputStreamReader(f.read(), "UTF-8"))) {
            String[] line;
            int index = 0;
            while ((line = reader.readNext()) != null) {
                index++;
                if (index == 1) continue; // skip first line.

                Faction t = new Faction(Integer.parseInt(line[0]), null);
                t.name = line[2];
 
                result.add(t);
            }

            return result;
        } catch (IOException e) {
            throw new FileReadException(f.path(), e);
        }
    }

    public static final void toCSV(FileHandle root, GameObjectList<Faction> data) {
        FileHandle f = root.child(SAVE_FILE);
        try (CSVWriter writer = new CSVWriter(f.writer(false, "UTF-8"))) {
            writer.writeNext(GlobalStrings.getString(GlobalStrings.Keys.FACTION_SAVE_HEADER).split(","));
            for (Faction d : data) {
                writer.writeNext(new String[]{
                        String.valueOf(d.getId()),
                        d.getAiTags(),
                        d.getName(),
                        XmlHelper.saveColorToXml(d.color),
                        String.valueOf(d.leader.getId())
                });
            }
        } catch (IOException e) {
            throw new FileWriteException(f.path(), e);
        }

    }

    int getLeaderId() {
        return leaderId;
    }

    @Override
    @LuaAI.ExportToLua
    public String getName() {
        return name;
    }

    public Color getColor() {
        return color;
    }

    public void setLeader(Person p) {
        if (!this.getPersons().contains(p) || (p.getState() != Person.State.NORMAL && p.getState() != Person.State.CAPTIVE)) {
            throw new IllegalArgumentException("The leader must be in this faction " + this + ", attempting to assign person " + p);
        }
        this.leader = p;
    }

    void setLeaderUnchecked(Person p) {
        this.leader = p;
    }

    public GameObjectList<Person> getPersons() {
        return scenario.getPersons().filter(p -> p.getBelongedFaction() == this);
    }

    @LuaAI.ExportToLua
    public GameObjectList<Section> getSections() {
        return scenario.getSections().filter(s -> s.getBelongedFaction() == this);
    }

    public GameObjectList<Architecture> getArchitectures() {
        return scenario.getArchitectures().filter(a -> a.getBelongedFaction() == this);
    }

    public GameObjectList<Military> getMilitaries() {
        return scenario.getMilitaries().filter(m -> m.getBelongedFaction() == this);
    }

    public GameObjectList<Troop> getTroops() {
        return scenario.getTroops().filter(t -> t.getBelongedFaction() == this);
    }

    Person pickLeader() {
        return this.getPersons().max((p, q) -> Integer.compare(p.getAbilitySum(), q.getAbilitySum()));
    }

    Person getLeaderUnchecked() {
        return leader;
    }

    public Person getLeader() {
        if (leader == null) {
            throw new IllegalStateException("Every faction must have a leader");
        }
        return leader;
    }

    public String getLeaderName() {
        return getLeader().getName();
    }

    public int getArchitectureCount() {
        return getArchitectures().size();
    }

    public int getPersonCount() {
        return getPersons().size();
    }

    public long getFund() {
        return getArchitectures().getAll().stream().mapToLong(Architecture::getFund).sum();
    }

    public long getFood() {
        return getArchitectures().getAll().stream().mapToLong(Architecture::getFood).sum();
    }

    public int getTroopQuantity() {
        return getMilitaries().getAll().stream().mapToInt(Military::getQuantity).sum();
    }

    public void ai() {
        LuaAI.runFactionAi(scenario, this);
    }

}

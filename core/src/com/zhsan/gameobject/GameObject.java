package com.zhsan.gameobject;

import com.zhsan.gamecomponents.GlobalStrings;
import com.zhsan.lua.LuaAI;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Objects;

/**
 * Created by Peter on 17/3/2015.
 */
public abstract class GameObject {

    private final int id;
    private String aiTags = "";

    protected GameObject(int id) {
        this.id = id;
    }

    public abstract String getName();

    @LuaAI.ExportToLua
    public final int getId() {
        return id;
    }

    @LuaAI.ExportToLua
    public String getAiTags() {
        return aiTags;
    }

    @LuaAI.ExportToLua
    public GameObject setAiTags(String aiTags) {
        this.aiTags = aiTags;
        return this;
    }

    public final Object getField(String fname) {
        switch (fname) {
            case "Id":
                return id;
            case "Name":
                return getName();
            default:
                try {
                    Method m = this.getClass().getMethod("get" + fname);
                    return m.invoke(this);
                } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
                    return GlobalStrings.getString(GlobalStrings.Keys.NO_CONTENT);
                }
        }
    }

    public final String getFieldString(String name) {
        return getFieldString(name, true);
    }

    public final String getFieldString(String name, boolean round) {
        Object o = getField(name);
        if (o == null) {
            return GlobalStrings.getString(GlobalStrings.Keys.NO_CONTENT);
        } else if (o instanceof Float) {
            return round ? Long.toString(Math.round((float) o)) : Float.toString((Float) o);
        } else if (o instanceof GameObject) {
            return ((GameObject) o).getName();
        } else {
            return Objects.toString(o);
        }
    }

    public final boolean satisfyMethod(String fname) {
        try {
            String actualName = fname.substring(0, 1).toLowerCase() + fname.substring(1);
            Method m = this.getClass().getMethod(actualName);
            return (boolean) m.invoke(this);
        } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException | ClassCastException e) {
            return false;
        }
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + "{" +
                "id=" + id +
                '}';
    }
}

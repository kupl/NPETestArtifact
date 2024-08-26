package org.evosuite.analysis;

import spoon.reflect.declaration.CtElement;

public class VarInfo {
    String type;
    boolean isPrivate;
    boolean isNullable;

    CtElement value;

    public VarInfo(String type, boolean isPrivate, boolean isNullable, CtElement value) {
        this.type = type;
        this.isPrivate = isPrivate;
        this.value = value;
        this.isNullable = isNullable;
    }

    public String getType() {
        return type;
    }

    public boolean isPrivate() {
        return isPrivate;
    }
    

    public boolean getNullable() {
        return isNullable;
    }

    public CtElement getValue() {
        return value;
    }

    public String toString() {
        String valStr = "";
        if (value == null) valStr = "NULL";
        else valStr = value.toString();
        return "Type: " + type.toString() + ", " + "isPrivate: " + Boolean.toString(isPrivate) + ", " + 
                "isNullable: " + Boolean.toString(isNullable) + ", " + "Value: " + valStr;
    }
}

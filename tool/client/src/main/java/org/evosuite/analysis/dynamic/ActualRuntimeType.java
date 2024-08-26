package org.evosuite.analysis.dynamic;

import spoon.reflect.declaration.CtType;

import java.util.HashMap;
import java.util.Map;

public class ActualRuntimeType {
  private static final ActualRuntimeType instance = new ActualRuntimeType();

  private ActualRuntimeType() {
  }

  public static ActualRuntimeType getInstance() {
    return instance;
  }

  private static final Map<String, CtType<?>> variableNameToActualTypes = new HashMap<>();

  private boolean enabled = false;

  public boolean isEnabled() {
    return enabled;
  }

  public void enable() {
    enabled = true;
  }

  public void disable() {
    enabled = false;
  }

  public void reset() {
    enabled = false;
    variableNameToActualTypes.clear();
  }

  public Map<String, CtType<?>> getActualTypes() {
    return variableNameToActualTypes;
  }
}

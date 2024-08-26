package org.evosuite.analysis.dynamic;

import spoon.reflect.code.CtAbstractInvocation;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.code.CtStatement;
import spoon.reflect.declaration.CtMethod;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class MethodTrace {
  private static final MethodTrace instance = new MethodTrace();

  public static MethodTrace getInstance() {
    return instance;
  }

  private final Map<Integer, List<String>> statementIndexToCalledMethods = new HashMap<>();

  private final Set<String> calledMethodsSet = new HashSet<>();

  private final Set<String> globalNPEMethodCoverage = new HashSet<>();


  private enum Mode { OBJ, TC;}
  private Mode mode;

  private int currentIndex = -1;

  public void prepareRunningObjectSequence() {
    mode = Mode.OBJ;
    reset();
  }

  public void prepareRunningTestCase() {
    mode = Mode.TC;
    reset();
  }

  public void reset() {
    currentIndex = -1;
    statementIndexToCalledMethods.clear();
    calledMethodsSet.clear();
  }

  @SuppressWarnings("unused")
  public void incrementLine() {
    currentIndex++;
  }

  @SuppressWarnings("unused")
  public void recordMethodEntry(String methodKey) {
    switch(mode) {
      case OBJ:
        calledMethodsSet.add(methodKey);
        break;
      case TC:
        statementIndexToCalledMethods.computeIfAbsent(currentIndex, i -> new ArrayList<>()).add(methodKey);
        break;
      default:
        break;
    }
  }


  public Set<String> getGlobalNPEMethodCoverage() {
    return globalNPEMethodCoverage;
  }

}

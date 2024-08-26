package org.evosuite.commons.filters;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import spoon.reflect.cu.SourcePosition;
import spoon.reflect.declaration.CtElement;

public class NonTestCodeFilter extends BaseTypeFilter<CtElement> {
  private final List<String> subPaths = new ArrayList<>();

  public static final NonTestCodeFilter INSTANCE = new NonTestCodeFilter();

  private NonTestCodeFilter() {
    super(CtElement.class);
    subPaths.add("/src/test/");
    subPaths.add("/test/");
  }

  public void setup(String[] excludeTestDirectories) {
    if (excludeTestDirectories != null) {
      subPaths.addAll(Arrays.asList(excludeTestDirectories));
    }
  }

  @Override
  public boolean matches(CtElement element) {
    if (!super.matches(element)) {
      return false;
    }

    if (element.getPosition().equals(SourcePosition.NOPOSITION)) {
      return true;
    }

    for (String subPath : subPaths) {
      if (element.getPosition().getFile().getAbsolutePath().contains(subPath)) {
        return false;
      }
    }
    return true;
  }
}

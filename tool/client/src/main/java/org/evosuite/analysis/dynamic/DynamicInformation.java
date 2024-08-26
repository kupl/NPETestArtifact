package org.evosuite.analysis.dynamic;

/**
 * This class is not directly used by a tool.
 * Rather, the methods defined in this class are
 * inserted to the classes of the subject program
 * through javassist instrumentation API.
 */
public class DynamicInformation {
  private DynamicInformation() {
  }

  public static void prepareRunningObjectSequence() {
    MethodTrace.getInstance().prepareRunningObjectSequence();
    ActualRuntimeType.getInstance().disable();
  }

  public static void prepareRunningTestCase() {
    MethodTrace.getInstance().prepareRunningTestCase();
    ActualRuntimeType.getInstance().enable();
  }


  public static void reset() {
    MethodTrace.getInstance().reset();
    ActualRuntimeType.getInstance().reset();
  }

}

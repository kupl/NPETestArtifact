package org.evosuite.analysis.staticanalysis;

import org.evosuite.analysis.ASTUtils;
import org.evosuite.analysis.ClassInfo;
import org.evosuite.analysis.MethodInfo;
import org.evosuite.analysis.TypeUtils;
import spoon.reflect.code.CtFieldWrite;
import spoon.reflect.declaration.CtField;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;
import spoon.reflect.path.CtRole;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class NullableFieldAccessAnalyzer  {
  private static final NullableFieldAccessAnalyzer instance = new NullableFieldAccessAnalyzer();

  public static NullableFieldAccessAnalyzer getInstance() {
    return instance;
  }

  private final Map<String, Integer> nullableFieldAccessCounts = new HashMap<>();

  public void analyze(String methodKey) {
    CtMethod<?> method = ClassInfo.getInstance().getMethodBySig(methodKey);
    if (method == null || method.getBody() == null || nullableFieldAccessCounts.containsKey(methodKey)) {
      return;
    }

    CtType<?> declaringType = method.getDeclaringType();
    int weight = countNullableFieldAccess(method, declaringType);

    nullableFieldAccessCounts.put(methodKey, weight);
  }

  private int countNullableFieldAccess(CtMethod<?> method, CtType<?> declaringType) {
    /* Check nullable field access */
    int count = 0;
    Set<CtField<?>> nullableFields = NullableFieldAnalyzer.getInstance().getNullableFields();
    
    
    if (nullableFields == null) return 0;

    for (CtField<?> nullableField : nullableFields) {
      count += ASTUtils.countVariableAccess(method, nullableField);
    }
    return count;
  }

  public int getScore(String methodKey) {
    return nullableFieldAccessCounts.getOrDefault(methodKey, 0);
  }


  // -----------------------------------------------------------------------------


  // Check whether the methods has any access to nullable fields
  // if yes, return true. 
  // if there is no access to the field, t
  public boolean hasNullableFieldAccess(String methodKey) {
    CtMethod<?> method = ClassInfo.getInstance().getMethodBySig(methodKey);
   
    // logger.info("NULLABLE TESTING METHOD" + methodKey);

    if (method == null || method.getBody() == null) {
      return false;
    }

    /* Check nullable field access */
    Set<CtField<?>> nullableFields = NullableFieldAnalyzer.getInstance().getNullableFields();
    
    if (nullableFields == null) return false;

    for (CtField<?> nullableField : nullableFields) {
      if (!ASTUtils.hasVariableAccess(method, nullableField)) {
        return true;
      }
    }

    return false;
  }

  // Check whether the methods has any access to the given nullable field
  // if yes, return true;x
  public boolean isAccessedInMethod(String methodKey, CtField<?> field) {
    CtMethod<?> method = ClassInfo.getInstance().getMethodBySig(methodKey);
   
    if (method == null || method.getBody() == null) {
      return false;
    }

    return this.isAccessedInMethod(method, field);
  }

  // Check whether the methods has any access to the given nullable field
  // if yes, return true;
  public boolean isAccessedInMethod(CtMethod<?> method, CtField<?> field) {
    return !ASTUtils.hasVariableAccess(method, field);
  }

  //----------------------------------------------------------------------
  // Check whether the given method can redefine the given field.
  // if yes, return false
  public boolean hasFieldWrite(String methodKey, CtField<?> field) { 
    CtMethod<?> method = ClassInfo.getInstance().getMethodBySig(methodKey);

    return method.filterChildren(new TypeFilter<>(CtFieldWrite.class))
          .select((CtFieldWrite<?> fw) -> fw.getRoleInParent().equals(CtRole.ASSIGNED))
          .list()
          .isEmpty();
  }

  public List<CtFieldWrite<?>> getFieldWriteStmt (String methodKey, CtField<?> field) {
    CtMethod<?> method = ClassInfo.getInstance().getMethodBySig(methodKey);

    return method.filterChildren(new TypeFilter<>(CtFieldWrite.class))
          .select((CtFieldWrite<?> fw) -> fw.getRoleInParent().equals(CtRole.ASSIGNED))
          .list();
  }

  public Set<CtField<?>> getAccessedFieldsByMethod(String methodKey) {
    CtMethod<?> method = ClassInfo.getInstance().getMethodBySig(methodKey);

    if (method == null || method.getBody() == null) {
      return null;
    }

    CtType<?> declaringType = method.getDeclaringType();

    /* Check nullable field access */
    Set<CtField<?>> nullableFields = NullableFieldAnalyzer.getInstance().getNullableFields();
    Set<CtField<?>> result = new HashSet<>();

    if (nullableFields == null) return null;

    for (CtField<?> nullableField : nullableFields) {
      if (!ASTUtils.hasVariableAccess(method, nullableField)) {
        result.add(nullableField);
      }
    }

    return result;
  }


}

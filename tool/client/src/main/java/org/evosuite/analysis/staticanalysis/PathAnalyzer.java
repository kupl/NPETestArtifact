package org.evosuite.analysis.staticanalysis;

import org.evosuite.analysis.ASTUtils;
import org.evosuite.analysis.ClassInfo;
import org.evosuite.analysis.GraphManager;
import org.evosuite.analysis.MethodInfo;
import org.evosuite.analysis.PathBuilder;
import org.evosuite.analysis.TypeUtils;
import org.evosuite.analysis.controlflow.ControlFlowEdge;
import org.evosuite.analysis.controlflow.ControlFlowGraph;
import org.evosuite.analysis.controlflow.ControlFlowNode;
import org.evosuite.utils.LoggingUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.bytebuddy.matcher.MethodParameterTypeMatcher;
import spoon.reflect.code.BinaryOperatorKind;
import spoon.reflect.code.CtArrayAccess;
import spoon.reflect.code.CtAssignment;
import spoon.reflect.code.CtBinaryOperator;
import spoon.reflect.code.CtBlock;
import spoon.reflect.code.CtCatch;
import spoon.reflect.code.CtCodeElement;
import spoon.reflect.code.CtConditional;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtFieldRead;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.code.CtReturn;
import spoon.reflect.code.CtThisAccess;
import spoon.reflect.code.CtTypeAccess;
import spoon.reflect.code.CtVariableAccess;
import spoon.reflect.code.CtVariableRead;
import spoon.reflect.cu.SourcePosition;
import spoon.reflect.declaration.CtAnnotation;
import spoon.reflect.declaration.CtConstructor;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtExecutable;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtParameter;
import spoon.reflect.factory.CodeFactory;
import spoon.reflect.path.CtRole;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtLocalVariableReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.reference.CtVariableReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

public class PathAnalyzer {

  private static final Logger logger = LoggerFactory.getLogger(PathAnalyzer.class);

  private static final PathAnalyzer instance = new PathAnalyzer();

  String typeKey = "";
  String targetClass = "";

  private Set<String> doneReturnAnalysis = new HashSet<>();
  private Set<String> donePathAnalysis = new HashSet<>();
  private int totalPaths = 0;
  private float totalComplexity = 0f;

  private int totalFieldWrite = 0;

  private Set<String> targetMethodSet = new HashSet<>();

  private Set<String> doneComputeScore = new HashSet<>();
  
  Set<String> doneFieldAccess = new HashSet<>();
  Set<String> doneFieldWrite = new HashSet<>();

  public static PathAnalyzer getInstance() {
    return instance;
  }

  
	private static CtVariableReference<?> defined (ControlFlowNode n) {
		// Obtain the variables defined in this node
		CtVariableReference<?> def = null;
		if (n.getStatement() != null) {
			if (n.getStatement() instanceof CtLocalVariable) {
				CtLocalVariable<?> lv = ((CtLocalVariable<?>) n.getStatement());
				if (lv.getDefaultExpression() != null) {
					def = lv.getReference();
				}
			} else if (n.getStatement() instanceof CtAssignment) {
				CtExpression<?> e = ((CtAssignment) n.getStatement()).getAssigned();
				if (e instanceof CtVariableAccess) {
					def = ((CtVariableAccess<?>) e).getVariable();
				} else if (e instanceof CtArrayAccess) {
					CtExpression<?> exp = ((CtArrayAccess) e).getTarget();
					if (exp instanceof CtVariableAccess) {
						CtVariableReference<?> a = ((CtVariableAccess<?>) exp).getVariable();
						def = a;
					} else {
						System.out.println("Could not obtain variable from expression");
					}
				}
			}
		}
		return def;
	}

	private static Set<CtVariableReference<?>> used (ControlFlowNode n) {
		if (n.getStatement() == null) {
			return new HashSet<>();
		}

		// Obtain variables used in the given node
		HashSet<CtVariableReference<?>> used = new HashSet<>();
		for (CtVariableAccess<?> a: n.getStatement().getElements(new TypeFilter<CtVariableAccess<?>>(CtVariableAccess.class))) {
			used.add(a.getVariable());
		}
		return used;

	}

  // Propagate nodes in a path to find nullable variables in the given path
  public static void propagatePaths(String methodKey, Stack<ControlFlowNode> path) {
    CtElement next = null;

    if (path.isEmpty()) return;

    Set<String> nullableParams = NullLiteralAnalyzer.getInstance().getNullableParams(methodKey);    
      
    HashMap<String, Boolean> nullableVar = MethodInfo.getInstance().getNullableMap(methodKey);

    if (nullableVar.isEmpty()) {
      
      HashMap<String, Boolean> paramVarMap = new HashMap<>();
      
      for (String pp : nullableParams) {
        paramVarMap.put(pp, true);
      }
      
      nullableVar.putAll(paramVarMap);
    }

    do {
      ControlFlowNode node = path.pop();

      next = node.getStatement();

      if (next instanceof CtLocalVariable) {
        CtElement lhs = ((CtLocalVariable<?>) next).getReference();
        CtExpression<?> rhs = ((CtLocalVariable<?>) next).getAssignment();

        LoggingUtils.getEvoLogger().info(next.toString());

        if (rhs == null || rhs.getType() == null || TypeUtils.isPrimitive(rhs.getType())) {
          continue;
        }

        if (rhs instanceof CtConstructorCall) {
          nullableVar.put(lhs.toString(), false);

        } else if(rhs instanceof CtInvocation) {
          CtExecutable<?> calledInvocation = ((CtInvocation<?>) rhs).getExecutable().getExecutableDeclaration();

          String calledInvocationName = 
            calledInvocation == null ? ((CtInvocation<?>) rhs).getExecutable().getSignature() :
                                       calledInvocation.getSignature();

          nullableVar.put(lhs.toString(), MethodInfo.getInstance().isReturnNull(calledInvocationName));

        } else if (rhs instanceof CtVariableRead) {

          if (!nullableVar.keySet().contains(rhs.toString())) nullableVar.put(lhs.toString(), true);
          else nullableVar.put(lhs.toString(), nullableVar.get(rhs.toString()));
        } 

      } else if (next instanceof CtAssignment) {
        CtCodeElement rhs = next.getValueByRole(CtRole.ASSIGNMENT);
        CtCodeElement lhs = next.getValueByRole(CtRole.ASSIGNED);

        if (TypeUtils.isPrimitive(((CtExpression<?>)lhs).getType())) continue;

        if (rhs instanceof CtConstructorCall) {
          nullableVar.put(lhs.toString(), false);
        } else if (rhs instanceof CtInvocation) {
          String calledInvocationName = ((CtInvocation<?>) rhs).getExecutable().getSignature();

          nullableVar.put(lhs.toString(), MethodInfo.getInstance().isReturnNull(calledInvocationName));

        } else if (rhs instanceof CtVariableRead) {

          if (!nullableVar.keySet().contains(rhs.toString())) nullableVar.put(lhs.toString(), true);
          else nullableVar.put(lhs.toString(), nullableVar.get(rhs.toString()));
        } 
      } 

    } while (!path.isEmpty());

    if (!MethodInfo.getInstance().getNullableMap(methodKey).equals(nullableVar))
      MethodInfo.getInstance().updateNullableMap(methodKey, nullableVar);
  }

  private static boolean isExpNull(CtExpression<?> exp) {

    if (exp instanceof CtInvocation) {

    }


    return true;
  }

  private static boolean isConditionalNull(CtConditional<?> condEle, HashMap<String, Boolean> nullableVar) {
    CtExpression<Boolean> cond = condEle.getCondition();

    // if (cond isntanceo)


    CtExpression<?> thenExp = condEle.getThenExpression();
    CtExpression<?> elseExp = condEle.getElseExpression();


    return true;

  }

  private static HashSet<String> getNPEExp(ControlFlowNode node) {
    HashSet<String> result = new HashSet<>();
    
    List<CtVariableRead<?>> tmp = node.getStatement().getElements(new TypeFilter<CtVariableRead<?>>(CtVariableRead.class));

    if (tmp == null) return result;

    for (CtVariableRead<?> ff :tmp) {
      result.add(ff.toString());
    }    
    return result;
  }

  
  public static int propagatePath(String methodKey, Stack<ControlFlowNode> path) {
    CtElement next = null;

    if (path.isEmpty()) return 0;

    Set<String> nullableParams = NullLiteralAnalyzer.getInstance().getNullableParams(methodKey);    
      
    HashMap<String, Boolean> nullableVar = MethodInfo.getInstance().getNullableMap(methodKey);

    if (nullableVar.isEmpty()) {
      
      HashMap<String, Boolean> paramVarMap = new HashMap<>();
      
      if (nullableParams != null) {
        for (String pp : nullableParams) {
          paramVarMap.put(pp, true);
        }
        
        nullableVar.putAll(paramVarMap);
      }
    }

    ControlFlowNode node = null;

    do {
      node = path.pop();

      next = node.getStatement();

      if (next instanceof CtLocalVariable) {
        CtElement lhs = ((CtLocalVariable<?>) next).getReference();
        CtExpression<?> rhs = ((CtLocalVariable<?>) next).getAssignment();

        // LoggingUtils.getEvoLogger().info(next.toString());

        if (rhs == null || rhs.getType() == null || TypeUtils.isPrimitive(rhs.getType())) {
          continue;
        }

        if (rhs instanceof CtConstructorCall) {
          nullableVar.put(lhs.toString(), false);

        } else if(rhs instanceof CtInvocation) {
          CtExecutable<?> calledInvocation = ((CtInvocation<?>) rhs).getExecutable().getExecutableDeclaration();

          String calledInvocationName = 
            calledInvocation == null ? ((CtInvocation<?>) rhs).getExecutable().getSignature() :
                                       calledInvocation.getSignature();

          nullableVar.put(lhs.toString(), MethodInfo.getInstance().isReturnNull(calledInvocationName));

        } else if (rhs instanceof CtVariableRead) {

          if (!nullableVar.keySet().contains(rhs.toString())) nullableVar.put(lhs.toString(), true);
          else nullableVar.put(lhs.toString(), nullableVar.get(rhs.toString()));
        } else if (rhs instanceof CtConditional) {
          // LoggingUtils.getEvoLogger().info("CTCONDITIONAL");
          nullableVar.put(lhs.toString(), isConditionalNull((CtConditional<?>)rhs, nullableVar));
        }

      } else if (next instanceof CtAssignment) {
        CtCodeElement rhs = next.getValueByRole(CtRole.ASSIGNMENT);
        CtCodeElement lhs = next.getValueByRole(CtRole.ASSIGNED);

        if (TypeUtils.isPrimitive(((CtExpression<?>)lhs).getType())) continue;

        if (rhs instanceof CtConstructorCall) {
          nullableVar.put(lhs.toString(), false);
        } else if (rhs instanceof CtInvocation) {
          String calledInvocationName = ((CtInvocation<?>) rhs).getExecutable().getSignature();

          nullableVar.put(lhs.toString(), MethodInfo.getInstance().isReturnNull(calledInvocationName));

        } else if (rhs instanceof CtVariableRead) {

          if (!nullableVar.keySet().contains(rhs.toString())) nullableVar.put(lhs.toString(), true);
          else nullableVar.put(lhs.toString(), nullableVar.get(rhs.toString()));
        } 
      } 

    } while (!path.isEmpty());

    if (!MethodInfo.getInstance().getNullableMap(methodKey).equals(nullableVar))
      MethodInfo.getInstance().updateNullableMap(methodKey, nullableVar);

    HashSet<String> expSet = getNPEExp(node);

    if (expSet.isEmpty()) return 1;

    boolean result = false;
    for (String rr : expSet) {
      result |= nullableVar.getOrDefault(rr, true);      
    }

    if (result) return 1;
    else return 0;
  }

  public static boolean checkReturnPaths(String methodKey, Stack<ControlFlowNode> path) {
    CtElement next = null;

    if (path.isEmpty()) return false;

    Set<CtParameter<?>> nullableParams = NullLiteralAnalyzer.getInstance().getNullParams(methodKey);  
      
    HashMap<CtVariableReference<?>, Boolean> nullableVar = new HashMap<>();
    // MethodInfo.getInstance().getNullableMap(methodKey);

    if (nullableVar.isEmpty()) {
      
      HashMap<CtVariableReference<?>, Boolean> paramVarMap = new HashMap<>();
      
      for (CtParameter<?> pp : nullableParams) {
        
        paramVarMap.put((CtVariableReference<?>)pp.getDefaultExpression(), true);
      }
      
      nullableVar.putAll(paramVarMap);
    }

    ControlFlowNode node = null;

    do {

      node = path.pop();

      next = node.getStatement();

      if (next instanceof CtLocalVariable) {
        CtElement lhs = ((CtLocalVariable<?>) next).getReference();
        CtExpression<?> rhs = ((CtLocalVariable<?>) next).getAssignment();

        if (rhs == null || rhs.getType() == null || TypeUtils.isPrimitive(rhs.getType())) {
          node = path.pop();
          next = node.getStatement();
          continue;
        }

        if (rhs instanceof CtConstructorCall) {
          nullableVar.put((CtVariableReference<?>) lhs, false);

        } else if(rhs instanceof CtInvocation) {
          CtExecutable<?> calledInvocation = ((CtInvocation<?>) rhs).getExecutable().getExecutableDeclaration();

          String calledInvocationName = 
            calledInvocation == null ? ((CtInvocation<?>) rhs).getExecutable().getSignature() :
                                       calledInvocation.getSignature();

          nullableVar.put((CtVariableReference<?>) lhs, MethodInfo.getInstance().isReturnNull(calledInvocationName));

        } else if (rhs instanceof CtVariableRead) {
          
          if (!nullableVar.keySet().contains((((CtVariableRead)rhs).getVariable()))) nullableVar.put((CtVariableReference<?>) lhs, true);
          else nullableVar.put((CtVariableReference<?>) lhs, nullableVar.get((((CtVariableRead)rhs).getVariable())));
        } 

      } else if (next instanceof CtAssignment) {
        CtCodeElement rhs = next.getValueByRole(CtRole.ASSIGNMENT);
        CtExpression<?> lhs = ((CtAssignment)next).getAssigned();

        if (defined(node) == null) continue;

        CtVariableReference<?> var = defined(node);

        if (TypeUtils.isPrimitive(((CtExpression<?>)lhs).getType())) continue;

        if (rhs instanceof CtConstructorCall) {
          nullableVar.put(var, false);
        } else if (rhs instanceof CtInvocation) {
          String calledInvocationName = ((CtInvocation<?>) rhs).getExecutable().getSignature();

          nullableVar.put(var, MethodInfo.getInstance().isReturnNull(calledInvocationName));

        } else if (rhs instanceof CtVariableRead) {
          CtExpression<?> tmp = null;
          if (rhs instanceof CtFieldRead) {
            tmp = ((CtFieldRead<?>)rhs).getTarget();
            if (tmp instanceof CtVariableAccess) {

              if (!nullableVar.keySet().contains(var)) nullableVar.put((CtVariableReference<?>) lhs, true);
              else nullableVar.put(((CtVariableAccess<?>) lhs).getVariable(), nullableVar.get(var));              
            }
          } 

        } 
      } 

    } while (!path.isEmpty());

    boolean result = false;

    for (CtVariableReference<?> vv : used(node)) {
      result |= nullableVar.getOrDefault(vv, true);  
    }

    // LoggingUtils.getEvoLogger().info(nullableVar.toString() + " - " + Boolean.toString(result));
    
    return result;
  }

  
  private void analysisReturnPath(String methodKey) {

    HashSet<Stack<ControlFlowNode>> returnPaths = MethodInfo.getInstance().getReturnPaths(methodKey);

    for (Stack<ControlFlowNode> path : returnPaths) {
      propagatePaths(methodKey, path);
    }
  }

  private void analysisNullablePath(String methodKey) {
    // logger.debug("NULLABLE PATH ANALSYSI STARTS: " + methodKey);
    if (MethodInfo.getInstance().getMayNullPaths(methodKey).isEmpty()) return;
    donePathAnalysis.add(methodKey);

    // IF the given method has any path reaching to NPE
    // we set the value to false
  }

  // gather all paths to the return statements
  // to check whether the given method must not return nonnull.   
  private int buildMethodReturnPaths(String methodKey) {
    CtMethod<?> method = ClassInfo.getInstance().getMethodBySig(methodKey);

    CtTypeReference<?> returnType = method.getType();

    // Given methods does not have return value    
    if (TypeUtils.isVoidPrimitive(returnType) || TypeUtils.isPrimitive(returnType)) {
      MethodInfo.getInstance().setReturnNull(methodKey, false);
      return 0;
    }

    List<CtReturn<?>> returnEles = method.filterChildren(new TypeFilter<>(CtReturn.class))
      .select(e -> ((CtReturn<?>)e).getReturnedExpression() != null)
      .list();    

    // check whether there exists any return stmt which directly returns null
    if (returnEles.stream().filter(e -> TypeUtils.isNull(e.getReturnedExpression().getType())).count() != 0) {
      MethodInfo.getInstance().setReturnNull(methodKey, true);
      return 0;
    }

    HashSet<Stack<ControlFlowNode>> paths = new HashSet<>();

    // Remove return stmts which directly returns null
    returnEles.removeIf(e -> TypeUtils.isNull(e.getReturnedExpression().getType()));

    for (CtReturn<?> ret : returnEles) {      
      paths.addAll(PathBuilder.getInstance().buildBackPathByEle(methodKey, ret));
    }

    MethodInfo.getInstance().setReturnPaths(methodKey, paths);

    return 1;
  }
}

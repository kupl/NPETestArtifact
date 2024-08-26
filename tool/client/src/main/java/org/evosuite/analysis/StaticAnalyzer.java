package org.evosuite.analysis;

import org.evosuite.Properties;
import org.evosuite.analysis.controlflow.BranchKind;
import org.evosuite.analysis.controlflow.ControlFlowEdge;
import org.evosuite.analysis.controlflow.ControlFlowGraph;
import org.evosuite.analysis.controlflow.ControlFlowNode;
import org.evosuite.analysis.staticanalysis.ComplexityAnalyzer;
import org.evosuite.analysis.staticanalysis.NPEAnalysisManager;
import org.evosuite.analysis.staticanalysis.NullableFieldAnalyzer;
import org.evosuite.analysis.staticanalysis.PathAnalyzer;
import org.evosuite.classpath.ClassPathHandler;
import org.evosuite.seeding.ConstantPoolManager;

import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;
import java.util.Stack;
import java.util.stream.Collectors;

import org.evosuite.utils.LoggingUtils;
import org.objectweb.asm.Type;

import spoon.Launcher;
import spoon.reflect.code.CtArrayAccess;
import spoon.reflect.code.CtAssignment;
import spoon.reflect.code.CtBlock;
import spoon.reflect.code.CtCatch;
import spoon.reflect.code.CtCodeElement;
import spoon.reflect.code.CtComment;
import spoon.reflect.code.CtConditional;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtFieldAccess;
import spoon.reflect.code.CtFieldRead;
import spoon.reflect.code.CtFieldWrite;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.code.CtReturn;
import spoon.reflect.code.CtSuperAccess;
import spoon.reflect.code.CtTargetedExpression;
import spoon.reflect.code.CtThisAccess;
import spoon.reflect.code.CtVariableAccess;
import spoon.reflect.code.CtVariableRead;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtConstructor;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtField;
import spoon.reflect.declaration.CtInterface;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtParameter;
import spoon.reflect.declaration.CtVariable;
import spoon.reflect.path.CtRole;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtFieldReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.reference.CtVariableReference;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.support.reflect.code.CtBlockImpl;
import spoon.support.reflect.code.CtCaseImpl;
import spoon.support.reflect.code.CtConditionalImpl;
import spoon.support.reflect.code.CtForEachImpl;
import spoon.support.reflect.code.CtForImpl;
import spoon.support.reflect.code.CtIfImpl;
import spoon.support.reflect.code.CtLocalVariableImpl;
import spoon.support.reflect.code.CtSwitchImpl;
import spoon.support.reflect.code.CtWhileImpl;
import spoon.support.reflect.declaration.CtParameterImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StaticAnalyzer {

    private static final Logger logger = LoggerFactory.getLogger(StaticAnalyzer.class);
    private static final StaticAnalyzer instance = new StaticAnalyzer();

    private static Launcher l;

    private CtClass<?> codeClass;

	Set<CtVariableReference<?>> initialized = new HashSet<>();

    private HashMap<ControlFlowNode, InitFactors> nodeDefUseMap = new HashMap<>();

    public static StaticAnalyzer getInstance() {
        return instance;
    }

	public Set<CtVariableReference<?>> getInitialized() {
		return initialized;
	}

	private class FieldFactors {
        HashSet<ControlFlowNode> written = new HashSet<>();
        HashSet<ControlFlowNode> accessed = new HashSet<>();
	}
    
	private class InitFactors {
		Set<CtVariableReference<?>> defined = null;
		Set<CtVariableReference<?>> used = new HashSet<>();
	}

	public void run(ControlFlowNode node) {
		HashMap<ControlFlowNode, InitFactors> factors = new HashMap<>();
        initialized.clear();

		if (node.getParent() != null) {
			if (node.getParent().findNodesOfKind(BranchKind.BLOCK_END).size() > 0
					|| node.getParent().findNodesOfKind(BranchKind.BLOCK_BEGIN).size() > 0
					|| node.getParent().findNodesOfKind(BranchKind.CONVERGE).size() > 0) {
				throw new RuntimeException("Invalid node types. Simplify the graph with the simplify() method.");
			}
		}

		InitFactors fp = initialized(node, factors, true);
		initialized = fp.defined;
		initialized.addAll(fp.used);

        nodeDefUseMap.put(node, fp);
	}

	private Set<CtVariableReference<?>> defined (ControlFlowNode n) {
		// Obtain the variables defined in this node
		HashSet<CtVariableReference<?>> def = new HashSet<>();
		if (n.getStatement() != null) {
			if (n.getStatement() instanceof CtLocalVariable) {
				CtLocalVariable<?> lv = ((CtLocalVariable<?>) n.getStatement());
				if (lv.getDefaultExpression() != null) {
					def.add(lv.getReference());
				}
			} else if (n.getStatement() instanceof CtAssignment) {
				CtExpression<?> e = ((CtAssignment) n.getStatement()).getAssigned();
				if (e instanceof CtVariableAccess) {
					def.add(((CtVariableAccess<?>) e).getVariable());
				} else if (e instanceof CtArrayAccess) {
					CtExpression<?> exp = ((CtArrayAccess) e).getTarget();
					if (exp instanceof CtVariableAccess) {
						CtVariableReference<?> a = ((CtVariableAccess<?>) exp).getVariable();
						def.add(a);
					} else {
						System.out.println("Could not obtain variable from expression");
					}
				}
			}
		}
		return def;
	}

	private Set<CtVariableReference<?>> used (ControlFlowNode n) {
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
    
	private InitFactors initialized(ControlFlowNode n, HashMap<ControlFlowNode, InitFactors> factors, boolean includeDefinedInNode) {

		if (n.getParent() == null) {
			throw new RuntimeException("The node has no parent");
		}

		Set<CtVariableReference<?>> defN = includeDefinedInNode ? defined(n) : new HashSet<CtVariableReference<?>>();
		Set<CtVariableReference<?>> usedN = includeDefinedInNode ? used(n) : new HashSet<CtVariableReference<?>>();
		usedN.removeAll(defN);

		InitFactors result = new InitFactors();

		for (ControlFlowEdge e : n.getParent().incomingEdgesOf(n)) {
			if (e.isBackEdge()) {
				continue;
			}
			ControlFlowNode p = e.getSourceNode();

			InitFactors fp;
			if (factors.containsKey(p)) {
				fp = factors.get(p);
			} else {
				fp = initialized(p, factors, true);
			}

			//[Def_P for each P ]
			if (result.defined == null) {
				result.defined = new HashSet<>();
				result.defined.addAll(fp.defined);
			} else {
				result.defined.retainAll(fp.defined);
			}

			fp.used.removeAll(fp.defined);
			result.used.addAll(fp.used);
		}

		if (result.defined == null) {
			result.defined = defN;
		} else {
			result.defined.addAll(defN);
		}
		result.used.addAll(usedN);
		result.used.removeAll(result.defined);        
		// result.defined.removeAll(result.used);

		factors.put(n, result);
		return result;
	}    

    private void setMethodParamMap(String methodSig) {
        CtMethod<?> method = ClassInfo.getInstance().getMethodBySig(methodSig);
        if (method == null) return;
        List<CtParameter<?>> methodParamList = method.getParameters();
        MethodInfo.getInstance().setMethodParamMap(methodSig, methodParamList);
    }

    private CtFieldReference<?> usedFieldReference(CtElement ele) {
        List<CtFieldReference<?>> tmpList = ele.getElements(new TypeFilter<>(CtFieldReference.class));

        if (tmpList.size() == 0) return null;

        for (CtFieldReference<?> field : tmpList) {
            if (ClassInfo.getInstance().getFieldInfo().contains(field)) return field;
        }

        return null;
    }

    public boolean hasFieldWrite(String methodKey, CtField<?> field) { 
        CtMethod<?> method = ClassInfo.getInstance().getMethodBySig(methodKey);
    
        return method.filterChildren(new TypeFilter<>(CtFieldWrite.class))
              .select((CtFieldWrite<?> fw) -> fw.getRoleInParent().equals(CtRole.ASSIGNED))
              .list()
              .isEmpty();
      }

    private void updateFieldAccessWriteNodes(String key) {
        ControlFlowGraph graph = ClassInfo.getInstance().getGraphBySig(key);

        FieldFactors fp = new FieldFactors();

        for (ControlFlowNode n : graph.getAllStatementNodes()) {
            if (!n.getStatement().filterChildren(new TypeFilter<>(CtFieldWrite.class))
                .select((CtFieldWrite<?> fw) -> fw.getRoleInParent().equals(CtRole.ASSIGNED)).list().isEmpty())
                fp.written.add(n);

            if (!n.getStatement().filterChildren(new TypeFilter<>(CtFieldAccess.class)).list().isEmpty()) 
                fp.accessed.add(n);
        }

    }
    
    private boolean passingNode(ControlFlowNode node) {

        switch (node.getKind()) {
            case STATEMENT: 
                return false;
            case BRANCH:
                return false;

            default:
                return true;
        }
    }

    private HashSet<ControlFlowNode> getFieldWriteNodes(ControlFlowGraph graph) {
        HashSet<ControlFlowNode> result = new HashSet<>();

        if (graph == null) return result;

        for (ControlFlowNode n : graph.getAllStatementNodes()) {
            
            if (!passingNode(n) && (n.getStatement() == null || 
                n.getStatement().filterChildren(new TypeFilter<>(CtFieldWrite.class))
                .select((CtFieldWrite<?> fw) -> fw.getRoleInParent().equals(CtRole.ASSIGNED)).list().isEmpty()))
                continue;
            
            result.add(n);
        }

        return result;
    }

    private boolean isNPETarget(CtElement ele) {
        if (!hasInvocation(ele)) {
            CtInvocation<?> inv = (CtInvocation<?>) ele.filterChildren(new TypeFilter<>(CtInvocation.class)).list().get(0);
            
            if (inv.getDirectChildren().get(0) instanceof CtLiteral) {
                return false;
            } else if (inv.getDirectChildren().get(0) instanceof CtThisAccess) {
                if (inv.getExecutable() == null) return true;
                String callee;

                try {
                    if (inv.getExecutable().getActualMethod() == null) {
                        callee = inv.getExecutable().getSignature();
                    } else {
                        callee = inv.getExecutable().getActualMethod().getName();
                    }
                } catch (Exception e) {
                    callee = inv.getExecutable().getSignature();
                }

                
                // LoggingUtils.getEvoLogger().info("INVO CALLS BEFORE:" + callee);

                callee = ClassInfo.getInstance().getMethodKey(callee);

                // LoggingUtils.getEvoLogger().info("INVO CALLS:" + callee);

                if (!ClassInfo.getInstance().isInOurMethods(callee)) return true;
                // LoggingUtils.getEvoLogger().info("INVO CALLS DONE:" + callee);

                if (!MethodInfo.getInstance().isAnalyzed(callee)) {
                    // LoggingUtils.getEvoLogger().info("ANALYSIS STARTS");
                    analyzeMethod(callee);
                    // LoggingUtils.getEvoLogger().info("ANALYSIS STARTS DONE:" + Boolean.toString(MethodInfo.getInstance().hasNPENode(callee)));
                }

                return MethodInfo.getInstance().hasNPENode(callee);
                
            } else if (inv.toString().startsWith("java.util.")) return false;
            

            CtExpression<?> exp = inv.getTarget();

            if ((exp instanceof CtVariableAccess && 
                ClassInfo.getInstance().isNonNullFields(((CtVariableAccess<?>)exp).getVariable().getDeclaration()))) {
                
                return false;
            }

            return true;
        }

        return false;
    }

    private boolean isNPETarget(ControlFlowNode node) {
        return isNPETarget(node.getStatement());
    }

    private HashSet<ControlFlowNode> getVariableAccessNodes(ControlFlowGraph graph) {

        HashSet<ControlFlowNode> result = new HashSet<>();

        for (ControlFlowNode n : graph.getAllStatementNodes()) {
            // ASTUtils.printAllEle(n.getStatement());
            
            if (!hasInvocation(n.getStatement())) {
                CtInvocation<?> inv = (CtInvocation<?>) n.getStatement().filterChildren(new TypeFilter<>(CtInvocation.class)).list().get(0);

                if (inv.getDirectChildren().get(0) instanceof CtThisAccess || inv.getDirectChildren().get(0) instanceof CtLiteral) {
                    continue;
                } 

                result.add(n);
            }
        }

        return result;
    }

    
    private HashSet<ControlFlowNode> getFieldAccessNodes(ControlFlowGraph graph) {
        // CtMethod<?> method = ClassInfo.getInstance().getMethodBySig(key);

        HashSet<ControlFlowNode> result = new HashSet<>();

        for (ControlFlowNode n : graph.getAllStatementNodes()) {
            
            if (n.getStatement().filterChildren(new TypeFilter<>(CtFieldAccess.class)).list().isEmpty())
                continue;
            
            result.add(n);
        }

        return result;
    }
    
    
    private List<ControlFlowNode> getFieldWriteNodesInConstr(String key) {
        CtConstructor<?> method = ClassInfo.getInstance().getConstrBySig(key);

        return method.filterChildren(new TypeFilter<>(CtFieldWrite.class))
              .select((CtFieldWrite<?> fw) -> fw.getRoleInParent().equals(CtRole.ASSIGNED))
              .list();
    }

    // update read/written field information of each method
    // flag == 0 : read
    // flag == 1 : write
    private void updateMethodField(String method, CtElement ele, int flag) {
        CtFieldReference<?> usedField = usedFieldReference(ele);

        if (usedField != null) {
            if (flag == 0) MethodInfo.getInstance().addFieldRead(method, usedField);        
            else if (flag == 1) MethodInfo.getInstance().addFieldWrite(method, usedField);        
        }
    }

    private boolean hasBlock(CtElement ele) {
        List<CtBlock<?>> tmpList = ele.filterChildren(new TypeFilter<>(CtBlock.class)).list();
        // ele.filterChildren(new )
        return !(tmpList.isEmpty());
    }

    private void analyzeFieldManager (String sig) {
        CtMethod<?> check = ClassInfo.getInstance().getMethodBySig(sig);

        if (MethodInfo.getInstance().isAnalyzed(sig)) return;

        if (check == null) {
            CtConstructor<?> checkConstr = ClassInfo.getInstance().getConstrBySig(sig);

            if (checkConstr == null) return;

            analyzeParamToFieldConstr(sig);
        } else {
            analyzeParamToField(sig);
        }
    }

    // Analyze whether the field is written or read in the given method.
    private void analyzeFields(String methodSig, ListIterator<CtElement> iter) {
        CtElement next;
        if (iter == null) return;

        while (iter.hasNext()) {
            next = iter.next();
    
            if (next instanceof CtLocalVariable) {
                CtExpression<?> rhs = ((CtLocalVariable<?>) next).getAssignment();
    
                if (rhs == null) continue;

                updateMethodField(methodSig, rhs, 0);
        
            } else if (next instanceof CtAssignment) {
                CtCodeElement rhs = ((CtAssignment<?, ?>) next).getValueByRole(CtRole.ASSIGNMENT);
                CtCodeElement lhs = ((CtAssignment<?, ?>) next).getValueByRole(CtRole.ASSIGNED);

                updateMethodField(methodSig, lhs, 1);                
                updateMethodField(methodSig, rhs, 0);        

            } else if (hasBlock(next)) {
                List<CtBlock<?>> blocks = next.getElements(new TypeFilter<>(CtBlock.class));
                ListIterator<CtElement> tmpIter = blocks.get(0).getDirectChildren().listIterator();

                analyzeFields(methodSig, tmpIter);

            } else if (next instanceof CtInvocation) {
                
                String invoSig= ((CtInvocation<?>)next).getExecutable().getSignature();

                if (MethodInfo.getInstance().isAnalyzed(invoSig)) {
                    analyzeFieldManager(invoSig);
                }
            }
        }

        MethodInfo.getInstance().addAnalyzed(methodSig);
    }

    // Analayze any paramerters that affect the field. 
    private void analyzeParamToField(String methodSig) {
        List<CtParameter<?>> paramList = MethodInfo.getInstance().getMethodParams(methodSig);

        if (paramList.size() == 0) return;

        CtMethod<?> method = ClassInfo.getInstance().getMethodBySig(methodSig);

        if (method == null) return;

        ListIterator<CtElement> iter = null;

        try {
            iter = method.getBody().getDirectChildren().listIterator();
        } catch (Exception e) {
            LoggingUtils.getEvoLogger().info("CATCH EXCEPTION: " + e.toString());
            return;
        }

        analyzeFields(methodSig, iter);
    }

    private Stack<ControlFlowNode> getDefUsePath(ControlFlowGraph graph, ControlFlowNode targetNode) {
        Set<CtVariableReference<?>> usedVars = used(targetNode);

        Set<ControlFlowNode> nodes = graph.getAllNodes();

        for (ControlFlowNode n : nodes) {
            if (defined(n).stream().anyMatch(t -> ClassInfo.getInstance().getFieldInfo().contains(t))) {
                                
            }
        }
        

        return null;
    }

    private List<CtVariableReference<?>> paramToVariable(List<CtParameter<?>> params) {
        List<CtVariableReference<?>> tmpList = new ArrayList();

        return tmpList;
    }

    private void analyzeParamToFieldConstr(String constrSig) {
        ControlFlowGraph graph = ClassInfo.getInstance().getGraphBySig(constrSig);
        CtConstructor<?> targetConstr = ClassInfo.getInstance().getConstrBySig(constrSig);

        if (targetConstr == null) return;

        List<CtParameter<?>> paramList = targetConstr.getParameters();

        if (paramList.size() == 0) return;

        HashSet<ControlFlowNode> fieldWriteNodes = getFieldWriteNodes(graph);

        if (fieldWriteNodes.isEmpty()) return;

        for (CtParameter<?> pp : paramList) {
            // ASTUtils.printAllEle(pp);
        }

        for (ControlFlowNode n : fieldWriteNodes) {
            Set<CtVariableReference<?>> tmpNode = used(n);
            tmpNode.removeAll(defined(n));
            
            // HashSet<Stack<ControlFlowNode>> paths = PathBuilder.getInstance().buildDefUsePathByNode(constrSig, n);

            // for (Stack<ControlFlowNode> path : paths) {
            //     LoggingUtils.getEvoLogger().info(path.toString());
            // }

            for (CtVariableReference<?> vv : tmpNode) {
                if (vv.getDeclaration() instanceof CtParameter) {
                    int index = paramList.indexOf(vv.getDeclaration());

                    if (index == -1) continue;

                    MethodInfo.getInstance().addTargetParam(constrSig, index);
                }
            }
        }
    }
    
    private void analyzeParamToFieldMethod(String constrSig) {
        ControlFlowGraph graph = ClassInfo.getInstance().getGraphBySig(constrSig);

        HashSet<ControlFlowNode> fieldWriteNodes = getFieldWriteNodes(graph);

        if (fieldWriteNodes.isEmpty()) return;

        for (ControlFlowNode n : fieldWriteNodes) {
            if (passingNode(n)) continue;
            Set<CtVariableReference<?>> tmpNode = used(n);
        }

    }

    // update the index of the parameters (MUTATING TARGET PARAMS)
    private void trackVariable(String methodSig, CtVariableRead<?> targetEle) {
        CtVariable<?> decLocalVar = targetEle.getVariable().getDeclaration();
        List<CtParameter<?>> paramList = MethodInfo.getInstance().getMethodParams(methodSig);

        if (decLocalVar instanceof CtLocalVariableImpl) {
            CtExpression<?> rhs = ((CtLocalVariable<?>) decLocalVar).getAssignment();

            if (rhs == null || rhs instanceof CtLiteral) return;

            if (rhs instanceof CtVariableRead) {
                trackVariable(methodSig, (CtVariableRead<?>)rhs);
            } 
            else {
                List<CtVariableRead<?>> tmpList = rhs.filterChildren(new TypeFilter<>(CtVariableRead.class)).list();

                if (tmpList != null || !(tmpList.isEmpty())) {
                    for (CtVariableRead<?> tmpVar : tmpList) {
                        trackVariable(methodSig, tmpVar);
                    }
                }
            }

            return;

        } else if (decLocalVar instanceof CtParameterImpl) {
            int index = paramList.indexOf(decLocalVar);
            MethodInfo.getInstance().addTargetParam(methodSig, index);
            
        } else if (decLocalVar instanceof CtField) {
                CtFieldReference<?> usedField = usedFieldReference(targetEle);
                if (usedField != null) {
                    MethodInfo.getInstance().addTargetField(methodSig, usedField);
                }
        }
    }

    public static boolean hasVariableAccess(CtMethod<?> method, CtVariable<?> lv) {
        return method.filterChildren(new TypeFilter<>(CtTargetedExpression.class))
                .select((CtTargetedExpression<?, ?> expr) ->
                        expr.getTarget() instanceof CtVariableAccess &&
                                lv.equals(((CtVariableAccess<?>) expr.getTarget()).getVariable().getDeclaration()))
                .list()
                .isEmpty();
    }

    private void handleConditions(String methodSig, List<CtElement> eleList) {
        if (eleList == null || eleList.isEmpty()) return;

        for (CtElement ele : eleList) {
            List<CtElement> children = ele.getDirectChildren();

            if (children.stream().filter(e -> e instanceof CtCaseImpl).count() > 0) {
                List<CtElement> newList = children.stream().filter(e -> !(e instanceof CtCaseImpl)).collect(Collectors.toList());

                List<CtVariableRead<?>> varList = new ArrayList<>();

                for (CtElement e : newList) {
                    varList.addAll(e.filterChildren(new TypeFilter<>(CtVariableRead.class)).list());
                }

                for (CtVariableRead<?> gg : varList) {
                    trackVariable(methodSig, gg);
                }
            } else {
                List<CtElement> newList = children.stream().filter(e -> !(e instanceof CtBlockImpl)).collect(Collectors.toList());

                List<CtVariableRead<?>> varList = new ArrayList<>();

                for (CtElement e : newList) {
                    varList.addAll(e.filterChildren(new TypeFilter<>(CtVariableRead.class)).list());
                }

                for (CtVariableRead<?> gg : varList) {

                    trackVariable(methodSig, gg);

                }
            }
        }

    }

    private boolean hasInvocation(CtElement ele) {
        List<CtElement> mayNPEStmts = new ArrayList();

        try {
            mayNPEStmts = ele.filterChildren(new TypeFilter<>(CtInvocation.class))
                .select((CtInvocation<?> call) -> !(call instanceof CtAssignment) || !(call instanceof CtLocalVariable))
                .select((CtInvocation<?> call) -> !(call.getParent().getParent() instanceof CtCatch))
                .select((CtInvocation<?> call) -> !(call.getExecutable() == null))
                .list();

        } catch (Exception e) {
            logger.debug(e.toString());
        }

        return mayNPEStmts.isEmpty();
    }

    private void analyzeReturns(String methodKey) {
        MethodInfo.getInstance().updateAnalyzedReturns(methodKey);

        if (buildMethodReturnPaths(methodKey) == 0) {
          return;
        }

        HashSet<Stack<ControlFlowNode>> returnPaths = MethodInfo.getInstance().getReturnPaths(methodKey);

        for (Stack<ControlFlowNode> path : returnPaths) {
            
            MethodInfo.getInstance().setReturnNull(methodKey, PathAnalyzer.checkReturnPaths(methodKey, path));
        }
        
    }

    private boolean checkConditionalNull(CtElement ele) {
        CtExpression<?> elseExp = ((CtConditional<?>) ele).getElseExpression();
        CtExpression<?> thenExp = ((CtConditional<?>) ele).getThenExpression();

        if (TypeUtils.isNull(elseExp.getType()) || TypeUtils.isNull(thenExp.getType())) {
            return true;
        } 

        if (elseExp instanceof CtInvocation) {
            String exeSig = ((CtInvocation<?>) elseExp).getExecutable().getSignature();
            if (!MethodInfo.getInstance().isReturnAnalyzed(exeSig)) 
                analyzeReturns(exeSig);

            return MethodInfo.getInstance().isReturnNull(exeSig);

        } else if (thenExp instanceof CtInvocation) {
            String exeSig = ((CtInvocation<?>) thenExp).getExecutable().getSignature();

            if (!MethodInfo.getInstance().isReturnAnalyzed(exeSig)) 
                analyzeReturns(exeSig);

            return MethodInfo.getInstance().isReturnNull(exeSig);
        }

        return false;
    }

        
    private int buildMethodReturnPaths(String methodKey) {
        CtMethod<?> method = ClassInfo.getInstance().getMethodBySig(methodKey);

        if (method == null) return 0;

        CtTypeReference<?> returnType = method.getType();

        // Given methods does not have return value    
        if (TypeUtils.isVoidPrimitive(returnType) || TypeUtils.isPrimitive(returnType)) {
            MethodInfo.getInstance().setReturnNull(methodKey, false);
            return 0;
        }

        List<CtReturn<?>> returnEles = method.filterChildren(new TypeFilter<>(CtReturn.class))
                                            .select(e -> ((CtReturn<?>)e).getReturnedExpression() != null)
                                            .select(e -> !TypeUtils.isNull(((CtReturn<?>)e).getReturnedExpression().getType()))
                                            .select(e -> ((CtReturn<?>)e).filterChildren(new TypeFilter<>(CtConditional.class))
                                                                        .select(e1 -> checkConditionalNull(e1)).list().size() == 0
                                                    )
                                            .list();    
        
        // check whether there exists any return stmt which directly returns null
        if (returnEles.isEmpty() ||
            returnEles.stream().filter(e -> TypeUtils.isNull(e.getReturnedExpression().getType())).count() != 0 ||
            // returnEles.stream().filter(e -> e.filterChildren(new TypeFilter()))
            returnEles.stream().filter(e -> e instanceof CtInvocation).count() != 0) {

            MethodInfo.getInstance().setReturnNull(methodKey, true);
            return 0;
        }

        HashSet<Stack<ControlFlowNode>> defPaths = new HashSet<>();

        // Remove return stmts which directly returns null
        ControlFlowGraph graph = ClassInfo.getInstance().getGraphBySig(methodKey);

        if (graph == null) return 0;

        HashSet<ControlFlowNode> returnNodes = graph.getReturnNodes();

        for (ControlFlowNode ret : returnNodes) {
            if (isNPETarget(ret)) continue;

            Set<CtVariableReference<?>> usedVar = used(ret);

            defPaths.addAll(PathBuilder.getInstance().buildDefUsePathByNode(methodKey, ret));
        }

        MethodInfo.getInstance().setReturnPaths(methodKey, defPaths);

        return 1;
    }

    private int buildMayNPEPaths(String methodKey, ControlFlowNode node) {
        int result = 0;

        HashSet<Stack<ControlFlowNode>> paths = PathBuilder.getInstance().buildDefUsePathByNode(methodKey, node);

        for (Stack<ControlFlowNode> path : paths) {
            // LoggingUtils.getEvoLogger().info("PATH INFOS");
            // LoggingUtils.getEvoLogger().info(path.toString());
            result += PathAnalyzer.propagatePath(methodKey, path);
        }

        return result;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private void analysisNPEMethod(String methodKey) {
        ControlFlowGraph graph = ClassInfo.getInstance().getGraphBySig(methodKey);
        int numNPEPath = 0;
            
        float complexity = ComplexityAnalyzer.getInstance().getScore(methodKey);

        if (graph == null) {
            CtMethod<?> method = ClassInfo.getInstance().getMethodBySig(methodKey);

            if (method == null) return;

            List<CtInvocation<?>> invList = method.filterChildren(new TypeFilter(CtInvocation.class)).list();

            if (invList.stream().filter(e -> isNPETarget(e)).count() > 0) ClassInfo.getInstance().addTargetMethods(methodKey);

            return;
        }
        
        for (ControlFlowNode n : graph.getAllStatementNodes()) {

            if (isNPETarget(n)) {      

                if (!(complexity > 15)) {                
                    numNPEPath = buildMayNPEPaths(methodKey, n);

                    if (numNPEPath == 0) {
                        continue;
                    }

                    MethodInfo.getInstance().addMayNPENode(methodKey, n, numNPEPath);  
                    ClassInfo.getInstance().updateNPELineInfo(methodKey, n.getStatement().getPosition().getLine());
                } else {
                    MethodInfo.getInstance().addMayNPENode(methodKey, n, (int)ComplexityAnalyzer.getInstance().getScore(methodKey)/2);  
                    ClassInfo.getInstance().updateNPELineInfo(methodKey, n.getStatement().getPosition().getLine());
                }
            }
        }
        
        // if (MethodInfo.getInstance().hasNPENode(methodKey) && MethodInfo.getInstance().isPublic(methodKey)) {
        if (MethodInfo.getInstance().hasNPENode(methodKey) && (MethodInfo.getInstance().isPublic(methodKey) || MethodInfo.getInstance().isProtected(methodKey))) {
            ClassInfo.getInstance().addTargetMethods(methodKey);
        }

        return;
    }

    private void reachableNodes(String methodKey) {
        HashSet<ControlFlowNode> npeNodes = MethodInfo.getInstance().getNPENodes(methodKey);

        if (npeNodes == null || npeNodes.isEmpty()) return;

        for (ControlFlowNode n : npeNodes) {
            HashSet<ControlFlowNode> reachableNodes = GraphManager.getInstance().getReachableNodes(methodKey, n);

            if (reachableNodes == null || reachableNodes.isEmpty()) continue;

            for (ControlFlowNode nn : reachableNodes) {
                if (passingNode(nn) || nn.getStatement() == null) continue;

                List<CtLiteral<?>> tmpLiterals = nn.getStatement().filterChildren(new TypeFilter<>(CtLiteral.class)).list();

                for (CtLiteral<?> lit : tmpLiterals) {
                    
                    ConstantPoolManager.getInstance().updateNPEConstantPools(methodKey, lit);
                }
            }
        }
    }

    // Update the Class Information including CFGs for each method/constructor.
    public void setup() {
        String oriPath = Properties.TARGET_DIR;

        String oriClass = Properties.TARGET_CLASS;
        oriClass = oriClass.replace(".","/") + ".java";

        String classPath = oriPath + "/src/main/java";

        String targetClass = classPath + "/" + oriClass;

        File file = new File(targetClass);
        if (!file.exists()) {
            classPath = oriPath + "/src";
            targetClass = classPath + "/" + oriClass;
        }

        file = new File(targetClass);

        if (!file.exists()) {
            classPath = oriPath;
            targetClass = classPath + "/" + oriClass;
        }

        l = new Launcher();
        l.addInputResource(targetClass);
        l.buildModel();

        TypeUtils.setup(l.getFactory());

        List<CtClass<?>> tmpClass = l.getFactory().Package().getRootPackage().getElements(new TypeFilter<>(CtClass.class));
        if (tmpClass == null || tmpClass.isEmpty()) {
            codeClass = (CtClass<?>) l.getFactory().Package().getRootPackage().getElements(new TypeFilter<>(CtInterface.class)).get(0);
        } else {
            codeClass = tmpClass.get(0);
        }
        
        // Update initial information for the given class 
        // It includes the set of method/constructors and the fields.         
        ClassInfo.getInstance().setup(codeClass);
        ClassInfo.getInstance().initSet();
        ClassInfo.getInstance().setClassFields();
        ClassInfo.getInstance().buildCfgs();
    }

    private void analyzeMethod(String methodSig) {
        if (!ClassInfo.getInstance().isInOurMethods(methodSig) || MethodInfo.getInstance().isAnalyzed(methodSig)) return;

        MethodInfo.getInstance().addAnalyzed(methodSig);

        ComplexityAnalyzer.getInstance().analyze(methodSig);
        MethodInfo.getInstance().updateComplexity(methodSig, ComplexityAnalyzer.getInstance().getScore(methodSig));

        analyzeReturns(methodSig);

        analysisNPEMethod(methodSig);

        reachableNodes(methodSig);
    }

    public void analyze() {
        // First update the nullable fields in the given class
        NullableFieldAnalyzer.getInstance().analyze();

        for (String constrSig: ClassInfo.getInstance().getAllConstrs() ) {
            analyzeParamToFieldConstr(constrSig);
        }

        for (String methodSig : ClassInfo.getInstance().getAllMethods()) {
            analyzeMethod(methodSig);
        }

        MethodInfo.getInstance().updateMethodScore();
    }
}
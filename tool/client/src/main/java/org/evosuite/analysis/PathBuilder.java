package org.evosuite.analysis;

import static org.mockito.Mockito.verify;

import java.util.HashSet;
import java.util.Set;
import java.util.Stack;

import org.evosuite.analysis.controlflow.BranchKind;
import org.evosuite.analysis.controlflow.ControlFlowEdge;
import org.evosuite.analysis.controlflow.ControlFlowGraph;
import org.evosuite.analysis.controlflow.ControlFlowNode;
import org.evosuite.analysis.controlflow.NotFoundException;
import org.evosuite.utils.LoggingUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import spoon.reflect.code.CtArrayAccess;
import spoon.reflect.code.CtAssignment;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtIf;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.code.CtVariableAccess;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.reference.CtVariableReference;
import spoon.reflect.visitor.filter.TypeFilter;


public class PathBuilder {

    private static final Logger logger = LoggerFactory.getLogger(PathBuilder.class);

    private static final PathBuilder instance = new PathBuilder();

    private HashSet<Stack<ControlFlowNode>> backPathSet = new HashSet<>();
    private HashSet<Stack<ControlFlowNode>> forwardPathSet = new HashSet<>();

    // private HashMap<CtElement, HashSet<Stack<ControlFlowNode>>> pathMap = new HashMap<>();

    private static ControlFlowNode lastNode;

    
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


    public static PathBuilder getInstance() {
        return instance;
    }

    public void setLastNode(String methodSig, CtElement ele) {
        ControlFlowGraph graph = GraphManager.getInstance().buildMethodCfgBySig(methodSig);

        try {
            lastNode = graph.findNode(ele);
        } catch (NotFoundException e) {
            // TODO Auto-generated catch block
            lastNode = graph.getExitNode();
        }
    }

    private static boolean checkEnd(ControlFlowNode node) {
        if (lastNode != null && node.getId() == lastNode.getId()) return true;

        switch (node.getKind()) {
            case BEGIN:
                return true;
            case LOOPENTRY:
                return true;
            case BLOCK_END:
                return false;
            case STATEMENT:
                return false;
            default:
                return false;
        }
    }


    public Stack<ControlFlowNode> buildPathForward(ControlFlowNode startNode) {
        Set<ControlFlowNode> visited = new HashSet<>();
        Stack<ControlFlowNode> stack = new Stack<>();

        if (startNode.getParent() == null) {
            return null;
        }
        ControlFlowGraph graph = startNode.getParent();

        stack.push(startNode);
        do {
            ControlFlowNode n = stack.pop();

            //Finished when we arrive at the node
            //and the stack is empty
            if (n.equals(graph.findNodesOfKind(BranchKind.EXIT).get(0)) && stack.empty()) return stack; 

            //Skip this node if we have already visited
            if (n.getKind().equals(BranchKind.BEGIN)) continue;
            if (visited.contains(n)) continue;
            else visited.add(n);

            //Visit forward
            for (ControlFlowEdge e : graph.outgoingEdgesOf(n)) {
                n = e.getTargetNode();
                if (n.equals(startNode)) {
                    forwardPathSet.add(stack);
                    return stack;
                }
                stack.push(n);
            }
        } while (!stack.empty());

        return stack;
    }
    
    private boolean passingNode(ControlFlowNode node) {

        switch (node.getKind()) {
            case LOOPENTRY:
                return true;
            case LOOPEXIT:
                return true;
            case BRANCH:
                return true;
            case FALSE:
                return true;
            case TRUE:
                return true;
            default:
                return false;
        }
    }
    
	private void buildDefUsePath(ControlFlowNode startNode, Stack<ControlFlowNode> prevStack, Stack<ControlFlowNode> prevVisited, Set<CtVariableReference<?>> relatedVars) {
		if (startNode.getParent() == null) {
            LoggingUtils.getEvoLogger().info("PARENT IS NULL + " + startNode.toString());
			return;
		}

		ControlFlowGraph graph = startNode.getParent();
        Stack<ControlFlowNode> stack = new Stack<>();
        Stack<ControlFlowNode> visited = new Stack<>();        

        stack.addAll(prevStack);
        visited.addAll(prevVisited);
        
        // Unrolling any loops once.
        // if (visited.contains(startNode)) {
        //     for (ControlFlowEdge e : graph.incomingEdgesOf(startNode)) {
        //         ControlFlowNode n = e.getSourceNode();

        //         if (visited.contains(n)) continue;

        //         if (relatedVars.stream().anyMatch(v -> defined(n).contains(v))) {
        //             stack.push(n);
        //         }
        //         // visited.push(n);
        //         buildDefUsePath(n, stack, visited, relatedVars);
        //     }

        // } else visited.push(startNode);


        if (checkEnd(startNode)) {
            backPathSet.add(stack);
            return;
        }


        for (ControlFlowEdge e : graph.incomingEdgesOf(startNode)) {
            ControlFlowNode n = e.getSourceNode();

            // stack a new node
            if (relatedVars.stream().anyMatch(v -> defined(n).contains(v)) || 
                (n.getKind().equals(BranchKind.BRANCH) && relatedVars.stream().anyMatch(v -> used(n).contains(v))) ) {
                relatedVars.addAll(used(n));
                stack.push(n);
                
                Stack<ControlFlowNode> stackss = new Stack<>();
                stackss.addAll(stack);
            } else if (passingNode(n)) stack.push(n);


            visited.push(n);

            buildDefUsePath(n, stack, visited, relatedVars);

            visited.pop();
        }
    }    
    
	private void buildPathBackward(ControlFlowNode startNode, Stack<ControlFlowNode> prevStack, Set<ControlFlowNode> prevVisited) {
		if (startNode.getParent() == null) {
            LoggingUtils.getEvoLogger().info("PARENT IS NULL + " + startNode.toString());
			return;
		}

		ControlFlowGraph graph = startNode.getParent();
        Stack<ControlFlowNode> stack = new Stack<>();
        Set<ControlFlowNode> visited = new HashSet<>();        

        stack.addAll(prevStack);
        visited.addAll(prevVisited);

        // Unrolling any loops once.
        if (visited.contains(startNode)) {
            for (ControlFlowEdge e : graph.incomingEdgesOf(startNode)) {
                ControlFlowNode n = e.getSourceNode();

                if (visited.contains(n)) continue;
                
                stack.push(n);
                buildPathBackward(n, stack, visited);
            }

        } else visited.add(startNode);

        if (checkEnd(startNode)) {
            backPathSet.add(stack);
            return;
        }

        for (ControlFlowEdge e : graph.incomingEdgesOf(startNode)) {
            ControlFlowNode n = e.getSourceNode();
            // LoggingUtils.getEvoLogger().info(e.toString());
            
            // stack a new node
            stack.push(n);

            buildPathBackward(n, stack, visited);
            stack.pop();
        }
    }    
    
    private void buildPathBackward(ControlFlowNode startNode, ControlFlowNode exitNode, Stack<ControlFlowNode> prevStack, Set<ControlFlowNode> prevVisited) {
		if (startNode.getParent() == null) {
            LoggingUtils.getEvoLogger().info("PARENT IS NULL + " + startNode.toString());
			return;
		}

		ControlFlowGraph graph = startNode.getParent();
        Stack<ControlFlowNode> stack = new Stack<>();
        Set<ControlFlowNode> visited = new HashSet<>();        

        stack.addAll(prevStack);
        visited.addAll(prevVisited);

        // Unrolling any loops once.
        if (visited.contains(startNode)) {
            for (ControlFlowEdge e : graph.incomingEdgesOf(startNode)) {
                ControlFlowNode n = e.getSourceNode();

                if (visited.contains(n)) continue;
                
                stack.push(n);
                buildPathBackward(n, stack, visited);
            }

        } else visited.add(startNode);

        if (checkEnd(startNode) || startNode.equals(exitNode)) {
            backPathSet.add(stack);
            return;
        }

        for (ControlFlowEdge e : graph.incomingEdgesOf(startNode)) {
            ControlFlowNode n = e.getSourceNode();
            // LoggingUtils.getEvoLogger().info(e.toString());
            
            // stack a new node
            stack.push(n);

            buildPathBackward(n, exitNode, stack, visited);
            stack.pop();
        }
    }    

    public HashSet<Stack<ControlFlowNode>> testBackPath (ControlFlowNode startNode) {
        Stack<ControlFlowNode> stack = new Stack<>();
        Set<ControlFlowNode> visited = new HashSet<>();


        stack.push(startNode);
        buildPathBackward(startNode, stack, visited);

        // pathMap.put(startEle, pathSet);

        return backPathSet;
    }

    public void buildBackPathByNode (ControlFlowNode startNode) {
        Stack<ControlFlowNode> stack = new Stack<>();
        Set<ControlFlowNode> visited = new HashSet<>();
        
        stack.push(startNode);
        buildPathBackward(startNode, stack, visited);
        
        // pathMap.put(startEle, pathSet);

        backPathSet.clear();
    }

    

    public HashSet<Stack<ControlFlowNode>> buildBackPathReturnByNode(ControlFlowNode startNode) {
        if (!backPathSet.isEmpty()) backPathSet.clear();
        
        // ControlFlowGraph graph = GraphManager.getInstance().buildMethodCfgBySig(methodSig);

        Stack<ControlFlowNode> stack = new Stack<>();

        Set<ControlFlowNode> visited = new HashSet<>();
        buildPathBackward(startNode, stack, visited);

        // backPathSet.clear();
        return backPathSet;
 
    }    

    public HashSet<Stack<ControlFlowNode>> buildBackPathReturn(String methodSig) {
        if (!backPathSet.isEmpty()) backPathSet.clear();
        
        // ControlFlowGraph graph = GraphManager.getInstance().buildMethodCfgBySig(methodSig);
        ControlFlowGraph graph = ClassInfo.getInstance().getGraphBySig(methodSig);

        ControlFlowNode targetNode = graph.getExitNode();

        Stack<ControlFlowNode> stack = new Stack<>();

        Set<ControlFlowNode> visited = new HashSet<>();
        buildPathBackward(targetNode, stack, visited);

        // backPathSet.clear();
        return backPathSet;
 
    }

    
    public HashSet<Stack<ControlFlowNode>> buildDefUsePathByEle(String methodSig, CtElement startEle) {
        if (!backPathSet.isEmpty()) backPathSet.clear();

        ControlFlowGraph graph = ClassInfo.getInstance().getGraphBySig(methodSig);
        lastNode = null;

        try {
            ControlFlowNode targetNode = graph.findNode(startEle);
            Stack<ControlFlowNode> stack = new Stack<>();

            Stack<ControlFlowNode> visited = new Stack<>();
            Set<CtVariableReference<?>> relatedVars = new HashSet<>(used(targetNode));
            relatedVars.removeAll(defined(targetNode));

            stack.push(targetNode);
            buildDefUsePath(targetNode, stack, visited, relatedVars);
 
            // pathMap.put(startEle, backPathSet);

        } catch (NotFoundException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        // backPathSet.clear();
        return backPathSet;
    }

    
    public HashSet<Stack<ControlFlowNode>> buildDefUsePathByNode(String methodSig, ControlFlowNode targetNode) {
        if (!backPathSet.isEmpty()) backPathSet.clear();

        lastNode = null;

        Stack<ControlFlowNode> stack = new Stack<>();

        Stack<ControlFlowNode> visited = new Stack<>();
        Set<CtVariableReference<?>> relatedVars = new HashSet<>(used(targetNode));
        relatedVars.removeAll(defined(targetNode));

        stack.push(targetNode);
        buildDefUsePath(targetNode, stack, visited, relatedVars);
 
            // pathMap.put(startEle, backPathSet);

        // backPathSet.clear();
        return backPathSet;
    }




    public HashSet<Stack<ControlFlowNode>> buildBackPathByEle(String methodSig, CtElement startEle) {
        if (!backPathSet.isEmpty()) backPathSet.clear();

        ControlFlowGraph graph = ClassInfo.getInstance().getGraphBySig(methodSig);
        lastNode = null;

        try {
            ControlFlowNode targetNode = graph.findNode(startEle);
            Stack<ControlFlowNode> stack = new Stack<>();

            Set<ControlFlowNode> visited = new HashSet<>();

            stack.push(targetNode);
            buildPathBackward(targetNode, stack, visited);
 
            // pathMap.put(startEle, backPathSet);

        } catch (NotFoundException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        // backPathSet.clear();
        return backPathSet;
    }
    
    public HashSet<Stack<ControlFlowNode>> buildBackPathByEle(String methodSig, CtElement startEle, CtElement last) {
        if (!backPathSet.isEmpty()) backPathSet.clear();

        ControlFlowGraph graph = ClassInfo.getInstance().getGraphBySig(methodSig);

        try {
            ControlFlowNode targetNode = graph.findNode(startEle);
            lastNode = graph.findNode(last);

            Stack<ControlFlowNode> stack = new Stack<>();

            Set<ControlFlowNode> visited = new HashSet<>();

            stack.push(targetNode);
            buildPathBackward(targetNode, stack, visited);
 
            // pathMap.put(startEle, backPathSet);

        } catch (NotFoundException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        // backPathSet.clear();
        return backPathSet;
    }
}
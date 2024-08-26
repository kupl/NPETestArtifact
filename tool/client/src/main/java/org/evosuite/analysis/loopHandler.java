package org.evosuite.analysis;

import java.util.HashMap;
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

import spoon.reflect.declaration.CtElement;


public class loopHandler {

    private static final Logger logger = LoggerFactory.getLogger(loopHandler.class);

    private static final loopHandler instance = new loopHandler();    

    private HashMap<String, ControlFlowGraph> loopMap = new HashMap<String, ControlFlowGraph>();

    private HashSet<Stack<ControlFlowNode>> backPathSet = new HashSet<>();
    private HashSet<Stack<ControlFlowNode>> forwardPathSet = new HashSet<>();

    private HashMap<CtElement, HashSet<Stack<ControlFlowNode>>> pathMap = new HashMap<>();


    public static loopHandler getInstance() {
        return instance;
    }

    private static boolean checkEnd(ControlFlowNode node) {
        switch (node.getKind()) {
            case BEGIN:
                return true;
            case BLOCK_BEGIN:
                return true;
            case BLOCK_END:
                return false;
            case BRANCH:
                return true;
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

        stack.push(graph.findNodesOfKind(BranchKind.BEGIN).get(0));
        do {
            ControlFlowNode n = stack.pop();

            //Finished when we arrive at the node
            //and the stack is empty
            if (n.equals(startNode) && stack.empty()) return stack; 

            //Skip this node if we have already visited
            if (n.getKind().equals(BranchKind.BEGIN)) continue;
            if (visited.contains(n)) continue;
            else visited.add(n);

            //Visit the node

            //Visit forward
            for (ControlFlowEdge e : graph.outgoingEdgesOf(n)) {
                n = e.getTargetNode();
                if (n.equals(startNode)) return stack;
                stack.push(n);
            }
        } while (!stack.empty());

        return stack;
    }
    
	public static Stack<ControlFlowNode> buildPathBackward(ControlFlowNode startNode, ControlFlowNode endNode) {

		Set<ControlFlowNode> visited = new HashSet<>();
		Stack<ControlFlowNode> stack = new Stack<>();

		if (startNode.getParent() == null) {
			return null;
		}
		ControlFlowGraph graph = startNode.getParent();

		stack.push(graph.findNodesOfKind(BranchKind.BEGIN).get(0));
		do {
			ControlFlowNode n = stack.pop();

			//Don't do anything on the begin node
			if (n.getKind().equals(BranchKind.BEGIN)) continue;

			//Skip this node if we have already visited
			if (visited.contains(n)) continue;
			else visited.add(n);

			if ( checkEnd(n) || n.equals(endNode)) return stack;

			//Visit backwards
			for (ControlFlowEdge e : graph.incomingEdgesOf(n)) {
				n = e.getSourceNode();
				stack.push(n);
			}
		} while (!stack.empty());

        return stack;
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
            LoggingUtils.getEvoLogger().info("LAST STACK LENGTH:" + Integer.toString(stack.size()));

            LoggingUtils.getEvoLogger().info(stack.toString());
            backPathSet.add(stack);
            return;
        }

        // if (graph.incomingEdgesOf(startNode).size() > 1) {
        //     LoggingUtils.getEvoLogger().info("INCOMING EDGES TEST " + startNode.toString());
        //     LoggingUtils.getEvoLogger().info(graph.incomingEdgesOf(startNode).toString());
        // }

        for (ControlFlowEdge e : graph.incomingEdgesOf(startNode)) {
            ControlFlowNode n = e.getSourceNode();
            LoggingUtils.getEvoLogger().info(e.toString());
            
            // stack a new node
            stack.push(n);

            buildPathBackward(n, stack, visited);
        }
    }

    public HashMap<CtElement, HashSet<Stack<ControlFlowNode>>> getPathMap() {
        return pathMap;
    } 
    
    public HashSet<Stack<ControlFlowNode>> getPathSetByEle(CtElement ele) {
        if (pathMap.containsKey(ele)) return pathMap.get(ele);

        return null;
    } 

    public HashSet<Stack<ControlFlowNode>> testBackPath (ControlFlowNode startNode) {
        Stack<ControlFlowNode> stack = new Stack<>();
        Set<ControlFlowNode> visited = new HashSet<>();

        LoggingUtils.getEvoLogger().info("STARTNODE:" + startNode.toString());

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

    public void buildPathByEle(String sig, CtElement startEle) {
        ControlFlowGraph graph = MethodInfo.getInstance().getCfgBySig(sig);

        try {
            ControlFlowNode targetNode = graph.findNode(startEle);
            Stack<ControlFlowNode> stack = new Stack<>();

            Set<ControlFlowNode> visited = new HashSet<>();
            buildPathBackward(targetNode, stack, visited);

            pathMap.put(startEle, backPathSet);

        } catch (NotFoundException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        backPathSet.clear();
    }
}
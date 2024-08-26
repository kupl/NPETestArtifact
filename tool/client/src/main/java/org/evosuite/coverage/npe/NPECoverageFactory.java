/*
 * Copyright (C) 2010-2018 Gordon Fraser, Andrea Arcuri and EvoSuite
 * contributors
 *
 * This file is part of EvoSuite.
 *
 * EvoSuite is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation, either version 3.0 of the License, or
 * (at your option) any later version.
 *
 * EvoSuite is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with EvoSuite. If not, see <http://www.gnu.org/licenses/>.
 */
package org.evosuite.coverage.npe;

import org.evosuite.Properties;
import org.evosuite.analysis.ClassInfo;
import org.evosuite.analysis.MethodInfo;
import org.evosuite.analysis.controlflow.ControlFlowNode;
import org.evosuite.coverage.line.LineCoverageTestFitness;
import org.evosuite.instrumentation.LinePool;
import org.evosuite.seeding.ConstantPoolManager;
import org.evosuite.testcase.TestFitnessFunction;
import org.evosuite.testsuite.AbstractFitnessFactory;
import org.evosuite.utils.LoggingUtils;

import spoon.reflect.code.CtLiteral;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Coverage factory for observed exceptions
 *
 * @author Gordon Fraser, Jose Miguel Rojas
 */
public class NPECoverageFactory extends AbstractFitnessFactory<NPECoverageTestFitness> {

    private static final Map<String, NPECoverageTestFitness> goals = new LinkedHashMap<>();

    public static Map<String, NPECoverageTestFitness> getGoals() {
        return goals;
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public List<NPECoverageTestFitness> getCoverageGoals() {
        List<NPECoverageTestFitness> goals = new ArrayList<>();

        if (!ClassInfo.getInstance().getTargetMethods().isEmpty()) {
            for (String methodKey : ClassInfo.getInstance().getTargetMethods()) {
                HashSet<ControlFlowNode> npeNodes = MethodInfo.getInstance().getNPENodes(methodKey);

                for (ControlFlowNode node : npeNodes) {

                    CtElement tmp = node.getStatement();
                    String line = tmp.getPosition().toString();

                    goals.add(new NPECoverageTestFitness(Properties.TARGET_CLASS, methodKey, line, false));
                }

            }
        }

        return goals;
    }
}

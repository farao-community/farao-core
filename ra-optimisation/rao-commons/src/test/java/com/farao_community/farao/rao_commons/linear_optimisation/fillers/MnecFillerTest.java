/*
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.farao_community.farao.rao_commons.linear_optimisation.fillers;

import com.farao_community.farao.commons.Unit;
import com.farao_community.farao.data.crac_api.Instant;
import com.farao_community.farao.data.crac_api.cnec.BranchCnec;
import com.farao_community.farao.data.crac_api.cnec.Cnec;
import com.farao_community.farao.data.crac_api.threshold.BranchThresholdRule;
import com.farao_community.farao.data.crac_util.CracCleaner;
import com.farao_community.farao.rao_commons.RaoInputHelper;
import com.farao_community.farao.rao_commons.linear_optimisation.LinearProblem;
import com.farao_community.farao.rao_api.parameters.MnecParameters;
import com.google.ortools.linearsolver.MPConstraint;
import com.google.ortools.linearsolver.MPVariable;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.modules.junit4.PowerMockRunner;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.*;


/**
 * @author Peter Mitri {@literal <peter.mitri at rte-france.com>}
 */
@RunWith(PowerMockRunner.class)
public class MnecFillerTest extends AbstractFillerTest {
    private BranchCnec mnec1;
    private BranchCnec mnec2;
    private MnecParameters parameters;

    @Before
    public void setUp() {
        init();
        crac.newBranchCnec().setId("MNEC1 - N - preventive")
                .newNetworkElement().setId("DDE2AA1  NNL3AA1  1").add()
                .newThreshold().setMin(-1000.).setRule(BranchThresholdRule.ON_LEFT_SIDE).setMax(1000.0).setUnit(Unit.MEGAWATT).add()
                .optimized().monitored()
                .setInstant(Instant.PREVENTIVE)
                .add();
        mnec1 = crac.getBranchCnec("MNEC1 - N - preventive");

        crac.newBranchCnec().setId("MNEC2 - N - preventive")
                .newNetworkElement().setId("NNL2AA1  BBE3AA1  1").add()
                .newThreshold().setMin(-100.).setRule(BranchThresholdRule.ON_LEFT_SIDE).setMax(100.0).setUnit(Unit.MEGAWATT).add()
                .optimized().monitored()
                .setInstant(Instant.PREVENTIVE)
                .add();
        mnec2 = crac.getBranchCnec("MNEC2 - N - preventive");

        crac.desynchronize();
        CracCleaner cracCleaner = new CracCleaner();
        cracCleaner.cleanCrac(crac, network);
        RaoInputHelper.synchronize(crac, network);

        // fill the problem : the core filler is required
        coreProblemFiller = new CoreProblemFiller(
                linearProblem,
                network,
                Set.of(mnec1, mnec2),
                Collections.emptyMap(),
                0);
        coreProblemFiller.fill(sensitivityAndLoopflowResults);
    }

    private void fillProblemWithFiller(Unit unit) {
        parameters = new MnecParameters(50, 10, 3.5);
        MnecFiller mnecFiller = new MnecFiller(
                linearProblem,
                Map.of(mnec1, 900., mnec2, -200.),
                unit,
                parameters);
        mnecFiller.fill(sensitivityAndLoopflowResults);
    }

    @Test
    public void testAddMnecViolationVariables() {
        fillProblemWithFiller(Unit.MEGAWATT);
        crac.getBranchCnecs().forEach(cnec -> {
            MPVariable variable = linearProblem.getMnecViolationVariable(cnec);
            if (cnec.isMonitored()) {
                assertNotNull(variable);
                assertEquals(0, variable.lb(), DOUBLE_TOLERANCE);
                assertEquals(Double.POSITIVE_INFINITY, variable.ub(), DOUBLE_TOLERANCE);
            } else {
                assertNull(variable);
            }
        });
    }

    @Test
    public void testAddMnecMinFlowConstraints() {
        fillProblemWithFiller(Unit.MEGAWATT);

        crac.getBranchCnecs().stream().filter(cnec -> !cnec.isMonitored()).forEach(cnec ->
            assertNull(linearProblem.getMnecFlowConstraint(cnec, LinearProblem.MarginExtension.BELOW_THRESHOLD)));

        MPConstraint ct1Max = linearProblem.getMnecFlowConstraint(mnec1, LinearProblem.MarginExtension.BELOW_THRESHOLD);
        assertNotNull(ct1Max);
        assertEquals(Double.NEGATIVE_INFINITY, ct1Max.lb(), DOUBLE_TOLERANCE);
        double mnec1MaxFlow = 1000 - 3.5;
        assertEquals(mnec1MaxFlow, ct1Max.ub(), DOUBLE_TOLERANCE);
        assertEquals(1, ct1Max.getCoefficient(linearProblem.getFlowVariable(mnec1)), DOUBLE_TOLERANCE);
        assertEquals(-1, ct1Max.getCoefficient(linearProblem.getMnecViolationVariable(mnec1)), DOUBLE_TOLERANCE);

        MPConstraint ct1Min = linearProblem.getMnecFlowConstraint(mnec1, LinearProblem.MarginExtension.ABOVE_THRESHOLD);
        assertNotNull(ct1Min);
        double mnec1MinFlow = -1000 + 3.5;
        assertEquals(mnec1MinFlow, ct1Min.lb(), DOUBLE_TOLERANCE);
        assertEquals(Double.POSITIVE_INFINITY, ct1Min.ub(), DOUBLE_TOLERANCE);
        assertEquals(1, ct1Min.getCoefficient(linearProblem.getFlowVariable(mnec1)), DOUBLE_TOLERANCE);
        assertEquals(1, ct1Min.getCoefficient(linearProblem.getMnecViolationVariable(mnec1)), DOUBLE_TOLERANCE);

        MPConstraint ct2Max = linearProblem.getMnecFlowConstraint(mnec2, LinearProblem.MarginExtension.BELOW_THRESHOLD);
        assertNotNull(ct2Max);
        assertEquals(Double.NEGATIVE_INFINITY, ct2Max.lb(), DOUBLE_TOLERANCE);
        double mnec2MaxFlow = 100 - 3.5;
        assertEquals(mnec2MaxFlow, ct2Max.ub(), DOUBLE_TOLERANCE);
        assertEquals(1, ct2Max.getCoefficient(linearProblem.getFlowVariable(mnec2)), DOUBLE_TOLERANCE);
        assertEquals(-1, ct2Max.getCoefficient(linearProblem.getMnecViolationVariable(mnec2)), DOUBLE_TOLERANCE);

        MPConstraint ct2Min = linearProblem.getMnecFlowConstraint(mnec2, LinearProblem.MarginExtension.ABOVE_THRESHOLD);
        assertNotNull(ct2Min);
        double mnec2MinFlow = -250 + 3.5;
        assertEquals(mnec2MinFlow, ct2Min.lb(), DOUBLE_TOLERANCE);
        assertEquals(Double.POSITIVE_INFINITY, ct2Min.ub(), DOUBLE_TOLERANCE);
        assertEquals(1, ct2Min.getCoefficient(linearProblem.getFlowVariable(mnec2)), DOUBLE_TOLERANCE);
        assertEquals(1, ct2Min.getCoefficient(linearProblem.getMnecViolationVariable(mnec2)), DOUBLE_TOLERANCE);
    }

    @Test
    public void testAddMnecPenaltyCostMW() {
        fillProblemWithFiller(Unit.MEGAWATT);
        crac.getBranchCnecs().stream().filter(Cnec::isMonitored).forEach(cnec -> {
            MPVariable mnecViolationVariable = linearProblem.getMnecViolationVariable(cnec);
            assertEquals(10.0, linearProblem.getObjective().getCoefficient(mnecViolationVariable), DOUBLE_TOLERANCE);
        });
    }

    @Test
    public void testAddMnecPenaltyCostA() {
        fillProblemWithFiller(Unit.AMPERE);
        crac.getBranchCnecs().stream().filter(Cnec::isMonitored).forEach(cnec -> {
            MPVariable mnecViolationVariable = linearProblem.getMnecViolationVariable(cnec);
            assertEquals(10.0 / 0.658, linearProblem.getObjective().getCoefficient(mnecViolationVariable), DOUBLE_TOLERANCE);
        });
    }
}

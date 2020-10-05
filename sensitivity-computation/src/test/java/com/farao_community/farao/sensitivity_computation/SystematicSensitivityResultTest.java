/*
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.sensitivity_computation;

import com.farao_community.farao.data.crac_api.Cnec;
import com.farao_community.farao.data.crac_api.Crac;
import com.farao_community.farao.data.crac_api.RangeAction;
import com.farao_community.farao.data.crac_impl.utils.CommonCracCreation;
import com.farao_community.farao.data.crac_impl.utils.NetworkImportsUtil;
import com.farao_community.farao.flowbased_computation.glsk_provider.UcteGlskProvider;
import com.powsybl.computation.ComputationManager;
import com.powsybl.contingency.ContingenciesProvider;
import com.powsybl.iidm.network.Network;
import com.powsybl.sensitivity.*;
import com.powsybl.sensitivity.factors.functions.BranchFlow;
import com.powsybl.sensitivity.factors.functions.BranchIntensity;
import com.powsybl.sensitivity.factors.variables.LinearGlsk;
import com.powsybl.sensitivity.factors.variables.PhaseTapChangerAngle;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

/**
 * @author Sebastien Murgey {@literal <sebastien.murgey at rte-france.com>}
 */
public class SystematicSensitivityResultTest {
    private static final double EPSILON = 1e-2;

    private Network network;
    private Cnec nStateCnec;
    private Cnec contingencyCnec;
    private RangeAction rangeAction;
    private LinearGlsk linearGlsk;

    private RangeActionSensitivityProvider rangeActionSensitivityProvider;
    private PtdfSensitivityProvider ptdfSensitivityProvider;

    @Before
    public void setUp() {
        network = NetworkImportsUtil.import12NodesNetwork();
        Crac crac = CommonCracCreation.createWithPstRange();
        UcteGlskProvider glskProvider = new UcteGlskProvider(getClass().getResourceAsStream("/glsk_proportional_12nodes.xml"), network);
        glskProvider.selectInstant(Instant.parse("2016-07-28T22:30:00Z"));

        // Ra Provider
        rangeActionSensitivityProvider = new RangeActionSensitivityProvider();
        rangeActionSensitivityProvider.addSensitivityFactors(crac.getRangeActions(), crac.getCnecs());

        // Ptdf Provider
        ptdfSensitivityProvider = new PtdfSensitivityProvider(glskProvider);
        ptdfSensitivityProvider.addCnecs(crac.getCnecs());

        nStateCnec = crac.getCnec("cnec1basecase");
        rangeAction = crac.getRangeAction("pst");
        contingencyCnec = crac.getCnec("cnec1stateCurativeContingency1");
        linearGlsk = glskProvider.getGlsk(network, "10YFR-RTE------C");
    }

    @Test
    public void testCompleteRaResultManipulation() {
        // When
        SensitivityComputationResults sensitivityComputationResults = (new MockSensiFactory()).create(network, null, 0)
            .run(rangeActionSensitivityProvider, rangeActionSensitivityProvider, network.getVariantManager().getWorkingVariantId(), null).join();

        SystematicSensitivityResult result = new SystematicSensitivityResult(sensitivityComputationResults);

        // Then
        assertTrue(result.isSuccess());

        //  in basecase
        assertEquals(10, result.getReferenceFlow(nStateCnec), EPSILON);
        assertEquals(100, result.getReferenceIntensity(nStateCnec), EPSILON);
        assertEquals(0.5, result.getSensitivityOnFlow(rangeAction, nStateCnec), EPSILON);
        assertEquals(0.25, result.getSensitivityOnIntensity(rangeAction, nStateCnec), EPSILON);

        //  after contingency
        assertEquals(-20, result.getReferenceFlow(contingencyCnec), EPSILON);
        assertEquals(-200, result.getReferenceIntensity(contingencyCnec), EPSILON);
        assertEquals(-5, result.getSensitivityOnFlow(rangeAction, contingencyCnec), EPSILON);
        assertEquals(-5, result.getSensitivityOnIntensity(rangeAction, contingencyCnec), EPSILON);
    }

    @Test
    public void testCompletePtdfResultManipulation() {
        // When
        SensitivityComputationResults sensitivityComputationResults = (new MockSensiFactory()).create(network, null, 0)
            .run(ptdfSensitivityProvider, ptdfSensitivityProvider, network.getVariantManager().getWorkingVariantId(), null).join();

        SystematicSensitivityResult result = new SystematicSensitivityResult(sensitivityComputationResults);

        // Then
        assertTrue(result.isSuccess());

        //  in basecase
        assertEquals(40, result.getReferenceFlow(nStateCnec), EPSILON);
        assertEquals(0.140, result.getSensitivityOnFlow(linearGlsk, nStateCnec), EPSILON);

        //  after contingency
        assertEquals(-13, result.getReferenceFlow(contingencyCnec), EPSILON);
        assertEquals(6, result.getSensitivityOnFlow(linearGlsk, contingencyCnec), EPSILON);
    }

    @Test
    public void testIncompleteSensiResult() {
        // When
        SensitivityComputationResults sensitivityComputationResults = Mockito.mock(SensitivityComputationResults.class);
        Mockito.when(sensitivityComputationResults.isOk()).thenReturn(false);
        SystematicSensitivityResult result = new SystematicSensitivityResult(sensitivityComputationResults);

        // Then
        assertFalse(result.isSuccess());
    }

    private final class MockSensiFactory implements SensitivityComputationFactory {
        private final class MockSensi implements SensitivityComputation {
            private Network network;

            private MockSensi(Network network) {
                this.network = network;
            }

            @Override
            public CompletableFuture<SensitivityComputationResults> run(SensitivityFactorsProvider sensitivityFactorsProvider, String s, SensitivityComputationParameters sensitivityComputationParameters) {
                List<SensitivityValue> values = sensitivityFactorsProvider.getFactors(network).stream()
                        .map(factor -> {
                            if (factor.getFunction() instanceof BranchFlow && factor.getVariable() instanceof PhaseTapChangerAngle) {
                                return new SensitivityValue(factor, 0.5, 10, 10);
                            } else if (factor.getFunction() instanceof BranchIntensity && factor.getVariable() instanceof PhaseTapChangerAngle) {
                                return new SensitivityValue(factor, 0.25, 100, -10);
                            } else if (factor.getFunction() instanceof BranchFlow && factor.getVariable() instanceof LinearGlsk) {
                                return new SensitivityValue(factor, 0.140, 40, -11);
                            } else {
                                throw new AssertionError();
                            }
                        })
                        .collect(Collectors.toList());
                return CompletableFuture.completedFuture(new SensitivityComputationResults(true, Collections.emptyMap(), "", values));
            }

            @Override
            public CompletableFuture<SensitivityComputationResults> run(SensitivityFactorsProvider sensitivityFactorsProvider, ContingenciesProvider contingenciesProvider, String s, SensitivityComputationParameters sensitivityComputationParameters) {
                List<SensitivityValue> nStateValues = sensitivityFactorsProvider.getFactors(network).stream()
                        .map(factor -> {
                            if (factor.getFunction() instanceof BranchFlow && factor.getVariable() instanceof PhaseTapChangerAngle) {
                                return new SensitivityValue(factor, 0.5, 10, 10);
                            } else if (factor.getFunction() instanceof BranchIntensity && factor.getVariable() instanceof PhaseTapChangerAngle) {
                                return new SensitivityValue(factor, 0.25, 100, -10);
                            } else if (factor.getFunction() instanceof BranchFlow && factor.getVariable() instanceof LinearGlsk) {
                                return new SensitivityValue(factor, 0.140, 40, -11);
                            } else {
                                throw new AssertionError();
                            }
                        })
                        .collect(Collectors.toList());
                Map<String, List<SensitivityValue>> contingenciesValues = contingenciesProvider.getContingencies(network).stream()
                        .collect(Collectors.toMap(
                            contingency -> contingency.getId(),
                            contingency -> sensitivityFactorsProvider.getFactors(network).stream()
                               .map(factor -> {
                                   if (factor.getFunction() instanceof BranchFlow && factor.getVariable() instanceof PhaseTapChangerAngle) {
                                       return new SensitivityValue(factor, -5, -20, 20);
                                   } else if (factor.getFunction() instanceof BranchIntensity && factor.getVariable() instanceof PhaseTapChangerAngle) {
                                       return new SensitivityValue(factor, 5, 200, -20);
                                   } else if (factor.getFunction() instanceof BranchFlow && factor.getVariable() instanceof LinearGlsk) {
                                       return new SensitivityValue(factor, 6, -13, 15);
                                   } else {
                                       throw new AssertionError();
                                   }
                               })
                               .collect(Collectors.toList())
                        ));
                return CompletableFuture.completedFuture(new SensitivityComputationResults(true, Collections.emptyMap(), "", nStateValues, contingenciesValues));
            }

            @Override
            public String getName() {
                return "MockSensi";
            }

            @Override
            public String getVersion() {
                return "0";
            }
        }

        @Override
        public SensitivityComputation create(Network network, ComputationManager computationManager, int i) {
            return new MockSensi(network);
        }
    }
}
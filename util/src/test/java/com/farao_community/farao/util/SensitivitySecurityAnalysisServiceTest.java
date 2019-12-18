/*
 * Copyright (c) 2018, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.util;

import com.farao_community.farao.data.crac_api.*;
import com.farao_community.farao.data.crac_impl.ComplexContingency;
import com.farao_community.farao.data.crac_impl.SimpleCnec;
import com.farao_community.farao.data.crac_impl.SimpleCrac;
import com.farao_community.farao.data.crac_impl.SimpleState;
import com.farao_community.farao.data.crac_impl.range_domain.AbsoluteFixedRange;
import com.farao_community.farao.data.crac_impl.range_domain.RelativeDynamicRange;
import com.farao_community.farao.data.crac_impl.range_domain.RelativeFixedRange;
import com.farao_community.farao.data.crac_impl.remedial_action.network_action.*;
import com.farao_community.farao.data.crac_impl.remedial_action.range_action.*;
import com.farao_community.farao.data.crac_impl.threshold.AbsoluteFlowThreshold;
import com.farao_community.farao.data.crac_impl.threshold.VoltageThreshold;
import com.farao_community.farao.data.crac_impl.usage_rule.FreeToUse;
import com.farao_community.farao.data.crac_impl.usage_rule.OnConstraint;
import com.farao_community.farao.data.crac_impl.usage_rule.OnContingency;
import com.powsybl.computation.ComputationManager;
import com.powsybl.computation.local.LocalComputationManager;
import com.powsybl.iidm.import_.Importers;
import com.powsybl.iidm.network.Network;
import com.powsybl.sensitivity.*;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static com.farao_community.farao.data.crac_api.ActionType.CLOSE;
import static com.farao_community.farao.data.crac_api.ActionType.OPEN;
import static com.farao_community.farao.data.crac_api.Direction.IN;
import static com.farao_community.farao.data.crac_api.Direction.OUT;
import static com.farao_community.farao.data.crac_api.Side.LEFT;
import static com.farao_community.farao.data.crac_api.Side.RIGHT;
import static org.junit.Assert.*;

/**
 * @author Pengbo Wang {@literal <pengbo.wang at rte-international.com>}
 */
public class SensitivitySecurityAnalysisServiceTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(SensitivitySecurityAnalysisServiceTest.class);

    private Network network;
    private ComputationManager computationManager;
    private SimpleCrac crac;
    private SensitivityComputationFactory sensitivityComputationFactory;

    @Before
    public void setUp() {
        network = Importers.loadNetwork("TestCase12Nodes.uct", getClass().getResourceAsStream("/TestCase12Nodes.uct"));
        computationManager = LocalComputationManager.getDefault();
        crac = create();

        sensitivityComputationFactory = new MockSensitivityComputationFactory();
        SensitivityComputationService.init(sensitivityComputationFactory, computationManager);
    }

    @Test
    public void testSensiSAresult() {
        SensitivityComputationResults precontingencyResult = Mockito.mock(SensitivityComputationResults.class);
        Map<Contingency, SensitivityComputationResults> resultMap = new HashMap<>();
        SensitivitySecurityAnalysisResult result = new SensitivitySecurityAnalysisResult(precontingencyResult, resultMap);
        result.setPrecontingencyResult(precontingencyResult);
        result.setResultMap(resultMap);
        assertNotNull(result);
        assertNotNull(result.getPrecontingencyResult());
        assertNotNull(result.getResultMap());
    }

    @Test
    public void testSensiSArunSensitivitySA() {
        SensitivitySecurityAnalysisResult result = SensitivitySecurityAnalysisService.runSensitivity(network, crac, computationManager, sensitivityComputationFactory);
        assertNotNull(result);
        assertTrue(result.getPrecontingencyResult().isOk());
        assertEquals(1, result.getResultMap().keySet().size());
    }

    private static SimpleCrac create() {
        NetworkElement networkElement1 = new NetworkElement("idNE1", "My Element 1");

        // Redispatching
        NetworkElement generator = new NetworkElement("idGenerator", "My Generator");
        Redispatching rd = new Redispatching(10, 20, 18, 1000, 12, generator);
        rd.setMinimumPower(rd.getMinimumPower() + 1);
        rd.setMaximumPower(rd.getMaximumPower() + 1);
        rd.setTargetPower(rd.getTargetPower() + 1);
        rd.setStartupCost(rd.getStartupCost() + 1);
        rd.setMarginalCost(rd.getMarginalCost() + 1);

        // Range domain
        RelativeFixedRange relativeFixedRange = new RelativeFixedRange(0, 1);
        relativeFixedRange.setMin(1);
        relativeFixedRange.setMax(10);
        RelativeDynamicRange relativeDynamicRange = new RelativeDynamicRange(0, 1);
        relativeDynamicRange.setMin(100);
        relativeDynamicRange.setMax(1000);
        AbsoluteFixedRange absoluteFixedRange = new AbsoluteFixedRange(0, 1);
        absoluteFixedRange.setMin(10);
        absoluteFixedRange.setMax(1000);

        // PstRange
        NetworkElement pst1 = new NetworkElement("BBE2AA1  BBE3AA1  1", "BBE2AA1  BBE3AA1  1");
        PstRange pstRange1 = new PstRange(null);
        pstRange1.setNetworkElement(pst1);

        // HvdcRange
        NetworkElement hvdc1 = new NetworkElement("idHvdc1", "My Hvdc 1");
        HvdcRange hvdcRange1 = new HvdcRange(null);
        hvdcRange1.setNetworkElement(hvdc1);

        // GeneratorRange
        NetworkElement generator1 = new NetworkElement("idGen1", "My Generator 1");
        InjectionRange injectionRange1 = new InjectionRange(null);
        injectionRange1.setNetworkElement(generator1);

        // Countertrading
        Countertrading countertrading = new Countertrading();

        // Topology
        NetworkElement line1 = new NetworkElement("idLine1", "My Line 1");
        Topology topology1 = new Topology(line1, OPEN);
        NetworkElement switch1 = new NetworkElement("idSwitch1", "My Switch 1");
        Topology topology2 = new Topology(null, null);
        topology2.setNetworkElement(switch1);
        topology2.setActionType(CLOSE);

        // Hvdc setpoint
        HvdcSetpoint hvdcSetpoint = new HvdcSetpoint(switch1, 0);
        hvdcSetpoint.setNetworkElement(line1);
        hvdcSetpoint.setSetpoint(1000);

        // Pst setpoint
        PstSetpoint pstSetpoint = new PstSetpoint(switch1, 0);
        pstSetpoint.setNetworkElement(pst1);
        pstSetpoint.setSetpoint(5);

        // Injection setpoint
        InjectionSetpoint injectionSetpoint = new InjectionSetpoint(switch1, 0);
        injectionSetpoint.setNetworkElement(generator1);
        injectionSetpoint.setSetpoint(100);

        NetworkElement line2 = new NetworkElement("idLine2", "My Line 2");
        NetworkElement line3 = new NetworkElement("idLine3", "My Line 3");

        List<NetworkElement> elementsList = new ArrayList<>(Arrays.asList(line2, line3));
        ComplexContingency contingency = new ComplexContingency("idContingency", "My contingency", null);
        contingency.setNetworkElements(elementsList);
        contingency.addNetworkElement(networkElement1);

        // Instant
        Instant basecase = new Instant(0);
        Instant curative = new Instant(-1);
        curative.setDuration(200);

        // State
        State stateBasecase = new SimpleState(Optional.empty(), basecase);
        State stateCurative = new SimpleState(Optional.empty(), null);
        stateCurative.setContingency(Optional.of(contingency));
        stateCurative.setInstant(curative);

        NetworkElement monitoredElement = new NetworkElement("idMR", "Monitored Element");

        // Thresholds
        AbsoluteFlowThreshold threshold1 = new AbsoluteFlowThreshold(Unit.AMPERE, LEFT, IN, 1000);
        threshold1.setSide(RIGHT);
        threshold1.setDirection(OUT);
        threshold1.setMaxValue(999);
        VoltageThreshold threshold2 = new VoltageThreshold(280, 300);
        threshold2.setMinValue(275);
        threshold2.setMaxValue(305);

        // CNECs
        SimpleCnec cnec1 = new SimpleCnec("idCnec", "Cnec", null, threshold1, stateCurative);
        cnec1.setCriticalNetworkElement(monitoredElement);
        SimpleCnec cnec2 = new SimpleCnec("idCnec2", "Cnec 2", monitoredElement, null, null);
        cnec2.setState(stateBasecase);
        cnec2.setThreshold(threshold2);

        // Usage rules
        FreeToUse freeToUse = new FreeToUse(null, null);
        freeToUse.setUsageMethod(UsageMethod.AVAILABLE);
        freeToUse.setState(stateBasecase);
        OnContingency onContingency = new OnContingency(UsageMethod.FORCED, stateCurative, null);
        onContingency.setContingency(contingency);
        OnConstraint onConstraint = new OnConstraint(UsageMethod.FORCED, stateCurative, null);
        onConstraint.setCnec(cnec1);

        // NetworkAction
        ComplexNetworkAction networkAction1 = new ComplexNetworkAction("id1", "name1", "operator1", new ArrayList<>(Arrays.asList(freeToUse)), new ArrayList<>(Arrays.asList(hvdcSetpoint)));
        networkAction1.addApplicableNetworkAction(topology2);
        ComplexNetworkAction networkAction2 = new ComplexNetworkAction("id2", "name2", "operator1", new ArrayList<>(Arrays.asList(freeToUse)), new ArrayList<>(Arrays.asList(pstSetpoint)));

        // RangeAction
        ComplexRangeAction rangeAction1 = new ComplexRangeAction("idRangeAction", "myRangeAction", "operator1", null, null, null);
        List<Range> ranges = new ArrayList<>(Arrays.asList(absoluteFixedRange, relativeDynamicRange));
        rangeAction1.setRanges(ranges);
        rangeAction1.addRange(relativeFixedRange);
        List<ApplicableRangeAction> elementaryRangeActions = new ArrayList<>(Arrays.asList(pstRange1));
        rangeAction1.setApplicableRangeActions(elementaryRangeActions);
        rangeAction1.addApplicableRangeAction(hvdcRange1);
        List<UsageRule> usageRules =  new ArrayList<>(Arrays.asList(freeToUse, onConstraint));
        rangeAction1.setUsageRules(usageRules);
        rangeAction1.addUsageRule(onContingency);

        ComplexRangeAction rangeAction2 = new ComplexRangeAction("idRangeAction2", "myRangeAction2", "operator1", usageRules, ranges, new ArrayList<>(Arrays.asList(pstRange1)));

        List<Cnec> cnecs = new ArrayList<>();
        cnecs.add(cnec1);

        SimpleCrac crac = new SimpleCrac("idCrac", "name", new ArrayList<>(), new ArrayList<>(), new ArrayList<>());

        crac.setCnecs(cnecs);
        crac.addCnec(cnec2);
        crac.setNetworkActions(new ArrayList<>(Arrays.asList(networkAction1)));
        crac.addNetworkRemedialAction(networkAction2);
        crac.setRangeActions(new ArrayList<>(Arrays.asList(rangeAction1)));
        crac.addRangeRemedialAction(rangeAction2);

        String branchId = "BBE2AA1  BBE3AA1  1";
        ComplexContingency contingency1 = new ComplexContingency("idContingency", "My contingency",
                Arrays.asList(new NetworkElement("BBE2AA1  BBE3AA1  1", "BBE2AA1  BBE3AA1  1")));
        crac.addContingency(contingency1);

        return crac;
    }

    public class MockSensitivityComputationFactory implements SensitivityComputationFactory {
        class MockSensitivityComputation implements SensitivityComputation {
            private final Network network;

            MockSensitivityComputation(Network network) {
                this.network = network;
            }

            @Override
            public CompletableFuture<SensitivityComputationResults> run(SensitivityFactorsProvider sensitivityFactorsProvider, String s, SensitivityComputationParameters sensitivityComputationParameters) {
                return CompletableFuture.completedFuture(randomResults(network, sensitivityFactorsProvider));
            }

            private SensitivityComputationResults randomResults(Network network, SensitivityFactorsProvider sensitivityFactorsProvider) {
                List<SensitivityValue> randomSensitivities = sensitivityFactorsProvider.getFactors(network).stream().map(factor -> new SensitivityValue(factor, Math.random(), Math.random(), Math.random())).collect(Collectors.toList());
                return new SensitivityComputationResults(true, Collections.emptyMap(), "", randomSensitivities);
            }

            @Override
            public String getName() {
                return "Mock";
            }

            @Override
            public String getVersion() {
                return "Mock";
            }
        }

        @Override
        public SensitivityComputation create(Network network, ComputationManager computationManager, int i) {
            return new MockSensitivityComputation(network);
        }
    }
}

/*
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.flowbased_computation.impl;

import com.farao_community.farao.commons.ZonalData;
import com.farao_community.farao.data.crac_api.Crac;
import com.farao_community.farao.data.crac_api.Instant;
import com.farao_community.farao.data.crac_api.cnec.FlowCnec;
import com.farao_community.farao.data.crac_api.network_action.NetworkAction;
import com.farao_community.farao.data.flowbased_domain.DataMonitoredBranch;
import com.farao_community.farao.data.flowbased_domain.DataPtdfPerCountry;
import com.farao_community.farao.data.rao_result_api.ComputationStatus;
import com.farao_community.farao.data.rao_result_api.RaoResult;
import com.farao_community.farao.data.rao_result_impl.ElementaryFlowCnecResult;
import com.farao_community.farao.data.rao_result_impl.FlowCnecResult;
import com.farao_community.farao.data.rao_result_impl.RaoResultImpl;
import com.farao_community.farao.flowbased_computation.FlowbasedComputationParameters;
import com.farao_community.farao.flowbased_computation.FlowbasedComputationProvider;
import com.farao_community.farao.flowbased_computation.FlowbasedComputationResult;
import com.powsybl.iidm.network.Network;
import com.powsybl.sensitivity.factors.variables.LinearGlsk;
import org.junit.Before;
import org.junit.Test;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.farao_community.farao.commons.Unit.AMPERE;
import static com.farao_community.farao.commons.Unit.MEGAWATT;
import static com.farao_community.farao.data.rao_result_api.OptimizationState.AFTER_CRA;
import static com.farao_community.farao.data.rao_result_api.OptimizationState.INITIAL;
import static org.junit.Assert.*;

/**
 * @author Sebastien Murgey {@literal <sebastien.murgey at rte-france.com>}
 */
public class FlowbasedComputationImplTest {
    private static final double EPSILON = 1e-3;
    private FlowbasedComputationProvider flowBasedComputationProvider;
    private Network network;
    private Crac crac;
    private ZonalData<LinearGlsk> glsk;
    private FlowbasedComputationParameters parameters;

    @Before
    public void setUp() {
        flowBasedComputationProvider = new FlowbasedComputationImpl();
        network = ExampleGenerator.network();
        glsk = ExampleGenerator.glskProvider();
        parameters = FlowbasedComputationParameters.load();
    }

    @Test
    public void testProviderName() {
        assertEquals("SimpleIterativeFlowBased", flowBasedComputationProvider.getName());
    }

    @Test
    public void testProviderVersion() {
        assertEquals("1.0.0", flowBasedComputationProvider.getVersion());
    }

    @Test
    public void testRunWithCra() {
        crac = ExampleGenerator.crac("crac.json");
        assertTrue(network.getBranch("FR-BE").getTerminal1().isConnected());
        assertTrue(network.getBranch("FR-BE").getTerminal2().isConnected());
        FlowbasedComputationResult result = flowBasedComputationProvider.run(network, crac, null, glsk, parameters).join();
        checkAssertions(result);
        checkCurativeAssertions(result);
    }

    @Test
    public void testRunWithCraRaoResult() {
        crac = ExampleGenerator.crac("crac_for_rao_result.json");
        assertTrue(network.getBranch("FR-BE").getTerminal1().isConnected());
        assertTrue(network.getBranch("FR-BE").getTerminal2().isConnected());
        RaoResult raoResult = createRaoResult(crac.getFlowCnecs(), crac.getNetworkAction("Open line FR-BE"));
        FlowbasedComputationResult result = flowBasedComputationProvider.run(network, crac, raoResult, glsk, parameters).join();
        checkAssertions(result);
        checkCurativeAssertions(result);
    }

    @Test
    public void testRunPraWithForced() {
        crac = ExampleGenerator.crac("crac_with_forced.json");
        FlowbasedComputationResult result = flowBasedComputationProvider.run(network, crac, null, glsk, parameters).join();
        checkAssertions(result);
    }

    @Test
    public void testRunPraWithExtension() {
        crac = ExampleGenerator.crac("crac_with_extension.json");
        FlowbasedComputationResult result = flowBasedComputationProvider.run(network, crac, null, glsk, parameters).join();
        checkAssertions(result);
    }

    private void checkAssertions(FlowbasedComputationResult result) {
        assertEquals(FlowbasedComputationResult.Status.SUCCESS, result.getStatus());

        assertEquals(50, getPreventiveFref(result, "FR-BE - N - preventive"), EPSILON);
        assertEquals(100, getPreventiveFmax(result, "FR-BE - N - preventive"), EPSILON);
        assertEquals(0.375, getPreventivePtdf(result, "FR-BE - N - preventive", "10YFR-RTE------C"), EPSILON);
        assertEquals(-0.375, getPreventivePtdf(result, "FR-BE - N - preventive", "10YBE----------2"), EPSILON);
        assertEquals(0.125, getPreventivePtdf(result, "FR-BE - N - preventive", "10YCB-GERMANY--8"), EPSILON);
        assertEquals(-0.125, getPreventivePtdf(result, "FR-BE - N - preventive", "10YNL----------L"), EPSILON);

        assertEquals(50, getPreventiveFref(result, "FR-DE - N - preventive"), EPSILON);
        assertEquals(100, getPreventiveFmax(result, "FR-DE - N - preventive"), EPSILON);
        assertEquals(0.375, getPreventivePtdf(result, "FR-DE - N - preventive", "10YFR-RTE------C"), EPSILON);
        assertEquals(0.125, getPreventivePtdf(result, "FR-DE - N - preventive", "10YBE----------2"), EPSILON);
        assertEquals(-0.375, getPreventivePtdf(result, "FR-DE - N - preventive", "10YCB-GERMANY--8"), EPSILON);
        assertEquals(-0.125, getPreventivePtdf(result, "FR-DE - N - preventive", "10YNL----------L"), EPSILON);

        assertEquals(50, getPreventiveFref(result, "BE-NL - N - preventive"), EPSILON);
        assertEquals(100, getPreventiveFmax(result, "BE-NL - N - preventive"), EPSILON);
        assertEquals(0.125, getPreventivePtdf(result, "BE-NL - N - preventive", "10YFR-RTE------C"), EPSILON);
        assertEquals(0.375, getPreventivePtdf(result, "BE-NL - N - preventive", "10YBE----------2"), EPSILON);
        assertEquals(-0.125, getPreventivePtdf(result, "BE-NL - N - preventive", "10YCB-GERMANY--8"), EPSILON);
        assertEquals(-0.375, getPreventivePtdf(result, "BE-NL - N - preventive", "10YNL----------L"), EPSILON);

        assertEquals(50, getPreventiveFref(result, "DE-NL - N - preventive"), EPSILON);
        assertEquals(100, getPreventiveFmax(result, "FR-BE - N - preventive"), EPSILON);
        assertEquals(0.125, getPreventivePtdf(result, "DE-NL - N - preventive", "10YFR-RTE------C"), EPSILON);
        assertEquals(-0.125, getPreventivePtdf(result, "DE-NL - N - preventive", "10YBE----------2"), EPSILON);
        assertEquals(0.375, getPreventivePtdf(result, "DE-NL - N - preventive", "10YCB-GERMANY--8"), EPSILON);
        assertEquals(-0.375, getPreventivePtdf(result, "DE-NL - N - preventive", "10YNL----------L"), EPSILON);

        String onOutageId = "outage";
        assertEquals(0., getCurativeFref(result, "N-1 FR-BE", "FR-BE - N-1 - N-1 FR-BE", onOutageId), EPSILON);
        assertEquals(100, getCurativeFmax(result, "N-1 FR-BE", "FR-BE - N-1 - N-1 FR-BE", onOutageId), EPSILON);
        assertEquals(0., getCurativePtdf(result, "N-1 FR-BE", "FR-BE - N-1 - N-1 FR-BE", "10YFR-RTE------C", onOutageId), EPSILON);
        assertEquals(0., getCurativePtdf(result, "N-1 FR-BE", "FR-BE - N-1 - N-1 FR-BE", "10YBE----------2", onOutageId), EPSILON);
        assertEquals(0., getCurativePtdf(result, "N-1 FR-BE", "FR-BE - N-1 - N-1 FR-BE", "10YCB-GERMANY--8", onOutageId), EPSILON);
        assertEquals(0., getCurativePtdf(result, "N-1 FR-BE", "FR-BE - N-1 - N-1 FR-BE", "10YNL----------L", onOutageId), EPSILON);

        assertEquals(100, getCurativeFref(result, "N-1 FR-BE", "FR-DE - N-1 - N-1 FR-BE", onOutageId), EPSILON);
        assertEquals(100, getCurativeFmax(result, "N-1 FR-BE", "FR-DE - N-1 - N-1 FR-BE", onOutageId), EPSILON);
        assertEquals(0.75, getCurativePtdf(result, "N-1 FR-BE", "FR-DE - N-1 - N-1 FR-BE", "10YFR-RTE------C", onOutageId), EPSILON);
        assertEquals(-0.25, getCurativePtdf(result, "N-1 FR-BE", "FR-DE - N-1 - N-1 FR-BE", "10YBE----------2", onOutageId), EPSILON);
        assertEquals(-0.25, getCurativePtdf(result, "N-1 FR-BE", "FR-DE - N-1 - N-1 FR-BE", "10YCB-GERMANY--8", onOutageId), EPSILON);
        assertEquals(-0.25, getCurativePtdf(result, "N-1 FR-BE", "FR-DE - N-1 - N-1 FR-BE", "10YNL----------L", onOutageId), EPSILON);

        assertEquals(0, getCurativeFref(result, "N-1 FR-BE", "BE-NL - N-1 - N-1 FR-BE", onOutageId), EPSILON);
        assertEquals(100, getCurativeFmax(result, "N-1 FR-BE", "BE-NL - N-1 - N-1 FR-BE", onOutageId), EPSILON);
        assertEquals(-0.25, getCurativePtdf(result, "N-1 FR-BE", "BE-NL - N-1 - N-1 FR-BE", "10YFR-RTE------C", onOutageId), EPSILON);
        assertEquals(0.75, getCurativePtdf(result, "N-1 FR-BE", "BE-NL - N-1 - N-1 FR-BE", "10YBE----------2", onOutageId), EPSILON);
        assertEquals(-0.25, getCurativePtdf(result, "N-1 FR-BE", "BE-NL - N-1 - N-1 FR-BE", "10YCB-GERMANY--8", onOutageId), EPSILON);
        assertEquals(-0.25, getCurativePtdf(result, "N-1 FR-BE", "BE-NL - N-1 - N-1 FR-BE", "10YNL----------L", onOutageId), EPSILON);

        assertEquals(100, getCurativeFref(result, "N-1 FR-BE", "DE-NL - N-1 - N-1 FR-BE", onOutageId), EPSILON);
        assertEquals(100, getCurativeFmax(result, "N-1 FR-BE", "DE-NL - N-1 - N-1 FR-BE", onOutageId), EPSILON);
        assertEquals(0.5, getCurativePtdf(result, "N-1 FR-BE", "DE-NL - N-1 - N-1 FR-BE", "10YFR-RTE------C", onOutageId), EPSILON);
        assertEquals(-0.5, getCurativePtdf(result, "N-1 FR-BE", "DE-NL - N-1 - N-1 FR-BE", "10YBE----------2", onOutageId), EPSILON);
        assertEquals(0.5, getCurativePtdf(result, "N-1 FR-BE", "DE-NL - N-1 - N-1 FR-BE", "10YCB-GERMANY--8", onOutageId), EPSILON);
        assertEquals(-0.5, getCurativePtdf(result, "N-1 FR-BE", "DE-NL - N-1 - N-1 FR-BE", "10YNL----------L", onOutageId), EPSILON);
    }

    private void checkCurativeAssertions(FlowbasedComputationResult result) {
        String afterCraId = "curative";
        assertEquals(0., getCurativeFref(result, "N-1 FR-BE", "FR-BE - AfterCra - N-1 FR-BE", afterCraId), EPSILON);
        assertEquals(100, getCurativeFmax(result, "N-1 FR-BE", "FR-BE - AfterCra - N-1 FR-BE", afterCraId), EPSILON);
        assertEquals(0., getCurativePtdf(result, "N-1 FR-BE", "FR-BE - AfterCra - N-1 FR-BE", "10YFR-RTE------C", afterCraId), EPSILON);
        assertEquals(0., getCurativePtdf(result, "N-1 FR-BE", "FR-BE - AfterCra - N-1 FR-BE", "10YBE----------2", afterCraId), EPSILON);
        assertEquals(0., getCurativePtdf(result, "N-1 FR-BE", "FR-BE - AfterCra - N-1 FR-BE", "10YCB-GERMANY--8", afterCraId), EPSILON);
        assertEquals(0., getCurativePtdf(result, "N-1 FR-BE", "FR-BE - AfterCra - N-1 FR-BE", "10YNL----------L", afterCraId), EPSILON);

        assertEquals(100, getCurativeFref(result, "N-1 FR-BE", "FR-DE - AfterCra - N-1 FR-BE", afterCraId), EPSILON);
        assertEquals(100, getCurativeFmax(result, "N-1 FR-BE", "FR-DE - AfterCra - N-1 FR-BE", afterCraId), EPSILON);
        assertEquals(0.75, getCurativePtdf(result, "N-1 FR-BE", "FR-DE - AfterCra - N-1 FR-BE", "10YFR-RTE------C", afterCraId), EPSILON);
        assertEquals(-0.25, getCurativePtdf(result, "N-1 FR-BE", "FR-DE - AfterCra - N-1 FR-BE", "10YBE----------2", afterCraId), EPSILON);
        assertEquals(-0.25, getCurativePtdf(result, "N-1 FR-BE", "FR-DE - AfterCra - N-1 FR-BE", "10YCB-GERMANY--8", afterCraId), EPSILON);
        assertEquals(-0.25, getCurativePtdf(result, "N-1 FR-BE", "FR-DE - AfterCra - N-1 FR-BE", "10YNL----------L", afterCraId), EPSILON);

        assertEquals(0, getCurativeFref(result, "N-1 FR-BE", "BE-NL - AfterCra - N-1 FR-BE", afterCraId), EPSILON);
        assertEquals(100, getCurativeFmax(result, "N-1 FR-BE", "BE-NL - AfterCra - N-1 FR-BE", afterCraId), EPSILON);
        assertEquals(-0.25, getCurativePtdf(result, "N-1 FR-BE", "BE-NL - AfterCra - N-1 FR-BE", "10YFR-RTE------C", afterCraId), EPSILON);
        assertEquals(0.75, getCurativePtdf(result, "N-1 FR-BE", "BE-NL - AfterCra - N-1 FR-BE", "10YBE----------2", afterCraId), EPSILON);
        assertEquals(-0.25, getCurativePtdf(result, "N-1 FR-BE", "BE-NL - AfterCra - N-1 FR-BE", "10YCB-GERMANY--8", afterCraId), EPSILON);
        assertEquals(-0.25, getCurativePtdf(result, "N-1 FR-BE", "BE-NL - AfterCra - N-1 FR-BE", "10YNL----------L", afterCraId), EPSILON);

        assertEquals(100, getCurativeFref(result, "N-1 FR-BE", "DE-NL - AfterCra - N-1 FR-BE", afterCraId), EPSILON);
        assertEquals(100, getCurativeFmax(result, "N-1 FR-BE", "DE-NL - AfterCra - N-1 FR-BE", afterCraId), EPSILON);
        assertEquals(0.5, getCurativePtdf(result, "N-1 FR-BE", "DE-NL - AfterCra - N-1 FR-BE", "10YFR-RTE------C", afterCraId), EPSILON);
        assertEquals(-0.5, getCurativePtdf(result, "N-1 FR-BE", "DE-NL - AfterCra - N-1 FR-BE", "10YBE----------2", afterCraId), EPSILON);
        assertEquals(0.5, getCurativePtdf(result, "N-1 FR-BE", "DE-NL - AfterCra - N-1 FR-BE", "10YCB-GERMANY--8", afterCraId), EPSILON);
        assertEquals(-0.5, getCurativePtdf(result, "N-1 FR-BE", "DE-NL - AfterCra - N-1 FR-BE", "10YNL----------L", afterCraId), EPSILON);
    }

    private double getPreventiveFref(FlowbasedComputationResult result, String monitoredBranch) {
        return result.getFlowBasedDomain().getDataPreContingency().findMonitoredBranchById(monitoredBranch).getFref();
    }

    private double getPreventiveFmax(FlowbasedComputationResult result, String monitoredBranch) {
        return result.getFlowBasedDomain().getDataPreContingency().findMonitoredBranchById(monitoredBranch).getFmax();
    }

    private double getPreventivePtdf(FlowbasedComputationResult result, String monitoredBranch, String glskId) {
        return result.getFlowBasedDomain().getDataPreContingency().findMonitoredBranchById(monitoredBranch).findPtdfByCountry(glskId).getPtdf();
    }

    private double getCurativeFref(FlowbasedComputationResult result, String contingency, String monitoredBranch, String instantId) {
        return result.getFlowBasedDomain().findContingencyById(contingency).findMonitoredBranchByIdAndInstant(monitoredBranch, instantId).getFref();
    }

    private double getCurativeFmax(FlowbasedComputationResult result, String contingency, String monitoredBranch, String instantId) {
        return result.getFlowBasedDomain().findContingencyById(contingency).findMonitoredBranchByIdAndInstant(monitoredBranch, instantId).getFmax();
    }

    private double getCurativePtdf(FlowbasedComputationResult result, String contingency, String monitoredBranch, String glskId, String instantId) {
        return result.getFlowBasedDomain().findContingencyById(contingency).findMonitoredBranchByIdAndInstant(monitoredBranch, instantId).findPtdfByCountry(glskId).getPtdf();
    }

    private Map<String, Double> frefResultById(FlowbasedComputationResult result) {
        return result.getFlowBasedDomain().getDataPreContingency().getDataMonitoredBranches().stream()
                .collect(Collectors.toMap(
                        DataMonitoredBranch::getId,
                        DataMonitoredBranch::getFref));
    }

    private Map<String, Double> fmaxResultById(FlowbasedComputationResult result) {
        return result.getFlowBasedDomain().getDataPreContingency().getDataMonitoredBranches().stream()
                .collect(Collectors.toMap(
                        DataMonitoredBranch::getId,
                        DataMonitoredBranch::getFmax));
    }

    private Map<String, Map<String, Double>> ptdfResultById(FlowbasedComputationResult result) {
        return result.getFlowBasedDomain().getDataPreContingency().getDataMonitoredBranches().stream()
            .collect(Collectors.toMap(
                DataMonitoredBranch::getId,
                dataMonitoredBranch -> dataMonitoredBranch.getPtdfList().stream()
                .collect(Collectors.toMap(
                    DataPtdfPerCountry::getCountry,
                    DataPtdfPerCountry::getPtdf
                ))
            ));
    }

    private RaoResult createRaoResult(Set<FlowCnec> flowCnecs, NetworkAction na) {
        RaoResultImpl raoResult = new RaoResultImpl();

        // Warning: these results on cnecs are not relevant, and maybe not coherent with
        // the hardcoded results of FlowbasedComputationProviderMock, used in this test class.
        flowCnecs.forEach(cnec -> {
            FlowCnecResult flowCnecResult = raoResult.getAndCreateIfAbsentFlowCnecResult(cnec);

            flowCnecResult.getAndCreateIfAbsentResultForOptimizationState(INITIAL);
            ElementaryFlowCnecResult elementaryFlowCnecResult = flowCnecResult.getResult(INITIAL);

            elementaryFlowCnecResult.setFlow(100., MEGAWATT);
            elementaryFlowCnecResult.setMargin(101., MEGAWATT);
            elementaryFlowCnecResult.setRelativeMargin(102., MEGAWATT);
            elementaryFlowCnecResult.setLoopFlow(103., MEGAWATT);
            elementaryFlowCnecResult.setCommercialFlow(104., MEGAWATT);

            elementaryFlowCnecResult.setFlow(110., AMPERE);
            elementaryFlowCnecResult.setMargin(111., AMPERE);
            elementaryFlowCnecResult.setRelativeMargin(112., AMPERE);
            elementaryFlowCnecResult.setLoopFlow(113., AMPERE);
            elementaryFlowCnecResult.setCommercialFlow(114., AMPERE);

            elementaryFlowCnecResult.setPtdfZonalSum(0.1);

            flowCnecResult.getAndCreateIfAbsentResultForOptimizationState(AFTER_CRA);
            elementaryFlowCnecResult = flowCnecResult.getResult(AFTER_CRA);

            elementaryFlowCnecResult.setFlow(200., MEGAWATT);
            elementaryFlowCnecResult.setMargin(201., MEGAWATT);
            elementaryFlowCnecResult.setRelativeMargin(202., MEGAWATT);
            elementaryFlowCnecResult.setLoopFlow(203., MEGAWATT);

            elementaryFlowCnecResult.setFlow(210., AMPERE);
            elementaryFlowCnecResult.setMargin(211., AMPERE);
            elementaryFlowCnecResult.setRelativeMargin(212., AMPERE);
            elementaryFlowCnecResult.setLoopFlow(213., AMPERE);

            elementaryFlowCnecResult.setPtdfZonalSum(0.1);
        });

        raoResult.getAndCreateIfAbsentNetworkActionResult(na).addActivationForState(crac.getState("N-1 FR-BE", Instant.CURATIVE));

        raoResult.setComputationStatus(ComputationStatus.DEFAULT);

        return raoResult;
    }
}

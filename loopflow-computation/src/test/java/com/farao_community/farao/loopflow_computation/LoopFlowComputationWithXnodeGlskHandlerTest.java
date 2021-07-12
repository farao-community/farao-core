/*
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.farao_community.farao.loopflow_computation;

import com.farao_community.farao.commons.EICode;
import com.farao_community.farao.commons.ZonalData;
import com.farao_community.farao.data.crac_api.cnec.FlowCnec;
import com.farao_community.farao.data.refprog.reference_program.ReferenceProgram;
import com.farao_community.farao.sensitivity_analysis.SystematicSensitivityResult;
import com.powsybl.iidm.network.*;
import com.powsybl.sensitivity.factors.variables.LinearGlsk;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.Map;
import java.util.Set;

import static junit.framework.TestCase.assertEquals;

/**
 * @author Joris Mancini {@literal <joris.mancini at rte-france.com>}
 */
public class LoopFlowComputationWithXnodeGlskHandlerTest {
    private static final double DOUBLE_TOLERANCE = 0.01;

    private Network mockNetwork() {
        Network network = Mockito.mock(Network.class);
        Generator gen = Mockito.mock(Generator.class);
        Load load = Mockito.mock(Load.class);
        Mockito.when(network.getGenerator("gen")).thenReturn(gen);
        Mockito.when(network.getLoad("load")).thenReturn(load);
        Mockito.doReturn(mockInjection(true)).when(gen).getTerminal();
        Mockito.doReturn(mockInjection(true)).when(load).getTerminal();
        return network;
    }

    private Terminal mockInjection(boolean inMainComponent) {
        Bus bus = Mockito.mock(Bus.class);
        Mockito.doReturn(inMainComponent).when(bus).isInMainConnectedComponent();
        Terminal.BusView busView = Mockito.mock(Terminal.BusView.class);
        Mockito.doReturn(bus).when(busView).getBus();
        Terminal terminal = Mockito.mock(Terminal.class);
        Mockito.doReturn(busView).when(terminal).getBusView();
        return terminal;
    }

    @Test
    public void testCommercialFlowsWithCnecAfterDanglingLineContingency() {
        ZonalData<LinearGlsk> glsk = Mockito.mock(ZonalData.class);
        ReferenceProgram referenceProgram = Mockito.mock(ReferenceProgram.class);
        XnodeGlskHandler xnodeGlskHandler = Mockito.mock(XnodeGlskHandler.class);
        Network network = mockNetwork();
        Mockito.when(xnodeGlskHandler.getNetwork()).thenReturn(network);

        LoopFlowComputation loopFlowComputation = new LoopFlowComputationWithXnodeGlskHandler(
                glsk,
                referenceProgram,
                xnodeGlskHandler
        );

        FlowCnec preventiveCnec = Mockito.mock(FlowCnec.class);
        FlowCnec cnecAfterClassicContingency = Mockito.mock(FlowCnec.class);
        FlowCnec cnecAfterDanglingContingency = Mockito.mock(FlowCnec.class);
        LinearGlsk classicLinearGlsk = Mockito.mock(LinearGlsk.class);
        LinearGlsk virtualHubLinearGlsk = Mockito.mock(LinearGlsk.class);
        Mockito.when(xnodeGlskHandler.isLinearGlskValidForCnec(preventiveCnec, classicLinearGlsk)).thenReturn(true);
        Mockito.when(xnodeGlskHandler.isLinearGlskValidForCnec(preventiveCnec, virtualHubLinearGlsk)).thenReturn(true);
        Mockito.when(xnodeGlskHandler.isLinearGlskValidForCnec(cnecAfterClassicContingency, classicLinearGlsk)).thenReturn(true);
        Mockito.when(xnodeGlskHandler.isLinearGlskValidForCnec(cnecAfterClassicContingency, virtualHubLinearGlsk)).thenReturn(true);
        Mockito.when(xnodeGlskHandler.isLinearGlskValidForCnec(cnecAfterDanglingContingency, classicLinearGlsk)).thenReturn(true);
        Mockito.when(xnodeGlskHandler.isLinearGlskValidForCnec(cnecAfterDanglingContingency, virtualHubLinearGlsk)).thenReturn(false);

        SystematicSensitivityResult systematicSensitivityResult = Mockito.mock(SystematicSensitivityResult.class);
        Mockito.when(systematicSensitivityResult.getSensitivityOnFlow(classicLinearGlsk, preventiveCnec)).thenReturn(0.5);
        Mockito.when(systematicSensitivityResult.getSensitivityOnFlow(virtualHubLinearGlsk, preventiveCnec)).thenReturn(-1.2);
        Mockito.when(systematicSensitivityResult.getSensitivityOnFlow(classicLinearGlsk, cnecAfterClassicContingency)).thenReturn(-1.8);
        Mockito.when(systematicSensitivityResult.getSensitivityOnFlow(virtualHubLinearGlsk, cnecAfterClassicContingency)).thenReturn(2.3);
        Mockito.when(systematicSensitivityResult.getSensitivityOnFlow(classicLinearGlsk, cnecAfterDanglingContingency)).thenReturn(1.5);
        Mockito.when(systematicSensitivityResult.getSensitivityOnFlow(virtualHubLinearGlsk, cnecAfterDanglingContingency)).thenReturn(4.2);

        Mockito.when(classicLinearGlsk.getGLSKs()).thenReturn(Map.of("gen", 10f));
        Mockito.when(virtualHubLinearGlsk.getGLSKs()).thenReturn(Map.of("load", 10f));

        EICode frCode = new EICode("FR--------------");
        EICode alegroCode = new EICode("Alegro----------");
        Mockito.when(referenceProgram.getGlobalNetPosition(frCode)).thenReturn(2000.);
        Mockito.when(referenceProgram.getGlobalNetPosition(alegroCode)).thenReturn(600.);
        Mockito.when(referenceProgram.getListOfAreas()).thenReturn(Set.of(frCode, alegroCode));
        Mockito.when(glsk.getData("FR--------------")).thenReturn(classicLinearGlsk);
        Mockito.when(glsk.getData("Alegro----------")).thenReturn(virtualHubLinearGlsk);

        LoopFlowResult loopFlowResult = loopFlowComputation.buildLoopFlowsFromReferenceFlowAndPtdf(
                systematicSensitivityResult,
                Set.of(preventiveCnec, cnecAfterClassicContingency, cnecAfterDanglingContingency)
        );

        assertEquals(2000. * 0.5 + 600. * (-1.2), loopFlowResult.getCommercialFlow(preventiveCnec), DOUBLE_TOLERANCE);
        assertEquals(2000. * (-1.8) + 600. * 2.3, loopFlowResult.getCommercialFlow(cnecAfterClassicContingency), DOUBLE_TOLERANCE);
        assertEquals(2000. * 1.5, loopFlowResult.getCommercialFlow(cnecAfterDanglingContingency), DOUBLE_TOLERANCE);
    }
}

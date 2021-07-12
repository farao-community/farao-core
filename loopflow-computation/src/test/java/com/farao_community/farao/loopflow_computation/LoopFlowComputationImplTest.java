/*
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.loopflow_computation;

import com.farao_community.farao.commons.ZonalData;
import com.farao_community.farao.data.crac_api.Crac;
import com.farao_community.farao.data.crac_loopflow_extension.LoopFlowThresholdImpl;
import com.farao_community.farao.data.refprog.reference_program.ReferenceProgram;
import com.farao_community.farao.sensitivity_analysis.SystematicSensitivityResult;
import com.powsybl.iidm.network.*;
import com.powsybl.sensitivity.factors.variables.LinearGlsk;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.Map;

import static junit.framework.TestCase.*;
import static org.mockito.ArgumentMatchers.any;

/**
 * @author Pengbo Wang {@literal <pengbo.wang at rte-international.com>}
 * @author Baptiste Seguinot {@literal <baptiste.seguinot at rte-france.com>}
 */
public class LoopFlowComputationImplTest {

    private static final double DOUBLE_TOLERANCE = 0.1;
    private Crac crac;

    @Before
    public void setUp() {
        crac = ExampleGenerator.crac();

        LoopFlowThresholdImpl loopFlowExtensionMock = Mockito.mock(LoopFlowThresholdImpl.class);
        crac.getFlowCnec("FR-BE1").addExtension(LoopFlowThresholdImpl.class, loopFlowExtensionMock);
        crac.getFlowCnec("BE1-BE2").addExtension(LoopFlowThresholdImpl.class, loopFlowExtensionMock);
        crac.getFlowCnec("BE2-NL").addExtension(LoopFlowThresholdImpl.class, loopFlowExtensionMock);
        crac.getFlowCnec("FR-DE").addExtension(LoopFlowThresholdImpl.class, loopFlowExtensionMock);
        crac.getFlowCnec("DE-NL").addExtension(LoopFlowThresholdImpl.class, loopFlowExtensionMock);
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
    public void calculateLoopFlowTest() {
        ZonalData<LinearGlsk> glsk = ExampleGenerator.glskProvider();
        ReferenceProgram referenceProgram = ExampleGenerator.referenceProgram();
        SystematicSensitivityResult ptdfsAndFlows = ExampleGenerator.systematicSensitivityResult(crac, glsk);

        Network network = Mockito.mock(Network.class);
        Generator gen = Mockito.mock(Generator.class);
        Load load = Mockito.mock(Load.class);
        Mockito.when(network.getGenerator(any())).thenReturn(gen);
        Mockito.when(network.getLoad(any())).thenReturn(load);
        Mockito.doReturn(mockInjection(true)).when(gen).getTerminal();
        Mockito.doReturn(mockInjection(true)).when(load).getTerminal();

        LoopFlowComputation loopFlowComputation = new LoopFlowComputationImpl(glsk, referenceProgram, network);
        LoopFlowResult loopFlowResult = loopFlowComputation.buildLoopFlowsFromReferenceFlowAndPtdf(ptdfsAndFlows, crac.getFlowCnecs());

        assertEquals(-50., loopFlowResult.getLoopFlow(crac.getFlowCnec("FR-BE1")), DOUBLE_TOLERANCE);
        assertEquals(200., loopFlowResult.getLoopFlow(crac.getFlowCnec("BE1-BE2")), DOUBLE_TOLERANCE);
        assertEquals(-50., loopFlowResult.getLoopFlow(crac.getFlowCnec("BE2-NL")), DOUBLE_TOLERANCE);
        assertEquals(50., loopFlowResult.getLoopFlow(crac.getFlowCnec("FR-DE")), DOUBLE_TOLERANCE);
        assertEquals(50., loopFlowResult.getLoopFlow(crac.getFlowCnec("DE-NL")), DOUBLE_TOLERANCE);

        assertEquals(80, loopFlowResult.getCommercialFlow(crac.getFlowCnec("FR-BE1")), DOUBLE_TOLERANCE);
        assertEquals(80., loopFlowResult.getCommercialFlow(crac.getFlowCnec("BE1-BE2")), DOUBLE_TOLERANCE);
        assertEquals(80, loopFlowResult.getCommercialFlow(crac.getFlowCnec("BE2-NL")), DOUBLE_TOLERANCE);
        assertEquals(120., loopFlowResult.getCommercialFlow(crac.getFlowCnec("FR-DE")), DOUBLE_TOLERANCE);
        assertEquals(120., loopFlowResult.getCommercialFlow(crac.getFlowCnec("DE-NL")), DOUBLE_TOLERANCE);

        assertEquals(30., loopFlowResult.getReferenceFlow(crac.getFlowCnec("FR-BE1")), DOUBLE_TOLERANCE);
        assertEquals(280., loopFlowResult.getReferenceFlow(crac.getFlowCnec("BE1-BE2")), DOUBLE_TOLERANCE);
        assertEquals(30., loopFlowResult.getReferenceFlow(crac.getFlowCnec("BE2-NL")), DOUBLE_TOLERANCE);
        assertEquals(170., loopFlowResult.getReferenceFlow(crac.getFlowCnec("FR-DE")), DOUBLE_TOLERANCE);
        assertEquals(170., loopFlowResult.getReferenceFlow(crac.getFlowCnec("DE-NL")), DOUBLE_TOLERANCE);
    }

    @Test
    public void testIsInMainComponent() {
        LinearGlsk linearGlsk = Mockito.mock(LinearGlsk.class);
        Map<String, Float> map = Map.of("gen1", 5f, "load1", 6f, "load2", 6f);
        Mockito.doReturn(map).when(linearGlsk).getGLSKs();

        Network network = Mockito.mock(Network.class);

        Mockito.doReturn(null).when(network).getGenerator("gen1");
        Mockito.doReturn(null).when(network).getLoad("load1");
        Mockito.doReturn(null).when(network).getLoad("load2");

        assertFalse(LoopFlowComputationImpl.isInMainComponent(linearGlsk, network));

        Generator gen1 = Mockito.mock(Generator.class);
        Load load1 = Mockito.mock(Load.class);
        Load load2 = Mockito.mock(Load.class);
        Mockito.doReturn(gen1).when(network).getGenerator("gen1");
        Mockito.doReturn(load1).when(network).getLoad("load1");
        Mockito.doReturn(load2).when(network).getLoad("load2");

        Mockito.doReturn(mockInjection(false)).when(gen1).getTerminal();
        Mockito.doReturn(mockInjection(false)).when(load1).getTerminal();
        Mockito.doReturn(mockInjection(false)).when(load2).getTerminal();
        assertFalse(LoopFlowComputationImpl.isInMainComponent(linearGlsk, network));

        Mockito.doReturn(mockInjection(true)).when(gen1).getTerminal();
        Mockito.doReturn(mockInjection(false)).when(load1).getTerminal();
        Mockito.doReturn(mockInjection(false)).when(load2).getTerminal();
        assertTrue(LoopFlowComputationImpl.isInMainComponent(linearGlsk, network));

        Mockito.doReturn(mockInjection(false)).when(gen1).getTerminal();
        Mockito.doReturn(mockInjection(true)).when(load1).getTerminal();
        Mockito.doReturn(mockInjection(false)).when(load2).getTerminal();
        assertTrue(LoopFlowComputationImpl.isInMainComponent(linearGlsk, network));

        Mockito.doReturn(mockInjection(false)).when(gen1).getTerminal();
        Mockito.doReturn(mockInjection(false)).when(load1).getTerminal();
        Mockito.doReturn(mockInjection(true)).when(load2).getTerminal();
        assertTrue(LoopFlowComputationImpl.isInMainComponent(linearGlsk, network));

        Mockito.doReturn(mockInjection(false)).when(gen1).getTerminal();
        Mockito.doReturn(mockInjection(true)).when(load1).getTerminal();
        Mockito.doReturn(mockInjection(true)).when(load2).getTerminal();
        assertTrue(LoopFlowComputationImpl.isInMainComponent(linearGlsk, network));

        Mockito.doReturn(null).when(network).getGenerator("gen1");
        Mockito.doReturn(null).when(network).getLoad("load1");
        assertTrue(LoopFlowComputationImpl.isInMainComponent(linearGlsk, network));
    }

    @Test
    public void testComputeLoopFlowsWithIsolatedGlsk() {
        ZonalData<LinearGlsk> glsk = ExampleGenerator.glskProvider();
        ReferenceProgram referenceProgram = ExampleGenerator.referenceProgram();
        SystematicSensitivityResult ptdfsAndFlows = ExampleGenerator.systematicSensitivityResult(crac, glsk);

        Network network = Mockito.mock(Network.class);

        Generator genDe = Mockito.mock(Generator.class);
        Generator genNl = Mockito.mock(Generator.class);
        Load loadFr = Mockito.mock(Load.class);
        Load loadBe = Mockito.mock(Load.class);

        Mockito.when(network.getGenerator("Generator DE")).thenReturn(genDe);
        Mockito.when(network.getGenerator("Generator NL")).thenReturn(genNl);
        Mockito.when(network.getGenerator("Generator FR")).thenReturn(null);
        Mockito.when(network.getGenerator("Generator BE 1")).thenReturn(null);
        Mockito.when(network.getLoad("Generator DE")).thenReturn(null);
        Mockito.when(network.getLoad("Generator NL")).thenReturn(null);
        Mockito.when(network.getLoad("Generator FR")).thenReturn(loadFr);
        Mockito.when(network.getLoad("Generator BE 1")).thenReturn(loadBe);

        Mockito.doReturn(mockInjection(true)).when(genDe).getTerminal();
        Mockito.doReturn(mockInjection(false)).when(genNl).getTerminal();
        Mockito.doReturn(mockInjection(true)).when(loadFr).getTerminal();
        Mockito.doReturn(mockInjection(false)).when(loadBe).getTerminal();

        LoopFlowComputation loopFlowComputation = new LoopFlowComputationImpl(glsk, referenceProgram, network);
        LoopFlowResult loopFlowResult = loopFlowComputation.buildLoopFlowsFromReferenceFlowAndPtdf(ptdfsAndFlows, crac.getFlowCnecs());

        assertEquals(30., loopFlowResult.getLoopFlow(crac.getFlowCnec("FR-BE1")), DOUBLE_TOLERANCE);
        assertEquals(280., loopFlowResult.getLoopFlow(crac.getFlowCnec("BE1-BE2")), DOUBLE_TOLERANCE);
        assertEquals(30., loopFlowResult.getLoopFlow(crac.getFlowCnec("BE2-NL")), DOUBLE_TOLERANCE);
        assertEquals(170., loopFlowResult.getLoopFlow(crac.getFlowCnec("FR-DE")), DOUBLE_TOLERANCE);
        assertEquals(170., loopFlowResult.getLoopFlow(crac.getFlowCnec("DE-NL")), DOUBLE_TOLERANCE);

        assertEquals(0, loopFlowResult.getCommercialFlow(crac.getFlowCnec("FR-BE1")), DOUBLE_TOLERANCE);
        assertEquals(0., loopFlowResult.getCommercialFlow(crac.getFlowCnec("BE1-BE2")), DOUBLE_TOLERANCE);
        assertEquals(0., loopFlowResult.getCommercialFlow(crac.getFlowCnec("BE2-NL")), DOUBLE_TOLERANCE);
        assertEquals(0., loopFlowResult.getCommercialFlow(crac.getFlowCnec("FR-DE")), DOUBLE_TOLERANCE);
        assertEquals(0., loopFlowResult.getCommercialFlow(crac.getFlowCnec("DE-NL")), DOUBLE_TOLERANCE);

        assertEquals(30., loopFlowResult.getReferenceFlow(crac.getFlowCnec("FR-BE1")), DOUBLE_TOLERANCE);
        assertEquals(280., loopFlowResult.getReferenceFlow(crac.getFlowCnec("BE1-BE2")), DOUBLE_TOLERANCE);
        assertEquals(30., loopFlowResult.getReferenceFlow(crac.getFlowCnec("BE2-NL")), DOUBLE_TOLERANCE);
        assertEquals(170., loopFlowResult.getReferenceFlow(crac.getFlowCnec("FR-DE")), DOUBLE_TOLERANCE);
        assertEquals(170., loopFlowResult.getReferenceFlow(crac.getFlowCnec("DE-NL")), DOUBLE_TOLERANCE);
    }
}

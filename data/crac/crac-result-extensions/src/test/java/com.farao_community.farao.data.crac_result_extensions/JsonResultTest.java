/*
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.farao_community.farao.data.crac_result_extensions;

import com.farao_community.farao.commons.FaraoException;
import com.farao_community.farao.commons.Unit;
import com.farao_community.farao.data.crac_api.*;
import com.farao_community.farao.data.crac_api.threshold.BranchThresholdRule;
import com.farao_community.farao.data.crac_impl.SimpleCrac;
import com.farao_community.farao.data.crac_impl.NetworkActionImpl;
import com.farao_community.farao.data.crac_impl.PstSetpointImpl;
import com.farao_community.farao.data.crac_impl.TopologicalActionImpl;
import com.farao_community.farao.data.crac_impl.remedial_action.range_action.PstRangeActionImpl;
import com.farao_community.farao.data.crac_io_api.CracExporters;
import com.farao_community.farao.data.crac_io_api.CracImporters;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collections;

import static junit.framework.TestCase.*;

/**
 * @author Baptiste Seguinot {@literal <baptiste.seguinot at rte-france.com>}
 */
public class JsonResultTest {

    private static final double DOUBLE_TOLERANCE = 0.01;

    @Test
    public void cracRoundTripTest() {
        // Crac
        SimpleCrac simpleCrac = new SimpleCrac("cracId");

        simpleCrac.newBranchCnec()
            .setId("cnec1prev")
            .newNetworkElement().setId("ne1").add()
            .newThreshold().setUnit(Unit.AMPERE).setRule(BranchThresholdRule.ON_LEFT_SIDE).setMin(-500.).add()
            .setInstant(Instant.PREVENTIVE)
            .add();
        simpleCrac.newBranchCnec()
            .setId("cnec2prev")
            .newNetworkElement().setId("ne2").add()
            .newThreshold().setUnit(Unit.PERCENT_IMAX).setRule(BranchThresholdRule.ON_LEFT_SIDE).setMin(-0.3).add()
            .setInstant(Instant.PREVENTIVE)
            .add();

        // RangeActions : PstWithRange
        NetworkElement networkElement1 = new NetworkElement("pst1networkElement");
        simpleCrac.addNetworkElement("pst1networkElement");
        PstRangeActionImpl pstRangeAction1 = new PstRangeActionImpl("pst1", networkElement1);
        simpleCrac.addRangeAction(pstRangeAction1);

        // NetworkActions:
        // TopologicalActionImpl
        NetworkElement networkElement2 = new NetworkElement("networkActionNetworkElement");
        simpleCrac.addNetworkElement(networkElement2);
        TopologicalActionImpl topology = new TopologicalActionImpl(networkElement2, ActionType.CLOSE);
        simpleCrac.addNetworkAction(new NetworkActionImpl("topoRaId", "topoRaName", "RTE", new ArrayList<>(), Collections.singleton(topology)));

        // PstSetpointImpl
        PstSetpointImpl pstSetpoint = new PstSetpointImpl(networkElement2, 12.0, RangeDefinition.CENTERED_ON_ZERO);
        simpleCrac.addNetworkAction(new NetworkActionImpl("pstSetPointRaId", "pstSetPointRaName", "RTE", new ArrayList<>(), Collections.singleton(pstSetpoint)));

        // add a ResultVariantManager to the Crac
        simpleCrac.addExtension(ResultVariantManager.class, new ResultVariantManager());

        // add variants
        simpleCrac.getExtension(ResultVariantManager.class).createVariant("variant1");
        simpleCrac.getExtension(ResultVariantManager.class).createVariant("variant2");

        // CracResult
        CracResultExtension cracResultExtension = simpleCrac.getExtension(CracResultExtension.class);
        cracResultExtension.getVariant("variant1").setFunctionalCost(10);
        cracResultExtension.getVariant("variant1").setNetworkSecurityStatus(CracResult.NetworkSecurityStatus.UNSECURED);

        // CnecResult
        CnecResultExtension cnecResultExtension = simpleCrac.getBranchCnec("cnec2prev").getExtension(CnecResultExtension.class);
        cnecResultExtension.getVariant("variant1").setFlowInA(75.0);
        cnecResultExtension.getVariant("variant1").setFlowInMW(50.0);
        cnecResultExtension.getVariant("variant1").setMinThresholdInMW(-1000);
        cnecResultExtension.getVariant("variant1").setMaxThresholdInMW(1000);
        cnecResultExtension.getVariant("variant1").setMinThresholdInA(-700);
        cnecResultExtension.getVariant("variant1").setMaxThresholdInA(700);
        cnecResultExtension.getVariant("variant1").setAbsolutePtdfSum(0.2);
        cnecResultExtension.getVariant("variant2").setFlowInA(750.0);
        cnecResultExtension.getVariant("variant2").setFlowInMW(450.0);

        String preventiveStateId = simpleCrac.getPreventiveState().getId();

        // PstRangeResult
        RangeActionResultExtension rangeActionResultExtension = simpleCrac.getRangeAction("pst1").getExtension(RangeActionResultExtension.class);
        double pstRangeSetPointVariant1 = 4.0;
        double pstRangeSetPointVariant2 = 14.0;
        Integer pstRangeTapVariant1 = 2;
        Integer pstRangeTapVariant2 = 6;
        rangeActionResultExtension.getVariant("variant1").setSetPoint(preventiveStateId, pstRangeSetPointVariant1);
        ((PstRangeResult) rangeActionResultExtension.getVariant("variant1")).setTap(preventiveStateId, pstRangeTapVariant1);
        rangeActionResultExtension.getVariant("variant2").setSetPoint(preventiveStateId, pstRangeSetPointVariant2);
        ((PstRangeResult) rangeActionResultExtension.getVariant("variant2")).setTap(preventiveStateId, pstRangeTapVariant2);

        // NetworkActionResult for topology
        NetworkActionResultExtension topologyResultExtension = simpleCrac.getNetworkAction("topoRaId").getExtension(NetworkActionResultExtension.class);
        topologyResultExtension.getVariant("variant1").activate(preventiveStateId);
        topologyResultExtension.getVariant("variant2").deactivate(preventiveStateId);

        // NetworkActionResult for pstSetpoint
        NetworkActionResultExtension pstSetpointResultExtension = simpleCrac.getNetworkAction("pstSetPointRaId").getExtension(NetworkActionResultExtension.class);
        pstSetpointResultExtension.getVariant("variant1").activate(preventiveStateId);
        pstSetpointResultExtension.getVariant("variant2").deactivate(preventiveStateId);

        // export Crac
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        CracExporters.exportCrac(simpleCrac, "Json", outputStream);

        // import Crac
        Crac crac;
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(outputStream.toByteArray())) {
            crac = CracImporters.importCrac("unknown.json", inputStream);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        // assert
        // assert that the ResultVariantManager exists and contains the expected results
        assertNotNull(crac.getExtension(ResultVariantManager.class));
        assertEquals(2, crac.getExtension(ResultVariantManager.class).getVariants().size());
        assertTrue(crac.getExtension(ResultVariantManager.class).getVariants().contains("variant1"));
        assertTrue(crac.getExtension(ResultVariantManager.class).getVariants().contains("variant2"));

        // assert that the CracResultExtension exists and contains the expected results
        assertNotNull(crac.getExtension(CracResultExtension.class));
        assertEquals(10.0, crac.getExtension(CracResultExtension.class).getVariant("variant1").getCost(), DOUBLE_TOLERANCE);
        assertEquals(10.0, crac.getExtension(CracResultExtension.class).getVariant("variant1").getFunctionalCost(), DOUBLE_TOLERANCE);
        assertEquals(0.0, crac.getExtension(CracResultExtension.class).getVariant("variant1").getVirtualCost(), DOUBLE_TOLERANCE);
        assertEquals(CracResult.NetworkSecurityStatus.UNSECURED, crac.getExtension(CracResultExtension.class).getVariant("variant1").getNetworkSecurityStatus());

        // assert that cnecs exist in the crac
        assertEquals(2, crac.getBranchCnecs().size());
        assertNotNull(crac.getBranchCnec("cnec1prev"));
        assertNotNull(crac.getBranchCnec("cnec2prev"));

        // assert that the second one has a CnecResult extension with the expected content
        assertEquals(1, crac.getBranchCnec("cnec2prev").getExtensions().size());
        CnecResultExtension extCnec = crac.getBranchCnec("cnec2prev").getExtension(CnecResultExtension.class);
        assertNotNull(extCnec);
        assertEquals(50.0, extCnec.getVariant("variant1").getFlowInMW(), DOUBLE_TOLERANCE);
        assertEquals(75.0, extCnec.getVariant("variant1").getFlowInA(), DOUBLE_TOLERANCE);
        assertEquals(-1000.0, extCnec.getVariant("variant1").getMinThresholdInMW(),  DOUBLE_TOLERANCE);
        assertEquals(1000.0, extCnec.getVariant("variant1").getMaxThresholdInMW(),  DOUBLE_TOLERANCE);
        assertEquals(-700.0, extCnec.getVariant("variant1").getMinThresholdInA(),  DOUBLE_TOLERANCE);
        assertEquals(700.0, extCnec.getVariant("variant1").getMaxThresholdInA(),  DOUBLE_TOLERANCE);
        assertEquals(0.2, extCnec.getVariant("variant1").getAbsolutePtdfSum(), DOUBLE_TOLERANCE);

        // assert that the PstWithRange has a RangeActionResultExtension with the expected content
        assertEquals(1, crac.getRangeAction("pst1").getExtensions().size());
        RangeActionResultExtension rangeActionResultExtension1 = crac.getRangeAction("pst1").getExtension(RangeActionResultExtension.class);
        assertNotNull(rangeActionResultExtension1);
        assertEquals(pstRangeSetPointVariant1, rangeActionResultExtension1.getVariant("variant1").getSetPoint(preventiveStateId));
        assertEquals(pstRangeTapVariant1, ((PstRangeResult) rangeActionResultExtension1.getVariant("variant1")).getTap(preventiveStateId));
        assertEquals(pstRangeSetPointVariant2, rangeActionResultExtension1.getVariant("variant2").getSetPoint(preventiveStateId));
        assertEquals(pstRangeTapVariant2, ((PstRangeResult) rangeActionResultExtension1.getVariant("variant2")).getTap(preventiveStateId));

        // assert that the TopologicalActionImpl has a NetworkActionResultExtension with the expected content
        assertEquals(1, crac.getNetworkAction("topoRaId").getExtensions().size());
        NetworkActionResultExtension exportedTopologyResultExtension = crac.getNetworkAction("topoRaId").getExtension(NetworkActionResultExtension.class);
        assertNotNull(exportedTopologyResultExtension);
        assertTrue(exportedTopologyResultExtension.getVariant("variant1").isActivated(preventiveStateId));
        assertFalse(exportedTopologyResultExtension.getVariant("variant2").isActivated(preventiveStateId));

        // assert that the PstSetpointImpl has a NetworkActionResultExtension with the expected content
        assertEquals(1, crac.getNetworkAction("pstSetPointRaId").getExtensions().size());
        NetworkActionResultExtension exportedPstSetpointResultExtension = crac.getNetworkAction("pstSetPointRaId").getExtension(NetworkActionResultExtension.class);
        assertNotNull(exportedPstSetpointResultExtension);
        assertTrue(exportedPstSetpointResultExtension.getVariant("variant1").isActivated(preventiveStateId));
        assertFalse(exportedPstSetpointResultExtension.getVariant("variant2").isActivated(preventiveStateId));
    }

    @Test
    public void cracImportTest() {
        Crac crac = CracImporters.importCrac("small-crac-with-result-extensions.json", getClass().getResourceAsStream("/small-crac-with-result-extensions.json"));

        // ResultVariantManager
        assertNotNull(crac.getExtension(ResultVariantManager.class));
        assertEquals(2, crac.getExtension(ResultVariantManager.class).getVariants().size());
        assertTrue(crac.getExtension(ResultVariantManager.class).getVariants().contains("variant1"));
        assertTrue(crac.getExtension(ResultVariantManager.class).getVariants().contains("variant2"));

        // CracResultExtension
        CracResultExtension extCrac = crac.getExtension(CracResultExtension.class);
        assertNotNull(extCrac);
        assertEquals(15.0, extCrac.getVariant("variant1").getCost(), DOUBLE_TOLERANCE);
        assertEquals(10.0, extCrac.getVariant("variant1").getFunctionalCost(), DOUBLE_TOLERANCE);
        assertEquals(5.0, extCrac.getVariant("variant1").getVirtualCost(), DOUBLE_TOLERANCE);
        assertEquals(CracResult.NetworkSecurityStatus.UNSECURED, extCrac.getVariant("variant1").getNetworkSecurityStatus());

        // CnecResultExtension
        CnecResultExtension extCnec = crac.getBranchCnec("Tieline BE FR - Défaut - N-1 NL1-NL3").getExtension(CnecResultExtension.class);
        assertNotNull(extCnec);
        assertEquals(-450.0, extCnec.getVariant("variant2").getFlowInMW(), DOUBLE_TOLERANCE);
        assertEquals(750.0, extCnec.getVariant("variant2").getFlowInA(), DOUBLE_TOLERANCE);
        assertEquals(0.85, extCnec.getVariant("variant2").getAbsolutePtdfSum(), DOUBLE_TOLERANCE);
    }

    @Test
    public void cracImportWithUnknownFieldInExtension() {
        try {
            Crac crac = CracImporters.importCrac("small-crac-errored.json", getClass().getResourceAsStream("/small-crac-errored.json"));
            fail();
        } catch (FaraoException e) {
            // should throw
            assertTrue(e.getMessage().contains("Unexpected field"));
        }
    }
}

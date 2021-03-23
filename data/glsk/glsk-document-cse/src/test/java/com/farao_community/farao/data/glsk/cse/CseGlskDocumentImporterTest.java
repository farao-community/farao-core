/*
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.data.glsk.cse;

import com.farao_community.farao.data.glsk.api.AbstractGlskPoint;
import com.farao_community.farao.data.glsk.api.GlskDocument;
import com.farao_community.farao.data.glsk.api.io.GlskDocumentImporters;
import com.powsybl.action.util.Scalable;
import com.powsybl.iidm.import_.Importers;
import com.powsybl.iidm.network.Network;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

/**
 * @author Sebastien Murgey {@literal <sebastien.murgey at rte-france.com>}
 */
public class CseGlskDocumentImporterTest {
    private static final double EPSILON = 1e-3;

    @Test
    public void checkCseGlskDocumentImporterCorrectlyImportManualGskBlocks() {
        CseGlskDocument cseGlskDocument = CseGlskDocument.importGlsk(getClass().getResourceAsStream("/testGlsk.xml"));
        List<AbstractGlskPoint> list = cseGlskDocument.getGlskPoints("FR_MANUAL");
        assertFalse(list.isEmpty());
        assertEquals(1, list.size());
        assertEquals(1, list.get(0).getGlskShiftKeys().size());
        assertEquals(2, list.get(0).getGlskShiftKeys().get(0).getRegisteredResourceArrayList().size());
    }

    @Test
    public void checkCseGlskDocumentImporterCorrectlyConvertManualGskBlocks() {
        Network network = Importers.loadNetwork("testCase.xiidm", getClass().getResourceAsStream("/testCase.xiidm"));
        GlskDocument glskDocument = GlskDocumentImporters.importGlsk(getClass().getResourceAsStream("/testGlsk.xml"));
        Scalable manualScalable = glskDocument.getZonalScalable(network).getData("FR_MANUAL");

        assertNotNull(manualScalable);
        assertEquals(2000., network.getGenerator("FFR1AA1 _generator").getTargetP(), EPSILON);
        assertEquals(3000., network.getGenerator("FFR3AA1 _generator").getTargetP(), EPSILON);

        manualScalable.scale(network, 1000.);
        assertEquals(2700., network.getGenerator("FFR1AA1 _generator").getTargetP(), EPSILON);
        assertEquals(3300., network.getGenerator("FFR3AA1 _generator").getTargetP(), EPSILON);
    }

    @Test
    public void checkCseGlskDocumentImporterCorrectlyImportReserveGskBlocks() {
        CseGlskDocument cseGlskDocument = CseGlskDocument.importGlsk(getClass().getResourceAsStream("/testGlsk.xml"));
        List<AbstractGlskPoint> list = cseGlskDocument.getGlskPoints("FR_RESERVE");
        assertFalse(list.isEmpty());
        assertEquals(1, list.size());
        assertEquals(1, list.get(0).getGlskShiftKeys().size());
        assertEquals(2, list.get(0).getGlskShiftKeys().get(0).getRegisteredResourceArrayList().size());
    }

    @Test
    public void checkCseGlskDocumentImporterCorrectlyConvertReserveGskBlocksDown() {
        Network network = Importers.loadNetwork("testCase.xiidm", getClass().getResourceAsStream("/testCase.xiidm"));
        GlskDocument glskDocument = GlskDocumentImporters.importGlsk(getClass().getResourceAsStream("/testGlsk.xml"));
        Scalable reserveScalable = glskDocument.getZonalScalable(network).getData("FR_RESERVE");

        assertNotNull(reserveScalable);
        assertEquals(2000., network.getGenerator("FFR1AA1 _generator").getTargetP(), EPSILON);
        assertEquals(2000., network.getGenerator("FFR2AA1 _generator").getTargetP(), EPSILON);

        reserveScalable.scale(network, -900.);
        assertEquals(1400., network.getGenerator("FFR1AA1 _generator").getTargetP(), EPSILON);
        assertEquals(1700., network.getGenerator("FFR2AA1 _generator").getTargetP(), EPSILON);
    }

    @Test
    public void checkCseGlskDocumentImporterCorrectlyConvertReserveGskBlocksUp() {
        Network network = Importers.loadNetwork("testCase.xiidm", getClass().getResourceAsStream("/testCase.xiidm"));
        GlskDocument glskDocument = GlskDocumentImporters.importGlsk(getClass().getResourceAsStream("/testGlsk.xml"));
        Scalable reserveScalable = glskDocument.getZonalScalable(network).getData("FR_RESERVE");

        assertNotNull(reserveScalable);
        assertEquals(2000., network.getGenerator("FFR1AA1 _generator").getTargetP(), EPSILON);
        assertEquals(2000., network.getGenerator("FFR2AA1 _generator").getTargetP(), EPSILON);

        reserveScalable.scale(network, 1000.);
        assertEquals(2600., network.getGenerator("FFR1AA1 _generator").getTargetP(), EPSILON);
        assertEquals(2400., network.getGenerator("FFR2AA1 _generator").getTargetP(), EPSILON);
    }

    @Test
    public void checkCseGlskDocumentImporterCorrectlyImportPropGskBlocks() {
        CseGlskDocument cseGlskDocument = CseGlskDocument.importGlsk(getClass().getResourceAsStream("/testGlsk.xml"));
        List<AbstractGlskPoint> list = cseGlskDocument.getGlskPoints("FR_PROPGSK");
        assertFalse(list.isEmpty());
        assertEquals(1, list.size());
        assertEquals(1, list.get(0).getGlskShiftKeys().size());
        assertEquals(3, list.get(0).getGlskShiftKeys().get(0).getRegisteredResourceArrayList().size());
    }

    @Test
    public void checkCseGlskDocumentImporterCorrectlyConvertPropGskBlocks() {
        Network network = Importers.loadNetwork("testCase.xiidm", getClass().getResourceAsStream("/testCase.xiidm"));
        GlskDocument glskDocument = GlskDocumentImporters.importGlsk(getClass().getResourceAsStream("/testGlsk.xml"));
        Scalable propGskScalable = glskDocument.getZonalScalable(network).getData("FR_PROPGSK");

        assertNotNull(propGskScalable);
        assertEquals(2000., network.getGenerator("FFR1AA1 _generator").getTargetP(), EPSILON);
        assertEquals(2000., network.getGenerator("FFR2AA1 _generator").getTargetP(), EPSILON);
        assertEquals(3000., network.getGenerator("FFR3AA1 _generator").getTargetP(), EPSILON);

        propGskScalable.scale(network, 700.);
        assertEquals(2200., network.getGenerator("FFR1AA1 _generator").getTargetP(), EPSILON);
        assertEquals(2200., network.getGenerator("FFR2AA1 _generator").getTargetP(), EPSILON);
        assertEquals(3300., network.getGenerator("FFR3AA1 _generator").getTargetP(), EPSILON);
    }

    @Test
    public void checkCseGlskDocumentImporterCorrectlyImportPropGlskBlocks() {
        CseGlskDocument cseGlskDocument = CseGlskDocument.importGlsk(getClass().getResourceAsStream("/testGlsk.xml"));
        List<AbstractGlskPoint> list = cseGlskDocument.getGlskPoints("FR_PROPGLSK");
        assertFalse(list.isEmpty());
        assertEquals(1, list.size());
        assertEquals(2, list.get(0).getGlskShiftKeys().size());
        assertEquals(3, list.get(0).getGlskShiftKeys().get(0).getRegisteredResourceArrayList().size());
        assertEquals(2, list.get(0).getGlskShiftKeys().get(1).getRegisteredResourceArrayList().size());
    }

    @Test
    public void checkCseGlskDocumentImporterCorrectlyConvertPropGlskBlocks() {
        Network network = Importers.loadNetwork("testCase.xiidm", getClass().getResourceAsStream("/testCase.xiidm"));
        GlskDocument glskDocument = GlskDocumentImporters.importGlsk(getClass().getResourceAsStream("/testGlsk.xml"));
        Scalable propGlskScalable = glskDocument.getZonalScalable(network).getData("FR_PROPGLSK");

        assertNotNull(propGlskScalable);
        assertEquals(2000., network.getGenerator("FFR1AA1 _generator").getTargetP(), EPSILON);
        assertEquals(2000., network.getGenerator("FFR2AA1 _generator").getTargetP(), EPSILON);
        assertEquals(3000., network.getGenerator("FFR3AA1 _generator").getTargetP(), EPSILON);
        assertEquals(1000., network.getLoad("FFR1AA1 _load").getP0(), EPSILON);
        assertEquals(3500., network.getLoad("FFR2AA1 _load").getP0(), EPSILON);

        propGlskScalable.scale(network, 1000.);
        assertEquals(2200., network.getGenerator("FFR1AA1 _generator").getTargetP(), EPSILON);
        assertEquals(2200., network.getGenerator("FFR2AA1 _generator").getTargetP(), EPSILON);
        assertEquals(3300., network.getGenerator("FFR3AA1 _generator").getTargetP(), EPSILON);
        assertEquals(933.3334, network.getLoad("FFR1AA1 _load").getP0(), EPSILON);
        assertEquals(3266.6667, network.getLoad("FFR2AA1 _load").getP0(), EPSILON);
    }

    @Test
    public void checkCseGlskDocumentImporterCorrectlyImportMeritOrderGskBlocks() {
        CseGlskDocument cseGlskDocument = CseGlskDocument.importGlsk(getClass().getResourceAsStream("/testGlsk.xml"));
        List<AbstractGlskPoint> list = cseGlskDocument.getGlskPoints("FR_MERITORDER");
        assertFalse(list.isEmpty());
        assertEquals(1, list.size());
        assertEquals(7, list.get(0).getGlskShiftKeys().size());
        assertEquals(1, list.get(0).getGlskShiftKeys().get(0).getRegisteredResourceArrayList().size());
        assertEquals(1, list.get(0).getGlskShiftKeys().get(1).getRegisteredResourceArrayList().size());
        assertEquals(1, list.get(0).getGlskShiftKeys().get(2).getRegisteredResourceArrayList().size());
        assertEquals(1, list.get(0).getGlskShiftKeys().get(3).getRegisteredResourceArrayList().size());
        assertEquals(1, list.get(0).getGlskShiftKeys().get(1).getRegisteredResourceArrayList().size());
        assertEquals(1, list.get(0).getGlskShiftKeys().get(2).getRegisteredResourceArrayList().size());
        assertEquals(1, list.get(0).getGlskShiftKeys().get(3).getRegisteredResourceArrayList().size());
    }

    @Test
    public void checkCseGlskDocumentImporterCorrectlyConvertMeritOrderGskBlocksDown() {
        Network network = Importers.loadNetwork("testCase.xiidm", getClass().getResourceAsStream("/testCase.xiidm"));
        GlskDocument glskDocument = GlskDocumentImporters.importGlsk(getClass().getResourceAsStream("/testGlsk.xml"));
        Scalable meritOrderGskScalable = glskDocument.getZonalScalable(network).getData("FR_MERITORDER");

        assertNotNull(meritOrderGskScalable);
        assertEquals(2000., network.getGenerator("FFR1AA1 _generator").getTargetP(), EPSILON);
        assertEquals(2000., network.getGenerator("FFR2AA1 _generator").getTargetP(), EPSILON);
        assertEquals(3000., network.getGenerator("FFR3AA1 _generator").getTargetP(), EPSILON);

        meritOrderGskScalable.scale(network, -4000.);
        assertEquals(2000., network.getGenerator("FFR1AA1 _generator").getTargetP(), EPSILON);
        assertEquals(1000., network.getGenerator("FFR2AA1 _generator").getTargetP(), EPSILON);
        assertEquals(0., network.getGenerator("FFR3AA1 _generator").getTargetP(), EPSILON);
    }

    @Test
    public void checkCseGlskDocumentImporterCorrectlyConvertMeritOrderGskBlocksUp() {
        Network network = Importers.loadNetwork("testCase.xiidm", getClass().getResourceAsStream("/testCase.xiidm"));
        GlskDocument glskDocument = GlskDocumentImporters.importGlsk(getClass().getResourceAsStream("/testGlsk.xml"));
        Scalable meritOrderGskScalable = glskDocument.getZonalScalable(network).getData("FR_MERITORDER");

        assertNotNull(meritOrderGskScalable);
        assertEquals(2000., network.getGenerator("FFR1AA1 _generator").getTargetP(), EPSILON);
        assertEquals(2000., network.getGenerator("FFR2AA1 _generator").getTargetP(), EPSILON);
        assertEquals(3000., network.getGenerator("FFR3AA1 _generator").getTargetP(), EPSILON);

        meritOrderGskScalable.scale(network, 5000.);
        assertEquals(5000., network.getGenerator("FFR1AA1 _generator").getTargetP(), EPSILON);
        assertEquals(4000., network.getGenerator("FFR2AA1 _generator").getTargetP(), EPSILON);
        assertEquals(3000., network.getGenerator("FFR3AA1 _generator").getTargetP(), EPSILON);
    }

    @Test
    public void checkCseGlskDocumentImporterCorrectlyImportHybridGskBlocks() {
        Network network = Importers.loadNetwork("testCase.xiidm", getClass().getResourceAsStream("/testCase.xiidm"));
        CseGlskDocument cseGlskDocument = CseGlskDocument.importGlsk(getClass().getResourceAsStream("/testGlskHybrid.xml"));

        List<AbstractGlskPoint> list = cseGlskDocument.getGlskPoints("10YCH-SWISSGRIDZ");
        assertFalse(list.isEmpty());
        assertEquals(1, list.size());
        assertEquals(2, list.get(0).getGlskShiftKeys().size());
        assertEquals(2, list.get(0).getGlskShiftKeys().get(0).getRegisteredResourceArrayList().size());
        assertEquals(1, (list.get(0).getGlskShiftKeys().get(0)).getOrderInHybridCseGlsk());
        double maximumShift = (list.get(0).getGlskShiftKeys().get(0)).getMaximumShift();
        assertEquals(1000.0, maximumShift, EPSILON);
        assertEquals(2, (list.get(0).getGlskShiftKeys().get(1)).getOrderInHybridCseGlsk());
        assertEquals(Integer.MAX_VALUE, (list.get(0).getGlskShiftKeys().get(1)).getMaximumShift(), EPSILON);

        Scalable hybridGlsk1 = cseGlskDocument.getZonalScalable(network).getData("10YCH-SWISSGRIDZ@1");
        assertNotNull(hybridGlsk1);
        assertEquals(2000., network.getGenerator("FFR1AA1 _generator").getTargetP(), EPSILON);
        assertEquals(2000., network.getGenerator("FFR2AA1 _generator").getTargetP(), EPSILON);
        hybridGlsk1.scale(network, maximumShift);
        assertEquals(2500., network.getGenerator("FFR1AA1 _generator").getTargetP(), EPSILON);
        assertEquals(2500., network.getGenerator("FFR2AA1 _generator").getTargetP(), EPSILON);

        Scalable hybridGlsk2 = cseGlskDocument.getZonalScalable(network).getData("10YCH-SWISSGRIDZ@2");
        assertNotNull(hybridGlsk2);

        List<AbstractGlskPoint> list2 = cseGlskDocument.getGlskPoints("FR_MANUAL");
        assertFalse(list2.isEmpty());
        assertEquals(1, list2.size());
        assertEquals(1, list2.get(0).getGlskShiftKeys().size());
        assertEquals(2, list2.get(0).getGlskShiftKeys().get(0).getRegisteredResourceArrayList().size());
    }
}

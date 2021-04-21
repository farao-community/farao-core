/*
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.farao_community.farao.data.crac_loopflow_extension;

import org.junit.Test;

/**
 * @author Baptiste Seguinot {@literal <baptiste.seguinot at rte-france.com>}
 */
public class JsonLoopFlowThresholdImplImportExportTest {

    @Test
    public void roundTripTest() {
        /*
        // Crac
        Crac simpleCrac = new CracImpl("cracId");

        simpleCrac.newFlowCnec()
            .setId("cnec1")
            .newNetworkElement().setId("ne1").add()
            .setInstant(Instant.PREVENTIVE)
            .newThreshold().setRule(BranchThresholdRule.ON_LEFT_SIDE).setUnit(Unit.AMPERE).setMin(-500.).add()
            .add();
        simpleCrac.getBranchCnec("cnec1").addExtension(LoopFlowThresholdImpl.class, new LoopFlowThresholdImpl(100, Unit.AMPERE));

        simpleCrac.newFlowCnec()
            .setId("cnec2")
            .newNetworkElement().setId("ne2").add()
            .setInstant(Instant.PREVENTIVE)
            .newThreshold().setRule(BranchThresholdRule.ON_LEFT_SIDE).setUnit(Unit.PERCENT_IMAX).setMin(-0.3).add()
            .add();
        simpleCrac.getBranchCnec("cnec2").addExtension(LoopFlowThresholdImpl.class, new LoopFlowThresholdImpl(30, Unit.PERCENT_IMAX));

        simpleCrac.newFlowCnec()
            .setId("cnec3")
            .newNetworkElement().setId("ne3").add()
            .setInstant(Instant.PREVENTIVE)
            .newThreshold().setRule(BranchThresholdRule.ON_LEFT_SIDE).setUnit(Unit.MEGAWATT).setMin(-700.).setMax(700.).add()
            .add();

        // export Crac
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        CracExporters.exportCrac(simpleCrac, "Json", outputStream);

        // import Crac
        Crac importedCrac;
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(outputStream.toByteArray())) {
            importedCrac = CracImporters.importCrac("unknown.json", inputStream);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        // test
        assertNotNull(simpleCrac.getBranchCnec("cnec1").getExtension(LoopFlowThresholdImpl.class));
        assertEquals(100, simpleCrac.getBranchCnec("cnec1").getExtension(LoopFlowThresholdImpl.class).getInputThreshold(), 0.1);
        assertEquals(Unit.AMPERE, simpleCrac.getBranchCnec("cnec1").getExtension(LoopFlowThresholdImpl.class).getInputThresholdUnit());

        assertNotNull(simpleCrac.getBranchCnec("cnec2").getExtension(LoopFlowThresholdImpl.class));
        assertEquals(30, simpleCrac.getBranchCnec("cnec2").getExtension(LoopFlowThresholdImpl.class).getInputThreshold(), 0.1);
        assertEquals(Unit.PERCENT_IMAX, simpleCrac.getBranchCnec("cnec2").getExtension(LoopFlowThresholdImpl.class).getInputThresholdUnit());

        assertNull(simpleCrac.getBranchCnec("cnec3").getExtension(LoopFlowThresholdImpl.class));
         */
    }
}
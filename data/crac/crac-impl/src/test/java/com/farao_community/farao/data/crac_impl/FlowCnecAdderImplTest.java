/*
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.farao_community.farao.data.crac_impl;

import com.farao_community.farao.commons.FaraoException;
import com.farao_community.farao.commons.Unit;
import com.farao_community.farao.data.crac_api.*;
import com.farao_community.farao.data.crac_api.cnec.BranchCnec;
import com.farao_community.farao.data.crac_api.cnec.adder.BranchCnecAdder;
import com.farao_community.farao.data.crac_api.threshold.BranchThresholdRule;
import com.farao_community.farao.data.crac_impl.cnec.adder.FlowCnecAdderImpl;
import org.junit.Before;
import org.junit.Test;

import java.util.Optional;

import static com.farao_community.farao.data.crac_api.Side.LEFT;
import static org.junit.Assert.*;

/**
 * @author Peter Mitri {@literal <peter.mitri at rte-france.com>}
 */
public class FlowCnecAdderImplTest {
    private static final double DOUBLE_TOLERANCE = 1e-6;
    private SimpleCrac crac;
    private Contingency contingency1;
    private Instant instant1;
    private Instant instant2;

    @Before
    public void setUp() {
        crac = new SimpleCrac("test-crac");
        contingency1 = crac.newContingency().withId("conId1").add();
        instant1 = crac.newInstant().withId("instId1").setSeconds(10).add();
        instant2 = crac.newInstant().withId("instId2").setSeconds(20).add();
    }

    @Test(expected = NullPointerException.class)
    public void testNullParentFail() {
        new FlowCnecAdderImpl(null);
    }

    @Test(expected = FaraoException.class)
    public void testUniqueNetworkElement() {
        crac.newBranchCnec()
                .newNetworkElement().withId("neId1").withName("neName1").add()
                .newNetworkElement();
    }

    @Test(expected = FaraoException.class)
    public void testNoIdFail() {
        crac.newBranchCnec()
            .withId("cnecName1")
            .setInstant(instant1)
            .setContingency(contingency1)
            .newNetworkElement().withId("neId1").withName("neName1").add()
            .newThreshold().setUnit(Unit.MEGAWATT).setRule(BranchThresholdRule.ON_LEFT_SIDE).setMax(1000.0).add()
            .add();
    }

    @Test(expected = FaraoException.class)
    public void testNoStateInstantFail() {
        crac.newBranchCnec()
            .withId("cnecId1")
            .withName("cnecName1")
            .setContingency(contingency1)
            .newThreshold().setUnit(Unit.MEGAWATT).setMax(1000.0).add()
            .newNetworkElement().withId("neId1").withName("neName1").add()
            .add();
    }

    @Test(expected = FaraoException.class)
    public void testNoNetworkElementFail() {
        crac.newBranchCnec()
            .withId("cnecId1")
            .withName("cnecName1")
            .setInstant(instant1)
            .setContingency(contingency1)
            .newThreshold().setRule(BranchThresholdRule.ON_HIGH_VOLTAGE_LEVEL).setUnit(Unit.MEGAWATT).setMax(1000.0).add()
            .add();
    }

    @Test(expected = FaraoException.class)
    public void testNoThresholdFail() {
        crac.newBranchCnec()
            .withId("cnecId1")
            .withName("cnecName1")
            .setInstant(instant1)
            .setContingency(contingency1)
            .newNetworkElement().withId("neId1").withName("neName1").add()
            .add();
    }

    @Test
    public void testAdd() {
        BranchCnec cnec1 = crac.newBranchCnec()
            .withId("cnecId1")
            .withName("cnecName1")
            .setInstant(instant1)
            .setContingency(contingency1)
            .setOperator("cnec1Operator")
            .newNetworkElement().withId("neId1").withName("neName1").add()
            .newThreshold().setUnit(Unit.MEGAWATT).setRule(BranchThresholdRule.ON_LEFT_SIDE).setMax(1000.0).setMin(-1000.0).add()
            .add();
        BranchCnec cnec2 = crac.newBranchCnec()
            .withId("cnecId2")
            .setInstant(instant2)
            .setOperator("cnec2Operator")
            .newNetworkElement().withId("neId2").add()
            .newThreshold().setUnit(Unit.MEGAWATT).setRule(BranchThresholdRule.ON_LEFT_SIDE).setMax(500.0).add()
            .add();
        assertEquals(2, crac.getBranchCnecs().size());

        // Verify 1st cnec content
        assertEquals(cnec1, crac.getBranchCnec("cnecId1"));
        assertEquals("cnecName1", cnec1.getName());
        assertEquals(contingency1, cnec1.getState().getContingency().orElseThrow());
        assertEquals(instant1, cnec1.getState().getInstant());
        assertEquals("cnec1Operator", cnec1.getOperator());
        assertEquals("neName1", cnec1.getNetworkElement().getName());
        assertEquals(1000.0, cnec1.getUpperBound(LEFT, Unit.MEGAWATT).orElseThrow(), DOUBLE_TOLERANCE);
        assertEquals(-1000.0, cnec1.getLowerBound(LEFT, Unit.MEGAWATT).orElseThrow(), DOUBLE_TOLERANCE);

        // Verify 2nd cnec content
        assertEquals(cnec2, crac.getBranchCnec("cnecId2"));
        assertEquals("cnecId2", cnec2.getName());
        assertEquals(instant2, cnec2.getState().getInstant());
        assertEquals("cnec2Operator", cnec2.getOperator());
        assertEquals(Optional.empty(), cnec2.getState().getContingency());
        assertEquals("neId2", cnec2.getNetworkElement().getName());
        assertEquals(500.0, cnec2.getUpperBound(LEFT, Unit.MEGAWATT).orElseThrow(), DOUBLE_TOLERANCE);
        assertFalse(cnec2.getLowerBound(LEFT, Unit.MEGAWATT).isPresent());
    }

    @Test
    public void testFrmHandling() {
        double maxValueInMw = 100.0;
        double frmInMw = 5.0;
        BranchCnecAdder cnecAdder = crac.newBranchCnec();
        BranchCnec cnec = cnecAdder.withId("Cnec ID")
            .setInstant(instant1)
            .setContingency(contingency1)
            .newNetworkElement().withId("Network Element ID").add()
            .newThreshold().setUnit(Unit.MEGAWATT).setRule(BranchThresholdRule.ON_LEFT_SIDE).setMax(maxValueInMw).setMin(-maxValueInMw).add()
            .setReliabilityMargin(frmInMw)
            .add();
        assertEquals(maxValueInMw - frmInMw, cnec.getUpperBound(LEFT, Unit.MEGAWATT).orElseThrow(FaraoException::new), 0.0);
        assertEquals(frmInMw - maxValueInMw, cnec.getLowerBound(LEFT, Unit.MEGAWATT).orElseThrow(FaraoException::new), 0.0);
    }

    @Test
    public void testNotOptimizedMonitored() {
        BranchCnecAdder cnecAdder = crac.newBranchCnec();
        BranchCnec cnec = cnecAdder.withId("Cnec ID")
            .setInstant(instant1)
            .setContingency(contingency1)
            .newNetworkElement().withId("Network Element ID").add()
            .newThreshold().setUnit(Unit.MEGAWATT).setRule(BranchThresholdRule.ON_LEFT_SIDE).setMax(100.0).setMin(-100.0).add()
            .monitored()
            .add();
        assertFalse(cnec.isOptimized());
        assertTrue(cnec.isMonitored());
    }

    @Test
    public void testOptimizedNotMonitored() {
        BranchCnecAdder cnecAdder = crac.newBranchCnec();
        BranchCnec cnec = cnecAdder.withId("Cnec ID")
            .setInstant(instant1)
            .setContingency(contingency1)
            .newNetworkElement().withId("Network Element ID").add()
            .newThreshold().setUnit(Unit.MEGAWATT).setRule(BranchThresholdRule.ON_LEFT_SIDE).setMax(100.0).setMin(-100.0).add()
            .optimized()
            .add();
        assertTrue(cnec.isOptimized());
        assertFalse(cnec.isMonitored());
    }

    @Test
    public void testNotOptimizedNotMonitored() {
        BranchCnecAdder cnecAdder = crac.newBranchCnec();
        BranchCnec cnec = cnecAdder.withId("Cnec ID")
            .setInstant(instant1)
            .setContingency(contingency1)
            .newNetworkElement().withId("Network Element ID").add()
            .newThreshold().setUnit(Unit.MEGAWATT).setRule(BranchThresholdRule.ON_LEFT_SIDE).setMax(100.0).setMin(-100.0).add()
            .add();
        assertFalse(cnec.isOptimized());
        assertFalse(cnec.isMonitored());
    }
}

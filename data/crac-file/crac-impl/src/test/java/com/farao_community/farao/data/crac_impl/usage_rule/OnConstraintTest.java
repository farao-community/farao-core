/*
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.farao_community.farao.data.crac_impl.usage_rule;

import com.farao_community.farao.data.crac_api.*;
import com.farao_community.farao.data.crac_impl.SimpleCnec;
import com.farao_community.farao.data.crac_impl.SimpleState;
import com.farao_community.farao.data.crac_impl.threshold.RelativeFlowThreshold;
import org.junit.Test;

import java.util.Optional;

import static org.junit.Assert.*;

/**
 * @author Joris Mancini {@literal <joris.mancini at rte-france.com>}
 */
public class OnConstraintTest {

    @Test
    public void getCnec() {
        State initialState = new SimpleState(
            Optional.empty(),
            new Instant("initial-instant", 0)
        );

        OnConstraint rule1 = new OnConstraint(
            UsageMethod.AVAILABLE,
            initialState,
            new SimpleCnec(
                "cnec",
                new NetworkElement("ne1"),
                new RelativeFlowThreshold(Unit.AMPERE, Side.LEFT, Direction.IN, 80),
                initialState
            )
        );

        assertEquals("cnec", rule1.getCnec().getId());
    }

    @Test
    public void testEqualsSameObject() {
        State initialState = new SimpleState(
            Optional.empty(),
            new Instant("initial-instant", 0)
        );

        OnConstraint rule1 = new OnConstraint(
            UsageMethod.AVAILABLE,
            initialState,
            new SimpleCnec(
                "cnec",
                new NetworkElement("ne1"),
                new RelativeFlowThreshold(Unit.AMPERE, Side.LEFT, Direction.IN, 80),
                initialState
            )
        );

        assertEquals(rule1, rule1);
    }

    @Test
    public void testEqualsTrue() {
        State initialState = new SimpleState(
            Optional.empty(),
            new Instant("initial-instant", 0)
        );

        OnConstraint rule1 = new OnConstraint(
            UsageMethod.AVAILABLE,
            initialState,
            new SimpleCnec(
                "cnec",
                new NetworkElement("ne1"),
                new RelativeFlowThreshold(Unit.AMPERE, Side.LEFT, Direction.IN, 80),
                initialState
            )
        );

        OnConstraint rule2 = new OnConstraint(
            UsageMethod.AVAILABLE,
            initialState,
            new SimpleCnec(
                "cnec",
                new NetworkElement("ne1"),
                new RelativeFlowThreshold(Unit.AMPERE, Side.LEFT, Direction.IN, 80),
                initialState
            )
        );

        assertEquals(rule1, rule2);
    }

    @Test
    public void testEqualsFalseNotTheSameObject() {
        State initialState = new SimpleState(
            Optional.empty(),
            new Instant("initial-instant", 0)
        );

        OnConstraint rule1 = new OnConstraint(
            UsageMethod.AVAILABLE,
            initialState,
            new SimpleCnec(
                "cnec",
                new NetworkElement("ne1"),
                new RelativeFlowThreshold(Unit.AMPERE, Side.LEFT, Direction.IN, 80),
                initialState
            )
        );

        assertFalse(rule1.equals(new Instant("fail", 10)));
    }

    @Test
    public void testEqualsFalseForUsageMethod() {
        State initialState = new SimpleState(
            Optional.empty(),
            new Instant("initial-instant", 0)
        );

        OnConstraint rule1 = new OnConstraint(
            UsageMethod.AVAILABLE,
            initialState,
            new SimpleCnec(
                "cnec",
                new NetworkElement("ne1"),
                new RelativeFlowThreshold(Unit.AMPERE, Side.LEFT, Direction.IN, 80),
                initialState
            )
        );

        OnConstraint rule2 = new OnConstraint(
            UsageMethod.FORCED,
            initialState,
            new SimpleCnec(
                "cnec",
                new NetworkElement("ne1"),
                new RelativeFlowThreshold(Unit.AMPERE, Side.LEFT, Direction.IN, 80),
                initialState
            )
        );

        assertNotEquals(rule1, rule2);
    }

    @Test
    public void testEqualsFalseForThreshold() {
        State initialState = new SimpleState(
            Optional.empty(),
            new Instant("initial-instant", 0)
        );

        OnConstraint rule1 = new OnConstraint(
            UsageMethod.AVAILABLE,
            initialState,
            new SimpleCnec(
                "cnec",
                new NetworkElement("ne1"),
                new RelativeFlowThreshold(Unit.AMPERE, Side.LEFT, Direction.IN, 80),
                initialState
            )
        );

        OnConstraint rule2 = new OnConstraint(
            UsageMethod.FORCED,
            initialState,
            new SimpleCnec(
                "cnec",
                new NetworkElement("ne1"),
                new RelativeFlowThreshold(Unit.MEGAWATT, Side.LEFT, Direction.IN, 80),
                initialState
            )
        );

        assertNotEquals(rule1, rule2);
    }

    @Test
    public void testHashCode() {
        State initialState = new SimpleState(
            Optional.empty(),
            new Instant("initial-instant", 0)
        );

        OnConstraint rule1 = new OnConstraint(
            UsageMethod.AVAILABLE,
            initialState,
            new SimpleCnec(
                "cnec",
                new NetworkElement("ne1"),
                new RelativeFlowThreshold(Unit.AMPERE, Side.LEFT, Direction.IN, 80),
                initialState
            )
        );

        OnConstraint rule2 = new OnConstraint(
            UsageMethod.AVAILABLE,
            initialState,
            new SimpleCnec(
                "cnec",
                new NetworkElement("ne1"),
                new RelativeFlowThreshold(Unit.AMPERE, Side.LEFT, Direction.IN, 80),
                initialState
            )
        );

        assertEquals(rule1.hashCode(), rule2.hashCode());
    }

    @Test
    public void testHashCodeFalseForUsageMethod() {
        State initialState = new SimpleState(
            Optional.empty(),
            new Instant("initial-instant", 0)
        );

        OnConstraint rule1 = new OnConstraint(
            UsageMethod.AVAILABLE,
            initialState,
            new SimpleCnec(
                "cnec",
                new NetworkElement("ne1"),
                new RelativeFlowThreshold(Unit.AMPERE, Side.LEFT, Direction.IN, 80),
                initialState
            )
        );

        OnConstraint rule2 = new OnConstraint(
            UsageMethod.FORCED,
            initialState,
            new SimpleCnec(
                "cnec",
                new NetworkElement("ne1"),
                new RelativeFlowThreshold(Unit.AMPERE, Side.LEFT, Direction.IN, 80),
                initialState
            )
        );

        assertNotEquals(rule1.hashCode(), rule2.hashCode());
    }

    @Test
    public void testHashCodeFalseForThreshold() {
        State initialState = new SimpleState(
            Optional.empty(),
            new Instant("initial-instant", 0)
        );

        OnConstraint rule1 = new OnConstraint(
            UsageMethod.AVAILABLE,
            initialState,
            new SimpleCnec(
                "cnec",
                new NetworkElement("ne1"),
                new RelativeFlowThreshold(Unit.AMPERE, Side.LEFT, Direction.IN, 80),
                initialState
            )
        );

        OnConstraint rule2 = new OnConstraint(
            UsageMethod.AVAILABLE,
            initialState,
            new SimpleCnec(
                "cnec",
                new NetworkElement("ne1"),
                new RelativeFlowThreshold(Unit.MEGAWATT, Side.LEFT, Direction.IN, 80),
                initialState
            )
        );

        assertNotEquals(rule1.hashCode(), rule2.hashCode());
    }
}

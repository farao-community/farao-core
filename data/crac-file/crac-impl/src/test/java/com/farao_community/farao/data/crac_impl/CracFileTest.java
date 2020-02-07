/*
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.farao_community.farao.data.crac_impl;

import com.farao_community.farao.commons.FaraoException;
import com.farao_community.farao.data.crac_api.*;
import com.farao_community.farao.data.crac_impl.threshold.AbsoluteFlowThreshold;
import com.farao_community.farao.data.crac_impl.usage_rule.FreeToUse;
import org.junit.Test;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static com.farao_community.farao.data.crac_api.Direction.*;
import static com.farao_community.farao.data.crac_api.Side.*;
import static org.junit.Assert.*;

/**
 * General test file
 *
 * @author Viktor Terrier {@literal <viktor.terrier at rte-france.com>}
 */
public class CracFileTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(CracFileTest.class);

    @Test
    public void testGetInstant() {
        SimpleCrac simpleCrac = new SimpleCrac("test-crac");
        assertEquals(0, simpleCrac.getInstants().size());
    }

    @Test
    public void testAddInstant() {
        SimpleCrac simpleCrac = new SimpleCrac("test-crac");
        assertEquals(0, simpleCrac.getInstants().size());
        simpleCrac.addInstant(new Instant("initial-instant", 0));
        assertEquals(1, simpleCrac.getInstants().size());
        assertNotNull(simpleCrac.getInstant("initial-instant"));
        try {
            simpleCrac.addInstant(new Instant("initial-instant", 12));
            fail();
        } catch (FaraoException e) {
            assertEquals("An instant with the same ID but different seconds already exists.", e.getMessage());
        }
        try {
            simpleCrac.addInstant(new Instant("fail-initial", 0));
            fail();
        } catch (FaraoException e) {
            assertEquals("An instant with the same seconds but different ID already exists.", e.getMessage());
        }
        assertEquals(1, simpleCrac.getInstants().size());
        simpleCrac.addInstant(new Instant("curative", 60));
        assertEquals(2, simpleCrac.getInstants().size());
        assertNotNull(simpleCrac.getInstant("curative"));
    }

    @Test
    public void testGetContingency() {
        Crac simpleCrac = new SimpleCrac("test-crac");
        assertEquals(0, simpleCrac.getContingencies().size());
    }

    @Test
    public void testAddContingency() {
        Crac simpleCrac = new SimpleCrac("test-crac");
        assertEquals(0, simpleCrac.getContingencies().size());
        simpleCrac.addContingency(new ComplexContingency("contingency-1", Collections.singleton(new NetworkElement("ne1"))));
        assertEquals(1, simpleCrac.getContingencies().size());
        assertNotNull(simpleCrac.getContingency("contingency-1"));
        try {
            simpleCrac.addContingency(new ComplexContingency("contingency-1", Collections.singleton(new NetworkElement("ne2"))));
            fail();
        } catch (FaraoException e) {
            assertEquals("A contingency with the same ID and different network elements already exists.", e.getMessage());
        }
        try {
            simpleCrac.addContingency(new ComplexContingency("contingency-2", Collections.singleton(new NetworkElement("ne1"))));
        } catch (FaraoException e) {
            fail();
        }
        assertEquals(2, simpleCrac.getContingencies().size());
        simpleCrac.addContingency(new ComplexContingency("contingency-3", Collections.singleton(new NetworkElement("ne3"))));
        assertEquals(3, simpleCrac.getContingencies().size());
        assertNotNull(simpleCrac.getContingency("contingency-3"));
        assertNull(simpleCrac.getContingency("contingency-fail"));
    }

    @Test
    public void testStates() {
        SimpleCrac simpleCrac = new SimpleCrac("test-crac");
        assertNull(simpleCrac.getPreventiveState());
        assertEquals(0, simpleCrac.getContingencies().size());
        assertEquals(0, simpleCrac.getInstants().size());

        simpleCrac.addState(new SimpleState(Optional.empty(), new Instant("initial-instant", 0)));
        assertNotNull(simpleCrac.getPreventiveState());
        assertEquals("initial-instant", simpleCrac.getPreventiveState().getInstant().getId());

        assertEquals(simpleCrac.getInstant("initial-instant"), simpleCrac.getPreventiveState().getInstant());

        simpleCrac.addState(new SimpleState(
                Optional.of(new ComplexContingency("contingency", Collections.singleton(new NetworkElement("network-element")))),
                new Instant("after-contingency", 60))
        );

        try {
            simpleCrac.addState(new SimpleState(Optional.empty(), new Instant("initial-instant-fail", 0)));
            fail();
        } catch (FaraoException e) {
            assertEquals("An instant with the same seconds but different ID already exists.", e.getMessage());
        }

        try {
            simpleCrac.addState(new SimpleState(Optional.empty(), new Instant("initial-instant", 12)));
            fail();
        } catch (FaraoException e) {
            assertEquals("An instant with the same ID but different seconds already exists.", e.getMessage());
        }

        assertEquals(2, simpleCrac.getInstants().size());
        assertEquals(2, simpleCrac.getStates().size());
        assertEquals(1, simpleCrac.getContingencies().size());

        simpleCrac.addState(new SimpleState(
            Optional.of(new ComplexContingency("contingency-2", Collections.singleton(new NetworkElement("network-element-2")))),
            new Instant("after-contingency", 60))
        );

        assertEquals(2, simpleCrac.getStatesFromInstant("after-contingency").size());

        // Different states pointing at the same instant object
        Instant instant = simpleCrac.getInstant("after-contingency");
        simpleCrac.getStates(instant).forEach(state -> assertSame(instant, state.getInstant()));

        simpleCrac.addState(new SimpleState(
            Optional.of(new ComplexContingency("contingency-2", Collections.singleton(new NetworkElement("network-element-2")))),
            new Instant("after-contingency-bis", 70))
        );

        // Different states pointing at the same contingency object
        Contingency contingency = simpleCrac.getContingency("contingency-2");
        assertEquals(2, simpleCrac.getStates(contingency).size());
        simpleCrac.getStates(contingency).forEach(state -> {
            assertTrue(state.getContingency().isPresent());
            assertSame(contingency, state.getContingency().get());
        }
        );

        State testState = simpleCrac.getState(contingency, instant);
        assertTrue(testState.getContingency().isPresent());
        assertSame(testState.getContingency().get(), contingency);
        assertSame(testState.getInstant(), instant);
    }

    @Test
    public void testGetStatesWithPreventiveInstantId() {
        SimpleCrac simpleCrac = new SimpleCrac("test-crac");

        assertNull(simpleCrac.getStatesFromInstant("initial-instant"));

        simpleCrac.addState(new SimpleState(Optional.empty(), new Instant("initial-instant", 0)));
        assertNotNull(simpleCrac.getStatesFromInstant("initial-instant"));
        assertEquals(1, simpleCrac.getStatesFromInstant("initial-instant").size());
        assertSame(simpleCrac.getStatesFromInstant("initial-instant").iterator().next(), simpleCrac.getPreventiveState());
    }

    @Test
    public void testGetStatesWithInstantIds() {
        SimpleCrac simpleCrac = new SimpleCrac("test-crac");

        simpleCrac.addState(new SimpleState(
            Optional.of(new ComplexContingency("contingency", Collections.singleton(new NetworkElement("network-element")))),
            new Instant("after-contingency", 60))
        );

        simpleCrac.addState(new SimpleState(
            Optional.of(new ComplexContingency("contingency-2", Collections.singleton(new NetworkElement("network-element-2")))),
            new Instant("after-contingency", 60))
        );

        simpleCrac.addState(new SimpleState(
            Optional.of(new ComplexContingency("contingency-2", Collections.singleton(new NetworkElement("network-element-2")))),
            new Instant("after-contingency-bis", 70))
        );

        assertEquals(2, simpleCrac.getStatesFromInstant("after-contingency").size());
        assertEquals(1, simpleCrac.getStatesFromInstant("after-contingency-bis").size());
    }

    @Test
    public void testGetStatesWithContingencyIds() {
        SimpleCrac simpleCrac = new SimpleCrac("test-crac");

        simpleCrac.addState(new SimpleState(
            Optional.of(new ComplexContingency("contingency", Collections.singleton(new NetworkElement("network-element")))),
            new Instant("after-contingency", 60))
        );

        simpleCrac.addState(new SimpleState(
            Optional.of(new ComplexContingency("contingency-2", Collections.singleton(new NetworkElement("network-element-2")))),
            new Instant("after-contingency", 60))
        );

        simpleCrac.addState(new SimpleState(
            Optional.of(new ComplexContingency("contingency-2", Collections.singleton(new NetworkElement("network-element-2")))),
            new Instant("after-contingency-bis", 70))
        );

        assertEquals(1, simpleCrac.getStatesFromContingency("contingency").size());
        assertEquals(2, simpleCrac.getStatesFromContingency("contingency-2").size());
    }

    @Test
    public void testGetStateWithIds() {
        SimpleCrac simpleCrac = new SimpleCrac("test-crac");

        simpleCrac.addState(new SimpleState(
            Optional.of(new ComplexContingency("contingency", Collections.singleton(new NetworkElement("network-element")))),
            new Instant("after-contingency", 60))
        );

        assertNotNull(simpleCrac.getState("contingency", "after-contingency"));
    }

    @Test
    public void testGetStateWithNotExistingContingencyId() {
        SimpleCrac simpleCrac = new SimpleCrac("test-crac");

        simpleCrac.addState(new SimpleState(
            Optional.of(new ComplexContingency("contingency", Collections.singleton(new NetworkElement("network-element")))),
            new Instant("after-contingency", 60))
        );

        assertNull(simpleCrac.getState("fail-contingency", "after-contingency"));
    }

    @Test
    public void testGetStateWithNotExistingInstantId() {
        SimpleCrac simpleCrac = new SimpleCrac("test-crac");

        simpleCrac.addState(new SimpleState(
            Optional.of(new ComplexContingency("contingency", Collections.singleton(new NetworkElement("network-element")))),
            new Instant("after-contingency", 60))
        );

        assertNull(simpleCrac.getState("contingency", "fail-after-contingency"));
    }

    @Test
    public void testGetCnecWithIds() {
        SimpleCrac simpleCrac = new SimpleCrac("test-crac");

        Cnec cnec = new SimpleCnec(
                "cnec",
                new NetworkElement("network-element-1"),
                new AbsoluteFlowThreshold(Unit.AMPERE, LEFT, IN, 1000.),
                new SimpleState(
                    Optional.of(new ComplexContingency("co", Collections.singleton(new NetworkElement("network-element-2")))),
                    new Instant("after-co", 60)
                )
        );

        simpleCrac.addCnec(cnec);

        assertEquals(1, simpleCrac.getCnecs("co", "after-co").size());
        Cnec getCnec = simpleCrac.getCnecs("co", "after-co").iterator().next();
        assertEquals("cnec", getCnec.getId());
        assertEquals("network-element-1", getCnec.getNetworkElement().getId());
    }

    @Test
    public void testOrderedStates() {
        Crac simpleCrac = new SimpleCrac("simple-crac");
        State state1 = new SimpleState(
            Optional.of(new ComplexContingency("contingency-1", Collections.singleton(new NetworkElement("ne1")))),
            new Instant("auto", 60)
        );

        State state2 = new SimpleState(
            Optional.of(new ComplexContingency("contingency-1", Collections.singleton(new NetworkElement("ne1")))),
            new Instant("auto-later", 70)
        );

        State state3 = new SimpleState(
            Optional.of(new ComplexContingency("contingency-1", Collections.singleton(new NetworkElement("ne1")))),
            new Instant("curative", 120)
        );

        simpleCrac.addState(state3);
        simpleCrac.addState(state1);
        simpleCrac.addState(state2);

        Iterator<State> states = simpleCrac.getStatesFromContingency("contingency-1").iterator();
        assertEquals(
                60,
                states.next().getInstant().getSeconds()
        );
        assertEquals(
                70,
                states.next().getInstant().getSeconds()
        );
        assertEquals(
                120,
                states.next().getInstant().getSeconds()
        );

        State state4 = new SimpleState(
            Optional.of(new ComplexContingency("contingency-1", Collections.singleton(new NetworkElement("ne1")))),
            new Instant("intermediate", 100)
        );

        simpleCrac.addState(state4);

        states = simpleCrac.getStatesFromContingency("contingency-1").iterator();
        assertEquals(
                60,
                states.next().getInstant().getSeconds()
        );
        assertEquals(
                70,
                states.next().getInstant().getSeconds()
        );
        assertEquals(
                100,
                states.next().getInstant().getSeconds()
        );
        assertEquals(
                120,
                states.next().getInstant().getSeconds()
        );
    }

    @Test
    public void testAddCnecWithNoConflicts() {
        Crac simpleCrac = new SimpleCrac("simple-crac");

        Cnec cnec1 = new SimpleCnec(
                "cnec1",
                new NetworkElement("network-element-1"),
                new AbsoluteFlowThreshold(Unit.AMPERE, LEFT, IN, 1000.),
                new SimpleState(Optional.empty(), new Instant("initial-instant", 0))
        );

        simpleCrac.addCnec(cnec1);
        assertEquals(0, simpleCrac.getContingencies().size());
        assertNotNull(simpleCrac.getInstant("initial-instant"));
        assertNotNull(simpleCrac.getPreventiveState());
        assertEquals(1, simpleCrac.getCnecs(simpleCrac.getPreventiveState()).size());
        assertSame(simpleCrac.getCnecs(simpleCrac.getPreventiveState()).iterator().next().getState(), simpleCrac.getPreventiveState());

        Cnec cnec2 = new SimpleCnec(
                "cnec2",
                new NetworkElement("network-element-1"),
                new AbsoluteFlowThreshold(Unit.AMPERE, LEFT, IN, 1000.),
                new SimpleState(
                    Optional.of(new ComplexContingency("co", Collections.singleton(new NetworkElement("network-element-2")))),
                    new Instant("after-co", 60)
                )
        );

        simpleCrac.addCnec(cnec2);
        assertEquals(1, simpleCrac.getContingencies().size());
        assertNotNull(simpleCrac.getInstant("after-co"));
        assertNotNull(simpleCrac.getState("co", "after-co"));
        assertSame(simpleCrac.getCnecs(simpleCrac.getState("co", "after-co")).iterator().next().getState(), simpleCrac.getState(simpleCrac.getContingency("co"), simpleCrac.getInstant("after-co")));
    }

    @Test
    public void testAddCnecWithAlreadyExistingState() {
        Crac simpleCrac = new SimpleCrac("simple-crac");

        simpleCrac.addState(new SimpleState(
            Optional.of(new ComplexContingency("co", Collections.singleton(new NetworkElement("network-element-2")))),
            new Instant("after-co", 60)
        ));

        assertEquals(1, simpleCrac.getContingencies().size());
        assertNotNull(simpleCrac.getInstant("after-co"));
        assertNotNull(simpleCrac.getState(simpleCrac.getContingency("co"), simpleCrac.getInstant("after-co")));

        Cnec cnec = new SimpleCnec(
                "cnec2",
                new NetworkElement("network-element-1"),
                new AbsoluteFlowThreshold(Unit.AMPERE, LEFT, IN, 1000.),
                new SimpleState(
                    Optional.of(new ComplexContingency("co", Collections.singleton(new NetworkElement("network-element-2")))),
                    new Instant("after-co", 60)
                )
        );

        simpleCrac.addCnec(cnec);
        assertEquals(1, simpleCrac.getContingencies().size());
        assertNotNull(simpleCrac.getInstant("after-co"));
        assertNotNull(simpleCrac.getState(simpleCrac.getContingency("co"), simpleCrac.getInstant("after-co")));
        assertSame(
                simpleCrac.getCnecs(simpleCrac.getState(simpleCrac.getContingency("co"), simpleCrac.getInstant("after-co"))).iterator().next().getState(),
                simpleCrac.getState(simpleCrac.getContingency("co"), simpleCrac.getInstant("after-co")));
    }

    @Test
    public void testAddCnecWithTwoIdenticalCnecs() {
        Crac simpleCrac = new SimpleCrac("simple-crac");

        Cnec cnec1 = new SimpleCnec(
                "cnec1",
                new NetworkElement("network-element-1"),
                new AbsoluteFlowThreshold(Unit.AMPERE, LEFT, IN, 1000.),
                new SimpleState(
                    Optional.of(new ComplexContingency("co", Collections.singleton(new NetworkElement("network-element-2")))),
                    new Instant("after-co", 60)
                )
        );

        Cnec cnec2 = new SimpleCnec(
                "cnec1",
                new NetworkElement("network-element-1"),
                new AbsoluteFlowThreshold(Unit.AMPERE, LEFT, IN, 1000.),
                new SimpleState(
                    Optional.of(new ComplexContingency("co", Collections.singleton(new NetworkElement("network-element-2")))),
                    new Instant("after-co", 60)
                )
        );

        assertEquals(0, simpleCrac.getCnecs().size());
        simpleCrac.addCnec(cnec1);
        assertEquals(1, simpleCrac.getCnecs().size());
        simpleCrac.addCnec(cnec2);
        assertEquals(1, simpleCrac.getCnecs().size());
    }

    @Test
    public void testAddRangeActionWithNoConflict() {
        Crac simpleCrac = new SimpleCrac("simple-crac");

        RangeAction rangeAction = Mockito.mock(RangeAction.class);
        State state = Mockito.mock(State.class);
        Instant instant = Mockito.mock(Instant.class);
        Mockito.when(instant.getId()).thenReturn("instantid");
        Mockito.when(state.getInstant()).thenReturn(instant);
        Mockito.when(state.getContingency()).thenReturn(Optional.empty());
        Mockito.when(rangeAction.getUsageRules()).thenReturn(Collections.singletonList(new FreeToUse(UsageMethod.AVAILABLE, state)));

        simpleCrac.addRangeAction(rangeAction);

        assertNotNull(simpleCrac.getPreventiveState());
        assertEquals(0, simpleCrac.getCnecs().size());
    }
}

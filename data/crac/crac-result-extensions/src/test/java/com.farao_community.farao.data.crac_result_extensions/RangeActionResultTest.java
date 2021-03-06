/*
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.farao_community.farao.data.crac_result_extensions;

import com.farao_community.farao.data.crac_api.*;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * @author Joris Mancini {@literal <joris.mancini at rte-france.com>}
 */
public class RangeActionResultTest {
    private static final double EPSILON = 0.01;

    private RangeActionResult rangeActionResult;
    private State initialState;
    private State outage1;
    private State curative1;
    private State outage2;
    private State curative2;

    @Before
    public void setUp() {
        initialState = Mockito.mock(State.class);
        outage1 = Mockito.mock(State.class);
        curative1 = Mockito.mock(State.class);
        outage2 = Mockito.mock(State.class);
        curative2 = Mockito.mock(State.class);

        Mockito.when(initialState.getId()).thenReturn("preventive");
        Mockito.when(outage1.getId()).thenReturn("co1 - outage");
        Mockito.when(curative1.getId()).thenReturn("co1 - curative");
        Mockito.when(outage2.getId()).thenReturn("co2 - outage");
        Mockito.when(curative2.getId()).thenReturn("co2 - curative");

        Set<String> states = new HashSet<>();
        states.add(initialState.getId());
        states.add(outage1.getId());
        states.add(curative1.getId());
        states.add(outage2.getId());
        states.add(curative2.getId());
        rangeActionResult = new RangeActionResult(states);
    }

    @Test
    public void constructor() {
        assertTrue(rangeActionResult.setPointPerStates.containsKey(initialState.getId()));
        assertTrue(rangeActionResult.setPointPerStates.containsKey(outage1.getId()));
        assertTrue(rangeActionResult.setPointPerStates.containsKey(curative1.getId()));
        assertTrue(rangeActionResult.setPointPerStates.containsKey(outage2.getId()));
        assertTrue(rangeActionResult.setPointPerStates.containsKey(curative2.getId()));
        assertEquals(5, rangeActionResult.setPointPerStates.size());
    }

    @Test
    public void getSetPoint() {
        rangeActionResult.setSetPoint(outage1.getId(), 15.);
        assertEquals(Double.NaN, rangeActionResult.getSetPoint(initialState.getId()), EPSILON);
        assertEquals(15., rangeActionResult.getSetPoint(outage1.getId()), EPSILON);
    }
}

/*
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.search_tree_rao;

import com.farao_community.farao.commons.FaraoException;
import com.farao_community.farao.rao_api.RaoParameters;
import com.farao_community.farao.rao_api.json.JsonRaoParameters;
import com.powsybl.commons.AbstractConverterTest;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;

import static org.junit.Assert.*;

/**
 * @author Baptiste Seguinot {@literal <baptiste.seguinot at rte-france.com>}
 */
public class JsonSearchTreeRaoParametersTest extends AbstractConverterTest {

    @Test
    public void roundTrip() throws IOException {
        RaoParameters parameters = new RaoParameters();
        parameters.addExtension(SearchTreeRaoParameters.class, new SearchTreeRaoParameters());
        parameters.getExtension(SearchTreeRaoParameters.class).setPreventiveRaoStopCriterion(SearchTreeRaoParameters.PreventiveRaoStopCriterion.MIN_OBJECTIVE);
        parameters.getExtension(SearchTreeRaoParameters.class).setCurativeRaoStopCriterion(SearchTreeRaoParameters.CurativeRaoStopCriterion.PREVENTIVE_OBJECTIVE_AND_SECURE);
        parameters.getExtension(SearchTreeRaoParameters.class).setCurativeRaoMinObjImprovement(983);
        parameters.getExtension(SearchTreeRaoParameters.class).setMaximumSearchDepth(10);
        parameters.getExtension(SearchTreeRaoParameters.class).setRelativeNetworkActionMinimumImpactThreshold(0.1);
        parameters.getExtension(SearchTreeRaoParameters.class).setAbsoluteNetworkActionMinimumImpactThreshold(20);
        roundTripTest(parameters, JsonRaoParameters::write, JsonRaoParameters::read, "/SearchTreeRaoParameters.json");
    }

    @Test
    public void readError() throws IOException {
        try (InputStream is = getClass().getResourceAsStream("/SearchTreeRaoParametersError.json")) {
            JsonRaoParameters.read(is);
            fail();
        } catch (FaraoException e) {
            // should throw
            assertTrue(e.getMessage().contains("Unexpected field"));
        }
    }

    @Test
    public void update() {
        RaoParameters parameters = JsonRaoParameters.read(getClass().getResourceAsStream("/RaoParameters_default.json"));
        JsonRaoParameters.update(parameters, getClass().getResourceAsStream("/RaoParameters_update.json"));
        SearchTreeRaoParameters extension = parameters.getExtension(SearchTreeRaoParameters.class);
        assertNotNull(extension);
        assertEquals(SearchTreeRaoParameters.PreventiveRaoStopCriterion.MIN_OBJECTIVE, extension.getPreventiveRaoStopCriterion());
        assertEquals(5, extension.getMaximumSearchDepth());
        assertEquals(0, extension.getRelativeNetworkActionMinimumImpactThreshold(), 1e-6);
        assertEquals(1, extension.getAbsoluteNetworkActionMinimumImpactThreshold(), 1e-6);
        assertEquals(8, extension.getLeavesInParallel());
        assertTrue(extension.getSkipNetworkActionsFarFromMostLimitingElement());
        assertEquals(2, extension.getMaxNumberOfBoundariesForSkippingNetworkActions());
    }

    @Test(expected = FaraoException.class)
    public void testWrongStopCriterionError() {
        JsonRaoParameters.read(getClass().getResourceAsStream("/SearchTreeRaoParametersStopCriterionError.json"));
    }

    @Test(expected = FaraoException.class)
    public void curativeRaoStopCriterionError() {
        JsonRaoParameters.read(getClass().getResourceAsStream("/SearchTreeRaoParametersCurativeStopCriterionError.json"));
    }
}

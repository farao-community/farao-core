/*
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.rao_api.json;

import com.farao_community.farao.commons.FaraoException;
import com.farao_community.farao.rao_api.RaoParameters;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.google.auto.service.AutoService;
import com.powsybl.commons.AbstractConverterTest;
import com.powsybl.commons.extensions.AbstractExtension;
import com.powsybl.sensitivity.SensitivityAnalysisParameters;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * @author Baptiste Seguinot {@literal <baptiste.seguinot at rte-france.com>}
 */
public class JsonRaoParametersTest extends AbstractConverterTest {

    @Test
    public void roundTripDefault() throws IOException {
        RaoParameters parameters = new RaoParameters();
        roundTripTest(parameters, JsonRaoParameters::write, JsonRaoParameters::read, "/RaoParameters.json");
    }

    @Test
    public void roundTripWithFallback() throws IOException {
        RaoParameters parameters = new RaoParameters();
        SensitivityAnalysisParameters fallbackParameters = new SensitivityAnalysisParameters();
        fallbackParameters.getLoadFlowParameters().setDc(true);
        parameters.setFallbackSensitivityAnalysisParameters(fallbackParameters);
        roundTripTest(parameters, JsonRaoParameters::write, JsonRaoParameters::read, "/RaoParametersWithFallback.json");
    }

    @Test
    public void roundTripSet() throws IOException {
        RaoParameters parameters = new RaoParameters();
        parameters.setObjectiveFunction(RaoParameters.ObjectiveFunction.MAX_MIN_MARGIN_IN_AMPERE);
        parameters.setMaxIterations(30);
        parameters.setPstPenaltyCost(10);
        parameters.setPstSensitivityThreshold(0.2);
        parameters.setFallbackOverCost(10);
        parameters.setRaoWithLoopFlowLimitation(true);
        parameters.setLoopFlowAcceptableAugmentation(20.);
        parameters.setLoopFlowApproximationLevel(RaoParameters.LoopFlowApproximationLevel.UPDATE_PTDF_WITH_TOPO_AND_PST);
        parameters.setLoopFlowConstraintAdjustmentCoefficient(0.5);
        List<String> countries = new ArrayList<>();
        countries.add("BE");
        countries.add("FR");
        parameters.setLoopflowCountries(countries);
        parameters.setMnecViolationCost(20);
        parameters.setMnecAcceptableMarginDiminution(30);
        parameters.setMnecConstraintAdjustmentCoefficient(3);
        parameters.setNegativeMarginObjectiveCoefficient(100);
        List<String> stringBoundaries = new ArrayList<>(Arrays.asList("{FR}-{ES}", "{ES}-{PT}", "{BE}-{22Y201903144---9}-{DE}-{22Y201903145---4}"));
        parameters.setRelativeMarginPtdfBoundariesFromString(stringBoundaries);
        parameters.setPtdfSumLowerBound(0.05);
        parameters.setPerimetersInParallel(15);
        parameters.setPostCheckRaoResults(true);
        roundTripTest(parameters, JsonRaoParameters::write, JsonRaoParameters::read, "/RaoParametersSet.json");
    }

    @Test
    public void writeExtension() throws IOException {
        RaoParameters parameters = new RaoParameters();
        parameters.addExtension(DummyExtension.class, new DummyExtension());
        writeTest(parameters, JsonRaoParameters::write, AbstractConverterTest::compareTxt, "/RaoParametersWithExtension.json");
    }

    @Test
    public void readExtension() {
        RaoParameters parameters = JsonRaoParameters.read(getClass().getResourceAsStream("/RaoParametersWithExtension.json"));
        assertEquals(1, parameters.getExtensions().size());
        assertNotNull(parameters.getExtension(DummyExtension.class));
        assertNotNull(parameters.getExtensionByName("dummy-extension"));
    }

    @Test(expected = FaraoException.class)
    public void readError() {
        JsonRaoParameters.read(getClass().getResourceAsStream("/RaoParametersError.json"));
    }

    @Test(expected = FaraoException.class)
    public void loopFlowApproximationLevelError() {
        JsonRaoParameters.read(getClass().getResourceAsStream("/RaoParametersWithLoopFlowError.json"));
    }

    static class DummyExtension extends AbstractExtension<RaoParameters> {

        DummyExtension() {
            super();
        }

        @Override
        public String getName() {
            return "dummy-extension";
        }
    }

    @AutoService(JsonRaoParameters.ExtensionSerializer.class)
    public static class DummySerializer implements JsonRaoParameters.ExtensionSerializer<DummyExtension> {

        @Override
        public void serialize(DummyExtension extension, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException {
            jsonGenerator.writeStartObject();
            jsonGenerator.writeEndObject();
        }

        @Override
        public DummyExtension deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) {
            return new DummyExtension();
        }

        @Override
        public String getExtensionName() {
            return "dummy-extension";
        }

        @Override
        public String getCategoryName() {
            return "rao-parameters";
        }

        @Override
        public Class<? super DummyExtension> getExtensionClass() {
            return DummyExtension.class;
        }
    }
}

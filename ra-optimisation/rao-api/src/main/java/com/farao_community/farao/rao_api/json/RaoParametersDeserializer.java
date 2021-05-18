/*
 * Copyright (c) 2019, All partners of the iTesla project (http://www.itesla-project.eu/consortium)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.rao_api.json;

import com.farao_community.farao.commons.FaraoException;
import com.farao_community.farao.rao_api.RaoParameters;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.powsybl.commons.extensions.Extension;
import com.powsybl.commons.json.JsonUtil;
import com.powsybl.sensitivity.SensitivityAnalysisParameters;
import com.powsybl.sensitivity.json.JsonSensitivityAnalysisParameters;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Baptiste Seguinot {@literal <baptiste.seguinot at rte-france.com>}
 */
public class RaoParametersDeserializer extends StdDeserializer<RaoParameters> {

    RaoParametersDeserializer() {
        super(RaoParameters.class);
    }

    @Override
    public RaoParameters deserialize(JsonParser parser, DeserializationContext deserializationContext) throws IOException {
        return deserialize(parser, deserializationContext, new RaoParameters());
    }

    @Override
    public RaoParameters deserialize(JsonParser parser, DeserializationContext deserializationContext, RaoParameters parameters) throws IOException {

        List<Extension<RaoParameters>> extensions = Collections.emptyList();
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            switch (parser.getCurrentName()) {
                case "version":
                    parser.nextToken();
                    break;
                case "objective-function":
                    parameters.setObjectiveFunction(stringToObjectiveFunction(parser.nextTextValue()));
                    break;
                case "max-number-of-iterations":
                    parser.nextToken();
                    parameters.setMaxIterations(parser.getIntValue());
                    break;
                case "pst-penalty-cost":
                    parser.nextToken();
                    parameters.setPstPenaltyCost(parser.getDoubleValue());
                    break;
                case "pst-sensitivity-threshold":
                    parser.nextToken();
                    parameters.setPstSensitivityThreshold(parser.getDoubleValue());
                    break;
                case "sensitivity-fallback-over-cost":
                    parser.nextToken();
                    parameters.setFallbackOverCost(parser.getDoubleValue());
                    break;
                case "rao-with-loop-flow-limitation":
                    parser.nextToken();
                    parameters.setRaoWithLoopFlowLimitation(parser.getBooleanValue());
                    break;
                case "loop-flow-acceptable-augmentation":
                    parser.nextToken();
                    parameters.setLoopFlowAcceptableAugmentation(parser.getDoubleValue());
                    break;
                case "loop-flow-approximation":
                    parameters.setLoopFlowApproximationLevel(stringToLoopFlowApproximationLevel(parser.nextTextValue()));
                    break;
                case "loop-flow-constraint-adjustment-coefficient":
                    parser.nextToken();
                    parameters.setLoopFlowConstraintAdjustmentCoefficient(parser.getDoubleValue());
                    break;
                case "loop-flow-violation-cost":
                    parser.nextToken();
                    parameters.setLoopFlowViolationCost(parser.getDoubleValue());
                    break;
                case "loop-flow-countries":
                    parser.nextToken();
                    ObjectMapper objectMapper = new ObjectMapper();
                    JsonNode arrayNode = objectMapper.readTree(parser);
                    List<String> countryStrings = objectMapper.readValue(arrayNode.traverse(), new TypeReference<ArrayList<String>>() { });
                    parameters.setLoopflowCountries(countryStrings);
                    break;
                case "mnec-acceptable-margin-diminution":
                    parser.nextToken();
                    parameters.setMnecAcceptableMarginDiminution(parser.getDoubleValue());
                    break;
                case "mnec-violation-cost":
                    parser.nextToken();
                    parameters.setMnecViolationCost(parser.getDoubleValue());
                    break;
                case "mnec-constraint-adjustment-coefficient":
                    parser.nextToken();
                    parameters.setMnecConstraintAdjustmentCoefficient(parser.getDoubleValue());
                    break;
                case "negative-margin-objective-coefficient":
                    parser.nextToken();
                    parameters.setNegativeMarginObjectiveCoefficient(parser.getDoubleValue());
                    break;
                case "sensitivity-parameters":
                    parser.nextToken();
                    JsonSensitivityAnalysisParameters.deserialize(parser, deserializationContext, parameters.getDefaultSensitivityAnalysisParameters());
                    break;
                case "fallback-sensitivity-parameters":
                    parser.nextToken();
                    if (parameters.getFallbackSensitivityAnalysisParameters() == null) {
                        parameters.setFallbackSensitivityAnalysisParameters(new SensitivityAnalysisParameters());
                    }
                    JsonSensitivityAnalysisParameters.deserialize(parser, deserializationContext, parameters.getFallbackSensitivityAnalysisParameters());
                    break;
                case "relative-margin-ptdf-boundaries":
                    if (parser.getCurrentToken() == JsonToken.START_ARRAY) {
                        List<String> boundaries = new ArrayList<>();
                        while (parser.nextToken() != JsonToken.END_ARRAY) {
                            boundaries.add(parser.getValueAsString());
                        }
                        parameters.setRelativeMarginPtdfBoundariesFromString(boundaries);
                    }
                    break;
                case "ptdf-sum-lower-bound":
                    parser.nextToken();
                    parameters.setPtdfSumLowerBound(parser.getDoubleValue());
                    break;
                case "perimeters-in-parallel":
                    parser.nextToken();
                    parameters.setPerimetersInParallel(parser.getIntValue());
                    break;
                case "post-check-rao-results":
                    parser.nextToken();
                    parameters.setPostCheckRaoResults(parser.getBooleanValue());
                    break;
                case "extensions":
                    parser.nextToken();
                    if (parameters.getExtensions().isEmpty()) {
                        extensions = JsonUtil.readExtensions(parser, deserializationContext, JsonRaoParameters.getExtensionSerializers());
                    } else {
                        JsonUtil.updateExtensions(parser, deserializationContext, JsonRaoParameters.getExtensionSerializers(), parameters);
                    }
                    break;
                default:
                    throw new FaraoException("Unexpected field: " + parser.getCurrentName());
            }
        }

        JsonRaoParameters.getExtensionSerializers().addExtensions(parameters, extensions);
        return parameters;
    }

    private RaoParameters.ObjectiveFunction stringToObjectiveFunction(String string) {
        try {
            return RaoParameters.ObjectiveFunction.valueOf(string);
        } catch (IllegalArgumentException e) {
            throw new FaraoException(String.format("Unknown objective function value: %s", string));
        }
    }

    private RaoParameters.LoopFlowApproximationLevel stringToLoopFlowApproximationLevel(String string) {
        try {
            return RaoParameters.LoopFlowApproximationLevel.valueOf(string);
        } catch (IllegalArgumentException e) {
            throw new FaraoException(String.format("Unknown loopflow approximation level: %s", string));
        }
    }
}

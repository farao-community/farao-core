/*
 * Copyright (c) 2018, All partners of the iTesla project (http://www.itesla-project.eu/consortium)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.rao_api.json;

import com.farao_community.farao.commons.FaraoException;
import com.farao_community.farao.rao_api.RaoParameters;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.powsybl.commons.json.JsonUtil;
import com.powsybl.sensitivity.json.JsonSensitivityAnalysisParameters;

import java.io.IOException;

/**
 * @author Baptiste Seguinot {@literal <baptiste.seguinot at rte-france.com>}
 */
public class RaoParametersSerializer extends StdSerializer<RaoParameters> {

    RaoParametersSerializer() {
        super(RaoParameters.class);
    }

    @Override
    public void serialize(RaoParameters parameters, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException {
        jsonGenerator.writeStartObject();
        jsonGenerator.writeStringField("version", RaoParameters.VERSION);
        jsonGenerator.writeObjectField("objective-function", parameters.getObjectiveFunction());
        jsonGenerator.writeNumberField("max-number-of-iterations", parameters.getMaxIterations());
        jsonGenerator.writeNumberField("pst-penalty-cost", parameters.getPstPenaltyCost());
        jsonGenerator.writeNumberField("pst-sensitivity-threshold", parameters.getPstSensitivityThreshold());
        jsonGenerator.writeNumberField("sensitivity-fallback-over-cost", parameters.getFallbackOverCost());
        jsonGenerator.writeBooleanField("rao-with-loop-flow-limitation", parameters.isRaoWithLoopFlowLimitation());
        jsonGenerator.writeNumberField("loop-flow-acceptable-augmentation", parameters.getLoopFlowAcceptableAugmentation());
        jsonGenerator.writeObjectField("loop-flow-approximation", parameters.getLoopFlowApproximationLevel());
        jsonGenerator.writeNumberField("loop-flow-constraint-adjustment-coefficient", parameters.getLoopFlowConstraintAdjustmentCoefficient());
        jsonGenerator.writeNumberField("loop-flow-violation-cost", parameters.getLoopFlowViolationCost());
        jsonGenerator.writeFieldName("loop-flow-countries");
        jsonGenerator.writeStartArray();
        parameters.getLoopflowCountries().stream().map(country -> country.toString()).sorted().forEach(s -> {
            try {
                jsonGenerator.writeString(s);
            } catch (IOException e) {
                throw new FaraoException("error while serializing loopflow countries", e);
            }
        });
        jsonGenerator.writeEndArray();
        jsonGenerator.writeNumberField("mnec-acceptable-margin-diminution", parameters.getMnecAcceptableMarginDiminution());
        jsonGenerator.writeNumberField("mnec-violation-cost", parameters.getMnecViolationCost());
        jsonGenerator.writeNumberField("mnec-constraint-adjustment-coefficient", parameters.getMnecConstraintAdjustmentCoefficient());
        jsonGenerator.writeNumberField("negative-margin-objective-coefficient", parameters.getNegativeMarginObjectiveCoefficient());
        jsonGenerator.writeArrayFieldStart("relative-margin-ptdf-boundaries");
        for (String countryPair : parameters.getRelativeMarginPtdfBoundariesAsString()) {
            jsonGenerator.writeString(countryPair);
        }
        jsonGenerator.writeEndArray();
        jsonGenerator.writeNumberField("ptdf-sum-lower-bound", parameters.getPtdfSumLowerBound());
        jsonGenerator.writeNumberField("perimeters-in-parallel", parameters.getPerimetersInParallel());
        jsonGenerator.writeBooleanField("post-check-rao-results", parameters.isPostCheckRaoResults());
        jsonGenerator.writeFieldName("sensitivity-parameters");
        JsonSensitivityAnalysisParameters.serialize(parameters.getDefaultSensitivityAnalysisParameters(), jsonGenerator, serializerProvider);
        if (parameters.getFallbackSensitivityAnalysisParameters() != null) {
            jsonGenerator.writeFieldName("fallback-sensitivity-parameters");
            JsonSensitivityAnalysisParameters.serialize(parameters.getFallbackSensitivityAnalysisParameters(), jsonGenerator, serializerProvider);
        }
        JsonUtil.writeExtensions(parameters, jsonGenerator, serializerProvider, JsonRaoParameters.getExtensionSerializers());
        jsonGenerator.writeEndObject();
    }
}

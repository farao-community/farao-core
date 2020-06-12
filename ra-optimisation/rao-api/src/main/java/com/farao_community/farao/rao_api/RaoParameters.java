/*
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.rao_api;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.powsybl.commons.config.PlatformConfig;
import com.powsybl.commons.extensions.AbstractExtendable;
import com.powsybl.commons.extensions.Extension;
import com.powsybl.commons.extensions.ExtensionConfigLoader;
import com.powsybl.commons.extensions.ExtensionProviders;

import java.util.Objects;

/**
 * Parameters for rao
 * Extensions may be added, for instance for implementation-specific parameters.
 *
 * @author Baptiste Seguinot {@literal <baptiste.seguinot at rte-france.com>}
 */
public class RaoParameters extends AbstractExtendable<RaoParameters> {

    /**
     * A configuration loader interface for the RaoParameters extensions loaded from the platform configuration
     * @param <E> The extension class
     */
    public static interface ConfigLoader<E extends Extension<RaoParameters>> extends ExtensionConfigLoader<RaoParameters, E> {
    }

    public static final String VERSION = "1.0";

    private static final Supplier<ExtensionProviders<ConfigLoader>> PARAMETERS_EXTENSIONS_SUPPLIER =
        Suppliers.memoize(() -> ExtensionProviders.createProvider(ConfigLoader.class, "rao-parameters"));

    /**
     * Load parameters from platform default config.
     */
    public static RaoParameters load() {
        return load(PlatformConfig.defaultConfig());
    }

    /**
     * Load parameters from a provided platform config.
     */
    public static RaoParameters load(PlatformConfig platformConfig) {
        Objects.requireNonNull(platformConfig);

        RaoParameters parameters = new RaoParameters();
        load(parameters, platformConfig);
        parameters.readExtensions(platformConfig);

        return parameters;
    }

    protected static void load(RaoParameters parameters, PlatformConfig platformConfig) {
        Objects.requireNonNull(parameters);
        Objects.requireNonNull(platformConfig);

        platformConfig.getOptionalModuleConfig("rao-parameters")
            .ifPresent(config -> {
                parameters.setRaoWithLoopFlowLimitation(config.getBooleanProperty("rao-with-loop-flow-limitation", DEFAULT_RAO_WITH_LOOP_FLOW_LIMITATION));
                parameters.setLoopflowApproximation(config.getBooleanProperty("loopflow-approximation", DEFAULT_LOOPFLOW_APPROXIMATION));
                parameters.setLoopflowConstraintAdjustmentCoefficient(config.getDoubleProperty("loopflow-constraint-adjustment-coefficient", DEFAULT_LOOPFLOW_CONSTRAINT_ADJUSTMENT_COEFFICIENT));
                parameters.setLoopflowViolationCost(config.getDoubleProperty("loopflow-violation-cost", DEFAULT_LOOPFLOW_VIOLATION_COST));
            });
    }

    private void readExtensions(PlatformConfig platformConfig) {
        for (ExtensionConfigLoader provider : PARAMETERS_EXTENSIONS_SUPPLIER.get().getProviders()) {
            addExtension(provider.getExtensionClass(), provider.load(platformConfig));
        }
    }

    //loop flow parameter section
    static final boolean DEFAULT_RAO_WITH_LOOP_FLOW_LIMITATION = false; //loop flow is for CORE D2CC, default value set to false
    static final boolean DEFAULT_LOOPFLOW_APPROXIMATION = false;
    private static final double DEFAULT_LOOPFLOW_CONSTRAINT_ADJUSTMENT_COEFFICIENT = 0.0;
    private static final double DEFAULT_LOOPFLOW_VIOLATION_COST = 0.0;

    private boolean raoWithLoopFlowLimitation = DEFAULT_RAO_WITH_LOOP_FLOW_LIMITATION;
    private double loopflowConstraintAdjustmentCoefficient = DEFAULT_LOOPFLOW_CONSTRAINT_ADJUSTMENT_COEFFICIENT;
    private double loopflowViolationCost = DEFAULT_LOOPFLOW_VIOLATION_COST;

    /**
     *  loopflow approximation means using previous calculated ptdf and net position values to compute loopflow
     *  ptdf is calculated by a sensitivity analysis, net position is derived from current network
     *  if "loopflowApproximation" is set to "false", then each loopflow computation do a sensi for ptdf;
     *  if "loopflowApproximation" is set to "true", loopflow computation tries to use previous saved ptdf and netposition.
     *  note: Loopflow = reference flow - ptdf * net position
     */
    private boolean loopflowApproximation = DEFAULT_LOOPFLOW_APPROXIMATION;

    public void setRaoWithLoopFlowLimitation(boolean raoWithLoopFlowLimitation) {
        this.raoWithLoopFlowLimitation = raoWithLoopFlowLimitation;
    }

    public void setLoopflowConstraintAdjustmentCoefficient(double loopflowConstraintAdjustmentCoefficient) {
        this.loopflowConstraintAdjustmentCoefficient = loopflowConstraintAdjustmentCoefficient;
    }

    public void setLoopflowViolationCost(double loopflowViolationCost) {
        this.loopflowViolationCost = loopflowViolationCost;
    }

    public boolean isRaoWithLoopFlowLimitation() {
        return raoWithLoopFlowLimitation;
    }

    public boolean isLoopflowApproximation() {
        return loopflowApproximation;
    }

    public void setLoopflowApproximation(boolean loopflowApproximation) {
        this.loopflowApproximation = loopflowApproximation;
    }

    public double getLoopflowConstraintAdjustmentCoefficient() {
        return loopflowConstraintAdjustmentCoefficient;
    }

    public double getLoopflowViolationCost() {
        return loopflowViolationCost;
    }
    //end loop flow parameter section
}

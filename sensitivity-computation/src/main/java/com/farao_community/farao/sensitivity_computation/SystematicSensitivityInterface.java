/*
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.farao_community.farao.sensitivity_computation;

import com.farao_community.farao.commons.FaraoException;
import com.farao_community.farao.commons.Unit;
import com.farao_community.farao.data.crac_api.Cnec;
import com.farao_community.farao.data.crac_api.RangeAction;
import com.farao_community.farao.flowbased_computation.glsk_provider.GlskProvider;
import com.powsybl.iidm.network.Network;
import com.powsybl.sensitivity.SensitivityComputationParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.Set;


/**
 * An interface with the engine that computes sensitivities and flows needed in the RAO.
 *
 * @author Viktor Terrier {@literal <viktor.terrier at rte-france.com>}
 * @author Philippe Edwards {@literal <philippe.edwards at rte-france.com>}
 * @author Baptiste Seguinot {@literal <baptiste.seguinot at rte-france.com>}
 */
public final class SystematicSensitivityInterface {

    private static final Logger LOGGER = LoggerFactory.getLogger(SystematicSensitivityInterface.class);

    /**
     * Sensitivity configurations, containing the default and fallback configurations
     * of the sensitivity computation
     */
    private SensitivityComputationParameters defaultParameters;
    private SensitivityComputationParameters fallbackParameters;

    /**
     * The sensitivity provider to be used in the sensitivity computation
     */
    private SensitivityProvider sensitivityProvider;

    /**
     * A boolean indicating whether or not the fallback mode of the sensitivity computation
     * engine is active.
     */
    private boolean fallbackMode = false;

    /**
     * Builder
     */
    public static final class SystematicSensitivityInterfaceBuilder {
        private SensitivityComputationParameters defaultParameters;
        private SensitivityComputationParameters fallbackParameters;
        private MultipleSensitivityProvider multipleSensitivityProvider = new MultipleSensitivityProvider();
        private boolean providerInitialised = false;

        private SystematicSensitivityInterfaceBuilder() {

        }

        public SystematicSensitivityInterfaceBuilder withFallbackParameters(SensitivityComputationParameters fallbackParameters) {
            this.fallbackParameters = fallbackParameters;
            return this;
        }

        public SystematicSensitivityInterfaceBuilder withDefaultParameters(SensitivityComputationParameters defaultParameters) {
            this.defaultParameters = defaultParameters;
            return this;
        }

        public SystematicSensitivityInterfaceBuilder withSensitivityProvider(SensitivityProvider sensitivityProvider) {
            this.multipleSensitivityProvider.addProvider(sensitivityProvider);
            providerInitialised = true;
            return this;
        }

        public SystematicSensitivityInterfaceBuilder withPtdfSensitivities(GlskProvider glskProvider, Set<Cnec> cnecs) {
            PtdfSensitivityProvider ptdfSensitivityProvider = new PtdfSensitivityProvider(glskProvider);
            ptdfSensitivityProvider.addCnecs(cnecs);
            this.multipleSensitivityProvider.addProvider(ptdfSensitivityProvider);
            providerInitialised = true;
            return this;
        }

        public SystematicSensitivityInterfaceBuilder withRangeActionSensitivities(Set<RangeAction> rangeActions, Set<Cnec> cnecs) {
            RangeActionSensitivityProvider rangeActionSensitivityProvider = new RangeActionSensitivityProvider();
            rangeActionSensitivityProvider.addSensitivityFactors(rangeActions, cnecs);
            multipleSensitivityProvider.addProvider(rangeActionSensitivityProvider);
            providerInitialised = true;
            return this;
        }

        public SystematicSensitivityInterface build() {
            if (!providerInitialised) {
                throw new SensitivityComputationException("Sensitivity provider is mandatory when building a SystematicSensitivityInterface.");
            }
            if (Objects.isNull(defaultParameters)) {
                defaultParameters = new SensitivityComputationParameters();
            }
            SystematicSensitivityInterface systematicSensitivityInterface = new SystematicSensitivityInterface();
            systematicSensitivityInterface.defaultParameters = defaultParameters;
            systematicSensitivityInterface.fallbackParameters = fallbackParameters;
            systematicSensitivityInterface.sensitivityProvider = multipleSensitivityProvider;
            return systematicSensitivityInterface;
        }
    }

    public static SystematicSensitivityInterfaceBuilder builder() {
        return new SystematicSensitivityInterfaceBuilder();
    }

    private SystematicSensitivityInterface() {

    }

    public boolean isFallback() {
        return fallbackMode;
    }

    /**
     * Run the systematic sensitivity analysis on the given network and crac, and associates the
     * SystematicSensitivityAnalysisResult to the given network variant.
     *
     * Throw a SensitivityComputationException if the computation fails.
     */
    public SystematicSensitivityResult run(Network network, Set<Cnec> cnecs, Unit defaultUnit) {
        SensitivityComputationParameters sensitivityComputationParameters = fallbackMode ? fallbackParameters : defaultParameters;
        if (Objects.isNull(sensitivityProvider)) {
            throw new SensitivityComputationException("Sensitivity provider was not defined.");
        }

        try {
            return runWithConfig(network, cnecs, sensitivityComputationParameters, defaultUnit);
        } catch (SensitivityComputationException e) {
            if (!fallbackMode && fallbackParameters != null) { // default mode fails, retry in fallback mode
                LOGGER.warn("Error while running the sensitivity computation with default parameters, fallback sensitivity parameters are now used.");
                fallbackMode = true;
                return run(network, cnecs, defaultUnit);
            } else if (!fallbackMode) { // no fallback mode available, throw an exception
                throw new SensitivityComputationException("Sensitivity computation failed with default parameters. No fallback parameters available.", e);
            } else { // fallback mode fails, throw an exception
                throw new SensitivityComputationException("Sensitivity computation failed with all available sensitivity parameters.", e);
            }
        }
    }

    // Method for tests
    SystematicSensitivityResult run(Network network, Set<Cnec> cnecs) {
        return run(network, cnecs, Unit.AMPERE);
    }

    /**
     * Run the systematic sensitivity analysis with given SensitivityComputationParameters, throw a
     * SensitivityComputationException is the computation fails.
     */
    private SystematicSensitivityResult runWithConfig(Network network, Set<Cnec> cnecs, SensitivityComputationParameters sensitivityComputationParameters, Unit defaultUnit) {

        try {
            SystematicSensitivityResult tempSystematicSensitivityAnalysisResult = SystematicSensitivityService
                .runSensitivity(network, network.getVariantManager().getWorkingVariantId(), sensitivityProvider, sensitivityComputationParameters);

            if (!tempSystematicSensitivityAnalysisResult.isSuccess()) {
                throw new SensitivityComputationException("Some output data of the sensitivity computation are missing.");
            }

            checkSensiResults(cnecs, tempSystematicSensitivityAnalysisResult, defaultUnit);
            return tempSystematicSensitivityAnalysisResult;

        } catch (Exception e) {
            throw new SensitivityComputationException("Sensitivity computation fails.", e);
        }
    }

    private void checkSensiResults(Set<Cnec> cnecs, SystematicSensitivityResult systematicSensitivityAnalysisResult, Unit defaultUnit) {
        if (!systematicSensitivityAnalysisResult.isSuccess()) {
            throw new SensitivityComputationException("Status of the sensitivity result indicates a failure.");
        }

        if (cnecs.stream()
            .map(systematicSensitivityAnalysisResult::getReferenceFlow)
            .anyMatch(f -> Double.isNaN(f))) {
            throw new SensitivityComputationException("Flow values are missing from the output of the sensitivity analysis.");
        }

        if (cnecs.stream()
            .map(systematicSensitivityAnalysisResult::getReferenceIntensity)
            .anyMatch(f -> Double.isNaN(f)) && !isFallback() && defaultUnit.equals(Unit.AMPERE)) {
            // in default mode, this means that there is an error in the sensitivity computation, or an
            // incompatibility with the sensitivity computation mode (i.e. the sensitivity computation is
            // made in DC mode and no intensity are computed).
            throw new FaraoException("Intensity values are missing from the output of the sensitivity analysis. Min margin cannot be calculated in AMPERE.");
        }
    }
}

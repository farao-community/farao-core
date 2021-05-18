/*
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.rao_api;

import com.farao_community.farao.commons.FaraoException;
import com.farao_community.farao.commons.Unit;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.powsybl.commons.config.PlatformConfig;
import com.powsybl.commons.extensions.AbstractExtendable;
import com.powsybl.commons.extensions.Extension;
import com.powsybl.commons.extensions.ExtensionConfigLoader;
import com.powsybl.commons.extensions.ExtensionProviders;
import com.powsybl.iidm.network.Country;
import com.powsybl.sensitivity.SensitivityAnalysisParameters;

import java.util.*;
import java.util.stream.Collectors;

import static java.lang.Math.max;

/**
 * Parameters for rao
 * Extensions may be added, for instance for implementation-specific parameters.
 *
 * @author Baptiste Seguinot {@literal <baptiste.seguinot at rte-france.com>}
 */
public class RaoParameters extends AbstractExtendable<RaoParameters> {

    public enum ObjectiveFunction {
        MAX_MIN_MARGIN_IN_MEGAWATT(Unit.MEGAWATT, false),
        MAX_MIN_MARGIN_IN_AMPERE(Unit.AMPERE, false),
        MAX_MIN_RELATIVE_MARGIN_IN_MEGAWATT(Unit.MEGAWATT, true),
        MAX_MIN_RELATIVE_MARGIN_IN_AMPERE(Unit.AMPERE, true);

        private Unit unit;
        private boolean requirePtdf;

        ObjectiveFunction(Unit unit, boolean requirePtdf) {
            this.unit = unit;
            this.requirePtdf = requirePtdf;
        }

        public Unit getUnit() {
            return unit;
        }

        public boolean doesRequirePtdf() {
            return requirePtdf;
        }

        public boolean relativePositiveMargins() {
            return this.equals(MAX_MIN_RELATIVE_MARGIN_IN_MEGAWATT) || this.equals(MAX_MIN_RELATIVE_MARGIN_IN_AMPERE);
        }
    }

    public enum LoopFlowApproximationLevel {
        FIXED_PTDF, // compute PTDFs only once at beginning of RAO (best performance, worst accuracy)
        UPDATE_PTDF_WITH_TOPO, // recompute PTDFs after every topological change in the network (worse performance, better accuracy for AC, best accuracy for DC)
        UPDATE_PTDF_WITH_TOPO_AND_PST; // recompute PTDFs after every topological or PST change in the network (worst performance, best accuracy for AC)

        public boolean shouldUpdatePtdfWithTopologicalChange() {
            return !this.equals(FIXED_PTDF);
        }

        public boolean shouldUpdatePtdfWithPstChange() {
            return this.equals(UPDATE_PTDF_WITH_TOPO_AND_PST);
        }
    }

    public static final ObjectiveFunction DEFAULT_OBJECTIVE_FUNCTION = ObjectiveFunction.MAX_MIN_MARGIN_IN_MEGAWATT;
    public static final int DEFAULT_MAX_ITERATIONS = 10;
    public static final double DEFAULT_FALLBACK_OVER_COST = 0;
    public static final boolean DEFAULT_RAO_WITH_LOOP_FLOW_LIMITATION = false; //loop flow is for CORE D2CC, default value set to false
    public static final boolean DEFAULT_SECURITY_ANALYSIS_WITHOUT_RAO = false;
    public static final double DEFAULT_PST_SENSITIVITY_THRESHOLD = 0.0;
    public static final double DEFAULT_LOOP_FLOW_ACCEPTABLE_AUGMENTATION = 0.0;
    public static final LoopFlowApproximationLevel DEFAULT_LOOP_FLOW_APPROXIMATION_LEVEL = LoopFlowApproximationLevel.FIXED_PTDF;
    public static final double DEFAULT_LOOP_FLOW_CONSTRAINT_ADJUSTMENT_COEFFICIENT = 0.0;
    public static final double DEFAULT_LOOP_FLOW_VIOLATION_COST = 0.0;
    public static final double DEFAULT_PST_PENALTY_COST = 0.01;
    public static final double DEFAULT_MNEC_ACCEPTABLE_MARGIN_DIMINUTION = 50.0;
    public static final double DEFAULT_MNEC_VIOLATION_COST = 10.0;
    public static final double DEFAULT_MNEC_CONSTRAINT_ADJUSTMENT_COEFFICIENT = 0.0;
    public static final double DEFAULT_NEGATIVE_MARGIN_OBJECTIVE_COEFFICIENT = 1000;
    public static final double DEFAULT_PTDF_SUM_LOWER_BOUND = 0.01;
    public static final int DEFAULT_PERIMETERS_IN_PARALLEL = 1;
    public static final boolean DEFAULT_POST_CHECK_RAO_RESULTS = false;

    private ObjectiveFunction objectiveFunction = DEFAULT_OBJECTIVE_FUNCTION;
    private int maxIterations = DEFAULT_MAX_ITERATIONS;
    private double pstPenaltyCost = DEFAULT_PST_PENALTY_COST;
    private double pstSensitivityThreshold = DEFAULT_PST_SENSITIVITY_THRESHOLD;
    private double fallbackOverCost = DEFAULT_FALLBACK_OVER_COST;
    private boolean raoWithLoopFlowLimitation = DEFAULT_RAO_WITH_LOOP_FLOW_LIMITATION;
    private double mnecAcceptableMarginDiminution = DEFAULT_MNEC_ACCEPTABLE_MARGIN_DIMINUTION; // always in MW
    private LoopFlowApproximationLevel loopFlowApproximationLevel = DEFAULT_LOOP_FLOW_APPROXIMATION_LEVEL;
    private double loopFlowConstraintAdjustmentCoefficient = DEFAULT_LOOP_FLOW_CONSTRAINT_ADJUSTMENT_COEFFICIENT;
    private double loopFlowViolationCost = DEFAULT_LOOP_FLOW_VIOLATION_COST;
    private Set<Country> loopflowCountries = new HashSet<>(); //Empty by default
    private double loopFlowAcceptableAugmentation = DEFAULT_LOOP_FLOW_ACCEPTABLE_AUGMENTATION; // always in MW
    private double mnecViolationCost = DEFAULT_MNEC_VIOLATION_COST; // "A equivalent cost per A violation" or "MW per MW", depending on the objective function
    private double mnecConstraintAdjustmentCoefficient = DEFAULT_MNEC_CONSTRAINT_ADJUSTMENT_COEFFICIENT; // always in MW
    private double negativeMarginObjectiveCoefficient = DEFAULT_NEGATIVE_MARGIN_OBJECTIVE_COEFFICIENT;
    private SensitivityAnalysisParameters defaultSensitivityAnalysisParameters = new SensitivityAnalysisParameters();
    private SensitivityAnalysisParameters fallbackSensitivityAnalysisParameters; // Must be null by default
    private List<ZoneToZonePtdfDefinition> relativeMarginPtdfBoundaries = new ArrayList<>();
    private double ptdfSumLowerBound = DEFAULT_PTDF_SUM_LOWER_BOUND; // prevents relative margins from diverging to +infinity
    private int perimetersInParallel = DEFAULT_PERIMETERS_IN_PARALLEL;
    private boolean postCheckRaoResults = DEFAULT_POST_CHECK_RAO_RESULTS; // fallback to initial situation if results are degraded (because of a curative cnec or mnec)

    public boolean isPostCheckRaoResults() {
        return postCheckRaoResults;
    }

    public void setPostCheckRaoResults(boolean postCheckRaoResults) {
        this.postCheckRaoResults = postCheckRaoResults;
    }

    public ObjectiveFunction getObjectiveFunction() {
        return objectiveFunction;
    }

    public RaoParameters setObjectiveFunction(ObjectiveFunction objectiveFunction) {
        this.objectiveFunction = objectiveFunction;
        return this;
    }

    public int getMaxIterations() {
        return maxIterations;
    }

    public RaoParameters setMaxIterations(int maxIterations) {
        this.maxIterations = maxIterations;
        return this;
    }

    public double getPstPenaltyCost() {
        return pstPenaltyCost;
    }

    public RaoParameters setPstPenaltyCost(double pstPenaltyCost) {
        this.pstPenaltyCost = max(0.0, pstPenaltyCost);
        return this;
    }

    public double getPstSensitivityThreshold() {
        return pstSensitivityThreshold;
    }

    public RaoParameters setPstSensitivityThreshold(double pstSensitivityThreshold) {
        this.pstSensitivityThreshold = pstSensitivityThreshold;
        return this;
    }

    public double getFallbackOverCost() {
        return fallbackOverCost;
    }

    public RaoParameters setFallbackOverCost(double overCost) {
        this.fallbackOverCost = max(0.0, overCost);
        return this;
    }

    public boolean isRaoWithLoopFlowLimitation() {
        return raoWithLoopFlowLimitation;
    }

    public RaoParameters setRaoWithLoopFlowLimitation(boolean raoWithLoopFlowLimitation) {
        this.raoWithLoopFlowLimitation = raoWithLoopFlowLimitation;
        return this;
    }

    public LoopFlowApproximationLevel getLoopFlowApproximationLevel() {
        return loopFlowApproximationLevel;
    }

    public RaoParameters setLoopFlowApproximationLevel(LoopFlowApproximationLevel loopFlowApproximation) {
        this.loopFlowApproximationLevel = loopFlowApproximation;
        return this;
    }

    public double getLoopFlowAcceptableAugmentation() {
        return loopFlowAcceptableAugmentation;
    }

    public void setLoopFlowAcceptableAugmentation(double loopFlowAcceptableAugmentation) {
        this.loopFlowAcceptableAugmentation = loopFlowAcceptableAugmentation;
    }

    public double getLoopFlowConstraintAdjustmentCoefficient() {
        return loopFlowConstraintAdjustmentCoefficient;
    }

    public RaoParameters setLoopFlowConstraintAdjustmentCoefficient(double loopFlowConstraintAdjustmentCoefficient) {
        this.loopFlowConstraintAdjustmentCoefficient = loopFlowConstraintAdjustmentCoefficient;
        return this;
    }

    public double getLoopFlowViolationCost() {
        return loopFlowViolationCost;
    }

    public RaoParameters setLoopFlowViolationCost(double loopflowViolationCost) {
        this.loopFlowViolationCost = loopflowViolationCost;
        return this;
    }

    public SensitivityAnalysisParameters getDefaultSensitivityAnalysisParameters() {
        return defaultSensitivityAnalysisParameters;
    }

    public RaoParameters setDefaultSensitivityAnalysisParameters(SensitivityAnalysisParameters sensiParameters) {
        this.defaultSensitivityAnalysisParameters = Objects.requireNonNull(sensiParameters);
        return this;
    }

    public SensitivityAnalysisParameters getFallbackSensitivityAnalysisParameters() {
        return fallbackSensitivityAnalysisParameters;
    }

    public RaoParameters setFallbackSensitivityAnalysisParameters(SensitivityAnalysisParameters sensiParameters) {
        this.fallbackSensitivityAnalysisParameters = Objects.requireNonNull(sensiParameters);
        return this;
    }

    public double getMnecAcceptableMarginDiminution() {
        return mnecAcceptableMarginDiminution;
    }

    public RaoParameters setMnecAcceptableMarginDiminution(double mnecAcceptableMarginDiminution) {
        this.mnecAcceptableMarginDiminution = mnecAcceptableMarginDiminution;
        return this;
    }

    public double getMnecConstraintAdjustmentCoefficient() {
        return mnecConstraintAdjustmentCoefficient;
    }

    public RaoParameters setMnecConstraintAdjustmentCoefficient(double mnecConstraintAdjustmentCoefficient) {
        this.mnecConstraintAdjustmentCoefficient = mnecConstraintAdjustmentCoefficient;
        return this;
    }

    public double getMnecViolationCost() {
        return mnecViolationCost;
    }

    public RaoParameters setMnecViolationCost(double mnecViolationCost) {
        this.mnecViolationCost = mnecViolationCost;
        return this;
    }

    public double getNegativeMarginObjectiveCoefficient() {
        return negativeMarginObjectiveCoefficient;
    }

    public RaoParameters setNegativeMarginObjectiveCoefficient(double negativeMarginObjectiveCoefficient) {
        this.negativeMarginObjectiveCoefficient = negativeMarginObjectiveCoefficient;
        return this;
    }

    public Set<Country> getLoopflowCountries() {
        return loopflowCountries;
    }

    public RaoParameters setLoopflowCountries(Set<Country> loopflowCountries) {
        this.loopflowCountries = loopflowCountries;
        return this;
    }

    public RaoParameters setLoopflowCountries(List<String> countryStrings) {
        this.loopflowCountries = convertToCountrySet(countryStrings);
        return this;
    }

    public List<ZoneToZonePtdfDefinition> getRelativeMarginPtdfBoundaries() {
        return relativeMarginPtdfBoundaries;
    }

    public RaoParameters setRelativeMarginPtdfBoundaries(List<ZoneToZonePtdfDefinition> boundaries) {
        this.relativeMarginPtdfBoundaries = boundaries;
        return this;
    }

    public List<String> getRelativeMarginPtdfBoundariesAsString() {
        return relativeMarginPtdfBoundaries.stream()
                .map(ZoneToZonePtdfDefinition::toString)
                .collect(Collectors.toList());
    }

    public RaoParameters setRelativeMarginPtdfBoundariesFromString(List<String> boundaries) {
        this.relativeMarginPtdfBoundaries = boundaries.stream()
            .map(ZoneToZonePtdfDefinition::new)
            .collect(Collectors.toList());
        return this;
    }

    public double getPtdfSumLowerBound() {
        return ptdfSumLowerBound;
    }

    public void setPtdfSumLowerBound(double ptdfSumLowerBound) {
        this.ptdfSumLowerBound = ptdfSumLowerBound;
    }

    public int getPerimetersInParallel() {
        return perimetersInParallel;
    }

    public void setPerimetersInParallel(int perimetersInParallel) {
        this.perimetersInParallel = perimetersInParallel;
    }

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
     * @return RaoParameters from platform default config.
     */
    public static RaoParameters load() {
        return load(PlatformConfig.defaultConfig());
    }

    /**
     * @param platformConfig PlatformConfig where the RaoParameters should be read from
     * @return RaoParameters from the provided platform config
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
                parameters.setObjectiveFunction(config.getEnumProperty("objective-function", ObjectiveFunction.class, DEFAULT_OBJECTIVE_FUNCTION));
                parameters.setMaxIterations(config.getIntProperty("max-number-of-iterations", DEFAULT_MAX_ITERATIONS));
                parameters.setPstPenaltyCost(config.getDoubleProperty("pst-penalty-cost", DEFAULT_PST_PENALTY_COST));
                parameters.setPstSensitivityThreshold(config.getDoubleProperty("pst-sensitivity-threshold", DEFAULT_PST_SENSITIVITY_THRESHOLD));
                parameters.setFallbackOverCost(config.getDoubleProperty("sensitivity-fallback-over-cost", DEFAULT_FALLBACK_OVER_COST));
                parameters.setRaoWithLoopFlowLimitation(config.getBooleanProperty("rao-with-loop-flow-limitation", DEFAULT_RAO_WITH_LOOP_FLOW_LIMITATION));
                parameters.setLoopFlowAcceptableAugmentation(config.getDoubleProperty("loop-flow-acceptable-augmentation", DEFAULT_LOOP_FLOW_ACCEPTABLE_AUGMENTATION));
                parameters.setLoopFlowApproximationLevel(config.getEnumProperty("loop-flow-approximation", LoopFlowApproximationLevel.class, DEFAULT_LOOP_FLOW_APPROXIMATION_LEVEL));
                parameters.setLoopFlowConstraintAdjustmentCoefficient(config.getDoubleProperty("loop-flow-constraint-adjustment-coefficient", DEFAULT_LOOP_FLOW_CONSTRAINT_ADJUSTMENT_COEFFICIENT));
                parameters.setLoopFlowViolationCost(config.getDoubleProperty("loop-flow-violation-cost", DEFAULT_LOOP_FLOW_VIOLATION_COST));
                parameters.setLoopflowCountries(convertToCountrySet(config.getStringListProperty("loop-flow-countries", new ArrayList<>())));
                parameters.setMnecAcceptableMarginDiminution(config.getDoubleProperty("mnec-acceptable-margin-diminution", DEFAULT_MNEC_ACCEPTABLE_MARGIN_DIMINUTION));
                parameters.setMnecViolationCost(config.getDoubleProperty("mnec-violation-cost", DEFAULT_MNEC_VIOLATION_COST));
                parameters.setMnecConstraintAdjustmentCoefficient(config.getDoubleProperty("mnec-constraint-adjustment-coefficient", DEFAULT_MNEC_CONSTRAINT_ADJUSTMENT_COEFFICIENT));
                parameters.setNegativeMarginObjectiveCoefficient(config.getDoubleProperty("negative-margin-objective-coefficient", DEFAULT_NEGATIVE_MARGIN_OBJECTIVE_COEFFICIENT));
                parameters.setRelativeMarginPtdfBoundariesFromString(config.getStringListProperty("relative-margin-ptdf-boundaries", new ArrayList<>()));
                parameters.setPtdfSumLowerBound(config.getDoubleProperty("ptdf-sum-lower-bound", DEFAULT_PTDF_SUM_LOWER_BOUND));
                parameters.setPerimetersInParallel(config.getIntProperty("perimeters-in-parallel", DEFAULT_PERIMETERS_IN_PARALLEL));
                parameters.setPostCheckRaoResults(config.getBooleanProperty("post-check-rao-results", DEFAULT_POST_CHECK_RAO_RESULTS));
            });

        // NB: Only the default sensitivity parameters are loaded, not the fallback ones...
        parameters.setDefaultSensitivityAnalysisParameters(SensitivityAnalysisParameters.load(platformConfig));
    }

    private static Set<Country> convertToCountrySet(List<String> countryStringList) {
        Set<Country> countryList = new HashSet<>();
        for (String countryString : countryStringList) {
            try {
                countryList.add(Country.valueOf(countryString));
            } catch (Exception e) {
                throw new FaraoException(String.format("[%s] in loopflow countries could not be recognized as a country", countryString));
            }
        }
        return countryList;
    }

    private void readExtensions(PlatformConfig platformConfig) {
        for (ExtensionConfigLoader provider : PARAMETERS_EXTENSIONS_SUPPLIER.get().getProviders()) {
            addExtension(provider.getExtensionClass(), provider.load(platformConfig));
        }
    }
}

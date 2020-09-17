/*
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.farao_community.farao.rao_commons;

import com.farao_community.farao.commons.FaraoException;
import com.farao_community.farao.commons.Unit;
import com.farao_community.farao.data.crac_api.Cnec;
import com.farao_community.farao.data.crac_api.PstRange;
import com.farao_community.farao.data.crac_api.RangeAction;
import com.farao_community.farao.data.crac_loopflow_extension.CnecLoopFlowExtension;
import com.farao_community.farao.data.crac_result_extensions.*;
import com.farao_community.farao.rao_commons.linear_optimisation.LinearProblem;
import com.farao_community.farao.rao_commons.linear_optimisation.fillers.MaxLoopFlowFiller;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.TwoWindingsTransformer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Objects;

import static com.farao_community.farao.rao_commons.RaoData.NO_WORKING_VARIANT;
import static java.lang.String.format;

/**
 * @author Joris Mancini {@literal <joris.mancini at rte-france.com>}
 */
public class RaoDataManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(RaoDataManager.class);

    private RaoData raoData;

    RaoDataManager(RaoData raoData) {
        this.raoData = raoData;
    }

    /**
     * This method works from the working variant. It is filling CRAC result extension of the working variant
     * with values in network of the working variant.
     */
    public void fillRangeActionResultsWithNetworkValues() {
        if (raoData.getWorkingVariantId() == null) {
            throw new FaraoException(NO_WORKING_VARIANT);
        }
        for (RangeAction rangeAction : raoData.getAvailableRangeActions()) {
            double valueInNetwork = rangeAction.getCurrentValue(raoData.getNetwork());
            RangeActionResultExtension rangeActionResultMap = rangeAction.getExtension(RangeActionResultExtension.class);
            RangeActionResult rangeActionResult = rangeActionResultMap.getVariant(raoData.getWorkingVariantId());
            rangeActionResult.setSetPoint(raoData.getOptimizedState().getId(), valueInNetwork);
            if (rangeAction instanceof PstRange) {
                ((PstRangeResult) rangeActionResult).setTap(raoData.getOptimizedState().getId(), ((PstRange) rangeAction).computeTapPosition(valueInNetwork));
            }
        }
    }

    /**
     * This method works from the working variant. It is applying on the network working variant
     * according to the values present in the CRAC result extension of the working variant.
     */
    public void applyRangeActionResultsOnNetwork() {
        if (raoData.getWorkingVariantId() == null) {
            throw new FaraoException(NO_WORKING_VARIANT);
        }
        for (RangeAction rangeAction : raoData.getAvailableRangeActions()) {
            RangeActionResultExtension rangeActionResultMap = rangeAction.getExtension(RangeActionResultExtension.class);
            rangeAction.apply(raoData.getNetwork(),
                rangeActionResultMap.getVariant(raoData.getWorkingVariantId()).getSetPoint(raoData.getOptimizedState().getId()));
        }
    }

    /**
     * This method compares CRAC result extension of two different variants. It compares the set point values
     * of all the range actions.
     *
     * @param variantId1: First variant to compare.
     * @param variantId2: Second variant to compare.
     * @return True if all the range actions are set at the same values and false otherwise.
     */
    public boolean sameRemedialActions(String variantId1, String variantId2) {
        for (RangeAction rangeAction : raoData.getAvailableRangeActions()) {
            RangeActionResultExtension rangeActionResultMap = rangeAction.getExtension(RangeActionResultExtension.class);
            double value1 = rangeActionResultMap.getVariant(variantId1).getSetPoint(raoData.getOptimizedState().getId());
            double value2 = rangeActionResultMap.getVariant(variantId2).getSetPoint(raoData.getOptimizedState().getId());
            if (value1 != value2 && (!Double.isNaN(value1) || !Double.isNaN(value2))) {
                return false;
            }
        }
        return true;
    }

    public void fillRangeActionResultsWithLinearProblem(LinearProblem linearProblem) {
        LOGGER.debug(format("Expected minimum margin: %.2f", linearProblem.getMinimumMarginVariable().solutionValue()));
        LOGGER.debug(format("Expected optimisation criterion: %.2f", linearProblem.getObjective().value()));
        for (RangeAction rangeAction: raoData.getAvailableRangeActions()) {
            if (rangeAction instanceof PstRange) {
                String networkElementId = rangeAction.getNetworkElements().iterator().next().getId();
                double rangeActionVal = linearProblem.getRangeActionSetPointVariable(rangeAction).solutionValue();
                PstRange pstRange = (PstRange) rangeAction;
                TwoWindingsTransformer transformer = raoData.getNetwork().getTwoWindingsTransformer(networkElementId);

                int approximatedPostOptimTap = pstRange.computeTapPosition(rangeActionVal);
                double approximatedPostOptimAngle = transformer.getPhaseTapChanger().getStep(approximatedPostOptimTap).getAlpha();

                RangeActionResultExtension pstRangeResultMap = rangeAction.getExtension(RangeActionResultExtension.class);
                PstRangeResult pstRangeResult = (PstRangeResult) pstRangeResultMap.getVariant(raoData.getWorkingVariantId());
                pstRangeResult.setSetPoint(raoData.getOptimizedState().getId(), approximatedPostOptimAngle);
                pstRangeResult.setTap(raoData.getOptimizedState().getId(), approximatedPostOptimTap);
                LOGGER.debug(format("Range action %s has been set to tap %d", pstRange.getName(), approximatedPostOptimTap));
            }
        }
    }

    /**
     * Add results of the systematic analysis (flows and objective function value) in the
     * Crac result variant of the situation.
     */
    public void fillCracResultsWithSensis(double cost, double overCost) {
        raoData.getCracResult().setFunctionalCost(cost);
        raoData.getCracResult().addVirtualCost(overCost);
        raoData.getCracResult().setNetworkSecurityStatus(cost < 0 ?
            CracResult.NetworkSecurityStatus.UNSECURED : CracResult.NetworkSecurityStatus.SECURED);
        updateCnecExtensions();
    }

    public void fillCracResultsWithLoopFlowConstraints(Map<String, Double> loopFlows, Map<Cnec, Double> loopFlowShifts, Network network) {
        raoData.getCnecs().forEach(cnec -> {
            CnecLoopFlowExtension cnecLoopFlowExtension = cnec.getExtension(CnecLoopFlowExtension.class);

            if (!Objects.isNull(cnecLoopFlowExtension)) {
                double loopFlowThreshold = Math.abs(cnecLoopFlowExtension.getInputThreshold(Unit.MEGAWATT, network));
                double initialLoopFlow = Math.abs(loopFlows.get(cnec.getId()));

                cnecLoopFlowExtension.setLoopFlowConstraintInMW(Math.max(initialLoopFlow, loopFlowThreshold - cnec.getFrm()));
                cnecLoopFlowExtension.setLoopflowShift(loopFlowShifts.get(cnec));
            }
        });
    }

    public void fillCracResultsWithLoopFlows(Map<String, Double> loopFlows, double violationCost) {
        raoData.getCnecs().forEach(cnec -> {
            CnecResult cnecResult = cnec.getExtension(CnecResultExtension.class).getVariant(raoData.getWorkingVariantId());
            if (!Objects.isNull(cnec.getExtension(CnecLoopFlowExtension.class)) && loopFlows.containsKey(cnec.getId())) {
                cnecResult.setLoopflowInMW(loopFlows.get(cnec.getId()));
                cnecResult.setLoopflowThresholdInMW(cnec.getExtension(CnecLoopFlowExtension.class).getLoopFlowConstraintInMW());
            }
        });

        double loopFlowTotalViolationCost = 0.0;
        boolean loopFlowViolated = false;
        for (Cnec cnec : raoData.getCnecs()) {
            if (!Objects.isNull(cnec.getExtension(CnecLoopFlowExtension.class))) {
                double loopFlow = loopFlows.get(cnec.getId());
                double constraint = cnec.getExtension(CnecLoopFlowExtension.class).getLoopFlowConstraintInMW();
                if (Math.abs(loopFlow) > Math.abs(constraint)) {
                    loopFlowTotalViolationCost += violationCost * (Math.abs(loopFlow) - Math.abs(constraint));
                    loopFlowViolated = true;
                }
            }
        }
        raoData.getCracResult().addVirtualCost(loopFlowTotalViolationCost);

        if (loopFlowViolated && violationCost == 0.0) {
            raoData.getCracResult().setVirtualCost(MaxLoopFlowFiller.MAX_LOOP_FLOW_VIOLATION_COST); // "zero-loopflowViolationCost", no virtual cost available from Linear optim, set to MAX
            raoData.getCracResult().setNetworkSecurityStatus(CracResult.NetworkSecurityStatus.UNSECURED);
        }
    }

    public void updateCnecExtensions() {
        if (raoData.getWorkingVariantId() == null) {
            throw new FaraoException(NO_WORKING_VARIANT);
        }
        raoData.getCnecs().forEach(cnec -> {
            CnecResult cnecResult = cnec.getExtension(CnecResultExtension.class).getVariant(raoData.getWorkingVariantId());
            cnecResult.setFlowInMW(raoData.getSystematicSensitivityResult().getReferenceFlow(cnec));
            cnecResult.setFlowInA(raoData.getSystematicSensitivityResult().getReferenceIntensity(cnec));
            cnecResult.setThresholds(cnec);
        });
    }
}

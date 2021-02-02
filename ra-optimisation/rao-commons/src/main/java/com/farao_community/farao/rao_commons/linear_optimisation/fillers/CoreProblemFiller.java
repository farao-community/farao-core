/*
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.farao_community.farao.rao_commons.linear_optimisation.fillers;

import com.farao_community.farao.commons.FaraoException;
import com.farao_community.farao.data.crac_api.*;
import com.farao_community.farao.data.crac_api.cnec.BranchCnec;
import com.farao_community.farao.data.crac_api.cnec.Cnec;
import com.farao_community.farao.data.crac_result_extensions.RangeActionResultExtension;
import com.farao_community.farao.data.crac_result_extensions.ResultVariantManager;
import com.farao_community.farao.rao_api.RaoParameters;
import com.farao_community.farao.rao_commons.RaoData;
import com.farao_community.farao.rao_commons.RaoUtil;
import com.farao_community.farao.rao_commons.linear_optimisation.LinearProblem;
import com.google.ortools.linearsolver.MPConstraint;
import com.google.ortools.linearsolver.MPVariable;
import com.powsybl.iidm.network.Network;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

import static com.farao_community.farao.rao_api.RaoParameters.DEFAULT_PST_SENSITIVITY_THRESHOLD;
import static java.lang.String.format;

/**
 * @author Pengbo Wang {@literal <pengbo.wang at rte-international.com>}
 * @author Baptiste Seguinot {@literal <baptiste.seguinot at rte-france.com>}
 */
public class CoreProblemFiller implements ProblemFiller {
    private static final Logger LOGGER = LoggerFactory.getLogger(CoreProblemFiller.class);

    private final double pstSensitivityThreshold;
    private final Map<String, Integer> maxPstPerTso;
    private Set<RangeAction> availableRangeActions;

    public CoreProblemFiller(double pstSensitivityThreshold, Map<String, Integer> maxPstPerTso) {
        this.pstSensitivityThreshold = pstSensitivityThreshold;
        this.maxPstPerTso = maxPstPerTso;
    }

    // Method for tests
    public CoreProblemFiller() {
        this(DEFAULT_PST_SENSITIVITY_THRESHOLD, null);
    }

    @Override
    public void fill(RaoData raoData, LinearProblem linearProblem) {
        // chose range actions to use
        computeAvailableRangeActions(raoData);
        // add variables
        buildFlowVariables(raoData, linearProblem);
        availableRangeActions.forEach(rangeAction -> {
            buildRangeActionSetPointVariables(raoData.getNetwork(), rangeAction, linearProblem);
            buildRangeActionAbsoluteVariationVariables(rangeAction, linearProblem);
            buildRangeActionGroupConstraint(rangeAction, linearProblem);
        });

        // add constraints
        buildFlowConstraints(raoData, linearProblem);
        buildRangeActionConstraints(raoData, linearProblem);
    }

    @Override
    public void update(RaoData raoData, LinearProblem linearProblem) {
        // chose range actions to use
        computeAvailableRangeActions(raoData);
        // update reference flow and sensitivities of flow constraints
        updateFlowConstraints(raoData, linearProblem);
    }

    /**
     * If a TSO has a maximum number of usable ranges actions, this functions filters out the range actions with
     * the least impact on the most limiting element
     */
    private void computeAvailableRangeActions(RaoData raoData) {
        availableRangeActions = raoData.getAvailableRangeActions();
        if (!Objects.isNull(maxPstPerTso) && !maxPstPerTso.isEmpty()) {
            RaoParameters.ObjectiveFunction objFunction = raoData.getRaoParameters().getObjectiveFunction();
            BranchCnec mostLimitingElement = RaoUtil.getMostLimitingElement(raoData.getCnecs(), raoData.getWorkingVariantId(), objFunction.getUnit(), objFunction.relativePositiveMargins());
            maxPstPerTso.forEach((String tso, Integer maxPst) -> {
                Set<RangeAction> rangeActionsForTso = availableRangeActions.stream().filter(rangeAction -> rangeAction.getOperator().equals(tso)).collect(Collectors.toSet());
                if (rangeActionsForTso.size() > maxPst) {
                    LOGGER.debug("{} range actions will be filtered out, in order to respect the maximum number of range actions of {} for TSO {}", rangeActionsForTso.size() - maxPst, maxPst, tso);
                    rangeActionsForTso.stream().sorted((ra1, ra2) -> compareAbsoluteSensitivities(ra1, ra2, mostLimitingElement, raoData))
                            .collect(Collectors.toList()).subList(0, rangeActionsForTso.size() - maxPst)
                            .forEach(rangeAction -> availableRangeActions.remove(rangeAction));
                }
            });
        }
    }

    int compareAbsoluteSensitivities(RangeAction ra1, RangeAction ra2, BranchCnec cnec, RaoData raoData) {
        Double sensi1 = Math.abs(raoData.getSensitivity(cnec, ra1));
        Double sensi2 = Math.abs(raoData.getSensitivity(cnec, ra2));
        return sensi1.compareTo(sensi2);
    }

    /**
     * Build one flow variable F[c] for each Cnec c
     * This variable describes the estimated flow on the given Cnec c, in MEGAWATT
     */
    private void buildFlowVariables(RaoData raoData, LinearProblem linearProblem) {
        raoData.getCnecs().forEach(cnec ->
                linearProblem.addFlowVariable(-linearProblem.infinity(), linearProblem.infinity(), cnec)
        );
    }

    /**
     * Build one set point variable S[r] for each RangeAction r
     * This variable describes the set point of the given RangeAction r, given :
     * <ul>
     *     <li>in DEGREE for PST range actions</li>
     * </ul>
     *
     * This set point of the a RangeAction is bounded between the min/max variations
     * of the RangeAction :
     *
     * initialSetPoint[r] - maxNegativeVariation[r] <= S[r]
     * S[r] >= initialSetPoint[r] + maxPositiveVariation[r]
     */
    private void buildRangeActionSetPointVariables(Network network, RangeAction rangeAction, LinearProblem linearProblem) {
        double minSetPoint = rangeAction.getMinValue(network);
        double maxSetPoint = rangeAction.getMaxValue(network);
        linearProblem.addRangeActionSetPointVariable(minSetPoint, maxSetPoint, rangeAction);
    }

    /**
     * Build one absolute variable variable AV[r] for each RangeAction r
     * This variable describes the absolute difference between the range action set point
     * and its initial value. It is given :
     * <ul>
     *     <li>in DEGREE for PST range actions</li>
     * </ul>
     */
    private void buildRangeActionAbsoluteVariationVariables(RangeAction rangeAction, LinearProblem linearProblem) {
        linearProblem.addAbsoluteRangeActionVariationVariable(0, linearProblem.infinity(), rangeAction);
    }

    /**
     * Build one flow constraint for each Cnec c.
     * This constraints link the estimated flow on a Cnec with the impact of the RangeActions
     * on this Cnec.
     *
     * F[c] = f_ref[c] + sum{r in RangeAction} sensitivity[c,r] * (S[r] - currentSetPoint[r])
     */
    private void buildFlowConstraints(RaoData raoData, LinearProblem linearProblem) {
        raoData.getCnecs().forEach(cnec -> {
            // create constraint
            double referenceFlow = raoData.getReferenceFlow(cnec);
            MPConstraint flowConstraint = linearProblem.addFlowConstraint(referenceFlow, referenceFlow, cnec);

            MPVariable flowVariable = linearProblem.getFlowVariable(cnec);
            if (flowVariable == null) {
                throw new FaraoException(format("Flow variable on %s has not been defined yet.", cnec.getId()));
            }

            flowConstraint.setCoefficient(flowVariable, 1);

            // add sensitivity coefficients
            addImpactOfRangeActionOnCnec(raoData, linearProblem, cnec);
        });
    }

    /**
     * Update the flow constraints, with the new reference flows and new sensitivities
     *
     * F[c] = f_ref[c] + sum{r in RangeAction} sensitivity[c,r] * (S[r] - currentSetPoint[r])
     */
    private void updateFlowConstraints(RaoData raoData, LinearProblem linearProblem) {
        raoData.getCnecs().forEach(cnec -> {
            double referenceFlow = raoData.getReferenceFlow(cnec);
            MPConstraint flowConstraint = linearProblem.getFlowConstraint(cnec);
            if (flowConstraint == null) {
                throw new FaraoException(format("Flow constraint on %s has not been defined yet.", cnec.getId()));
            }

            //reset bounds
            flowConstraint.setUb(referenceFlow);
            flowConstraint.setLb(referenceFlow);

            //reset sensitivity coefficients
            addImpactOfRangeActionOnCnec(raoData, linearProblem, cnec);
        });
    }

    private void addImpactOfRangeActionOnCnec(RaoData raoData, LinearProblem linearProblem, Cnec<?> cnec) {
        MPVariable flowVariable = linearProblem.getFlowVariable(cnec);
        MPConstraint flowConstraint = linearProblem.getFlowConstraint(cnec);

        if (flowVariable == null || flowConstraint == null) {
            throw new FaraoException(format("Flow variable and/or constraint on %s has not been defined yet.", cnec.getId()));
        }

        availableRangeActions.forEach(rangeAction -> {
            if (rangeAction instanceof PstRange) {
                addImpactOfPstOnCnec(raoData, linearProblem, rangeAction, cnec, flowConstraint);
            } else {
                throw new FaraoException("Type of RangeAction not yet handled by the LinearRao.");
            }
        });
    }

    private void addImpactOfPstOnCnec(RaoData raoData, LinearProblem linearProblem, RangeAction rangeAction, Cnec<?> cnec, MPConstraint flowConstraint) {
        MPVariable setPointVariable = linearProblem.getRangeActionSetPointVariable(rangeAction);
        if (setPointVariable == null) {
            throw new FaraoException(format("Range action variable for %s has not been defined yet.", rangeAction.getId()));
        }

        double sensitivity = raoData.getSensitivity(cnec, rangeAction);

        if (Math.abs(sensitivity) >= pstSensitivityThreshold) {
            double currentSetPoint = rangeAction.getCurrentValue(raoData.getNetwork());
            // care : might not be robust as getCurrentValue get the current setPoint from a network variant
            //        we need to be sure that this variant has been properly set
            flowConstraint.setLb(flowConstraint.lb() - sensitivity * currentSetPoint);
            flowConstraint.setUb(flowConstraint.ub() - sensitivity * currentSetPoint);

            flowConstraint.setCoefficient(setPointVariable, -sensitivity);
        }
    }

    /**
     * Build two range action constraints for each RangeAction r.
     * These constraints link the set point variable of the RangeAction with its absolute
     * variation variable.
     *
     * AV[r] >= S[r] - initialSetPoint[r]     (NEGATIVE)
     * AV[r] >= initialSetPoint[r] - S[r]     (POSITIVE)
     */
    private void buildRangeActionConstraints(RaoData raoData, LinearProblem linearProblem) {
        String preOptimVariantId = raoData.getCrac().getExtension(ResultVariantManager.class).getPrePerimeterVariantId();
        availableRangeActions.forEach(rangeAction -> {
            double initialSetPoint = rangeAction.getExtension(RangeActionResultExtension.class).getVariant(preOptimVariantId).getSetPoint(raoData.getOptimizedState().getId());
            MPConstraint varConstraintNegative = linearProblem.addAbsoluteRangeActionVariationConstraint(-initialSetPoint, linearProblem.infinity(), rangeAction, LinearProblem.AbsExtension.NEGATIVE);
            MPConstraint varConstraintPositive = linearProblem.addAbsoluteRangeActionVariationConstraint(initialSetPoint, linearProblem.infinity(), rangeAction, LinearProblem.AbsExtension.POSITIVE);

            MPVariable setPointVariable = linearProblem.getRangeActionSetPointVariable(rangeAction);
            MPVariable absoluteVariationVariable = linearProblem.getAbsoluteRangeActionVariationVariable(rangeAction);

            varConstraintNegative.setCoefficient(absoluteVariationVariable, 1);
            varConstraintNegative.setCoefficient(setPointVariable, -1);

            varConstraintPositive.setCoefficient(absoluteVariationVariable, 1);
            varConstraintPositive.setCoefficient(setPointVariable, 1);
        });
    }

    private static void buildRangeActionGroupConstraint(RangeAction rangeAction, LinearProblem linearProblem) {
        Optional<String> optGroupId = rangeAction.getGroupId();
        if (optGroupId.isPresent()) {
            String groupId = optGroupId.get();
            // For the first time the group ID is encountered a common variable for set point has to be created
            if (linearProblem.getRangeActionGroupSetPointVariable(groupId) == null) {
                linearProblem.addRangeActionGroupSetPointVariable(-linearProblem.infinity(), linearProblem.infinity(), groupId);
            }
            addRangeActionGroupConstraint(rangeAction, groupId, linearProblem);
        }
    }

    private static void addRangeActionGroupConstraint(RangeAction rangeAction, String groupId, LinearProblem linearProblem) {
        MPConstraint groupSetPointConstraint = linearProblem.addRangeActionGroupSetPointConstraint(0, 0, rangeAction);
        groupSetPointConstraint.setCoefficient(linearProblem.getRangeActionSetPointVariable(rangeAction), 1);
        groupSetPointConstraint.setCoefficient(linearProblem.getRangeActionGroupSetPointVariable(groupId), -1);
    }
}

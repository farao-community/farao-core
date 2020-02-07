/*
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.farao_community.farao.linear_rao;

import com.farao_community.farao.commons.FaraoException;
import com.farao_community.farao.data.crac_api.Cnec;
import com.farao_community.farao.data.crac_api.Crac;
import com.farao_community.farao.ra_optimisation.*;
import com.farao_community.farao.rao_api.RaoParameters;
import com.farao_community.farao.rao_api.RaoProvider;
import com.farao_community.farao.util.SystematicSensitivityAnalysisResult;
import com.farao_community.farao.util.SystematicSensitivityAnalysisService;
import com.google.auto.service.AutoService;
import com.powsybl.computation.ComputationManager;
import com.powsybl.iidm.network.Network;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;

import static java.lang.String.format;

/**
 * @author Joris Mancini {@literal <joris.mancini at rte-france.com>}
 */
@AutoService(RaoProvider.class)
public class LinearRao implements RaoProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger(LinearRao.class);
    private static final int MAX_ITERATIONS = 10;

    private SystematicSensitivityAnalysisResult preOptimSensitivityAnalysisResult;
    private SystematicSensitivityAnalysisResult postOptimSensitivityAnalysisResult;

    @Override
    public String getName() {
        return "LinearRao";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public CompletableFuture<RaoComputationResult> run(Network network,
                                                       Crac crac,
                                                       String variantId,
                                                       ComputationManager computationManager,
                                                       RaoParameters parameters) {
        preOptimSensitivityAnalysisResult = SystematicSensitivityAnalysisService.runAnalysis(network, crac, computationManager);
        postOptimSensitivityAnalysisResult = SystematicSensitivityAnalysisService.runAnalysis(network, crac, computationManager);
        SystematicSensitivityAnalysisResult midOptimSensitivityAnalysisResult;
        List<RemedialActionResult> oldRemedialActionsResult = new ArrayList<>();

        int iterationsLeft = MAX_ITERATIONS;
        if (iterationsLeft == 0) {
            return CompletableFuture.completedFuture(buildRaoComputationResult(crac, oldRemedialActionsResult));
        }

        double oldObjectiveFunction = getMinMargin(crac, preOptimSensitivityAnalysisResult);
        LinearRaoOptimizer linearRaoOptimizer = createLinearRaoOptimizer(crac, network, preOptimSensitivityAnalysisResult, computationManager, parameters);
        RaoComputationResult raoComputationResult = linearRaoOptimizer.run();
        if (raoComputationResult.getStatus() == RaoComputationResult.Status.FAILURE) {
            return CompletableFuture.completedFuture(raoComputationResult);
        }

        List<RemedialActionResult> newRemedialActionsResult = raoComputationResult.getPreContingencyResult().getRemedialActionResults();
        //TODO: manage CRA

        String originalNetworkVariant = network.getVariantManager().getWorkingVariantId();

        while (!sameRemedialActionResultLists(newRemedialActionsResult, oldRemedialActionsResult) && iterationsLeft > 0) {
            createAndSwitchToNewVariant(network, originalNetworkVariant);
            applyRAs(crac, network, newRemedialActionsResult);
            midOptimSensitivityAnalysisResult = SystematicSensitivityAnalysisService.runAnalysis(network, crac, computationManager);
            double newObjectiveFunction = getMinMargin(crac, midOptimSensitivityAnalysisResult);
            if (newObjectiveFunction < oldObjectiveFunction) {
                // TODO : limit the ranges
                LOGGER.warn("Linear Optimization found a worse result after an iteration: from {} to {}", oldObjectiveFunction, newObjectiveFunction);
                break;
            }
            postOptimSensitivityAnalysisResult = midOptimSensitivityAnalysisResult;
            oldObjectiveFunction = newObjectiveFunction;
            oldRemedialActionsResult = newRemedialActionsResult;

            linearRaoOptimizer.update(midOptimSensitivityAnalysisResult);
            raoComputationResult = linearRaoOptimizer.run();
            if (raoComputationResult.getStatus() == RaoComputationResult.Status.FAILURE) {
                return CompletableFuture.completedFuture(raoComputationResult);
            }
            newRemedialActionsResult = raoComputationResult.getPreContingencyResult().getRemedialActionResults();
            //TODO: manage CRA
            iterationsLeft -= 1;
        }

        return CompletableFuture.completedFuture(buildRaoComputationResult(crac, oldRemedialActionsResult));
    }

    LinearRaoOptimizer createLinearRaoOptimizer(Crac crac,
                                                        Network network,
                                                        SystematicSensitivityAnalysisResult systematicSensitivityAnalysisResult,
                                                        ComputationManager computationManager,
                                                        RaoParameters raoParameters) {
        return new LinearRaoOptimizer(crac, network, systematicSensitivityAnalysisResult, computationManager, raoParameters, new LinearRaoProblem());

    }

    private double getRemedialActionResultValue(RemedialActionResult remedialActionResult) {
        RemedialActionElementResult remedialActionElementResult = remedialActionResult.getRemedialActionElementResults().get(0);
        if (remedialActionElementResult instanceof PstElementResult) {
            PstElementResult pstElementResult = (PstElementResult) remedialActionElementResult;
            return pstElementResult.getPostOptimisationAngle();
        } else if (remedialActionElementResult instanceof RedispatchElementResult) {
            RedispatchElementResult redispatchElementResult = (RedispatchElementResult) remedialActionElementResult;
            return redispatchElementResult.getPostOptimisationTargetP();
        }
        return 0;
    }

    private boolean sameRemedialActionResultLists(List<RemedialActionResult> firstList, List<RemedialActionResult> secondList) {
        if (firstList.size() != secondList.size()) {
            return false;
        }
        Map<String, Double> firstMap = new HashMap<>();
        for (RemedialActionResult remedialActionResult : firstList) {
            firstMap.put(remedialActionResult.getId(), getRemedialActionResultValue(remedialActionResult));
        }
        for (RemedialActionResult remedialActionResult : secondList) {
            if (!firstMap.containsKey(remedialActionResult.getId()) ||
                    Math.abs(firstMap.get(remedialActionResult.getId()) - getRemedialActionResultValue(remedialActionResult)) > 0.001) {
                return false;
            }
        }
        return true;
    }

    private String createAndSwitchToNewVariant(Network network, String referenceNetworkVariant) {
        Objects.requireNonNull(referenceNetworkVariant);
        if (!network.getVariantManager().getVariantIds().contains(referenceNetworkVariant)) {
            throw new FaraoException(String.format("Unknown network variant %s", referenceNetworkVariant));
        }
        String uniqueId = getUniqueVariantId(network);
        network.getVariantManager().cloneVariant(referenceNetworkVariant, uniqueId);
        network.getVariantManager().setWorkingVariant(uniqueId);
        return uniqueId;
    }

    private String getUniqueVariantId(Network network) {
        String uniqueId;
        do {
            uniqueId = UUID.randomUUID().toString();
        } while (network.getVariantManager().getVariantIds().contains(uniqueId));
        return uniqueId;
    }

    private void applyRAs(Crac crac, Network network, List<RemedialActionResult> raResultList) {
        for (RemedialActionResult remedialActionResult : raResultList) {
            for (RemedialActionElementResult remedialActionElementResult : remedialActionResult.getRemedialActionElementResults()) {
                if (remedialActionElementResult instanceof PstElementResult) {
                    crac.getRangeAction(remedialActionElementResult.getId()).apply(network, ((PstElementResult) remedialActionElementResult).getPostOptimisationTapPosition());
                } else if (remedialActionElementResult instanceof RedispatchElementResult) {
                    crac.getRangeAction(remedialActionElementResult.getId()).apply(network, ((RedispatchElementResult) remedialActionElementResult).getPostOptimisationTargetP());
                }
            }
        }

    }

    private double getMinMargin(Crac crac, SystematicSensitivityAnalysisResult systematicSensitivityAnalysisResult) {
        double minMargin = Double.POSITIVE_INFINITY;

        for (Cnec cnec : crac.getCnecs()) {
            double margin = systematicSensitivityAnalysisResult.getCnecMarginMap().getOrDefault(cnec, Double.NaN);
            if (Double.isNaN(margin)) {
                throw new FaraoException(format("Cnec %s is not present in the linear RAO result. Bad behaviour.", cnec.getId()));
            }
            minMargin = Math.min(minMargin, margin);
        }

        return minMargin;
    }

    private RaoComputationResult buildRaoComputationResult(Crac crac, List<RemedialActionResult> raResultList) {
        LinearRaoResult resultExtension = new LinearRaoResult(LinearRaoResult.SecurityStatus.SECURED);
        PreContingencyResult preContingencyResult = createPreContingencyResultAndUpdateLinearRaoResult(crac, resultExtension, raResultList);
        List<ContingencyResult> contingencyResults = createContingencyResultsAndUpdateLinearRaoResult(crac, resultExtension);
        RaoComputationResult raoComputationResult = new RaoComputationResult(RaoComputationResult.Status.SUCCESS, preContingencyResult, contingencyResults);
        raoComputationResult.addExtension(LinearRaoResult.class, resultExtension);
        LOGGER.info("LinearRaoResult: mininum margin = {}, security status: {}", (int) resultExtension.getMinMargin(), resultExtension.getSecurityStatus());
        return raoComputationResult;
    }

    private List<ContingencyResult> createContingencyResultsAndUpdateLinearRaoResult(Crac crac, LinearRaoResult linearRaoResult) {
        List<ContingencyResult> contingencyResults = new ArrayList<>();
        crac.getContingencies().forEach(contingency -> {
            List<MonitoredBranchResult> contingencyMonitoredBranches = new ArrayList<>();
            crac.getStates(contingency).forEach(state -> crac.getCnecs(state).forEach(cnec ->
                    contingencyMonitoredBranches.add(createMonitoredBranchResultAndUpdateLinearRaoResult(cnec, linearRaoResult))));
            contingencyResults.add(new ContingencyResult(contingency.getId(), contingency.getName(), contingencyMonitoredBranches));
        });
        return contingencyResults;
    }

    private PreContingencyResult createPreContingencyResultAndUpdateLinearRaoResult(Crac crac, LinearRaoResult linearRaoResult, List<RemedialActionResult> raResultList) {
        List<MonitoredBranchResult> preContingencyMonitoredBranches = new ArrayList<>();
        if (crac.getPreventiveState() != null) {
            crac.getCnecs(crac.getPreventiveState()).forEach(cnec ->
                preContingencyMonitoredBranches.add(createMonitoredBranchResultAndUpdateLinearRaoResult(cnec, linearRaoResult)
                ));
        }
        return new PreContingencyResult(preContingencyMonitoredBranches, raResultList);
    }

    private MonitoredBranchResult createMonitoredBranchResultAndUpdateLinearRaoResult(Cnec cnec, LinearRaoResult linearRaoResult) {
        double marginPreOptim = preOptimSensitivityAnalysisResult.getCnecMarginMap().getOrDefault(cnec, Double.NaN);
        double marginPostOptim = postOptimSensitivityAnalysisResult.getCnecMarginMap().getOrDefault(cnec, Double.NaN);
        double maximumFlow = preOptimSensitivityAnalysisResult.getCnecMaxThresholdMap().getOrDefault(cnec, Double.NaN);
        if (Double.isNaN(marginPreOptim) || Double.isNaN(marginPostOptim) || Double.isNaN(maximumFlow)) {
            throw new FaraoException(format("Cnec %s is not present in the linear RAO result. Bad behaviour.", cnec.getId()));
        }
        linearRaoResult.updateResult(marginPostOptim);
        double preOptimFlow = maximumFlow - marginPreOptim;
        double postOptimFlow = maximumFlow - marginPostOptim;
        return new MonitoredBranchResult(cnec.getId(), cnec.getName(), cnec.getCriticalNetworkElement().getId(), maximumFlow, preOptimFlow, postOptimFlow);
    }
}

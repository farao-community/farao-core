/*
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.flowbased_computation.impl;

import com.farao_community.farao.commons.FaraoException;
import com.farao_community.farao.commons.Unit;
import com.farao_community.farao.commons.ZonalData;
import com.farao_community.farao.data.crac_api.*;
import com.farao_community.farao.data.crac_api.cnec.FlowCnec;
import com.farao_community.farao.data.crac_api.cnec.Side;
import com.farao_community.farao.data.crac_api.network_action.NetworkAction;
import com.farao_community.farao.data.crac_api.usage_rule.UsageMethod;
import com.farao_community.farao.data.flowbased_domain.*;
import com.farao_community.farao.data.rao_result_api.RaoResult;
import com.farao_community.farao.flowbased_computation.*;
import com.farao_community.farao.commons.RandomizedString;
import com.farao_community.farao.sensitivity_analysis.SystematicSensitivityInterface;
import com.farao_community.farao.sensitivity_analysis.SystematicSensitivityResult;
import com.google.auto.service.AutoService;
import com.powsybl.iidm.network.Network;
import com.powsybl.sensitivity.SensitivityAnalysisParameters;
import com.powsybl.sensitivity.factors.variables.LinearGlsk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Flowbased computation implementation
 *
 * @author Sebastien Murgey {@literal <sebastien.murgey at rte-france.com>}
 */
@AutoService(FlowbasedComputationProvider.class)
public class FlowbasedComputationImpl implements FlowbasedComputationProvider {

    private static final String INITIAL_STATE_WITH_PRA = "InitialStateWithPra";
    private Instant afterCraInstant = null;
    private Set<State> statesWithCras = new HashSet<>();
    private static final Logger LOGGER = LoggerFactory.getLogger(FlowbasedComputationImpl.class);

    @Override
    public String getName() {
        return "SimpleIterativeFlowBased";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public CompletableFuture<FlowbasedComputationResult> run(Network network, Crac crac, RaoResult raoResult, ZonalData<LinearGlsk> glsk, FlowbasedComputationParameters parameters) {
        Objects.requireNonNull(network);
        Objects.requireNonNull(crac);
        Objects.requireNonNull(glsk);
        Objects.requireNonNull(parameters);

        sortInstants(Arrays.asList(Instant.values()));

        SystematicSensitivityInterface systematicSensitivityInterface = SystematicSensitivityInterface.builder()
                .withDefaultParameters(parameters.getSensitivityAnalysisParameters())
                .withPtdfSensitivities(glsk, crac.getFlowCnecs(), Collections.singleton(Unit.MEGAWATT))
                .build();

        // Preventive perimeter
        String initialNetworkId = network.getVariantManager().getWorkingVariantId();
        network.getVariantManager().cloneVariant(initialNetworkId, INITIAL_STATE_WITH_PRA);
        network.getVariantManager().setWorkingVariant(INITIAL_STATE_WITH_PRA);
        if (raoResult == null) {
            LOGGER.debug("RAO result is null: applying all network actions from CRAC.");
            applyAllAvailableRemedialActionsForState(network, crac, crac.getPreventiveState());
        } else {
            LOGGER.debug("RAO result is not null: applying remedial actions selected by the RAO.");
            applyOptimalRemedialActionsForState(network, raoResult, crac.getPreventiveState(), crac.getNetworkActions());
        }
        SystematicSensitivityResult result = systematicSensitivityInterface.run(network);
        FlowbasedComputationResult flowBasedComputationResult = new FlowbasedComputationResultImpl(FlowbasedComputationResult.Status.SUCCESS, buildFlowbasedDomain(crac, glsk, result));

        // Curative perimeter
        if (afterCraInstant != null) {
            statesWithCras = findStatesWithCras(crac, raoResult);
            crac.getStatesFromInstant(afterCraInstant).forEach(state -> handleCurativeState(state, network, crac, raoResult, glsk, parameters.getSensitivityAnalysisParameters(), flowBasedComputationResult.getFlowBasedDomain()));
        } else {
            LOGGER.info("No curative computation in flowbased because 2 or less instants are defined in crac.");
        }

        // Restore initial variant at the end of the computation
        network.getVariantManager().setWorkingVariant(initialNetworkId);
        network.getVariantManager().removeVariant(INITIAL_STATE_WITH_PRA);

        return CompletableFuture.completedFuture(flowBasedComputationResult);
    }

    private void handleCurativeState(State state, Network network, Crac crac, RaoResult raoResult, ZonalData<LinearGlsk> glsk, SensitivityAnalysisParameters sensitivityAnalysisParameters, DataDomain flowbasedDomain) {
        if (statesWithCras.contains(state)) {
            if (raoResult == null) {
                applyAllAvailableRemedialActionsForState(network, crac, state);
            } else {
                applyOptimalRemedialActionsForState(network, raoResult, state, crac.getNetworkActions());
            }

            SystematicSensitivityInterface newSystematicSensitivityInterface = SystematicSensitivityInterface.builder()
                .withDefaultParameters(sensitivityAnalysisParameters)
                .withPtdfSensitivities(glsk, crac.getFlowCnecs(state), Collections.singleton(Unit.MEGAWATT))
                .build();
            SystematicSensitivityResult sensitivityResult = newSystematicSensitivityInterface.run(network);
            Optional<Contingency> contingencyOptional = state.getContingency();
            String contingencyId;
            if (contingencyOptional.isPresent()) {
                contingencyId = contingencyOptional.get().getId();
            } else {
                throw new FaraoException("Contingency shouldn't be empty in curative.");
            }

            List<DataMonitoredBranch> dataMonitoredBranches = flowbasedDomain.findContingencyById(contingencyId).getDataMonitoredBranches();
            dataMonitoredBranches.forEach(dataMonitoredBranch -> updateDataMonitoredBranch(dataMonitoredBranch, crac, sensitivityResult, glsk));
        }
    }

    private void updateDataMonitoredBranch(DataMonitoredBranch dataMonitoredBranch, Crac crac, SystematicSensitivityResult sensitivityResult, ZonalData<LinearGlsk> glsk) {
        if (dataMonitoredBranch.getInstantId().equals(afterCraInstant.toString())) {
            FlowCnec cnec = crac.getFlowCnec(dataMonitoredBranch.getId());
            dataMonitoredBranch.setFref(sensitivityResult.getReferenceFlow(cnec));
            glsk.getDataPerZone().forEach((zone, zonalData) -> {
                List<DataPtdfPerCountry> ptdfs = dataMonitoredBranch.getPtdfList().stream().filter(dataPtdfPerCountry -> dataPtdfPerCountry.getCountry().equals(zonalData.getId())).collect(Collectors.toList());
                if (ptdfs.size() == 1) {
                    double newPtdf = sensitivityResult.getSensitivityOnFlow(zonalData, cnec);
                    if (!Double.isNaN(newPtdf)) {
                        ptdfs.get(0).setPtdf(newPtdf);
                    } else {
                        ptdfs.get(0).setPtdf(0.0);
                    }
                } else {
                    LOGGER.info(String.format("Incorrect ptdf size for zone %s on branch %s: %s", zone, dataMonitoredBranch.getBranchId(), ptdfs.size()));
                }
            });
        }
    }

    private Set<State> findStatesWithCras(Crac crac, RaoResult raoResult) {
        if (raoResult == null) {
            crac.getStates().forEach(state -> findAllStatesWithCraUsageMethod(state, crac.getNetworkActions()));
        } else {
            crac.getStates().forEach(state -> {
                if (!raoResult.getOptimizedSetPointsOnState(state).isEmpty() || !raoResult.getActivatedNetworkActionsDuringState(state).isEmpty()) {
                    statesWithCras.add(state);
                }
            });
        }

        LOGGER.debug("{} curative states with CRAs.", statesWithCras.size());
        return statesWithCras;
    }

    private void findAllStatesWithCraUsageMethod(State state, Set<NetworkAction> networkActions) {
        if (state.getInstant() == afterCraInstant) {
            Optional<NetworkAction> fittingAction = networkActions.stream().filter(networkAction ->
                networkAction.getUsageMethod(state) != null).findAny();
            if (fittingAction.isPresent()) {
                statesWithCras.add(state);
            }
        }
    }

    private void sortInstants(List<Instant> instants) {
        Map<Integer, Instant> instantMap = new HashMap<>();

        for (Instant instant : instants) {
            instantMap.put(instant.getOrder(), instant);
        }
        List<Integer> seconds = new ArrayList<>(instantMap.keySet());
        Collections.sort(seconds);

        if (instants.size() == 1) {
            LOGGER.info("Only Preventive instant is present for flowbased computation.");
        } else if (instants.size() == 2) {
            LOGGER.info("Only Preventive and On outage instants are present for flowbased computation.");
        } else if (instants.size() >= 3) {
            LOGGER.debug("All instants are defined for flowbased computation.");
            // last instant
            afterCraInstant = instantMap.get(seconds.get(seconds.size() - 1));
        } else {
            throw new FaraoException("No instant defined for flowbased computation");
        }
    }

    private DataDomain buildFlowbasedDomain(Crac crac, ZonalData<LinearGlsk> glsk, SystematicSensitivityResult result) {
        return DataDomain.builder()
                .id(RandomizedString.getRandomizedString())
                .name("FlowBased results")
                .description("")
                .sourceFormat("code")
                .dataPreContingency(buildDataPreContingency(crac, glsk, result))
                .dataPostContingency(buildDataPostContingencies(crac, glsk, result))
                .glskData(buildDataGlskFactors(glsk))
                .build();
    }

    private List<DataGlskFactors> buildDataGlskFactors(ZonalData<LinearGlsk> glsk) {
        List<DataGlskFactors> glskFactors = new ArrayList<>();
        glsk.getDataPerZone().forEach((s, linearGlsk) -> glskFactors.add(new DataGlskFactors(s, linearGlsk.getGLSKs())));
        return glskFactors;
    }

    private List<DataPostContingency> buildDataPostContingencies(Crac crac, ZonalData<LinearGlsk> glsk, SystematicSensitivityResult result) {
        List<DataPostContingency> postContingencyList = new ArrayList<>();
        crac.getContingencies().forEach(contingency -> postContingencyList.add(buildDataPostContingency(crac, contingency, glsk, result)));
        return postContingencyList;
    }

    private DataPostContingency buildDataPostContingency(Crac crac, Contingency contingency, ZonalData<LinearGlsk> glsk, SystematicSensitivityResult result) {
        return DataPostContingency.builder()
                .contingencyId(contingency.getId())
                .dataMonitoredBranches(buildDataMonitoredBranches(crac, crac.getStates(contingency), glsk, result))
                .build();
    }

    private DataPreContingency buildDataPreContingency(Crac crac, ZonalData<LinearGlsk> glsk, SystematicSensitivityResult result) {
        return DataPreContingency.builder()
                .dataMonitoredBranches(buildDataMonitoredBranches(crac, Set.of(crac.getPreventiveState()), glsk, result))
                .build();
    }

    private List<DataMonitoredBranch> buildDataMonitoredBranches(Crac crac, Set<State> states, ZonalData<LinearGlsk> glsk, SystematicSensitivityResult result) {
        List<DataMonitoredBranch> branchResultList = new ArrayList<>();
        states.forEach(state -> crac.getFlowCnecs(state).forEach(cnec -> branchResultList.add(buildDataMonitoredBranch(cnec, glsk, result))));
        return branchResultList;
    }

    private DataMonitoredBranch buildDataMonitoredBranch(FlowCnec cnec, ZonalData<LinearGlsk> glsk, SystematicSensitivityResult result) {
        double maxThreshold = cnec.getUpperBound(Side.LEFT, Unit.MEGAWATT).orElse(Double.POSITIVE_INFINITY);
        double minThreshold = cnec.getLowerBound(Side.LEFT, Unit.MEGAWATT).orElse(Double.NEGATIVE_INFINITY);
        return new DataMonitoredBranch(
                cnec.getId(),
                cnec.getName(),
                cnec.getState().getInstant().toString(),
                cnec.getNetworkElement().getId(),
                Math.min(maxThreshold, -minThreshold),
                zeroIfNaN(result.getReferenceFlow(cnec)),
                buildDataPtdfPerCountry(cnec, glsk, result)
        );
    }

    private List<DataPtdfPerCountry> buildDataPtdfPerCountry(FlowCnec cnec, ZonalData<LinearGlsk> glskProvider, SystematicSensitivityResult result) {
        Map<String, LinearGlsk> glsks = glskProvider.getDataPerZone();
        return glsks.values().stream()
                .map(glsk ->
                        new DataPtdfPerCountry(
                                glsk.getId(),
                                zeroIfNaN(result.getSensitivityOnFlow(glsk.getId(), cnec))
                        )
                ).collect(Collectors.toList());
    }

    /**
     * Apply all remedial actions saved in CRAC, on a given network, at a given state.
     *
     * @param network Network on which remedial actions should be applied
     * @param crac CRAC that should contain result extension
     * @param state State for which the RAs should be applied
     */
    public static void applyAllAvailableRemedialActionsForState(Network network, Crac crac, State state) {
        crac.getNetworkActions().forEach(na -> {
            UsageMethod usageMethod = na.getUsageMethod(state);
            if (usageMethod.equals(UsageMethod.AVAILABLE) || usageMethod.equals(UsageMethod.FORCED)) {
                na.apply(network);
            } else if (usageMethod.equals(UsageMethod.TO_BE_EVALUATED)) {
                LOGGER.warn("Network action {} with usage method TO_BE_EVALUATED will not be applied, as we don't have access to the flow results.", na.getId());
                /*
                 * This method is only used in FlowbasedComputation.
                 * We do not assess the availability of such remedial actions: they're not supposed to exist.
                 * If it is needed in the future, we will have to loop around a sensitivity computation, followed by a
                 * re-assessment of additional available RAs and applying them, then re-running sensitivity, etc
                 * until the list of applied remedial actions stops changing
                 */
            }
        });
    }

    /**
     * Apply remedial actions saved in CRAC result extension on current working variant of given network, at a given state.
     *
     * @param network Network on which remedial actions should be applied
     * @param raoResult Result of Rao computation
     * @param state State for which the RAs should be applied
     */
    public static void applyOptimalRemedialActionsForState(Network network, RaoResult raoResult, State state, Set<NetworkAction> networkActions) {
        networkActions.forEach(na -> {
            if (raoResult.isActivated(state, na)) {
                na.apply(network);
            }
        });
        raoResult.getOptimizedSetPointsOnState(state).forEach((ra, setpoint) -> ra.apply(network, setpoint));
    }

    private double zeroIfNaN(double value) {
        return Double.isNaN(value) ? 0. : value;
    }
}

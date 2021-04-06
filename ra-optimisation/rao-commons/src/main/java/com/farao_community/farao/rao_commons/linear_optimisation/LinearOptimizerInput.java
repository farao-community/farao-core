package com.farao_community.farao.rao_commons.linear_optimisation;

import com.farao_community.farao.data.crac_api.RangeAction;
import com.farao_community.farao.data.crac_api.cnec.BranchCnec;
import com.farao_community.farao.data.crac_result_extensions.CnecResult;
import com.powsybl.iidm.network.Network;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class LinearOptimizerInput {
    private Set<BranchCnec> loopflowCnecs;
    private Set<BranchCnec> cnecs;
    private Set<RangeAction> rangeActions;
    private Network network;
    private Map<RangeAction, Double> preperimeterSetpoints; // can be removed if we don't change taps in the network after each depth
    private List<BranchCnec> mostLimitingElements;
    private Map<BranchCnec, CnecResult> initialCnecResults;
    private Map<BranchCnec, Double> prePerimeterCnecMarginsInAbsoluteMW;

    public static LinearOptimizerInputBuilder create() {
        return new LinearOptimizerInputBuilder();
    }

    public Set<BranchCnec> getLoopflowCnecs() {
        return loopflowCnecs;
    }

    public Set<BranchCnec> getCnecs() {
        return cnecs;
    }

    public Set<RangeAction> getRangeActions() {
        return rangeActions;
    }

    public Network getNetwork() {
        return network;
    }

    public Map<RangeAction, Double> getPreperimeterSetpoints() {
        return preperimeterSetpoints;
    }

    public BranchCnec getMostLimitingElement() {
        return mostLimitingElements.get(0);
    }

    public List<BranchCnec> getMostLimitingElements() {
        return mostLimitingElements;
    }

    public double getInitialAbsolutePtdfSum(BranchCnec cnec) {
        return initialCnecResults.get(cnec).getAbsolutePtdfSum();
    }

    public double getInitialFlowInMW(BranchCnec cnec) {
        return initialCnecResults.get(cnec).getFlowInMW();
    }

    public double getInitialLoopflowInMW(BranchCnec cnec) {
        return initialCnecResults.get(cnec).getLoopflowInMW();
    }

    public double getPrePerimeterMarginsInAbsoluteMW(BranchCnec cnec) {
        return prePerimeterCnecMarginsInAbsoluteMW.get(cnec);
    }

    public static final class LinearOptimizerInputBuilder {
        private Set<BranchCnec> loopflowCnecs;
        private Set<BranchCnec> cnecs;
        private Set<RangeAction> rangeActions;
        private Network network;
        private Map<RangeAction, Double> preperimeterSetpoints;
        private List<BranchCnec> mostLimitingElements;
        private Map<BranchCnec, CnecResult> initialCnecResults;
        private Map<BranchCnec, Double> prePerimeterCnecMarginsInAbsoluteMW;

        public LinearOptimizerInputBuilder withLoopflowCnecs(Set<BranchCnec> loopflowCnecs) {
            this.loopflowCnecs = loopflowCnecs;
            return this;
        }

        public LinearOptimizerInputBuilder withCnecs(Set<BranchCnec> cnecs) {
            this.cnecs = cnecs;
            return this;
        }

        public LinearOptimizerInputBuilder withRangeActions(Set<RangeAction> rangeActions) {
            this.rangeActions = rangeActions;
            return this;
        }

        public LinearOptimizerInputBuilder withNetwork(Network network) {
            this.network = network;
            return this;
        }

        public LinearOptimizerInputBuilder withPreperimeterSetpoints(Map<RangeAction, Double> preperimeterSetpoints) {
            this.preperimeterSetpoints = preperimeterSetpoints;
            return this;
        }

        public LinearOptimizerInputBuilder withMostLimitingElements(List<BranchCnec> mostLimitingElements) {
            this.mostLimitingElements = mostLimitingElements;
            return this;
        }

        public LinearOptimizerInputBuilder withInitialCnecResults(Map<BranchCnec, CnecResult> initialCnecResults) {
            this.initialCnecResults = initialCnecResults;
            return this;
        }

        public LinearOptimizerInputBuilder withPrePerimeterCnecMarginsInAbsoluteMW(Map<BranchCnec, Double> prePerimeterCnecMarginsInAbsoluteMW) {
            this.prePerimeterCnecMarginsInAbsoluteMW = prePerimeterCnecMarginsInAbsoluteMW;
            return this;
        }

        public LinearOptimizerInput build() {
            // TODO : check non null arguments
            LinearOptimizerInput linearOptimizerInput = new LinearOptimizerInput();
            linearOptimizerInput.loopflowCnecs = this.loopflowCnecs;
            linearOptimizerInput.cnecs = this.cnecs;
            linearOptimizerInput.rangeActions = this.rangeActions;
            linearOptimizerInput.network = this.network;
            linearOptimizerInput.preperimeterSetpoints = this.preperimeterSetpoints;
            linearOptimizerInput.mostLimitingElements = this.mostLimitingElements;
            linearOptimizerInput.initialCnecResults = this.initialCnecResults;
            linearOptimizerInput.prePerimeterCnecMarginsInAbsoluteMW = this.prePerimeterCnecMarginsInAbsoluteMW;
            return linearOptimizerInput;
        }
    }
}

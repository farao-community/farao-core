package com.farao_community.farao.data.crac_api.range_action;

/**
 * @author Baptiste Seguinot {@literal <baptiste.seguinot at rte-france.com>}
 * @author Peter Mitri {@literal <peter.mitri at rte-france.com>}
 */
public interface TapRangeAdder {

    TapRangeAdder withMinTap(int minTap);

    TapRangeAdder withMaxTap(int maxTap);

    TapRangeAdder withRangeType(RangeType rangeType);

    PstRangeActionAdder add();

}

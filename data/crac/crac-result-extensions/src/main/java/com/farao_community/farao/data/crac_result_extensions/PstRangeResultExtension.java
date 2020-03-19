package com.farao_community.farao.data.crac_result_extensions;

import com.farao_community.farao.data.crac_api.RangeAction;

/**
 * @author Joris Mancini {@literal <joris.mancini at rte-france.com>}
 */
public class PstRangeResultExtension extends ResultExtension<RangeAction, PstRangeResult> {
    /**
     * Extension name
     */
    @Override
    public String getName() {
        return "RangeActionResultExtension";
    }
}

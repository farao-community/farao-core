/*
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.farao_community.farao.rao_api.results;

import com.farao_community.farao.commons.Unit;
import com.farao_community.farao.data.crac_api.RangeAction;
import com.farao_community.farao.data.crac_api.cnec.BranchCnec;

/**
 * @author Joris Mancini {@literal <joris.mancini at rte-france.com>}
 */
public interface SensitivityResult {

    double getSensitivityValue(BranchCnec branchCnec, RangeAction rangeAction, Unit unit);
}
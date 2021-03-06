/*
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.farao_community.farao.data.crac_api.usage_rule;

import com.farao_community.farao.data.crac_api.State;

/**
 * The UsageRule defines conditions under which a RemedialAction can be used.
 *
 * @author Viktor Terrier {@literal <viktor.terrier at rte-france.com>}
 */
public interface UsageRule {

    /**
     * Get the {@link UsageMethod} of the usage rule
     */
    UsageMethod getUsageMethod();

    /**
     * Get the {@link UsageMethod} of the usage rule on a given state
     */
    UsageMethod getUsageMethod(State state);
}

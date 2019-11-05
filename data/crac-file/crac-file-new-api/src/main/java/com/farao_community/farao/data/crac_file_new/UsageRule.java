/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.farao_community.farao.data.crac_file_new;

/**
 * Business object of a usage rule in the CRAC file
 *
 * @author Xxx Xxx {@literal <xxx.xxx at rte-france.com>}
 */
public class UsageRule {

    public enum UsageMethod {
        FORCED,
        AVAILABLE,
        UNAVAILABLE
    }

    private UsageMethod usageMethod;
    private UsageContext usageContext;

    public UsageRule(UsageMethod usageMethod, UsageContext usageContext) {
        this.usageMethod = usageMethod;
        this.usageContext = usageContext;
    }
}

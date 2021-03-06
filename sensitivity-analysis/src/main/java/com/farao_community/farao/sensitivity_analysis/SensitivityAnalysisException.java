/*
 *
 *  * Copyright (c) 2020, RTE (http://www.rte-france.com)
 *  * This Source Code Form is subject to the terms of the Mozilla Public
 *  * License, v. 2.0. If a copy of the MPL was not distributed with this
 *  * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 */

package com.farao_community.farao.sensitivity_analysis;

import com.farao_community.farao.commons.FaraoException;

/**
 * @author Viktor Terrier {@literal <viktor.terrier at rte-france.com>}
 */
public class SensitivityAnalysisException extends FaraoException {

    public SensitivityAnalysisException(String message) {
        super(message);
    }

    public SensitivityAnalysisException(final String message, final Throwable cause) {
        super(message, cause);
    }
}

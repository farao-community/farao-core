/*
 *  Copyright (c) 2020, RTE (http://www.rte-france.com)
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.farao_community.farao.data.crac_io_xml;

import com.farao_community.farao.data.crac_api.Crac;
import com.farao_community.farao.data.crac_io_api.CracImporter;

import java.io.InputStream;

/**
 * @author Viktor Terrier {@literal <viktor.terrier at rte-france.com>}
 */
public class MergedFilteredCBImporter implements CracImporter {

    public MergedFilteredCBImporter()  {
    }

    @Override
    public Crac importCrac(InputStream inputStream) {
        return null;
    }

    @Override
    public boolean exists(String fileName, InputStream inputStream) {
        return false;
    }
}

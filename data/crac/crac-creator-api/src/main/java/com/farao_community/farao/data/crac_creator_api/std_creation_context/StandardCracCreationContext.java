/*
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.data.crac_creator_api.std_creation_context;

import com.farao_community.farao.data.crac_creator_api.CracCreationContext;

import java.util.List;

public interface StandardCracCreationContext extends CracCreationContext {

    List<BranchCnecCreationContext> getBranchCnecCreationContexts();

    List<RemedialActionCreationContext> getRemedialActionCreationContexts();
}

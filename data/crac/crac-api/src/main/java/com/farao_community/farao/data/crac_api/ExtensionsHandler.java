/*
 *  Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 */

package com.farao_community.farao.data.crac_api;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.powsybl.commons.extensions.Extension;
import com.powsybl.commons.extensions.ExtensionJsonSerializer;
import com.powsybl.commons.extensions.ExtensionProviders;

/**
 * @author Baptiste Seguinot {@literal <baptiste.seguinot at rte-france.com>}
 */
// TODO : delete ? remove from API ?
public final class ExtensionsHandler {

    private ExtensionsHandler() { }

    public interface ExtensionSerializer<E extends Identifiable, F extends Extension<E>> extends ExtensionJsonSerializer<E, F> { }

    private static final Supplier<ExtensionProviders<ExtensionSerializer>> SERIALIZER_SUPPLIER =
            Suppliers.memoize(() -> ExtensionProviders.createProvider(ExtensionSerializer.class));

    /**
     * Gets the known extension serializers.
     */
    public static ExtensionProviders<ExtensionSerializer> getExtensionsSerializers() {
        return SERIALIZER_SUPPLIER.get();
    }
}

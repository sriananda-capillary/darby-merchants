package com.sellerworx.modules.apollo.util;

import com.sellerworx.darby.annotation.NoObfuscation;
import com.sellerworx.darby.core.document.Documented;
import com.sellerworx.darby.util.ConfigKey;
import com.sellerworx.darby.util.TenantConfigKeys;

/**
 *
 * @author prashant.singla
 */

@NoObfuscation
@Documented(description = "used to add config keys for all the integration for apollo")
public class ApolloTenantConfigKeys extends TenantConfigKeys {

    public static final ConfigKey APOLLO_API_HOST = new ConfigKey("apolloApiHost");

    public static final ConfigKey APOLLO_API_USERNAME = new ConfigKey("apolloApiUsername");
    public static final ConfigKey APOLLO_API_PASSWORD = new ConfigKey("apolloApiPassword");
}

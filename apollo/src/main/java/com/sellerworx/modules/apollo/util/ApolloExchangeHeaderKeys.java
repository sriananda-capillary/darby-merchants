package com.sellerworx.modules.apollo.util;

import com.sellerworx.darby.util.ExchangeHeaderKeys;
import com.sellerworx.darby.util.KeyValueUtil;

import java.util.List;

public class ApolloExchangeHeaderKeys extends ExchangeHeaderKeys {

    public static final KeyValueUtil<String> APOLLO_ORDER_SOURCE = new KeyValueUtil("apolloOrderSource",
                                                                                    String.class);

}

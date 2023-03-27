package com.sellerworx.modules.apollo.util.helper;

import com.sellerworx.darby.annotation.NoObfuscation;
import com.sellerworx.darby.core.document.Documented;
import com.sellerworx.darby.util.SymbolUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 *
 * @author akashMane
 */

@Slf4j
@NoObfuscation
@Component("ApolloHelper")
@Documented(description = "used to add common methods for all the integration for apollo")
public class ApolloHelper {

    private static String SALES_ORDER_FILE_PREFIX = "Medsmart_SO";
    private static String SALES_ORDER_FILE_SUFFIX = "json";

    public static String getSalesOrderFileName(String locationCode) {
        StringBuilder fileName = new StringBuilder();
        fileName.append(SALES_ORDER_FILE_PREFIX);
        fileName.append(SymbolUtil.UNDERSCORE);
        fileName.append(locationCode);
        fileName.append(SymbolUtil.UNDERSCORE);

        return fileName.toString();
    }

    public String getVendorCode(String vendorName) {
        String regex = "^[a-zA-Z]{1,5}";
        vendorName = vendorName.replace(StringUtils.SPACE, StringUtils.EMPTY);

        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(vendorName);

        String vendorCode = StringUtils.EMPTY;
        if (matcher.find()) {
            vendorCode = matcher.group(0);
            log.info("first five or less char '" + vendorCode + "' as vendor code from vendor name : " + vendorName);
        } else {
            log.error("vendor name empty or have no char");
        }
        return vendorCode;
    }
}

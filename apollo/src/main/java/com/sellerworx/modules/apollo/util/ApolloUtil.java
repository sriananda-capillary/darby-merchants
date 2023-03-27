package com.sellerworx.modules.apollo.util;

import com.sellerworx.darby.annotation.NoObfuscation;
import com.sellerworx.darby.core.document.Documented;
import org.json.JSONObject;

/**
 * @author akashMane
 */

@NoObfuscation
@Documented(description = "used to add common utility for apollo")
public class ApolloUtil {
    public static final String ITEM_MASTER_ROOT_TAG = "Product";

    public static final String ITEM_MASTER_KEY_CATEGORY = "Category";
    public static final String ITEM_MASTER_KEY_PACK = "Pack";
    public static final String ITEM_MASTER_KEY_GENERIC = "Generic";
    public static final String ITEM_MASTER_KEY_MANUFACTURER = "Manufacturer";
    public static final String ITEM_MASTER_KEY_DIVISION = "Division";
    public static final String ITEM_MASTER_KEY_BOX_QTY = "Box_Qty";
    public static final String ITEM_MASTER_KEY_CASE_QTY = "Case_Qty";
    public static final String ITEM_MASTER_KEY_APOLLO_CODE = "Apollo_Code";
    public static final String ITEM_MASTER_KEY_SCHEME = "Scheme";
    public static final String ITEM_MASTER_KEY_BATCHNO = "BatchNo";
    public static final String ITEM_MASTER_KEY_EXPIRYDT = "ExpiryDt";
    public static final String ITEM_MASTER_KEY_PROMOCODE = "PromoCode";
    public static final String MJ_PRODUCT_ATTRIBUTE_ITEM_KEY = "itemNumber";

    public static final String QUANTITY = "quantity";
    public static final String LOCATION_REF_CODE = "locationReferenceCode";
    public static final String STOCK = "stock";

    public static final String ITEM_PRICE_ROOT_TAG = "Price";
    public static final String ITEM_PRICE_MRP = "MRP";
    public static final String BASE_SALE_PRICE = "Base_Sale_Price";
    public static final String INSTITUTIONAL_SALE_PRICE = "Institutional_Sale_Price";
    public static final String TRADE_SALE_PRICE = "Trade_Sale_Price";
    public static final String DISTRIBUTOR_SALE_PRICE = "Distributor_Sale_Price";

    public static final String ONE = "1";

    public static final String ITEM_MLPL_CODE = "MLPL_Code";
    public static final String ITEM_LOCATION_CODE = "Store_ID";

    public static final String ITEM_STOCK_ROOT_TAG = "Product";
    public static final String CLOSING_STOCK = "Closing_Stock";

    public static final String ORDER_NO = "OrderNo";
    public static final String CUST_CODE = "CustCode";
    public static final String ITEM = "Item";
    public static final String ITEM_CODE = "Itemcode";
    public static final String SO_QUANTITY = "Qty";
    public static final String SO_MRP = "MRP";
    public static final String SO_PRICE = "Price";
    public static final String ORDER_DETAILS = "OrderDetails";
    public static final String ORDER_TOTAL = "Order_Total";
    public static final String PAYMENT_TYPE = "Payment_Type";
    public static final String ORDERDATE = "OrderDate";
    public static final String STORE_ID = "Store_ID";
    public static final String REF_NO = "ApolloRefNo";
    public static final String SKU_COUNT = "SKUCount";

    public static final String MEMBER_CODE = "Code";
    public static final String MEMBER_STORE_ID = "Store_ID";
    public static final String MEMBER_CATEGORY = "Category";
    public static final String MEMBER_NAME = "Name";
    public static final String MEMBER_CITY = "City";
    public static final String MEMBER_STATE = "State";
    public static final String MEMBER_ADDRESS = "Address";
    public static final String MEMBER_ADDRESS1 = "Address1";
    public static final String MEMBER_ADDRESS2 = "Address2";
    public static final String MEMBER_MOBILE = "Mobile";
    public static final String MEMBER_EMAIL = "Email";
    public static final String MEMBER_DL_NO = "DL_No";
    public static final String MEMBER_GST_NO = "GST_no";
    public static final String MEMBER_PANEL = "Panel";
    public static final String MEMBER_PINCODE = "Pincode";
    public static final String MEMBER_STATUS = "Status";

    public static final String DEFAULT_PASSWORD = "defualt_password";

    public static final String MARTIAL_STATUS = "true";

    public static final String MEMBER_CUSTOMER_KEY = "Customer";

    //Customer information


    public static final String USERNAME = "UserName";

    public static final String DEFAULT_COUNTRY_NAME = "INDIA";
    public static final String DEFAULT_COUNTRY_CODE = "IN";

    public static final String VENDOR_NAME = "vendorName";
    public static final String VENDOR_CODE = "vendorCode";
    public static final String ORDER_FOR = "OrderFor";
    public static final String SUPPLIER_ORDER_NO = "SupplierOrderNo";
    public static final String INVALID_VENDORS = "invalidVendors";

    public static final String ACK_ORDER_ALREADY_ACKED_ERR_MSG_PREFIX =
            "Acknowledgement JSON is already received for Purchase Order Number:";

    public static final String INVOICE_ORDER_ALREADY_ACKED_ERR_MSG_PREFIX =
            "Invoice JSON is already received for Order Number:";

    public static final String SHIPMENT_ORDER_ALREADY_ACKED_ERR_MSG_PREFIX =
            "Shipment(ASN) JSON is already received for Purchase Order Number:";



    public static boolean validateMasterSchema(JSONObject jsonObject, String tagName) {
        boolean isValid = false;
        if (jsonObject.has(tagName)) {
            isValid = true;
        }
        return isValid;
    }
}

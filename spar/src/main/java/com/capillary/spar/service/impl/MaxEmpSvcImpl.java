package com.capillary.spar.service.impl;

import com.capillary.spar.service.MaxEmpService;
import com.landmark.max.MAXEmployeeDiscountLimitServiceStub;
import com.sellerworx.darby.core.soap.SoapModel;
import com.sellerworx.darby.enums.INTEGRATION_CHANNEL;
import com.sellerworx.darby.util.SOAPConnector;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import max.retail.stores.ws.employee.GetEmployeeDetailsE;
import max.retail.stores.ws.employee.GetEmployeeDetailsResponse;
import max.retail.stores.ws.employee.GetEmployeeDetailsResponseE;
import org.apache.axis2.AxisFault;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.rmi.RemoteException;


@Slf4j
@Service
@FieldDefaults(level = AccessLevel.PRIVATE)
public class MaxEmpSvcImpl implements MaxEmpService {

    @Autowired
    SOAPConnector soapConnector;

    private final String POS_NAME = INTEGRATION_CHANNEL.POS_LANDMARK_MAX.toString();
    private final String ENTITY = "Employee";

    public String getEmployee(final String targetEndPoint,
                              GetEmployeeDetailsE getEmployeeDetailsReq) {

        SoapModel soapModel = new SoapModel(targetEndPoint, "GetEmployeeDetails", getEmployeeDetailsReq,
                                            GetEmployeeDetailsE.MY_QNAME, GetEmployeeDetailsResponseE.MY_QNAME,
                                            POS_NAME, ENTITY);

        GetEmployeeDetailsResponse empDtlResp = (GetEmployeeDetailsResponse) soapConnector.callService(soapModel,
                                                                          () -> getEmplDetails(
                                                                          targetEndPoint, getEmployeeDetailsReq));

        if (null != empDtlResp && StringUtils.isNotBlank(empDtlResp.get_return())) {
            return empDtlResp.get_return();
        }
        return null;
    }

    private GetEmployeeDetailsResponse getEmplDetails(String targetEndPoint, GetEmployeeDetailsE request) {

        MAXEmployeeDiscountLimitServiceStub stub = null;
        GetEmployeeDetailsResponseE getEmployeeDetailsResponseE = null;
        try {
            stub = new MAXEmployeeDiscountLimitServiceStub(targetEndPoint);

            getEmployeeDetailsResponseE = stub.getEmployeeDetails(request);
        } catch (AxisFault axisFault) {
            log.error("exception occured {}", axisFault);
        } catch (RemoteException e) {
            log.error("exception occurred {}", e);
        }
        GetEmployeeDetailsResponse getEmployeeDetailsResponse =
            getEmployeeDetailsResponseE.getGetEmployeeDetailsResponse();
        return getEmployeeDetailsResponse;
    }

}

package com.capillary.spar.service;

import max.retail.stores.ws.employee.GetEmployeeDetailsE;

public interface MaxEmpService {
    public String getEmployee(final String targetEndPoint, GetEmployeeDetailsE getEmployeeDetailsReq);
}

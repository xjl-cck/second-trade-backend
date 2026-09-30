package org.example.secondhandtradingplatform.order.returnrequest.service;

import org.example.secondhandtradingplatform.order.returnrequest.dto.ReturnRequestCreateRequest;
import org.example.secondhandtradingplatform.order.returnrequest.dto.ReturnRequestHandleRequest;
import org.example.secondhandtradingplatform.order.returnrequest.dto.ReturnRequestResponse;

public interface ReturnRequestService {

    ReturnRequestResponse findByOrderId(Long orderId);

    ReturnRequestResponse create(Long orderId, ReturnRequestCreateRequest request);

    ReturnRequestResponse approve(Long returnRequestId, ReturnRequestHandleRequest request);

    ReturnRequestResponse reject(Long returnRequestId, ReturnRequestHandleRequest request);

    ReturnRequestResponse complete(Long returnRequestId, ReturnRequestHandleRequest request);
}

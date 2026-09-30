package org.example.secondhandtradingplatform.order.returnrequest.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.example.secondhandtradingplatform.common.Result;
import org.example.secondhandtradingplatform.order.returnrequest.dto.ReturnRequestCreateRequest;
import org.example.secondhandtradingplatform.order.returnrequest.dto.ReturnRequestHandleRequest;
import org.example.secondhandtradingplatform.order.returnrequest.dto.ReturnRequestResponse;
import org.example.secondhandtradingplatform.order.returnrequest.service.ReturnRequestService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
@Tag(name = "退货模块", description = "退货申请、审核和退款确认")
public class ReturnRequestController {

    private final ReturnRequestService returnRequestService;

    public ReturnRequestController(ReturnRequestService returnRequestService) {
        this.returnRequestService = returnRequestService;
    }

    @PostMapping("/{orderId}/return-requests")
    @Operation(summary = "申请退货")
    public Result<ReturnRequestResponse> create(@PathVariable Long orderId,
                                                @Valid @RequestBody ReturnRequestCreateRequest request) {
        return Result.success(returnRequestService.create(orderId, request));
    }

    @PostMapping("/return-requests/{returnRequestId}/approve")
    @Operation(summary = "同意退货")
    public Result<ReturnRequestResponse> approve(@PathVariable Long returnRequestId,
                                                 @Valid @RequestBody(required = false) ReturnRequestHandleRequest request) {
        return Result.success(returnRequestService.approve(returnRequestId, request));
    }

    @PostMapping("/return-requests/{returnRequestId}/reject")
    @Operation(summary = "驳回退货")
    public Result<ReturnRequestResponse> reject(@PathVariable Long returnRequestId,
                                                @Valid @RequestBody(required = false) ReturnRequestHandleRequest request) {
        return Result.success(returnRequestService.reject(returnRequestId, request));
    }

    @PostMapping("/return-requests/{returnRequestId}/complete")
    @Operation(summary = "确认退款完成")
    public Result<ReturnRequestResponse> complete(@PathVariable Long returnRequestId,
                                                  @Valid @RequestBody(required = false) ReturnRequestHandleRequest request) {
        return Result.success(returnRequestService.complete(returnRequestId, request));
    }
}

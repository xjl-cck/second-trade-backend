package org.example.secondhandtradingplatform.order.returnrequest.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.example.secondhandtradingplatform.common.exception.BizException;
import org.example.secondhandtradingplatform.common.exception.ErrorCode;
import org.example.secondhandtradingplatform.order.entity.TradeOrder;
import org.example.secondhandtradingplatform.order.mapper.TradeOrderMapper;
import org.example.secondhandtradingplatform.order.returnrequest.dto.ReturnRequestCreateRequest;
import org.example.secondhandtradingplatform.order.returnrequest.dto.ReturnRequestHandleRequest;
import org.example.secondhandtradingplatform.order.returnrequest.dto.ReturnRequestResponse;
import org.example.secondhandtradingplatform.order.returnrequest.entity.ReturnRequest;
import org.example.secondhandtradingplatform.order.returnrequest.mapper.ReturnRequestMapper;
import org.example.secondhandtradingplatform.order.returnrequest.service.ReturnRequestService;
import org.example.secondhandtradingplatform.security.UserContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Service
public class ReturnRequestServiceImpl implements ReturnRequestService {

    private static final int ORDER_DEALED = 1;
    private static final int ORDER_FINISHED = 3;
    private static final int RETURN_PENDING = 0;
    private static final int RETURN_APPROVED = 1;
    private static final int RETURN_REJECTED = 2;
    private static final int RETURN_COMPLETED = 3;

    private final ReturnRequestMapper returnRequestMapper;
    private final TradeOrderMapper tradeOrderMapper;

    public ReturnRequestServiceImpl(ReturnRequestMapper returnRequestMapper,
                                    TradeOrderMapper tradeOrderMapper) {
        this.returnRequestMapper = returnRequestMapper;
        this.tradeOrderMapper = tradeOrderMapper;
    }

    @Override
    public ReturnRequestResponse findByOrderId(Long orderId) {
        ReturnRequest returnRequest = returnRequestMapper.selectByOrderId(orderId);
        return returnRequest == null ? null : ReturnRequestResponse.from(returnRequest);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReturnRequestResponse create(Long orderId, ReturnRequestCreateRequest request) {
        Long userId = requireLoginUserId();
        TradeOrder order = getOrderForUpdate(orderId);
        if (!userId.equals(order.getBuyerId())) {
            throw new BizException(ErrorCode.FORBIDDEN, "只有买家可以申请退货");
        }
        if (!Integer.valueOf(ORDER_DEALED).equals(order.getStatus())
                && !Integer.valueOf(ORDER_FINISHED).equals(order.getStatus())) {
            throw new BizException(ErrorCode.BUSINESS_ERROR, "只有已成交或已完成订单可以申请退货");
        }
        if (returnRequestMapper.selectByOrderId(orderId) != null) {
            throw new BizException(ErrorCode.BUSINESS_ERROR, "该订单已经提交过退货申请");
        }

        ReturnRequest returnRequest = new ReturnRequest();
        returnRequest.setOrderId(order.getId());
        returnRequest.setBuyerId(order.getBuyerId());
        returnRequest.setSellerId(order.getSellerId());
        returnRequest.setReason(request.getReason().trim());
        returnRequest.setDescription(trimToNull(request.getDescription()));
        returnRequest.setStatus(RETURN_PENDING);
        returnRequest.setIsDeleted(0);
        returnRequestMapper.insert(returnRequest);
        return ReturnRequestResponse.from(returnRequest);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReturnRequestResponse approve(Long returnRequestId, ReturnRequestHandleRequest request) {
        ReturnRequest returnRequest = getReturnRequestForUpdate(returnRequestId);
        requireSeller(returnRequest);
        requireStatus(returnRequest, RETURN_PENDING, "只有待审核退货申请可以同意");
        returnRequest.setStatus(RETURN_APPROVED);
        returnRequest.setSellerRemark(trimToNull(request == null ? null : request.getRemark()));
        returnRequest.setApprovedTime(LocalDateTime.now());
        returnRequestMapper.updateById(returnRequest);
        return ReturnRequestResponse.from(returnRequest);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReturnRequestResponse reject(Long returnRequestId, ReturnRequestHandleRequest request) {
        ReturnRequest returnRequest = getReturnRequestForUpdate(returnRequestId);
        requireSeller(returnRequest);
        requireStatus(returnRequest, RETURN_PENDING, "只有待审核退货申请可以驳回");
        returnRequest.setStatus(RETURN_REJECTED);
        returnRequest.setSellerRemark(trimToNull(request == null ? null : request.getRemark()));
        returnRequestMapper.updateById(returnRequest);
        return ReturnRequestResponse.from(returnRequest);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReturnRequestResponse complete(Long returnRequestId, ReturnRequestHandleRequest request) {
        ReturnRequest returnRequest = getReturnRequestForUpdate(returnRequestId);
        requireSeller(returnRequest);
        requireStatus(returnRequest, RETURN_APPROVED, "只有退货中的申请可以确认退款");
        returnRequest.setStatus(RETURN_COMPLETED);
        String remark = trimToNull(request == null ? null : request.getRemark());
        if (remark != null) {
            returnRequest.setSellerRemark(remark);
        }
        returnRequest.setCompletedTime(LocalDateTime.now());
        returnRequestMapper.updateById(returnRequest);
        return ReturnRequestResponse.from(returnRequest);
    }

    private ReturnRequest getReturnRequestForUpdate(Long id) {
        ReturnRequest returnRequest = returnRequestMapper.selectByIdForUpdate(id);
        if (returnRequest == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "退货申请不存在");
        }
        return returnRequest;
    }

    private TradeOrder getOrderForUpdate(Long orderId) {
        TradeOrder order = tradeOrderMapper.selectByIdForUpdate(orderId);
        if (order == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "订单不存在");
        }
        return order;
    }

    private void requireSeller(ReturnRequest returnRequest) {
        if (!requireLoginUserId().equals(returnRequest.getSellerId())) {
            throw new BizException(ErrorCode.FORBIDDEN, "只有卖家可以处理退货申请");
        }
    }

    private void requireStatus(ReturnRequest returnRequest, int status, String message) {
        if (!Integer.valueOf(status).equals(returnRequest.getStatus())) {
            throw new BizException(ErrorCode.BUSINESS_ERROR, message);
        }
    }

    private Long requireLoginUserId() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        return userId;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}

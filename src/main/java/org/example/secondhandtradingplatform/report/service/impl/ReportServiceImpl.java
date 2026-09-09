package org.example.secondhandtradingplatform.report.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.example.secondhandtradingplatform.chat.entity.ChatMessage;
import org.example.secondhandtradingplatform.chat.mapper.ChatMessageMapper;
import org.example.secondhandtradingplatform.common.exception.BizException;
import org.example.secondhandtradingplatform.common.exception.ErrorCode;
import org.example.secondhandtradingplatform.product.entity.Product;
import org.example.secondhandtradingplatform.product.mapper.ProductMapper;
import org.example.secondhandtradingplatform.report.dto.ReportCreateRequest;
import org.example.secondhandtradingplatform.report.dto.ReportHandleRequest;
import org.example.secondhandtradingplatform.report.dto.ReportListRequest;
import org.example.secondhandtradingplatform.report.dto.ReportResponse;
import org.example.secondhandtradingplatform.report.entity.Report;
import org.example.secondhandtradingplatform.report.mapper.ReportMapper;
import org.example.secondhandtradingplatform.report.service.ReportService;
import org.example.secondhandtradingplatform.security.UserContext;
import org.example.secondhandtradingplatform.user.entity.SysUser;
import org.example.secondhandtradingplatform.user.mapper.SysUserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Set;

@Service
public class ReportServiceImpl implements ReportService {

    private static final int ADMIN_ROLE = 1;
    private static final int STATUS_PENDING = 0;
    private static final Set<String> TARGET_TYPES = Set.of("product", "user", "message");

    private final ReportMapper reportMapper;
    private final ProductMapper productMapper;
    private final SysUserMapper sysUserMapper;
    private final ChatMessageMapper chatMessageMapper;

    public ReportServiceImpl(ReportMapper reportMapper,
                             ProductMapper productMapper,
                             SysUserMapper sysUserMapper,
                             ChatMessageMapper chatMessageMapper) {
        this.reportMapper = reportMapper;
        this.productMapper = productMapper;
        this.sysUserMapper = sysUserMapper;
        this.chatMessageMapper = chatMessageMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReportResponse create(ReportCreateRequest request) {
        Long reporterId = requireLoginUserId();
        String targetType = request.getTargetType().trim();
        validateTarget(targetType, request.getTargetId(), reporterId);

        Report report = new Report();
        report.setReporterId(reporterId);
        report.setTargetType(targetType);
        report.setTargetId(request.getTargetId());
        report.setReason(request.getReason().trim());
        report.setDetail(trimToNull(request.getDetail()));
        report.setEvidenceUrls(trimToNull(request.getEvidenceUrls()));
        report.setStatus(STATUS_PENDING);
        report.setCreateTime(LocalDateTime.now());
        report.setIsDeleted(0);
        reportMapper.insert(report);
        return buildResponse(report);
    }

    @Override
    public Page<ReportResponse> listMine(ReportListRequest request) {
        Long userId = requireLoginUserId();
        return page(request, Wrappers.<Report>lambdaQuery()
                .eq(Report::getReporterId, userId));
    }

    @Override
    public Page<ReportResponse> listForAdmin(ReportListRequest request) {
        requireAdmin();
        return page(request, Wrappers.lambdaQuery(Report.class));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReportResponse handle(Long reportId, ReportHandleRequest request) {
        Long handlerId = requireAdmin();
        Report report = reportMapper.selectById(reportId);
        if (report == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "举报记录不存在");
        }
        if (!Integer.valueOf(STATUS_PENDING).equals(report.getStatus())) {
            throw new BizException(ErrorCode.BUSINESS_ERROR, "该举报已处理");
        }
        report.setStatus(request.getStatus());
        report.setHandlerId(handlerId);
        report.setHandleResult(request.getHandleResult().trim());
        report.setHandleTime(LocalDateTime.now());
        reportMapper.updateById(report);
        return buildResponse(report);
    }

    private Page<ReportResponse> page(ReportListRequest request, LambdaQueryWrapper<Report> query) {
        validateQuery(request);
        int pageNo = normalizePageNo(request.getPageNo());
        int pageSize = normalizePageSize(request.getPageSize());
        query.eq(request.getStatus() != null, Report::getStatus, request.getStatus())
                .eq(StringUtils.hasText(request.getTargetType()), Report::getTargetType, request.getTargetType())
                .orderByDesc(Report::getCreateTime)
                .orderByDesc(Report::getId);
        Page<Report> reportPage = reportMapper.selectPage(Page.of(pageNo, pageSize), query);
        Page<ReportResponse> responsePage = Page.of(pageNo, pageSize, reportPage.getTotal());
        responsePage.setRecords(reportPage.getRecords().stream().map(this::buildResponse).toList());
        return responsePage;
    }

    private void validateTarget(String targetType, Long targetId, Long reporterId) {
        if (!TARGET_TYPES.contains(targetType)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "举报对象类型只能为product、user、message");
        }
        if ("product".equals(targetType)) {
            Product product = productMapper.selectById(targetId);
            if (product == null) {
                throw new BizException(ErrorCode.NOT_FOUND, "商品不存在");
            }
            if (reporterId.equals(product.getSellerId())) {
                throw new BizException(ErrorCode.BUSINESS_ERROR, "不能举报自己发布的商品");
            }
            return;
        }
        if ("user".equals(targetType)) {
            if (reporterId.equals(targetId)) {
                throw new BizException(ErrorCode.BUSINESS_ERROR, "不能举报自己");
            }
            if (sysUserMapper.selectById(targetId) == null) {
                throw new BizException(ErrorCode.NOT_FOUND, "用户不存在");
            }
            return;
        }
        ChatMessage message = chatMessageMapper.selectById(targetId);
        if (message == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "消息不存在");
        }
        if (!reporterId.equals(message.getReceiverId())) {
            throw new BizException(ErrorCode.FORBIDDEN, "只能举报自己收到的消息");
        }
    }

    private void validateQuery(ReportListRequest request) {
        if (request.getStatus() != null && (request.getStatus() < 0 || request.getStatus() > 3)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "举报状态只能为0、1、2、3");
        }
        if (StringUtils.hasText(request.getTargetType()) && !TARGET_TYPES.contains(request.getTargetType())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "举报对象类型只能为product、user、message");
        }
    }

    private ReportResponse buildResponse(Report report) {
        ReportResponse response = ReportResponse.from(report);
        SysUser reporter = sysUserMapper.selectById(report.getReporterId());
        if (reporter != null) {
            response.setReporterNickname(reporter.getNickname());
        }
        if (report.getHandlerId() != null) {
            SysUser handler = sysUserMapper.selectById(report.getHandlerId());
            if (handler != null) {
                response.setHandlerNickname(handler.getNickname());
            }
        }
        return response;
    }

    private Long requireLoginUserId() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        return userId;
    }

    private Long requireAdmin() {
        Long userId = requireLoginUserId();
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null || !Integer.valueOf(ADMIN_ROLE).equals(user.getRole())) {
            throw new BizException(ErrorCode.FORBIDDEN, "仅管理员可以处理举报");
        }
        return userId;
    }

    private int normalizePageNo(Integer pageNo) {
        return pageNo == null || pageNo < 1 ? 1 : pageNo;
    }

    private int normalizePageSize(Integer pageSize) {
        if (pageSize == null || pageSize < 1) {
            return 10;
        }
        return Math.min(pageSize, 100);
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}

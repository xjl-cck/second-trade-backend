package org.example.secondhandtradingplatform.report.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.example.secondhandtradingplatform.common.Result;
import org.example.secondhandtradingplatform.report.dto.ReportCreateRequest;
import org.example.secondhandtradingplatform.report.dto.ReportHandleRequest;
import org.example.secondhandtradingplatform.report.dto.ReportListRequest;
import org.example.secondhandtradingplatform.report.dto.ReportResponse;
import org.example.secondhandtradingplatform.report.service.ReportService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reports")
@Tag(name = "举报模块", description = "商品、用户、消息举报及后台处理")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @PostMapping
    @Operation(summary = "提交举报", description = "支持举报商品、用户和自己收到的私聊消息")
    public Result<ReportResponse> create(@Valid @RequestBody ReportCreateRequest request) {
        return Result.success(reportService.create(request));
    }

    @GetMapping("/mine")
    @Operation(summary = "我的举报列表")
    public Result<Page<ReportResponse>> listMine(@Valid ReportListRequest request) {
        return Result.success(reportService.listMine(request));
    }

    @GetMapping("/admin")
    @Operation(summary = "后台举报列表", description = "仅管理员可按状态和举报对象类型筛选")
    public Result<Page<ReportResponse>> listForAdmin(@Valid ReportListRequest request) {
        return Result.success(reportService.listForAdmin(request));
    }

    @PutMapping("/admin/{reportId}")
    @Operation(summary = "后台处理举报", description = "仅管理员可将待处理举报更新为已受理、已驳回或已关闭")
    public Result<ReportResponse> handle(@PathVariable Long reportId,
                                         @Valid @RequestBody ReportHandleRequest request) {
        return Result.success(reportService.handle(reportId, request));
    }
}

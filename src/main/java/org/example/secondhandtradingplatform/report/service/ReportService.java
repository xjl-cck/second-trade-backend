package org.example.secondhandtradingplatform.report.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.example.secondhandtradingplatform.report.dto.ReportCreateRequest;
import org.example.secondhandtradingplatform.report.dto.ReportHandleRequest;
import org.example.secondhandtradingplatform.report.dto.ReportListRequest;
import org.example.secondhandtradingplatform.report.dto.ReportResponse;

public interface ReportService {

    ReportResponse create(ReportCreateRequest request);

    Page<ReportResponse> listMine(ReportListRequest request);

    Page<ReportResponse> listForAdmin(ReportListRequest request);

    ReportResponse handle(Long reportId, ReportHandleRequest request);
}

package org.example.secondhandtradingplatform.report.dto;

import lombok.Data;
import org.example.secondhandtradingplatform.report.entity.Report;

import java.time.LocalDateTime;

@Data
public class ReportResponse {

    private Long id;
    private Long reporterId;
    private String reporterNickname;
    private String targetType;
    private Long targetId;
    private String reason;
    private String detail;
    private String evidenceUrls;
    private Integer status;
    private Long handlerId;
    private String handlerNickname;
    private String handleResult;
    private LocalDateTime handleTime;
    private LocalDateTime createTime;

    public static ReportResponse from(Report report) {
        ReportResponse response = new ReportResponse();
        response.setId(report.getId());
        response.setReporterId(report.getReporterId());
        response.setTargetType(report.getTargetType());
        response.setTargetId(report.getTargetId());
        response.setReason(report.getReason());
        response.setDetail(report.getDetail());
        response.setEvidenceUrls(report.getEvidenceUrls());
        response.setStatus(report.getStatus());
        response.setHandlerId(report.getHandlerId());
        response.setHandleResult(report.getHandleResult());
        response.setHandleTime(report.getHandleTime());
        response.setCreateTime(report.getCreateTime());
        return response;
    }
}

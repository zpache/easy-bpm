package com.easybpm.console;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.easybpm.auth.CurrentUser;
import com.easybpm.common.ApiResponse;
import com.easybpm.common.BizException;
import com.easybpm.common.ErrorCode;
import com.easybpm.engine.BpmInstance;
import com.easybpm.engine.BpmInstanceMapper;
import com.easybpm.engine.InstanceStatus;
import com.easybpm.engine.InstanceView;
import com.easybpm.engine.WorkbenchCounts;
import com.easybpm.engine.WorkbenchQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private static final int TREND_DAYS = 14;

    private final WorkbenchQueryService query;
    private final BpmInstanceMapper instanceMapper;

    public DashboardController(WorkbenchQueryService query, BpmInstanceMapper instanceMapper) {
        this.query = query;
        this.instanceMapper = instanceMapper;
    }

    @GetMapping("/summary")
    public ApiResponse<DashboardSummary> summary() {
        WorkbenchCounts counts = query.counts(CurrentUser.requireApproverId());
        Map<String, Long> statusStats = new LinkedHashMap<>();
        long total = 0;
        for (InstanceStatus status : InstanceStatus.values()) {
            long count = instanceMapper.selectCount(new LambdaQueryWrapper<BpmInstance>()
                    .eq(BpmInstance::getStatus, status.name()));
            statusStats.put(status.name(), count);
            total += count;
        }
        List<InstanceView> recent = query.recent(8);
        return ApiResponse.ok(new DashboardSummary(counts, statusStats, total, recent));
    }

    /**
     * 管理员数据看板：通过率、平均审批时长、近 14 天发起/办结趋势、流程 TOP5。
     */
    @GetMapping("/stats")
    public ApiResponse<DashboardStats> stats() {
        if (!CurrentUser.require().isTenantAdmin()) {
            throw new BizException(ErrorCode.FORBIDDEN, "需要管理员权限");
        }

        long approved = instanceMapper.selectCount(new LambdaQueryWrapper<BpmInstance>()
                .eq(BpmInstance::getStatus, "APPROVED"));
        long rejected = instanceMapper.selectCount(new LambdaQueryWrapper<BpmInstance>()
                .eq(BpmInstance::getStatus, "REJECTED"));
        long decided = approved + rejected;
        double approvalRate = decided == 0 ? 0 : Math.round(approved * 1000.0 / decided) / 10.0;

        Double avgSeconds = selectAvgDurationSeconds();
        double avgHours = avgSeconds == null ? 0 : Math.round(avgSeconds / 360.0) / 10.0;

        LocalDate today = LocalDate.now();
        List<TrendPoint> trend = new ArrayList<>(TREND_DAYS);
        for (int i = TREND_DAYS - 1; i >= 0; i--) {
            LocalDate day = today.minusDays(i);
            LocalDateTime start = day.atStartOfDay();
            LocalDateTime end = day.plusDays(1).atStartOfDay();
            long started = instanceMapper.selectCount(new LambdaQueryWrapper<BpmInstance>()
                    .ge(BpmInstance::getStartedAt, start).lt(BpmInstance::getStartedAt, end));
            long finished = instanceMapper.selectCount(new LambdaQueryWrapper<BpmInstance>()
                    .ge(BpmInstance::getEndedAt, start).lt(BpmInstance::getEndedAt, end));
            trend.add(new TrendPoint(day.toString(), started, finished));
        }

        List<FlowStat> topFlows = selectTopFlows();
        return ApiResponse.ok(new DashboardStats(approvalRate, avgHours, trend, topFlows));
    }

    private Double selectAvgDurationSeconds() {
        QueryWrapper<BpmInstance> wrapper = new QueryWrapper<>();
        wrapper.select("AVG(TIMESTAMPDIFF(SECOND, started_at, ended_at)) AS avg_secs")
                .isNotNull("ended_at");
        List<Map<String, Object>> rows = instanceMapper.selectMaps(wrapper);
        if (rows.isEmpty() || rows.get(0) == null) {
            return null;
        }
        Object value = rows.get(0).get("avg_secs");
        if (value == null) {
            return null;
        }
        return value instanceof Number number ? number.doubleValue() : Double.parseDouble(value.toString());
    }

    private List<FlowStat> selectTopFlows() {
        QueryWrapper<BpmInstance> wrapper = new QueryWrapper<>();
        wrapper.select("flow_code", "flow_name", "COUNT(*) AS cnt")
                .groupBy("flow_code", "flow_name")
                .orderByDesc("cnt")
                .last("LIMIT 5");
        return instanceMapper.selectMaps(wrapper).stream()
                .map(row -> new FlowStat(
                        String.valueOf(row.get("flow_code")),
                        String.valueOf(row.get("flow_name")),
                        ((Number) row.get("cnt")).longValue()))
                .toList();
    }

    public record DashboardSummary(WorkbenchCounts counts, Map<String, Long> statusStats, long totalInstances,
                                   List<InstanceView> recent) {
    }

    public record DashboardStats(double approvalRate, double avgApprovalHours,
                                 List<TrendPoint> trend, List<FlowStat> topFlows) {
    }

    public record TrendPoint(String day, long started, long finished) {
    }

    public record FlowStat(String flowCode, String flowName, long count) {
    }
}


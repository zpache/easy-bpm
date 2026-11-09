package com.easybpm.console;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.easybpm.auth.CurrentUser;
import com.easybpm.common.ApiResponse;
import com.easybpm.engine.BpmInstance;
import com.easybpm.engine.BpmInstanceMapper;
import com.easybpm.engine.InstanceStatus;
import com.easybpm.engine.InstanceView;
import com.easybpm.engine.WorkbenchCounts;
import com.easybpm.engine.WorkbenchQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

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

    public record DashboardSummary(WorkbenchCounts counts, Map<String, Long> statusStats, long totalInstances,
                                   List<InstanceView> recent) {
    }
}

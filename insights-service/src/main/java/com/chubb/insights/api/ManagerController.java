package com.chubb.insights.api;

import com.chubb.insights.api.dto.ManagerDtos.Dashboard;
import com.chubb.insights.service.ManagerDashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/manager")
@RequiredArgsConstructor
public class ManagerController {

    private final ManagerDashboardService dashboardService;

    @GetMapping("/dashboard")
    public Dashboard dashboard(@RequestParam(required = false) String market) {
        return dashboardService.dashboard(market);
    }
}

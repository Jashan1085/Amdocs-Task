package com.amdocs.telecom.controller;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseStatus;

@Controller
public class DashboardController {
    private final com.amdocs.telecom.service.DashboardService service;
    public DashboardController(com.amdocs.telecom.service.DashboardService service) { this.service=service; }
    @GetMapping("/")
    public String home(Authentication authentication) { return authentication == null ? "redirect:/login" : "redirect:/dashboard"; }

    @GetMapping("/dashboard")
    public String dashboard(Authentication authentication) {
        if (has(authentication, "NETWORK_MANAGER")) { return "redirect:/manager/dashboard"; }
        if (has(authentication, "SERVICE_DESK")) { return "redirect:/service-desk/dashboard"; }
        if (has(authentication, "NETWORK_ENGINEER")) { return "redirect:/engineer/dashboard"; }
        return "redirect:/customer/dashboard";
    }
    @GetMapping("/customer/dashboard")
    public String customer(@org.springframework.security.core.annotation.AuthenticationPrincipal com.amdocs.telecom.security.TelecomPrincipal actor,Model model) { model.addAllAttributes(service.view(actor,com.amdocs.telecom.security.Role.CUSTOMER)); model.addAttribute("workflowBase","/customer"); return page(model, "Customer workspace", "Services and support", "View your services, report an issue and track your trouble tickets."); }
    @GetMapping("/service-desk/dashboard")
    public String desk(@org.springframework.security.core.annotation.AuthenticationPrincipal com.amdocs.telecom.security.TelecomPrincipal actor,Model model) { model.addAllAttributes(service.view(actor,com.amdocs.telecom.security.Role.SERVICE_DESK)); model.addAttribute("workflowBase","/service-desk"); return page(model, "Service desk", "Customer support operations", "Find a customer's services, raise a ticket on their behalf and review tickets."); }
    @GetMapping("/engineer/dashboard")
    public String engineer(@org.springframework.security.core.annotation.AuthenticationPrincipal com.amdocs.telecom.security.TelecomPrincipal actor,Model model) { model.addAllAttributes(service.view(actor,com.amdocs.telecom.security.Role.NETWORK_ENGINEER)); model.addAttribute("workflowBase","/engineer"); model.addAttribute("engineerWorkspace",true); return page(model, "Network engineer", "Your assigned work", "View assigned tickets, acknowledge incidents, request customer information and record resolutions."); }
    @GetMapping("/manager/dashboard")
    public String manager(@org.springframework.security.core.annotation.AuthenticationPrincipal com.amdocs.telecom.security.TelecomPrincipal actor,Model model) { model.addAllAttributes(service.view(actor,com.amdocs.telecom.security.Role.NETWORK_MANAGER)); model.addAttribute("workflowBase","/manager"); model.addAttribute("managerSla",true); return page(model, "Network manager", "Service assurance overview", "Review response and resolution SLA status, active counters and escalation levels."); }
    @GetMapping("/access-denied") @ResponseStatus(HttpStatus.FORBIDDEN)
    public String denied() { return "error/403"; }
    private String page(Model model, String title, String subtitle, String description) {
        model.addAttribute("title", title); model.addAttribute("subtitle", subtitle); model.addAttribute("description", description);
        return "dashboard";
    }
    private boolean has(Authentication authentication, String role) {
        return authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_" + role));
    }
}

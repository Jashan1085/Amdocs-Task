package com.amdocs.telecom.controller;
import com.amdocs.telecom.security.*;
import com.amdocs.telecom.service.NotificationService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import javax.servlet.http.HttpServletRequest;

@Controller
public class NotificationController {
    private final NotificationService service;
    public NotificationController(NotificationService service) { this.service=service; }
    @GetMapping({"/customer/notifications","/engineer/notifications","/service-desk/notifications","/manager/notifications"})
    public String inbox(@AuthenticationPrincipal TelecomPrincipal actor,HttpServletRequest request,Model model,
            @RequestParam(defaultValue="false") boolean unread,@RequestParam(defaultValue="0") int page) {
        Role role=role(request); model.addAttribute("base",base(request)); model.addAttribute("deskView",role==Role.SERVICE_DESK);
        model.addAttribute("unread",unread); model.addAttribute("results",service.inbox(actor,role,unread,page)); return "notifications/inbox";
    }
    @PostMapping({"/customer/notifications/{id}/read","/engineer/notifications/{id}/read","/manager/notifications/{id}/read"})
    public String read(@AuthenticationPrincipal TelecomPrincipal actor,HttpServletRequest request,@PathVariable long id) {
        service.read(actor,role(request),id); return "redirect:"+base(request)+"/notifications";
    }
    private String base(HttpServletRequest request) {
        String path=request.getRequestURI().substring(request.getContextPath().length()); return path.substring(0,path.indexOf('/',1));
    }
    private Role role(HttpServletRequest request) {
        switch(base(request)) {
            case "/customer": return Role.CUSTOMER;
            case "/engineer": return Role.NETWORK_ENGINEER;
            case "/manager": return Role.NETWORK_MANAGER;
            default: return Role.SERVICE_DESK;
        }
    }
}

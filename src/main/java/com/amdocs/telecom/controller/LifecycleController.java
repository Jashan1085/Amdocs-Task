package com.amdocs.telecom.controller;

import com.amdocs.telecom.dto.*;
import com.amdocs.telecom.model.*;
import com.amdocs.telecom.security.*;
import com.amdocs.telecom.service.LifecycleService;
import com.amdocs.telecom.exception.WorkflowException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;

@Controller
public class LifecycleController {
    private final LifecycleService lifecycle;
    public LifecycleController(LifecycleService lifecycle) { this.lifecycle=lifecycle; }
    private Role role(HttpServletRequest request) {
        String path=request.getRequestURI().substring(request.getContextPath().length());
        return path.startsWith("/engineer/") ? Role.NETWORK_ENGINEER : (path.startsWith("/service-desk/") ? Role.SERVICE_DESK : Role.CUSTOMER);
    }
    private String base(Role role) { return role==Role.NETWORK_ENGINEER ? "/engineer" : (role==Role.SERVICE_DESK ? "/service-desk" : "/customer"); }
    private void formModel(Model model,TroubleTicket ticket,Role role) {
        model.addAttribute("ticket",ticket); model.addAttribute("base",base(role)); model.addAttribute("resolutionCodes",ResolutionCode.values());
    }
    @GetMapping({"/customer/tickets/{id}/actions","/service-desk/tickets/{id}/actions","/engineer/tickets/{id}/actions"})
    public String form(@AuthenticationPrincipal TelecomPrincipal actor,HttpServletRequest request,Model model,@PathVariable long id,@RequestParam TicketAction action) {
        Role role=role(request); TroubleTicket ticket=lifecycle.read(actor,role,id); lifecycle.requireAction(role,ticket,action);
        LifecycleForm form=new LifecycleForm(); form.setAction(action); form.setRowVersion(ticket.getRowVersion());
        model.addAttribute("form",form); formModel(model,ticket,role); return "tickets/action";
    }
    @PostMapping({"/customer/tickets/{id}/actions","/service-desk/tickets/{id}/actions","/engineer/tickets/{id}/actions"})
    public String submit(@AuthenticationPrincipal TelecomPrincipal actor,HttpServletRequest request,Model model,@PathVariable long id,
            @Valid @ModelAttribute("form") LifecycleForm form,BindingResult errors,RedirectAttributes redirect) {
        Role role=role(request);
        if(errors.hasErrors()) {
            TroubleTicket ticket=lifecycle.read(actor,role,id); lifecycle.requireAction(role,ticket,form.getAction());
            formModel(model,ticket,role); return "tickets/action";
        }
        lifecycle.act(actor,role,id,form); redirect.addFlashAttribute("notice","Ticket updated successfully.");
        return "redirect:"+base(role)+"/tickets/"+id;
    }
    @GetMapping("/customer/tickets/{id}/feedback")
    public String feedback(@AuthenticationPrincipal TelecomPrincipal actor,Model model,@PathVariable long id) {
        TroubleTicket ticket=lifecycle.read(actor,Role.CUSTOMER,id); lifecycle.requireFeedback(ticket);
        FeedbackForm form=new FeedbackForm(); form.setRowVersion(ticket.getRowVersion());
        model.addAttribute("form",form); model.addAttribute("ticket",ticket); return "tickets/feedback";
    }
    @PostMapping("/customer/tickets/{id}/feedback")
    public String feedback(@AuthenticationPrincipal TelecomPrincipal actor,Model model,@PathVariable long id,
            @Valid @ModelAttribute("form") FeedbackForm form,BindingResult errors,RedirectAttributes redirect) {
        if(errors.hasErrors()) {
            TroubleTicket ticket=lifecycle.read(actor,Role.CUSTOMER,id); lifecycle.requireFeedback(ticket);
            model.addAttribute("ticket",ticket); return "tickets/feedback";
        }
        lifecycle.feedback(actor,id,form); redirect.addFlashAttribute("notice","Thank you. Your feedback has been recorded.");
        return "redirect:/customer/tickets/"+id;
    }
}

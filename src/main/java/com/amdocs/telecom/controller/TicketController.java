package com.amdocs.telecom.controller;

import com.amdocs.telecom.dto.*;
import com.amdocs.telecom.model.*;
import com.amdocs.telecom.security.TelecomPrincipal;
import com.amdocs.telecom.service.TicketWorkflowService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;

@Controller
public class TicketController {
    private final TicketWorkflowService workflow;
    private final com.amdocs.telecom.dao.NetworkEngineerDAO engineers;
    private final com.amdocs.telecom.service.LifecycleService lifecycle;
    private final com.amdocs.telecom.dao.impl.JdbcLifecycleDAO records;
    private final com.amdocs.telecom.dao.TelecomServiceDAO services;
    private final com.amdocs.telecom.dao.TicketStatusHistoryDAO histories;
    private final com.amdocs.telecom.service.SLAService slas;
    private final com.amdocs.telecom.dao.impl.JdbcSlaDAO escalations;
    public TicketController(TicketWorkflowService workflow,com.amdocs.telecom.dao.NetworkEngineerDAO engineers,
            com.amdocs.telecom.service.LifecycleService lifecycle,com.amdocs.telecom.dao.impl.JdbcLifecycleDAO records,
            com.amdocs.telecom.dao.TelecomServiceDAO services,com.amdocs.telecom.dao.TicketStatusHistoryDAO histories,
            com.amdocs.telecom.service.SLAService slas,com.amdocs.telecom.dao.impl.JdbcSlaDAO escalations) {
        this.workflow=workflow; this.engineers=engineers; this.lifecycle=lifecycle; this.records=records;
        this.services=services; this.histories=histories;
        this.slas=slas; this.escalations=escalations;
    }
    private boolean desk(HttpServletRequest request) {
        return request.getRequestURI().substring(request.getContextPath().length()).startsWith("/service-desk/");
    }
    private boolean engineer(HttpServletRequest request) { return request.getRequestURI().substring(request.getContextPath().length()).startsWith("/engineer/"); }
    private String base(HttpServletRequest request) { return engineer(request) ? "/engineer" : (desk(request) ? "/service-desk" : "/customer"); }
    @ModelAttribute
    public void common(HttpServletRequest request, Model model) {
        model.addAttribute("base",base(request)); model.addAttribute("desk",desk(request));
        model.addAttribute("engineer",engineer(request));
        model.addAttribute("priorities",TicketPriority.values()); model.addAttribute("statuses",TicketStatus.values());
        model.addAttribute("categories",IncidentCategory.values()); model.addAttribute("severities",Severity.values());
        model.addAttribute("listPath",base(request)+"/tickets");
    }
    @GetMapping({"/customer/services","/service-desk/services"})
    public String services(@AuthenticationPrincipal TelecomPrincipal actor, HttpServletRequest request, Model model,
            @RequestParam(required=false) String customerNumber, @RequestParam(defaultValue="0") int page) {
        if (!desk(request) || (customerNumber!=null && !customerNumber.trim().isEmpty())) {
            model.addAttribute("customer",workflow.customer(actor,desk(request),customerNumber));
            model.addAttribute("results",workflow.services(actor,desk(request),customerNumber,page));
        }
        model.addAttribute("customerNumber",customerNumber);
        return "tickets/services";
    }
    @GetMapping({"/customer/tickets","/service-desk/tickets"})
    public String tickets(@AuthenticationPrincipal TelecomPrincipal actor, HttpServletRequest request, Model model,
            @RequestParam(required=false) TicketStatus status, @RequestParam(required=false) TicketPriority priority,
            @RequestParam(defaultValue="0") int page) {
        model.addAttribute("results",workflow.tickets(actor,desk(request),status,priority,page));
        model.addAttribute("selectedStatus",status); model.addAttribute("selectedPriority",priority);
        return "tickets/list";
    }
    @GetMapping("/service-desk/queue")
    public String queue(@AuthenticationPrincipal TelecomPrincipal actor,HttpServletRequest request,Model model,
            @RequestParam(required=false) TicketStatus status,@RequestParam(required=false) TicketPriority priority,
            @RequestParam(defaultValue="0") int page) {
        if(request.getParameter("status")==null) { status=TicketStatus.OPEN; }
        model.addAttribute("queue",true); model.addAttribute("listPath","/service-desk/queue");
        return tickets(actor,request,model,status,priority,page);
    }
    @GetMapping("/engineer/tickets")
    public String engineerTickets(@AuthenticationPrincipal TelecomPrincipal actor,Model model,
            @RequestParam(required=false) TicketStatus status,@RequestParam(required=false) TicketPriority priority,
            @RequestParam(defaultValue="0") int page) {
        model.addAttribute("results",lifecycle.engineerTickets(actor,status,priority,page));
        model.addAttribute("selectedStatus",status); model.addAttribute("selectedPriority",priority);
        return "tickets/list";
    }
    @GetMapping({"/customer/tickets/new","/service-desk/tickets/new"})
    public String newTicket(@AuthenticationPrincipal TelecomPrincipal actor, HttpServletRequest request, Model model,
            @RequestParam long serviceId) {
        TicketCreateForm form=new TicketCreateForm(); form.setServiceId(serviceId);
        model.addAttribute("form",form);
        model.addAttribute("service",workflow.service(actor,desk(request),serviceId));
        return "tickets/new";
    }
    @PostMapping({"/customer/tickets","/service-desk/tickets"})
    public String create(@AuthenticationPrincipal TelecomPrincipal actor, HttpServletRequest request, Model model,
            @Valid @ModelAttribute("form") TicketCreateForm form, BindingResult errors, RedirectAttributes redirect) {
        if (errors.hasErrors() && form.getServiceId()!=null) {
            model.addAttribute("service",workflow.service(actor,desk(request),form.getServiceId()));
            return "tickets/new";
        }
        long id=workflow.create(actor,desk(request),form);
        redirect.addFlashAttribute("notice","Ticket created successfully.");
        return "redirect:"+base(request)+"/tickets/"+id;
    }
    @GetMapping({"/customer/tickets/{id}","/service-desk/tickets/{id}","/engineer/tickets/{id}"})
    public String detail(@AuthenticationPrincipal TelecomPrincipal actor, HttpServletRequest request, Model model,
            @PathVariable long id, @RequestParam(defaultValue="0") int page) {
        com.amdocs.telecom.security.Role role=engineer(request) ? com.amdocs.telecom.security.Role.NETWORK_ENGINEER
                : (desk(request) ? com.amdocs.telecom.security.Role.SERVICE_DESK : com.amdocs.telecom.security.Role.CUSTOMER);
        TroubleTicket ticket=lifecycle.read(actor,role,id);
        model.addAttribute("ticket",ticket);
        model.addAttribute("assignable",com.amdocs.telecom.service.AssignmentService.isAssignable(ticket.getStatus()));
        model.addAttribute("assignedEngineer",ticket.getAssignedEngineerId()==null ? null : engineers.findById(ticket.getAssignedEngineerId()).orElse(null));
        model.addAttribute("service",services.findById(ticket.getServiceId()).get());
        if(page<0 || page>1000000) { throw new com.amdocs.telecom.exception.WorkflowException(org.springframework.http.HttpStatus.BAD_REQUEST,"Invalid page number."); }
        model.addAttribute("results",new ResultPage<>(histories.findByTicketId(id,new PageRequest(21,page*20)),page));
        model.addAttribute("actions",com.amdocs.telecom.service.TicketTransitions.actions(role,ticket.getStatus()));
        model.addAttribute("resolution",records.resolution(id)); model.addAttribute("feedback",records.feedback(id));
        model.addAttribute("cancellation",records.cancellation(id));
        model.addAttribute("customerScope",role==com.amdocs.telecom.security.Role.CUSTOMER);
        model.addAttribute("sla",slas.evaluate(ticket)); model.addAttribute("escalation",escalations.state(id,false));
        model.addAttribute("escalationHistory",escalations.history(id));
        model.addAttribute("priorityEditable",com.amdocs.telecom.service.SlaOperationsService.editable(ticket));
        return "tickets/detail";
    }
}

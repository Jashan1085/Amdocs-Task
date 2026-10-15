package com.amdocs.telecom.controller;
import com.amdocs.telecom.dto.*;
import com.amdocs.telecom.model.*;
import com.amdocs.telecom.security.*;
import com.amdocs.telecom.service.*;
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
public class SlaController {
    private final SlaOperationsService operations;
    private final LifecycleService lifecycle;
    public SlaController(SlaOperationsService operations,LifecycleService lifecycle) { this.operations=operations; this.lifecycle=lifecycle; }
    @GetMapping({"/service-desk/sla","/manager/sla"})
    public String view(@AuthenticationPrincipal TelecomPrincipal actor,HttpServletRequest request,Model model,
            @RequestParam(required=false) TicketStatus status,@RequestParam(required=false) TicketPriority priority,@RequestParam(defaultValue="0") int page) {
        boolean manager=request.getRequestURI().substring(request.getContextPath().length()).startsWith("/manager/");
        Role role=manager ? Role.NETWORK_MANAGER : Role.SERVICE_DESK;
        model.addAttribute("base",manager ? "/manager" : "/service-desk"); model.addAttribute("managerView",manager);
        model.addAttribute("results",operations.list(actor,role,status,priority,page)); model.addAttribute("counters",operations.counters(actor,role));
        model.addAttribute("statuses",TicketStatus.values()); model.addAttribute("priorities",TicketPriority.values());
        model.addAttribute("selectedStatus",status); model.addAttribute("selectedPriority",priority); return "tickets/sla";
    }
    @GetMapping({"/service-desk/tickets/{id}/priority","/service-desk/tickets/{id}/resume-escalation"})
    public String edit(@AuthenticationPrincipal TelecomPrincipal actor,HttpServletRequest request,Model model,@PathVariable long id) {
        TroubleTicket ticket=lifecycle.read(actor,Role.SERVICE_DESK,id);
        boolean resuming=request.getRequestURI().endsWith("/resume-escalation");
        if(!SlaOperationsService.editable(ticket) || (resuming && ticket.getStatus()!=TicketStatus.ESCALATED)) {
            throw new WorkflowException(HttpStatus.CONFLICT,"This operation is unavailable for the ticket status.");
        }
        SlaChangeForm form=new SlaChangeForm(); form.setRowVersion(ticket.getRowVersion()); form.setPriority(ticket.getPriority());
        model.addAttribute("form",form); model(model,ticket,resuming); return "tickets/sla-edit";
    }
    @PostMapping({"/service-desk/tickets/{id}/priority","/service-desk/tickets/{id}/resume-escalation"})
    public String save(@AuthenticationPrincipal TelecomPrincipal actor,HttpServletRequest request,Model model,@PathVariable long id,
            @Valid @ModelAttribute("form") SlaChangeForm form,BindingResult errors,RedirectAttributes redirect) {
        boolean resuming=request.getRequestURI().endsWith("/resume-escalation");
        if(errors.hasErrors()) { model(model,lifecycle.read(actor,Role.SERVICE_DESK,id),resuming); return "tickets/sla-edit"; }
        if(resuming) { operations.resume(actor,id,form); } else { operations.priority(actor,id,form); }
        redirect.addFlashAttribute("notice",resuming ? "Ticket resumed to its recorded operational status." : "Priority and original-time SLA deadlines updated.");
        return "redirect:/service-desk/tickets/"+id;
    }
    private void model(Model model,TroubleTicket ticket,boolean resuming) {
        model.addAttribute("ticket",ticket); model.addAttribute("resuming",resuming); model.addAttribute("priorities",TicketPriority.values());
    }
}

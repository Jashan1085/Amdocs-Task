package com.amdocs.telecom.controller;

import com.amdocs.telecom.dto.AssignmentForm;
import com.amdocs.telecom.security.TelecomPrincipal;
import com.amdocs.telecom.service.AssignmentService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import javax.validation.Valid;

@Controller
@RequestMapping("/service-desk/tickets/{id}/assignment")
public class AssignmentController {
    private final AssignmentService assignments;
    public AssignmentController(AssignmentService assignments) { this.assignments=assignments; }
    @GetMapping
    public String form(@AuthenticationPrincipal TelecomPrincipal actor,@PathVariable long id,Model model) {
        AssignmentService.Options options=assignments.options(actor,id);
        AssignmentForm form=new AssignmentForm(); form.setRowVersion(options.getTicket().getRowVersion());
        model.addAttribute("form",form); model.addAttribute("options",options);
        return "tickets/assignment";
    }
    @PostMapping
    public String submit(@AuthenticationPrincipal TelecomPrincipal actor,@PathVariable long id,
            @Valid @ModelAttribute("form") AssignmentForm form,BindingResult errors,Model model,RedirectAttributes redirect) {
        if(errors.hasErrors()) {
            model.addAttribute("options",assignments.options(actor,id));
            return "tickets/assignment";
        }
        assignments.assign(actor,id,form);
        redirect.addFlashAttribute("notice","Engineer assignment saved.");
        return "redirect:/service-desk/tickets/"+id;
    }
}

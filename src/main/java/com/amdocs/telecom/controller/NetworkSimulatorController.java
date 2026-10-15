package com.amdocs.telecom.controller;
import com.amdocs.telecom.dto.NetworkEventForm;
import com.amdocs.telecom.security.TelecomPrincipal;
import com.amdocs.telecom.service.*;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import javax.validation.Valid;
import java.util.UUID;

@Controller @Profile("dev")
public class NetworkSimulatorController {
    private final NetworkEventService events;
    private final EventDispatcher dispatcher;
    public NetworkSimulatorController(NetworkEventService events,EventDispatcher dispatcher) { this.events=events; this.dispatcher=dispatcher; }
    @GetMapping("/service-desk/network/simulate")
    public String form(@AuthenticationPrincipal TelecomPrincipal actor,Model model) {
        events.nodes(actor,0); NetworkEventForm form=new NetworkEventForm(); form.setEventKey(UUID.randomUUID());
        model.addAttribute("form",form); model.addAttribute("types",NetworkEventForm.Type.values()); return "network/simulate";
    }
    @PostMapping("/service-desk/network/simulate")
    public String submit(@AuthenticationPrincipal TelecomPrincipal actor,Model model,@Valid @ModelAttribute("form") NetworkEventForm form,BindingResult errors) {
        if(errors.hasErrors()) { events.nodes(actor,0); model.addAttribute("types",NetworkEventForm.Type.values()); return "network/simulate"; }
        long id=events.simulate(actor,form); dispatcher.enqueue(id); return "redirect:/service-desk/network/events/"+id;
    }
}

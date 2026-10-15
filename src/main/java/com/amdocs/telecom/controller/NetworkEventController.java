package com.amdocs.telecom.controller;
import com.amdocs.telecom.dto.*;
import com.amdocs.telecom.security.TelecomPrincipal;
import com.amdocs.telecom.service.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import javax.validation.Valid;

@Controller
public class NetworkEventController {
    private final NetworkEventService events;
    private final EventDispatcher dispatcher;
    public NetworkEventController(NetworkEventService events,EventDispatcher dispatcher) { this.events=events; this.dispatcher=dispatcher; }
    @GetMapping("/service-desk/network/nodes")
    public String nodes(@AuthenticationPrincipal TelecomPrincipal actor,Model model,@RequestParam(defaultValue="0") int page) {
        model.addAttribute("results",events.nodes(actor,page)); model.addAttribute("form",new NetworkNodeForm()); return "network/nodes";
    }
    @PostMapping("/service-desk/network/nodes")
    public String create(@AuthenticationPrincipal TelecomPrincipal actor,Model model,@Valid @ModelAttribute("form") NetworkNodeForm form,BindingResult errors) {
        if(errors.hasErrors()) { model.addAttribute("results",events.nodes(actor,0)); return "network/nodes"; }
        return "redirect:/service-desk/network/nodes/"+events.node(actor,form);
    }
    @GetMapping("/service-desk/network/nodes/{id}")
    public String mappings(@AuthenticationPrincipal TelecomPrincipal actor,Model model,@PathVariable long id,@RequestParam(defaultValue="0") int page) {
        model.addAttribute("nodeId",id); model.addAttribute("results",events.mappings(actor,id,page)); return "network/mappings";
    }
    @PostMapping("/service-desk/network/nodes/{id}/services")
    public String link(@AuthenticationPrincipal TelecomPrincipal actor,@PathVariable long id,@RequestParam String serviceCode) {
        events.link(actor,id,serviceCode); return "redirect:/service-desk/network/nodes/"+id;
    }
    @GetMapping("/service-desk/network/events")
    public String list(@AuthenticationPrincipal TelecomPrincipal actor,Model model,@RequestParam(defaultValue="0") int page) {
        model.addAttribute("results",events.events(actor,page)); return "network/events";
    }
    @GetMapping("/service-desk/network/events/{id}")
    public String detail(@AuthenticationPrincipal TelecomPrincipal actor,Model model,@PathVariable long id,@RequestParam(defaultValue="0") int page) {
        model.addAttribute("event",events.event(actor,id)); model.addAttribute("results",events.tickets(actor,id,page)); return "network/detail";
    }
    @PostMapping("/service-desk/network/events/{id}/retry")
    public String retry(@AuthenticationPrincipal TelecomPrincipal actor,@PathVariable long id) {
        events.retry(actor,id); dispatcher.enqueue(id); return "redirect:/service-desk/network/events/"+id;
    }
}

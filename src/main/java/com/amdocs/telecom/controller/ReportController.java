package com.amdocs.telecom.controller;
import com.amdocs.telecom.dto.ReportForm;
import com.amdocs.telecom.report.*;
import com.amdocs.telecom.security.*;
import com.amdocs.telecom.service.ReportService;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.time.*;
import java.util.UUID;

@Controller
public class ReportController {
    private final ReportService service;
    private final Clock clock;
    public ReportController(ReportService service,Clock clock) { this.service=service; this.clock=clock; }
    @GetMapping({"/service-desk/reports","/manager/reports"})
    public String index(@AuthenticationPrincipal TelecomPrincipal actor,HttpServletRequest request,Model model) {
        ReportForm form=new ReportForm(); form.setFrom(LocalDate.now(clock.withZone(ZoneId.of("Asia/Kolkata"))).minusDays(30)); form.setTo(LocalDate.now(clock.withZone(ZoneId.of("Asia/Kolkata"))));
        form.setType(ReportType.TICKET_VOLUME); form.setFormat(ReportForm.Format.CSV); model.addAttribute("form",form); populate(model,actor,request); return "reports/index";
    }
    @PostMapping({"/service-desk/reports","/manager/reports"})
    public String submit(@AuthenticationPrincipal TelecomPrincipal actor,HttpServletRequest request,Model model,@Valid @ModelAttribute("form") ReportForm form,BindingResult errors) {
        if(errors.hasErrors()) { populate(model,actor,request); return "reports/index"; }
        UUID id=service.request(actor,role(request),form); return "redirect:"+base(request)+"/reports/"+id;
    }
    @GetMapping({"/service-desk/reports/{id}","/manager/reports/{id}"})
    public String status(@AuthenticationPrincipal TelecomPrincipal actor,HttpServletRequest request,Model model,@PathVariable UUID id) {
        model.addAttribute("base",base(request)); model.addAttribute("job",service.status(actor,role(request),id)); return "reports/status";
    }
    @GetMapping({"/service-desk/reports/{id}/download","/manager/reports/{id}/download"})
    public ResponseEntity<byte[]> download(@AuthenticationPrincipal TelecomPrincipal actor,HttpServletRequest request,@PathVariable UUID id) {
        ReportJob job=service.status(actor,role(request),id); byte[] bytes=service.download(actor,role(request),id);
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=\""+job.getFilename()+"\"")
                .header(HttpHeaders.CACHE_CONTROL,"no-store").header("X-Content-Type-Options","nosniff")
                .contentType(MediaType.parseMediaType(job.getFormat()==ReportForm.Format.CSV ? "text/csv;charset=UTF-8" : "text/plain;charset=UTF-8")).body(bytes);
    }
    @PostMapping({"/service-desk/reports/{id}/delete","/manager/reports/{id}/delete"})
    public String delete(@AuthenticationPrincipal TelecomPrincipal actor,HttpServletRequest request,@PathVariable UUID id) {
        service.delete(actor,role(request),id); return "redirect:"+base(request)+"/reports";
    }
    private void populate(Model model,TelecomPrincipal actor,HttpServletRequest request) {
        model.addAttribute("base",base(request)); model.addAttribute("types",ReportType.values()); model.addAttribute("formats",ReportForm.Format.values()); model.addAttribute("jobs",service.list(actor,role(request)));
    }
    private String base(HttpServletRequest request) { return request.getRequestURI().substring(request.getContextPath().length()).startsWith("/manager/") ? "/manager" : "/service-desk"; }
    private Role role(HttpServletRequest request) { return base(request).equals("/manager") ? Role.NETWORK_MANAGER : Role.SERVICE_DESK; }
}

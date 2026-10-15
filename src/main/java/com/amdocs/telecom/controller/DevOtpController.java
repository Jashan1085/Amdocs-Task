package com.amdocs.telecom.controller;

import com.amdocs.telecom.security.*;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import javax.servlet.http.HttpServletRequest;

@Controller
@Profile("dev")
public class DevOtpController {
    private final OtpDelivery delivery;
    public DevOtpController(OtpDelivery delivery) { this.delivery = delivery; }
    @GetMapping("/dev/otp")
    public String inbox(HttpServletRequest request, Model model) {
        PendingChallenge pending = SessionSecrets.pending(request.getSession(false));
        if (pending == null) { return "redirect:/login"; }
        model.addAttribute("code", delivery.preview(pending.getId(), SessionSecrets.binding(request.getSession())).orElse(null));
        model.addAttribute("returnUrl", pending.getPurpose() == OtpPurpose.LOGIN ? "/login/otp" : "/reset-password");
        return "auth/dev-otp";
    }
}

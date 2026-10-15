package com.amdocs.telecom.controller;

import com.amdocs.telecom.security.OtpDelivery;
import com.amdocs.telecom.security.OtpDeliveryUnavailableException;
import com.amdocs.telecom.security.TelecomPrincipal;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.transaction.TransactionException;

@ControllerAdvice
public class WebAdvice {
    private final OtpDelivery delivery;
    public WebAdvice(OtpDelivery delivery) { this.delivery = delivery; }
    @ModelAttribute
    public void common(Authentication authentication, Model model) {
        boolean signedIn = authentication != null && authentication.getPrincipal() instanceof TelecomPrincipal;
        model.addAttribute("signedIn", signedIn);
        if (signedIn) { model.addAttribute("displayName", ((TelecomPrincipal) authentication.getPrincipal()).getDisplayName()); }
        model.addAttribute("demoOtp", delivery.isDemo());
    }
    @ExceptionHandler({DataAccessException.class, TransactionException.class, OtpDeliveryUnavailableException.class})
    public ModelAndView unavailable() {
        ModelAndView result = new ModelAndView("error/503");
        result.setStatus(HttpStatus.SERVICE_UNAVAILABLE);
        return result;
    }
    @ExceptionHandler(com.amdocs.telecom.exception.WorkflowException.class)
    public ModelAndView workflow(com.amdocs.telecom.exception.WorkflowException exception) {
        ModelAndView result = new ModelAndView("error/workflow");
        result.setStatus(exception.getStatus());
        result.addObject("message", exception.getMessage());
        return result;
    }
}

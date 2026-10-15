package com.amdocs.telecom.controller;

import com.amdocs.telecom.dto.*;
import com.amdocs.telecom.security.*;
import com.amdocs.telecom.service.AuthenticationService;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.validation.Valid;
import java.util.Optional;
import java.time.Clock;

@Controller
public class AuthenticationController {
    private final AuthenticationService authentication;
    private final CaptchaService captchas;
    private final OtpDelivery delivery;
    private final Clock clock;
    public AuthenticationController(AuthenticationService authentication, CaptchaService captchas,
                                    OtpDelivery delivery, Clock clock) {
        this.authentication = authentication; this.captchas = captchas;
        this.delivery = delivery; this.clock = clock;
    }

    @GetMapping("/login")
    public String login(Model model, HttpSession session) {
        if (pending(session, OtpPurpose.LOGIN)) { return "redirect:/login/otp"; }
        model.addAttribute("form", new LoginForm());
        issueCaptcha(session, model);
        return "auth/login";
    }

    @PostMapping("/login")
    public String login(@Valid @ModelAttribute("form") LoginForm form, BindingResult errors,
                        HttpSession session, Model model) {
        session.removeAttribute(SessionSecrets.PENDING);
        boolean captchaValid = captchas.consume(session, form.getCaptchaId(), form.getCaptcha());
        if (!captchaValid) {
            authentication.captchaFailure(form.getUsername());
            model.addAttribute("message", "CAPTCHA answer was incorrect or expired. Enter the sum of the new numbers shown below and submit again.");
        }
        if (!errors.hasErrors() && captchaValid) {
            try {
                Optional<PendingChallenge> pending = authentication.beginLogin(form.getUsername(), form.getPassword(),
                        SessionSecrets.binding(session));
                if (pending.isPresent()) {
                    session.setAttribute(SessionSecrets.PENDING, pending.get());
                    return "redirect:/login/otp";
                }
            } catch (OtpDeliveryUnavailableException unavailable) {
                model.addAttribute("message", "Sign-in is temporarily unavailable. Please try again later.");
            }
        }
        form.setPassword(null);
        if (!model.containsAttribute("message")) { model.addAttribute("message", "Unable to sign in. Check your details and CAPTCHA, or try again later."); }
        issueCaptcha(session, model);
        return "auth/login";
    }

    @GetMapping("/login/otp")
    public String otp(HttpSession session, Model model) {
        if (!pending(session, OtpPurpose.LOGIN)) { return "redirect:/login"; }
        model.addAttribute("form", new OtpForm());
        demoCode(session, model);
        return "auth/otp";
    }

    @PostMapping("/login/otp")
    public String otp(@Valid @ModelAttribute("form") OtpForm form, BindingResult errors,
                      HttpServletRequest request, HttpServletResponse response, Model model) {
        HttpSession session = request.getSession();
        if (!pending(session, OtpPurpose.LOGIN)) { return "redirect:/login"; }
        Optional<TelecomPrincipal> principal = errors.hasErrors() ? Optional.empty()
                : authentication.completeLogin(SessionSecrets.pending(session), SessionSecrets.binding(session), form.getCode());
        if (!principal.isPresent()) {
            form.setCode(null);
            model.addAttribute("message", "Code could not be verified. Restart sign-in if it has expired or reached its attempt limit.");
            demoCode(session, model);
            return "auth/otp";
        }
        TelecomPrincipal user = principal.get();
        UsernamePasswordAuthenticationToken token = new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
        new ChangeSessionIdAuthenticationStrategy().onAuthentication(token, request, response);
        new HttpSessionCsrfTokenRepository().saveToken(null, request, response);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(token);
        SecurityContextHolder.setContext(context);
        new HttpSessionSecurityContextRepository().saveContext(context, request, response);
        session.removeAttribute(SessionSecrets.PENDING);
        return "redirect:/dashboard";
    }

    @GetMapping("/forgot-password")
    public String forgot(HttpSession session, Model model) {
        model.addAttribute("form", new ResetRequestForm());
        issueCaptcha(session, model);
        return "auth/forgot";
    }

    @PostMapping("/forgot-password")
    public String forgot(@Valid @ModelAttribute("form") ResetRequestForm form, BindingResult errors,
                         HttpSession session, Model model) {
        boolean valid = captchas.consume(session, form.getCaptchaId(), form.getCaptcha());
        if (!valid) { authentication.captchaFailure(form.getUsername()); }
        if (errors.hasErrors() || !valid) {
            model.addAttribute("message", "Check the form and CAPTCHA, then try again.");
            issueCaptcha(session, model);
            return "auth/forgot";
        }
        PendingChallenge challenge = authentication.beginReset(form.getUsername(), SessionSecrets.binding(session));
        session.setAttribute(SessionSecrets.PENDING, challenge);
        return "redirect:/reset-password";
    }

    @GetMapping("/reset-password")
    public String reset(HttpSession session, Model model) {
        if (!pending(session, OtpPurpose.RESET)) { return "redirect:/forgot-password"; }
        model.addAttribute("form", new ResetPasswordForm());
        demoCode(session, model);
        return "auth/reset";
    }

    @PostMapping("/reset-password")
    public String reset(@Valid @ModelAttribute("form") ResetPasswordForm form, BindingResult errors,
                        HttpSession session, Model model, RedirectAttributes redirect) {
        if (!pending(session, OtpPurpose.RESET)) { return "redirect:/forgot-password"; }
        if (!errors.hasErrors() && form.getPassword().equals(form.getConfirmation())
                && authentication.resetPassword(SessionSecrets.pending(session), SessionSecrets.binding(session),
                form.getCode(), form.getPassword())) {
            session.removeAttribute(SessionSecrets.PENDING);
            redirect.addFlashAttribute("notice", "Password updated. Sign in with your new password.");
            return "redirect:/login";
        }
        form.setCode(null); form.setPassword(null); form.setConfirmation(null);
        model.addAttribute("message", "Unable to reset. Check the code and matching passwords (12 characters minimum, 72 UTF-8 bytes maximum).");
        demoCode(session, model);
        return "auth/reset";
    }

    @PostMapping("/login/restart")
    public String restart(HttpSession session) {
        session.removeAttribute(SessionSecrets.PENDING);
        return "redirect:/login";
    }

    private void demoCode(HttpSession session, Model model) {
        PendingChallenge challenge = SessionSecrets.pending(session);
        if (delivery.isDemo() && challenge != null && clock.instant().isBefore(challenge.getExpiresAt())) {
            model.addAttribute("devCode", delivery.preview(challenge.getId(), SessionSecrets.binding(session)).orElse(null));
        }
    }

    private void issueCaptcha(HttpSession session, Model model) {
        CaptchaService.IssuedChallenge challenge = captchas.issue(session);
        model.addAttribute("captchaQuestion", challenge.getQuestion());
        model.addAttribute("captchaId", challenge.getId());
    }

    private boolean pending(HttpSession session, OtpPurpose purpose) {
        PendingChallenge pending = SessionSecrets.pending(session);
        if (pending != null && !clock.instant().isBefore(pending.getExpiresAt())) {
            session.removeAttribute(SessionSecrets.PENDING);
            return false;
        }
        return pending != null && pending.getPurpose() == purpose;
    }
}

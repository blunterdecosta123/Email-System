package com.example.emailsystem.controller;

import com.example.emailsystem.domain.EmailMessage;
import com.example.emailsystem.domain.User;
import com.example.emailsystem.service.EmailService;
import com.example.emailsystem.service.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class EmailController {

    private final UserService userService;
    private final EmailService emailService;

    public EmailController(UserService userService, EmailService emailService) {
        this.userService = userService;
        this.emailService = emailService;
    }

    @GetMapping("/")
    public String inbox(@AuthenticationPrincipal UserDetails principal, Model model) {
        User user = userService.getByEmail(principal.getUsername());
        model.addAttribute("messages", emailService.history(user));
        model.addAttribute("user", user);
        return "email/inbox";
    }

    @GetMapping("/compose")
    public String compose(Model model) {
        model.addAttribute("form", new ComposeForm("", "", ""));
        return "email/compose";
    }

    @PostMapping("/compose")
    public String send(
            @AuthenticationPrincipal UserDetails principal,
            @Valid @ModelAttribute("form") ComposeForm form,
            BindingResult bindingResult,
            RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            return "email/compose";
        }

        User user = userService.getByEmail(principal.getUsername());
        EmailMessage message = emailService.send(user, form.recipient(), form.subject(), form.body());
        String status = message.getDirection().name().equals("SENT")
                ? "Email sent."
                : "Email saved in history. Configure SMTP settings to send through a provider.";
        redirectAttributes.addFlashAttribute("success", status);
        return "redirect:/";
    }

    @PostMapping("/receive")
    public String receive(@AuthenticationPrincipal UserDetails principal, RedirectAttributes redirectAttributes) {
        User user = userService.getByEmail(principal.getUsername());
        try {
            int count = emailService.receive(user);
            redirectAttributes.addFlashAttribute("success", "Imported " + count + " message(s).");
        } catch (IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/";
    }

    @GetMapping("/messages/{id}")
    public String view(
            @AuthenticationPrincipal UserDetails principal,
            @PathVariable Long id,
            Model model) {
        User user = userService.getByEmail(principal.getUsername());
        model.addAttribute("message", emailService.getOwnedMessage(user, id));
        return "email/detail";
    }

    public record ComposeForm(
            @Email @NotBlank @Size(max = 180) String recipient,
            @NotBlank @Size(max = 180) String subject,
            @NotBlank @Size(max = 10000) String body) {
    }
}

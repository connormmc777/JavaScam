package com.offsec.ssd.elearn.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils; // Import Spring's StringUtils
import org.springframework.web.util.HtmlUtils; // Import HTML utility for sanitization

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.offsec.ssd.elearn.model.User;
import com.offsec.ssd.elearn.repository.UserRepository;
import com.offsec.ssd.elearn.service.RegistrationService;
import com.offsec.ssd.elearn.util.CryptoUtil;

import java.net.URI;
import java.net.URISyntaxException;

@Controller
public class RegistrationController {

    private static final Logger logger = LoggerFactory.getLogger(RegistrationController.class);

    @Autowired
    RegistrationService registrationService;

    @Autowired
    UserRepository userRepo;

    @GetMapping("/registration")
    public String getRegistrationPage(HttpServletRequest req, Model model, HttpServletResponse res) {
        return "registration";
    }

    @PostMapping("/registration")
    public String postRegistrationPage(HttpServletRequest req, Model model, HttpServletResponse res, RedirectAttributes redirectAttributes) {

        StringBuilder sb = new StringBuilder();
        boolean invalid = false;

        // Basic null checks
        if (!StringUtils.hasText(req.getParameter("firstname"))) {
            invalid = true;
            sb.append("Missing first name value.\n");
        }
        if (!StringUtils.hasText(req.getParameter("lastname"))) {
            invalid = true;
            sb.append("Missing last name value.\n");
        }
        String emailParam = req.getParameter("email");
        if (!StringUtils.hasText(emailParam) || !isValidEmailDomain(emailParam)) { // Use specific email domain validation
            invalid = true;
            sb.append("Invalid email address.\n");
        }
        if (!StringUtils.hasText(req.getParameter("username"))) {
            invalid = true;
            sb.append("Missing username value.\n");
        }
        if (!StringUtils.hasText(req.getParameter("password"))) {
            invalid = true;
            sb.append("Missing password value.\n");
        }
        String registrationTypeParam = req.getParameter("registrationtype");
        if (!StringUtils.hasText(registrationTypeParam)) {
            invalid = true;
            sb.append("Missing registration type.\n");
        } else if (!registrationTypeParam.equals("student") && !registrationTypeParam.equals("teacher")) {
            invalid = true;
            sb.append("Invalid registration type value.\n");
        }

        if (invalid) {
            logger.info("Invalid registration request: " + sb.toString());
            model.addAttribute("message", sb.toString());
            return "registration";
        }

        String firstname = HtmlUtils.htmlEscape(req.getParameter("firstname"));
        String lastname = HtmlUtils.htmlEscape(req.getParameter("lastname"));
        String email = HtmlUtils.htmlEscape(emailParam);
        String username = HtmlUtils.htmlEscape(req.getParameter("username"));
        String password = CryptoUtil.bcryptHashString(HtmlUtils.htmlEscape(req.getParameter("password")));
        String registrationType = registrationTypeParam;

        if (registrationType.equalsIgnoreCase("teacher")) {
            if (!email.toLowerCase().endsWith(".edu")) { // Enforce .edu domain for teachers
                model.addAttribute("message", "Only users with .edu email addresses may register as teachers.");
                return "registration";
            }
            if (!registrationService.isTeacherEmail(email)) {
                model.addAttribute("message", "Only users with .edu email addresses may register as teachers at this time (service check failed).");
                return "registration";
            }
        }

        User u = new User();
        u.setFirstName(firstname);
        u.setLastName(lastname);
        u.setEmail(email);
        u.setUsername(username);
        u.setPassword(password);

        if (registrationType.equalsIgnoreCase("teacher")) {
            u.setPublic(true);
            u.setTeacher(true);
            u.setStudent(false);
        } else {
            u.setPublic(false);
            u.setTeacher(false);
            u.setStudent(true);
        }

        try {
            userRepo.insertNewUser(u);
        } catch (DuplicateKeyException e) {
            model.addAttribute("message", "An account already exists for that email or username.");
            return "registration";
        }

        redirectAttributes.addAttribute("message", "Registration successful. Please log in to continue.");
        return "redirect:/login";
    }

    // Simple email format validation (you might want a more robust one)
    private boolean isValidEmailDomain(String email) {
        return email.contains("@") && email.contains(".");
    }
}
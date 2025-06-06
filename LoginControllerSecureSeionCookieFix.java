package com.offsec.ssd.elearn.controller;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.support.RequestContextUtils;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.offsec.ssd.elearn.annotation.RateLimiter;
import com.offsec.ssd.elearn.model.AccountLockout;
import com.offsec.ssd.elearn.model.LoginAttempt;
import com.offsec.ssd.elearn.model.RememberMeToken;
import com.offsec.ssd.elearn.model.User;
import com.offsec.ssd.elearn.repository.AccountLockoutsRepository;
import com.offsec.ssd.elearn.repository.LoginAttemptsRepository;
import com.offsec.ssd.elearn.repository.RememberMeTokenRepository;
import com.offsec.ssd.elearn.repository.UserRepository;
import com.offsec.ssd.elearn.service.RememberMeService;
import com.offsec.ssd.elearn.util.CryptoUtil; // Ensure this is still used for password hashing
import com.offsec.ssd.elearn.util.NetworkUtil;
import com.offsec.ssd.elearn.util.SessionUtil;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

 @Controller
public class LoginController {

    private static final Logger logger = LoggerFactory.getLogger(LoginController.class);

    @Autowired
    UserRepository userRepo;

    @Autowired
    private Environment environment;

    @Autowired
    private LoginAttemptsRepository loginAttemptsRepo;

    @Autowired
    private AccountLockoutsRepository accountLockoutsRepo;

    @Autowired
    private RememberMeTokenRepository rememberMeTokenRepo;

    @Value("${app.mainDashboardPage}")
    private String mainDashboardPage;

    @Value("${app.rememberMeDays}")
    private int REMEMBER_ME_EXPIRY_DAYS;

    @Autowired
    private RememberMeService rememberMeService;

    @Autowired
    private SessionUtil sessionUtil;

    @GetMapping("/login")
    public String getLoginPage(HttpServletRequest req, HttpServletResponse res, Model model) {
        // First check if already authenticated via session
        if(req.getSession(false) != null && Boolean.TRUE.equals(req.getSession(false).getAttribute("authenticated"))) {
            return "redirect:" + mainDashboardPage;
        }

        // Try remember-me login if not already authenticated
        if (rememberMeService.processRememberMe(req, res)) {
            return "redirect:" + mainDashboardPage;
        }

        // Handle flash messages for login errors/notifications
        Map<String, ?> inputFlashMap = RequestContextUtils.getInputFlashMap(req);
        if(inputFlashMap != null) {
            String message = (String) inputFlashMap.get("message");
            model.addAttribute("message", message);
        }

        return "login";
    }

   @PostMapping("/login")
@RateLimiter(limit = 50, duration = 60)
public String postLoginPage(HttpServletRequest req, HttpServletResponse response, Model model, RedirectAttributes redirectAttrs) {
    String username = req.getParameter("username");
    String password = req.getParameter("password");
    String rememberMe = req.getParameter("remember-me");
    String ipAddress = NetworkUtil.getClientIP(req);

    if (username == null || password == null) {
        redirectAttrs.addFlashAttribute("message", "Please enter a valid username and password.");
        return "redirect:/login";
    }

    User user = userRepo.getUserByUsername(username);
    if (user == null) {
        recordFailedAttempt(null, ipAddress);
        redirectAttrs.addFlashAttribute("message", "Invalid username or password.");
        return "redirect:/login";
    }

    // Check if account is locked
    AccountLockout lockout = accountLockoutsRepo.getActiveLockout(user.getId());
    if (lockout != null) {

        if (lockout.getUnlockTime() == null || lockout.getUnlockTime().isAfter(Instant.now())) {
            recordFailedAttempt(user.getId(),ipAddress);
            redirectAttrs.addFlashAttribute("message", "Your account is locked. Please try again later or contact support.");
            logger.info("Account " + user.getId() + " is trying to log in, but it's locked out");
            return "redirect:/login";
        } else {
            accountLockoutsRepo.removeActiveLockout(user.getId());
        }
    }

    if (CryptoUtil.matchStringWithHash(password, user.getPassword())) {
        // Successful login
        recordSuccessfulAttempt(user.getId(), ipAddress);

        // Create new session and hydrate it
        req.getSession().invalidate();
        HttpSession session = req.getSession(true);
        sessionUtil.hydrateSession(session, user);
        session.setAttribute("authenticated", true); // Mark session as authenticated

        // Set SameSite=Strict for JSESSIONID using the Set-Cookie header
        response.setHeader("Set-Cookie", String.format("%s=%s; Path=/; HttpOnly; %s; SameSite=Strict",
                "JSESSIONID",
                session.getId(),
                req.isSecure() ? "Secure" : ""));

        // Handle remember-me functionality
        if ("on".equals(rememberMe)) {
            handleRememberMe(user, response, req.isSecure());
        }

        logger.debug("Successful login for userId " + user.getId());

        return "redirect:" + mainDashboardPage;
    } else {
        // Failed login
        recordFailedAttempt(user.getId(), ipAddress);
        checkAndApplyLockout(user.getId());
        redirectAttrs.addFlashAttribute("message", "Invalid username or password.");
        return "redirect:/login";
    }
}

   private void handleRememberMe(User user, HttpServletResponse response, boolean isSecure) {
        // Generate random series and token (without CryptoUtil for this part)
        String series = java.util.UUID.randomUUID().toString();
        String token = java.util.UUID.randomUUID().toString();

        // Calculate expiry date
        Instant expiryDate = Instant.now().plus(REMEMBER_ME_EXPIRY_DAYS, ChronoUnit.DAYS);

        // Save token to database
        RememberMeToken rememberMeToken = new RememberMeToken(user.getId(), token, series, expiryDate);
        rememberMeTokenRepo.saveToken(rememberMeToken);

        // Create cookie
        String cookieValue = series + ":" + token;
        Cookie cookie = new Cookie("remember-me", cookieValue);
        cookie.setMaxAge(REMEMBER_ME_EXPIRY_DAYS * 24 * 60 * 60); // Convert days to seconds
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        cookie.setSecure(isSecure);
        response.addCookie(cookie);
    }


    private void recordSuccessfulAttempt(int userId, String ipAddress) {
        LoginAttempt attempt = new LoginAttempt(userId, Instant.now(), ipAddress, true);
        loginAttemptsRepo.insertLoginAttempt(attempt);
    }

    private void recordFailedAttempt(Integer userId, String ipAddress) {
        LoginAttempt attempt = new LoginAttempt(userId != null ? userId : -1, Instant.now(), ipAddress, false);
        loginAttemptsRepo.insertLoginAttempt(attempt);
    }

    private void checkAndApplyLockout(int userId) {
        Instant fifteenMinutesAgo = Instant.now().minus(Duration.ofMinutes(15));
        List<LoginAttempt> recentAttempts = loginAttemptsRepo.getRecentLoginAttempts(userId, fifteenMinutesAgo);

        if (recentAttempts != null && recentAttempts.size() >= 5) {
            AccountLockout lockout = new AccountLockout(userId, Instant.now(), Instant.now().plus(Duration.ofMinutes(30)));
            accountLockoutsRepo.insertAccountLockout(lockout);
        }
    }

    @PostMapping("/logout")
    public String logout(HttpServletRequest req, HttpServletResponse res) {
        // Get the user ID before invalidating the session
        Integer userId = (Integer) req.getSession().getAttribute("id");

        // Invalidate the session
        req.getSession().invalidate();

        // Clear any session-related cookies
        Cookie[] cookies = req.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (cookie.getName().equals("JSESSIONID")) {
                    cookie.setMaxAge(0);
                    cookie.setPath("/");
                    res.addCookie(cookie);
                    break; // Only clear JSESSIONID once
                }
            }
            for (Cookie cookie : cookies) {
                if (cookie.getName().equals("remember-me")) {
                    cookie.setMaxAge(0);
                    cookie.setPath("/");
                    cookie.setHttpOnly(true);
                    cookie.setSecure(req.isSecure());
                    res.addCookie(cookie);
                    break; // Only clear remember-me once
                }
            }
        }

        // Log the logout event
        if (userId != null) {
            logger.info("User with ID {} logged out successfully", userId);
        } else {
            logger.info("User logged out");
        }

        // Redirect to the login page
        return "redirect:/login";
    }

    @GetMapping("/logout")
    public String logoutGet(HttpServletRequest req, HttpServletResponse res) {
        return logout(req, res);
    }

}
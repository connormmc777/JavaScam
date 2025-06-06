package com.offsec.ssd.elearn.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.offsec.ssd.elearn.model.Course;
import com.offsec.ssd.elearn.model.User;
import com.offsec.ssd.elearn.service.AnnouncementService;
import com.offsec.ssd.elearn.repository.CourseRepository;
import com.offsec.ssd.elearn.repository.UserRepository;
import com.offsec.ssd.elearn.dto.AnnouncementDTO;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Date;
import java.text.SimpleDateFormat;
import java.text.ParseException;
import java.util.Calendar;
    @Controller
@RequestMapping("/announcements")
public class AnnouncementController {

    @Autowired
    private AnnouncementService announcementService;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private UserRepository userRepository;

    private static final Logger logger = LoggerFactory.getLogger(AnnouncementController.class);

    private boolean verifyTeacherCourseAccess(Integer userId, Integer courseId) {
        Course course = courseRepository.getCourseById(courseId);
        return course != null && course.getOwnerId() == userId;
    }

    @GetMapping("/manage")
    public String showAnnouncementManager(HttpServletRequest req, Model model, RedirectAttributes redirectAttributes) {
        Integer userId = (Integer) req.getSession(false).getAttribute("id");
        if (req.getSession(false) == null || userId == null) {
            redirectAttributes.addFlashAttribute("error", "You must be logged in.");
            return "redirect:/login";
        }
        User user = userRepository.getUserById(userId);
        if (user == null) {
            redirectAttributes.addFlashAttribute("error", "Invalid user.");
            return "redirect:/login";
        }
        List<Course> teacherCourses = courseRepository.getAllActiveCoursesByOwnerId(user.getId());
        List<AnnouncementDTO> activeAnnouncements = announcementService.getAnnouncementsByTeacher(user.getId());

        model.addAttribute("courses", teacherCourses);
        model.addAttribute("announcements", activeAnnouncements);
        model.addAttribute("newAnnouncement", new AnnouncementDTO());

        return "announcements/manage";
    }
    @PostMapping("/create")
    public String createAnnouncement(
            HttpServletRequest req,
            @RequestParam(value = "expires", required = false) String expires,
            @RequestParam(value = "courseId", required = true) Integer courseId,
            @RequestParam(value = "content", required = true) String content,
            RedirectAttributes redirectAttributes) {
        Integer userId = (Integer) req.getSession(false).getAttribute("id");
        if (req.getSession(false) == null || userId == null) {
            redirectAttributes.addFlashAttribute("error", "You must be logged in.");
            return "redirect:/login";
        }
        User user = userRepository.getUserById(userId);
        if (user == null) {
            redirectAttributes.addFlashAttribute("error", "Invalid user.");
            return "redirect:/login";
        }

        if (courseId == null) {
            redirectAttributes.addFlashAttribute("error", "Course must be selected");
            return "redirect:/announcements/manage";
        }

        if (!verifyTeacherCourseAccess(user.getId(), courseId)) {
            logger.warn("Unauthorized attempt to create announcement for course " + courseId + " by user " + user.getId());
            redirectAttributes.addFlashAttribute("error", "Unauthorized course access");
            return "redirect:/announcements/manage";
        }

        if (content == null || content.trim().isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Content cannot be empty");
            return "redirect:/announcements/manage";
        }

        if (content.length() > 256) {
            redirectAttributes.addFlashAttribute("error", "Content must be less than 256 characters");
            return "redirect:/announcements/manage";
        }

        try {
            AnnouncementDTO announcement = new AnnouncementDTO();
            announcement.setSenderId(user.getId());
            announcement.setCourseId(courseId);
            announcement.setContent(content);
            announcement.setDate(new Date());

            if (expires != null && !expires.trim().isEmpty()) {
                try {
                    SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
                    dateFormat.setLenient(false);
                    Date expiryDate = dateFormat.parse(expires);
                    if (expiryDate.before(new Date())) {
                        redirectAttributes.addFlashAttribute("error", "Expiry date must be in the future");
                        return "redirect:/announcements/manage";
                    }
                    announcement.setExpires(expiryDate);
                } catch (ParseException e) {
                    redirectAttributes.addFlashAttribute("error", "Invalid date format");
                    return "redirect:/announcements/manage";
                }
            } else {
                Calendar calendar = Calendar.getInstance();
                calendar.add(Calendar.DAY_OF_MONTH, 30);
                announcement.setExpires(calendar.getTime());
            }

            announcementService.createAnnouncement(announcement);
            redirectAttributes.addFlashAttribute("success", "Announcement created successfully");
        } catch (Exception e) {
            logger.error("Error creating announcement: " + e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Failed to create announcement");
        }

        return "redirect:/announcements/manage";
    }
@PostMapping("/delete/{id}")
    public String deleteAnnouncement(
            HttpServletRequest req,
            @PathVariable("id") Integer id,
            RedirectAttributes redirectAttributes) {
        Integer userId = (Integer) req.getSession(false).getAttribute("id");
        if (req.getSession(false) == null || userId == null) {
            redirectAttributes.addFlashAttribute("error", "You must be logged in.");
            return "redirect:/login";
        }
        User user = userRepository.getUserById(userId);
        if (user == null) {
            redirectAttributes.addFlashAttribute("error", "Invalid user.");
            return "redirect:/login";
        }

        try {
            AnnouncementDTO announcement = announcementService.getAnnouncementById(id);
            if (announcement == null) {
                redirectAttributes.addFlashAttribute("error", "Announcement not found");
                return "redirect:/announcements/manage";
            }
            if (!verifyTeacherCourseAccess(user.getId(), announcement.getCourseId())) {
                logger.warn("Unauthorized attempt to delete announcement " + id + " by user " + user.getId());
                redirectAttributes.addFlashAttribute("error", "Unauthorized announcement access");
                return "redirect:/announcements/manage";
            }
            announcementService.deleteAnnouncement(id, user.getId());
            redirectAttributes.addFlashAttribute("success", "Announcement deleted successfully");
        } catch (Exception e) {
            logger.error("Error deleting announcement: " + e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error", "Failed to delete announcement");
        }
        return "redirect:/announcements/manage";
    }
}
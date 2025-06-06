package com.offsec.ssd.elearn.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.offsec.ssd.elearn.dto.CourseDTO;
import com.offsec.ssd.elearn.model.Course;
import com.offsec.ssd.elearn.repository.CourseRepository;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;

@Controller
@RequestMapping("/teacher/courses")
public class TeacherCourseController {

    @Autowired
    private CourseRepository courseRepository;

    @GetMapping
    public String listCourses(HttpServletRequest req, Model model) {
        Integer teacherId = (Integer) req.getSession(false).getAttribute("id");
        List<Course> courses = courseRepository.getCoursesByOwnerId(teacherId);
        model.addAttribute("courses", courses);
        return "teacher/courses/list";
    }

    @GetMapping("/create")
    public String showCreateForm(Model model) {
        model.addAttribute("courseDTO", new CourseDTO());
        return "teacher/courses/create";
    }

    @PostMapping("/create")
    public String createCourse(
            @ModelAttribute("courseDTO") CourseDTO courseDTO,
            BindingResult bindingResult,
            HttpServletRequest req,
            RedirectAttributes redirectAttributes) {

        validateCourseDTO(courseDTO, bindingResult);

        if (bindingResult.hasErrors()) {
            return "teacher/courses/create";
        }

        try {
            Course course = new Course();
            course.setName(courseDTO.getName());
            course.setSummary(courseDTO.getSummary());

            // Process image if URL is provided
            if (courseDTO.getImage() != null && !courseDTO.getImage().trim().isEmpty()) {
                try {
                    String base64Image = courseDTO.downloadAndConvertImage();
                    course.setImage(base64Image);
                } catch (Exception e) {
                    bindingResult.addError(new FieldError("courseDTO", "image",
                            "Error processing image: " + e.getMessage()));
                    return "teacher/courses/create";
                }
            }

            course.setActive(courseDTO.isActive());
            course.setOwnerId((Integer) req.getSession(false).getAttribute("id"));

            courseRepository.insertCourse(course);
            redirectAttributes.addFlashAttribute("successMessage", "Course created successfully");
            return "redirect:/teacher/courses";
        } catch (Exception e) {
            bindingResult.addError(new FieldError("courseDTO", "name", "Error creating course"));
            return "teacher/courses/create";
        }
    }

    @GetMapping("/edit/{id}")
    public String showEditForm(@PathVariable("id") int id, HttpServletRequest req, Model model) {
        Course course = courseRepository.getCourseById(id);
        Integer loggedInTeacherId = (Integer) req.getSession(false).getAttribute("id");

        if (course == null || !course.getOwnerId().equals(loggedInTeacherId)) {
            return "redirect:/teacher/courses?error=unauthorized";
        }

        CourseDTO courseDTO = new CourseDTO();
        courseDTO.setName(course.getName());
        courseDTO.setSummary(course.getSummary());
        courseDTO.setImage(""); // Leave the URL field empty
        courseDTO.setActive(course.isActive());

        model.addAttribute("courseDTO", courseDTO);
        model.addAttribute("courseId", id);
        model.addAttribute("currentImage", course.getImage());
        return "teacher/courses/edit";
    }

    @PostMapping("/edit/{id}")
    public String updateCourse(
            @PathVariable("id") int id,
            @ModelAttribute("courseDTO") CourseDTO courseDTO,
            BindingResult bindingResult,
            HttpServletRequest req,
            RedirectAttributes redirectAttributes, Model model) {

        Integer loggedInTeacherId = (Integer) req.getSession(false).getAttribute("id");
        Course existingCourse = courseRepository.getCourseById(id);

        if (existingCourse == null || !existingCourse.getOwnerId().equals(loggedInTeacherId)) {
            return "redirect:/teacher/courses?error=unauthorized";
        }

        validateCourseDTO(courseDTO, bindingResult);

        if (bindingResult.hasErrors()) {
            model.addAttribute("currentImage", existingCourse.getImage());
            return "teacher/courses/edit";
        }

        try {
            existingCourse.setName(courseDTO.getName());
            existingCourse.setSummary(courseDTO.getSummary());

            // Only update the image if a new URL is provided
            if (courseDTO.getImage() != null && !courseDTO.getImage().trim().isEmpty()) {
                try {
                    String base64Image = courseDTO.downloadAndConvertImage();
                    existingCourse.setImage(base64Image);
                } catch (Exception e) {
                    bindingResult.addError(new FieldError("courseDTO", "image",
                            "Error processing image: " + e.getMessage()));
                    model.addAttribute("currentImage", existingCourse.getImage());
                    return "teacher/courses/edit";
                }
            }
            // If no new URL is provided, keep the existing image

            existingCourse.setActive(courseDTO.isActive());

            courseRepository.updateCourse(existingCourse);
            redirectAttributes.addFlashAttribute("successMessage", "Course updated successfully");
            return "redirect:/teacher/courses";
        } catch (Exception e) {
            bindingResult.addError(new FieldError("courseDTO", "name", "Error updating course"));
            model.addAttribute("currentImage", existingCourse.getImage());
            return "teacher/courses/edit";
        }
    }

    private void validateCourseDTO(CourseDTO courseDTO, BindingResult bindingResult) {
        if (!courseDTO.isNameValid()) {
            bindingResult.addError(new FieldError("courseDTO", "name",
                    "Course name is required and must be less than 128 characters"));
        }
        if (!courseDTO.isSummaryValid()) {
            bindingResult.addError(new FieldError("courseDTO", "summary",
                    "Course summary is required and must be less than 256 characters"));
        }
        if (!courseDTO.isImageUrlValid()) {
            bindingResult.addError(new FieldError("courseDTO", "image",
                    "Image URL must be empty or a valid image URL (jpg, jpeg, png, gif)"));
        }
    }
}
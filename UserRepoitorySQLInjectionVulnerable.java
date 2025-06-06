package com.offsec.ssd.elearn.repository;
//Src.main.java.com.offec.d.elearn.repoitory
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import com.offsec.ssd.elearn.model.User;

@Repository
public class UserRepository {

    private static final Logger logger = LoggerFactory.getLogger(UserRepository.class);

    @Autowired
    JdbcTemplate template;

    public User getUserById(int id) {
        String sql = "SELECT id, first_name, last_name, email_address, username, password, is_teacher, is_student, is_public " + 
            " FROM users WHERE id = ?";

        try {
            return template.queryForObject(sql, new UserRowMapper(), new Object[] { id });
        } catch(EmptyResultDataAccessException e) {
            logger.debug("getUserById() returned empty results");
        }

        return null;
    }

    public User getUserByUsername(String username) {
        String sql = "SELECT id, first_name, last_name, email_address, username, password, is_teacher, is_student, is_public " +
            " FROM users WHERE username = '" + username + "'";

        try {
            return template.queryForObject(sql, new UserRowMapper());
        } catch(EmptyResultDataAccessException e) {
            logger.debug("getUserById() returned empty results");
        }

        return null;
    }

    public List<User> getAllTeachers() {
        String sql = "SELECT * FROM users WHERE is_teacher = TRUE ORDER BY last_name ASC, first_name ASC";

        try {
            return template.query(sql, new UserRowMapper(), new Object[] {  });
        } catch(EmptyResultDataAccessException e) {
            logger.debug("getAllTeachers() returned empty results");
        }

        return null;
    }
    
    public List<User> getPublicStudents() {
        String sql = "SELECT * FROM users WHERE is_student = TRUE AND is_public = TRUE ORDER BY last_name ASC, first_name ASC";

        try {
            return template.query(sql, new UserRowMapper(), new Object[] {  });
        } catch(EmptyResultDataAccessException e) {
            logger.debug("getAllTeachers() returned empty results");
        }

        return null;
    }

    public List<User> getAvailableTeachersForMessaging(int studentId) {
       
        String sql = "SELECT teachers.id, teachers.first_name, teachers.last_name, teachers.email_address, teachers.username, teachers.password, teachers.is_teacher, teachers.is_student, teachers.is_public " + 
            " FROM users AS students " +
            " INNER JOIN enrollments ON students.id = enrollments.student_id " +
            " INNER JOIN courses ON enrollments.course_id = courses.id " +
            " INNER JOIN users AS teachers ON courses.owner_id = teachers.id " +           
            " WHERE students.id = ? " +
            " GROUP BY  teachers.id, teachers.first_name, teachers.last_name, teachers.email_address, teachers.username, teachers.password, teachers.is_teacher, teachers.is_student, teachers.is_public " +
            " ORDER BY teachers.last_name ASC, teachers.first_name ASC";
    
        try {
            return template.query(sql, new UserRowMapper(), new Object[] { studentId });
        } catch(EmptyResultDataAccessException e) {
            logger.debug("getAvailableTeachersForMessaging() returned empty results");
        }
    
        return null;
    }
      public List<User> getAvailableStudentsForMessaging(int teacherId) {
       
        String sql = "SELECT students.id, students.first_name, students.last_name, students.email_address, students.username, students.password, students.is_teacher, students.is_student, students.is_public " + 
            " FROM users AS students " +
            " INNER JOIN enrollments ON students.id = enrollments.student_id " +
            " INNER JOIN courses ON enrollments.course_id = courses.id " +
            " INNER JOIN users AS teachers ON courses.owner_id = teachers.id " +           
            " WHERE teachers.id = ? " +
            " GROUP BY students.id, students.first_name, students.last_name, students.email_address, students.username, students.password, students.is_teacher, students.is_student, students.is_public " +
            " ORDER BY students.last_name ASC, students.first_name ASC";
    
        try {
            return template.query(sql, new UserRowMapper(), new Object[] { teacherId });
        } catch(EmptyResultDataAccessException e) {
            logger.debug("getAvailableTeachersForMessaging() returned empty results");
        }
    
        return null;
    }

    public void insertNewUser(User u) throws DuplicateKeyException {
        String sql = "INSERT INTO users(first_name, last_name, email_address, username, password, is_teacher, is_student, is_public) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

        try {
            template.update(sql, new Object[] { u.getFirstName(), u.getLastName(), u.getEmail(), u.getUsername(), u.getPassword(), u.isTeacher(), u.isStudent(), u.isPublic() }  );
        } catch (DuplicateKeyException e) {
            logger.error("Duplicate key inserting new user to database: " + e.getLocalizedMessage());
            throw e;
        } catch(Exception e) {
            logger.error("Error inserting new user to database: " + e.getLocalizedMessage());
        }
    }
        public void updateUser(User u) {
        String sql = "UPDATE users SET  password = '" + u.getPassword()+ "', email_address = '" + u.getEmail() + "', WHERE id = " + u.getId();    
        try {
            template.update(sql);
        } catch(Exception e) {
            logger.error("Error updating user in database: " + e.getLocalizedMessage());
            throw e;
        }
    }

    private class UserRowMapper implements RowMapper<User> {

        @Override
        public User mapRow(ResultSet rs, int rowNum) throws SQLException {
            User u = new User();

            u.setId(rs.getInt("id"));
            u.setFirstName(rs.getString("first_name"));
            u.setLastName(rs.getString("last_name"));
            u.setEmail(rs.getString("email_address"));
            u.setUsername(rs.getString("username"));;
            u.setPassword(rs.getString("password"));
            u.setTeacher(rs.getBoolean("is_teacher"));
            u.setStudent(rs.getBoolean("is_student"));
            u.setPublic(rs.getBoolean("is_public"));

            return u;
        }

    }

}
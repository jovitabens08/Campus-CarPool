package com.campuscarpool.dao;

import com.campuscarpool.db.DBConnection;
import com.campuscarpool.model.BusinessException;
import com.campuscarpool.model.Student;

import java.sql.*;

public class StudentDAO {

    public int register(String name, String email, String contact) throws SQLException, BusinessException {
        String sql = "INSERT INTO students (student_name, email, contact_info) VALUES (?, ?, ?)";
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.setString(2, email);
            ps.setString(3, contact);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                rs.next();
                return rs.getInt(1);
            }
        } catch (SQLIntegrityConstraintViolationException e) {
            throw new BusinessException("A student with this email is already registered.");
        }
    }

    public Student findById(int id) throws SQLException {
        String sql = "SELECT student_id, student_name, email, contact_info FROM students WHERE student_id = ?";
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next()
                        ? new Student(rs.getInt(1), rs.getString(2), rs.getString(3), rs.getString(4))
                        : null;
            }
        }
    }
}

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;

public class StudentInformationFrame extends JFrame {
    // Components
    private JTextField txtRollNo, txtName, txtAge, txtEmail, txtPhone;
    private JComboBox<String> cbCourse, cbGender;
    private JTextArea txtAddress;
    private JTable studentTable;
    private DefaultTableModel tableModel;
    private ArrayList<Student> studentList;

    public StudentInformationFrame() {
        studentList = new ArrayList<>();

        // Frame settings
        setTitle("Student Information System");
        setSize(900, 700);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(10, 10));

        // Create components
        createInputPanel();
        createTablePanel();
        createButtonPanel();

        setVisible(true);
    }

    // Create input panel for student details
    private void createInputPanel() {
        JPanel inputPanel = new JPanel();
        inputPanel.setLayout(new GridBagLayout());
        inputPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(Color.BLUE, 2),
                "Student Details",
                0, 0, new Font("Arial", Font.BOLD, 14), Color.BLUE));
        inputPanel.setBackground(new Color(240, 248, 255));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 10, 5, 10);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Roll Number
        gbc.gridx = 0; gbc.gridy = 0;
        inputPanel.add(new JLabel("Roll No:"), gbc);
        gbc.gridx = 1;
        txtRollNo = new JTextField(15);
        inputPanel.add(txtRollNo, gbc);

        // Name
        gbc.gridx = 2; gbc.gridy = 0;
        inputPanel.add(new JLabel("Name:"), gbc);
        gbc.gridx = 3;
        txtName = new JTextField(15);
        inputPanel.add(txtName, gbc);

        // Age
        gbc.gridx = 0; gbc.gridy = 1;
        inputPanel.add(new JLabel("Age:"), gbc);
        gbc.gridx = 1;
        txtAge = new JTextField(15);
        inputPanel.add(txtAge, gbc);

        // Gender
        gbc.gridx = 2; gbc.gridy = 1;
        inputPanel.add(new JLabel("Gender:"), gbc);
        gbc.gridx = 3;
        cbGender = new JComboBox<>(new String[]{"Male", "Female", "Other"});
        inputPanel.add(cbGender, gbc);

        // Course
        gbc.gridx = 0; gbc.gridy = 2;
        inputPanel.add(new JLabel("Course:"), gbc);
        gbc.gridx = 1;
        cbCourse = new JComboBox<>(new String[]{
                "Computer Science", "Electronics", "Mechanical",
                "Civil", "Information Technology", "Electrical"});
        inputPanel.add(cbCourse, gbc);

        // Email
        gbc.gridx = 2; gbc.gridy = 2;
        inputPanel.add(new JLabel("Email:"), gbc);
        gbc.gridx = 3;
        txtEmail = new JTextField(15);
        inputPanel.add(txtEmail, gbc);

        // Phone
        gbc.gridx = 0; gbc.gridy = 3;
        inputPanel.add(new JLabel("Phone:"), gbc);
        gbc.gridx = 1;
        txtPhone = new JTextField(15);
        inputPanel.add(txtPhone, gbc);

        // Address
        gbc.gridx = 2; gbc.gridy = 3;
        inputPanel.add(new JLabel("Address:"), gbc);
        gbc.gridx = 3;
        gbc.gridheight = 2;
        txtAddress = new JTextArea(3, 15);
        txtAddress.setLineWrap(true);
        JScrollPane scrollPane = new JScrollPane(txtAddress);
        inputPanel.add(scrollPane, gbc);

        add(inputPanel, BorderLayout.NORTH);
    }

    // Create table panel to display students
    private void createTablePanel() {
        JPanel tablePanel = new JPanel(new BorderLayout());
        tablePanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(Color.GREEN, 2),
                "Student Records",
                0, 0, new Font("Arial", Font.BOLD, 14), Color.GREEN));

        // Create table
        String[] columns = {"Roll No", "Name", "Age", "Gender", "Course", "Email", "Phone", "Address"};
        tableModel = new DefaultTableModel(columns, 0);
        studentTable = new JTable(tableModel);
        studentTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        studentTable.setRowHeight(25);
        studentTable.getTableHeader().setBackground(new Color(70, 130, 180));
        studentTable.getTableHeader().setForeground(Color.WHITE);
        studentTable.getTableHeader().setFont(new Font("Arial", Font.BOLD, 12));

        JScrollPane scrollPane = new JScrollPane(studentTable);
        tablePanel.add(scrollPane, BorderLayout.CENTER);

        add(tablePanel, BorderLayout.CENTER);
    }

    // Create button panel
    private void createButtonPanel() {
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 10));
        buttonPanel.setBackground(new Color(245, 245, 245));

        JButton btnAdd = createButton("Add Student", Color.GREEN);
        JButton btnUpdate = createButton("Update", Color.ORANGE);
        JButton btnDelete = createButton("Delete", Color.RED);
        JButton btnClear = createButton("Clear Fields", Color.BLUE);
        JButton btnDisplay = createButton("Display All", Color.MAGENTA);

        // Add action listeners
        btnAdd.addActionListener(e -> addStudent());
        btnUpdate.addActionListener(e -> updateStudent());
        btnDelete.addActionListener(e -> deleteStudent());
        btnClear.addActionListener(e -> clearFields());
        btnDisplay.addActionListener(e -> displayAllStudents());

        buttonPanel.add(btnAdd);
        buttonPanel.add(btnUpdate);
        buttonPanel.add(btnDelete);
        buttonPanel.add(btnClear);
        buttonPanel.add(btnDisplay);

        add(buttonPanel, BorderLayout.SOUTH);
    }

    // Helper method to create styled buttons
    private JButton createButton(String text, Color color) {
        JButton button = new JButton(text);
        button.setBackground(color);
        button.setForeground(Color.WHITE);
        button.setFocusPainted(false);
        button.setFont(new Font("Arial", Font.BOLD, 12));
        button.setBorder(BorderFactory.createRaisedBevelBorder());
        button.setPreferredSize(new Dimension(130, 35));
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        return button;
    }

    // Add student
    private void addStudent() {
        if (validateInput()) {
            Student student = new Student(
                    txtRollNo.getText().trim(),
                    txtName.getText().trim(),
                    Integer.parseInt(txtAge.getText().trim()),
                    (String) cbGender.getSelectedItem(),
                    (String) cbCourse.getSelectedItem(),
                    txtEmail.getText().trim(),
                    txtPhone.getText().trim(),
                    txtAddress.getText().trim()
            );

            studentList.add(student);
            addToTable(student);
            clearFields();
            JOptionPane.showMessageDialog(this, "Student added successfully!",
                    "Success", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    // Update student
    private void updateStudent() {
        int selectedRow = studentTable.getSelectedRow();
        if (selectedRow == -1) {
            JOptionPane.showMessageDialog(this, "Please select a student to update!",
                    "Warning", JOptionPane.WARNING_MESSAGE);
            return;
        }

        if (validateInput()) {
            Student student = studentList.get(selectedRow);
            student.rollNo = txtRollNo.getText().trim();
            student.name = txtName.getText().trim();
            student.age = Integer.parseInt(txtAge.getText().trim());
            student.gender = (String) cbGender.getSelectedItem();
            student.course = (String) cbCourse.getSelectedItem();
            student.email = txtEmail.getText().trim();
            student.phone = txtPhone.getText().trim();
            student.address = txtAddress.getText().trim();

            updateTableRow(selectedRow, student);
            clearFields();
            JOptionPane.showMessageDialog(this, "Student updated successfully!",
                    "Success", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    // Delete student
    private void deleteStudent() {
        int selectedRow = studentTable.getSelectedRow();
        if (selectedRow == -1) {
            JOptionPane.showMessageDialog(this, "Please select a student to delete!",
                    "Warning", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(this,
                "Are you sure you want to delete this student?",
                "Confirm Delete", JOptionPane.YES_NO_OPTION);

        if (confirm == JOptionPane.YES_OPTION) {
            studentList.remove(selectedRow);
            tableModel.removeRow(selectedRow);
            clearFields();
            JOptionPane.showMessageDialog(this, "Student deleted successfully!",
                    "Success", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    // Display all students
    private void displayAllStudents() {
        if (studentList.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No students to display!",
                    "Information", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        StringBuilder info = new StringBuilder("Total Students: " + studentList.size() + "\n\n");
        for (int i = 0; i < studentList.size(); i++) {
            Student s = studentList.get(i);
            info.append("Student ").append(i + 1).append(":\n");
            info.append(s.toString()).append("\n\n");
        }

        JTextArea textArea = new JTextArea(info.toString());
        textArea.setEditable(false);
        textArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        JScrollPane scrollPane = new JScrollPane(textArea);
        scrollPane.setPreferredSize(new Dimension(500, 400));

        JOptionPane.showMessageDialog(this, scrollPane,
                "All Students", JOptionPane.INFORMATION_MESSAGE);
    }

    // Clear all input fields
    private void clearFields() {
        txtRollNo.setText("");
        txtName.setText("");
        txtAge.setText("");
        txtEmail.setText("");
        txtPhone.setText("");
        txtAddress.setText("");
        cbGender.setSelectedIndex(0);
        cbCourse.setSelectedIndex(0);
        studentTable.clearSelection();
    }

    // Validate input
    private boolean validateInput() {
        if (txtRollNo.getText().trim().isEmpty() ||
                txtName.getText().trim().isEmpty() ||
                txtAge.getText().trim().isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Please fill in all required fields (Roll No, Name, Age)!",
                    "Validation Error", JOptionPane.ERROR_MESSAGE);
            return false;
        }

        try {
            int age = Integer.parseInt(txtAge.getText().trim());
            if (age < 1 || age > 100) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this,
                    "Please enter a valid age (1-100)!",
                    "Validation Error", JOptionPane.ERROR_MESSAGE);
            return false;
        }

        return true;
    }

    // Add student to table
    private void addToTable(Student student) {
        Object[] row = {
                student.rollNo, student.name, student.age, student.gender,
                student.course, student.email, student.phone, student.address
        };
        tableModel.addRow(row);
    }

    // Update table row
    private void updateTableRow(int row, Student student) {
        tableModel.setValueAt(student.rollNo, row, 0);
        tableModel.setValueAt(student.name, row, 1);
        tableModel.setValueAt(student.age, row, 2);
        tableModel.setValueAt(student.gender, row, 3);
        tableModel.setValueAt(student.course, row, 4);
        tableModel.setValueAt(student.email, row, 5);
        tableModel.setValueAt(student.phone, row, 6);
        tableModel.setValueAt(student.address, row, 7);
    }

    // Student class
    class Student {
        String rollNo, name, gender, course, email, phone, address;
        int age;

        Student(String rollNo, String name, int age, String gender,
                String course, String email, String phone, String address) {
            this.rollNo = rollNo;
            this.name = name;
            this.age = age;
            this.gender = gender;
            this.course = course;
            this.email = email;
            this.phone = phone;
            this.address = address;
        }

        @Override
        public String toString() {
            return "Roll No: " + rollNo + "\n" +
                    "Name: " + name + "\n" +
                    "Age: " + age + "\n" +
                    "Gender: " + gender + "\n" +
                    "Course: " + course + "\n" +
                    "Email: " + email + "\n" +
                    "Phone: " + phone + "\n" +
                    "Address: " + address;
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new StudentInformationFrame());
    }
}
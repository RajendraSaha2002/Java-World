import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.io.*;
public class StudentManagementSystem extends JFrame{
    private ArrayList<Student> students = new ArrayList<>();
    private DefaultTableModel tableModel;
    private JTable studentTable;
    private JTextField txtId, txtName, txtAge, txtEmail, txtPhone, txtCourse;
    private JTextField txtSearchId;

    public StudentManagementSystem() {
        setTitle("Student Management System");
        setSize(1000, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        // Load existing data
        loadStudents();

        // Create main panel
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Title Panel
        JPanel titlePanel = new JPanel();
        titlePanel.setBackground(new Color(41, 128, 185));
        JLabel titleLabel = new JLabel("Student Management System");
        titleLabel.setFont(new Font("Arial", Font.BOLD, 24));
        titleLabel.setForeground(Color.WHITE);
        titlePanel.add(titleLabel);

        // Input Panel
        JPanel inputPanel = createInputPanel();

        // Button Panel
        JPanel buttonPanel = createButtonPanel();

        // Table Panel
        JPanel tablePanel = createTablePanel();

        // Search Panel
        JPanel searchPanel = createSearchPanel();

        // Combine input and button panels
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.add(inputPanel, BorderLayout.CENTER);
        topPanel.add(buttonPanel, BorderLayout.SOUTH);

        // Add all panels to main panel
        mainPanel.add(titlePanel, BorderLayout.NORTH);
        mainPanel.add(topPanel, BorderLayout.WEST);
        mainPanel.add(tablePanel, BorderLayout.CENTER);
        mainPanel.add(searchPanel, BorderLayout.SOUTH);

        add(mainPanel);
        refreshTable();
    }

    private JPanel createInputPanel() {
        JPanel panel = new JPanel(new GridLayout(7, 2, 5, 10));
        panel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(52, 73, 94), 2),
                "Student Information",
                0, 0, new Font("Arial", Font.BOLD, 14)
        ));
        panel.setPreferredSize(new Dimension(350, 0));

        txtId = new JTextField();
        txtName = new JTextField();
        txtAge = new JTextField();
        txtEmail = new JTextField();
        txtPhone = new JTextField();
        txtCourse = new JTextField();

        panel.add(new JLabel("Student ID:"));
        panel.add(txtId);
        panel.add(new JLabel("Name:"));
        panel.add(txtName);
        panel.add(new JLabel("Age:"));
        panel.add(txtAge);
        panel.add(new JLabel("Email:"));
        panel.add(txtEmail);
        panel.add(new JLabel("Phone:"));
        panel.add(txtPhone);
        panel.add(new JLabel("Course:"));
        panel.add(txtCourse);

        return panel;
    }

    private JPanel createButtonPanel() {
        JPanel panel = new JPanel(new GridLayout(1, 4, 10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JButton btnAdd = createStyledButton("Add", new Color(46, 204, 113));
        JButton btnUpdate = createStyledButton("Update", new Color(52, 152, 219));
        JButton btnDelete = createStyledButton("Delete", new Color(231, 76, 60));
        JButton btnClear = createStyledButton("Clear", new Color(149, 165, 166));

        btnAdd.addActionListener(e -> addStudent());
        btnUpdate.addActionListener(e -> updateStudent());
        btnDelete.addActionListener(e -> deleteStudent());
        btnClear.addActionListener(e -> clearFields());

        panel.add(btnAdd);
        panel.add(btnUpdate);
        panel.add(btnDelete);
        panel.add(btnClear);

        return panel;
    }

    private JButton createStyledButton(String text, Color color) {
        JButton button = new JButton(text);
        button.setBackground(color);
        button.setForeground(Color.WHITE);
        button.setFocusPainted(false);
        button.setFont(new Font("Arial", Font.BOLD, 12));
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        return button;
    }

    private JPanel createTablePanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(52, 73, 94), 2),
                "Student Records",
                0, 0, new Font("Arial", Font.BOLD, 14)
        ));

        String[] columns = {"ID", "Name", "Age", "Email", "Phone", "Course"};
        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        studentTable = new JTable(tableModel);
        studentTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        studentTable.setRowHeight(25);
        studentTable.getTableHeader().setFont(new Font("Arial", Font.BOLD, 12));
        studentTable.getTableHeader().setBackground(new Color(52, 73, 94));
        studentTable.getTableHeader().setForeground(Color.WHITE);

        studentTable.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                int row = studentTable.getSelectedRow();
                if (row != -1) {
                    txtId.setText(tableModel.getValueAt(row, 0).toString());
                    txtName.setText(tableModel.getValueAt(row, 1).toString());
                    txtAge.setText(tableModel.getValueAt(row, 2).toString());
                    txtEmail.setText(tableModel.getValueAt(row, 3).toString());
                    txtPhone.setText(tableModel.getValueAt(row, 4).toString());
                    txtCourse.setText(tableModel.getValueAt(row, 5).toString());
                }
            }
        });

        JScrollPane scrollPane = new JScrollPane(studentTable);
        panel.add(scrollPane, BorderLayout.CENTER);

        return panel;
    }

    private JPanel createSearchPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));
        panel.setBorder(BorderFactory.createTitledBorder("Search Student"));

        panel.add(new JLabel("Search by ID:"));
        txtSearchId = new JTextField(15);
        panel.add(txtSearchId);

        JButton btnSearch = createStyledButton("Search", new Color(155, 89, 182));
        btnSearch.addActionListener(e -> searchStudent());
        panel.add(btnSearch);

        JButton btnShowAll = createStyledButton("Show All", new Color(52, 73, 94));
        btnShowAll.addActionListener(e -> refreshTable());
        panel.add(btnShowAll);

        return panel;
    }

    private void addStudent() {
        try {
            String id = txtId.getText().trim();
            String name = txtName.getText().trim();
            int age = Integer.parseInt(txtAge.getText().trim());
            String email = txtEmail.getText().trim();
            String phone = txtPhone.getText().trim();
            String course = txtCourse.getText().trim();

            if (id.isEmpty() || name.isEmpty() || email.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Please fill all required fields!",
                        "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            // Check if ID already exists
            for (Student s : students) {
                if (s.getId().equals(id)) {
                    JOptionPane.showMessageDialog(this, "Student ID already exists!",
                            "Error", JOptionPane.ERROR_MESSAGE);
                    return;
                }
            }

            Student student = new Student(id, name, age, email, phone, course);
            students.add(student);
            saveStudents();
            refreshTable();
            clearFields();

            JOptionPane.showMessageDialog(this, "Student added successfully!",
                    "Success", JOptionPane.INFORMATION_MESSAGE);

        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Please enter valid age!",
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void updateStudent() {
        try {
            String id = txtId.getText().trim();

            if (id.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Please select a student to update!",
                        "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            for (int i = 0; i < students.size(); i++) {
                if (students.get(i).getId().equals(id)) {
                    String name = txtName.getText().trim();
                    int age = Integer.parseInt(txtAge.getText().trim());
                    String email = txtEmail.getText().trim();
                    String phone = txtPhone.getText().trim();
                    String course = txtCourse.getText().trim();

                    students.set(i, new Student(id, name, age, email, phone, course));
                    saveStudents();
                    refreshTable();
                    clearFields();

                    JOptionPane.showMessageDialog(this, "Student updated successfully!",
                            "Success", JOptionPane.INFORMATION_MESSAGE);
                    return;
                }
            }

            JOptionPane.showMessageDialog(this, "Student not found!",
                    "Error", JOptionPane.ERROR_MESSAGE);

        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Please enter valid age!",
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void deleteStudent() {
        String id = txtId.getText().trim();

        if (id.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please select a student to delete!",
                    "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(this,
                "Are you sure you want to delete this student?",
                "Confirm Delete", JOptionPane.YES_NO_OPTION);

        if (confirm == JOptionPane.YES_OPTION) {
            for (int i = 0; i < students.size(); i++) {
                if (students.get(i).getId().equals(id)) {
                    students.remove(i);
                    saveStudents();
                    refreshTable();
                    clearFields();

                    JOptionPane.showMessageDialog(this, "Student deleted successfully!",
                            "Success", JOptionPane.INFORMATION_MESSAGE);
                    return;
                }
            }

            JOptionPane.showMessageDialog(this, "Student not found!",
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void searchStudent() {
        String searchId = txtSearchId.getText().trim();

        if (searchId.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please enter a Student ID to search!",
                    "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        tableModel.setRowCount(0);
        boolean found = false;

        for (Student s : students) {
            if (s.getId().equals(searchId)) {
                tableModel.addRow(new Object[]{
                        s.getId(), s.getName(), s.getAge(),
                        s.getEmail(), s.getPhone(), s.getCourse()
                });
                found = true;
                break;
            }
        }

        if (!found) {
            JOptionPane.showMessageDialog(this, "Student not found!",
                    "Information", JOptionPane.INFORMATION_MESSAGE);
            refreshTable();
        }
    }

    private void refreshTable() {
        tableModel.setRowCount(0);
        for (Student s : students) {
            tableModel.addRow(new Object[]{
                    s.getId(), s.getName(), s.getAge(),
                    s.getEmail(), s.getPhone(), s.getCourse()
            });
        }
        txtSearchId.setText("");
    }

    private void clearFields() {
        txtId.setText("");
        txtName.setText("");
        txtAge.setText("");
        txtEmail.setText("");
        txtPhone.setText("");
        txtCourse.setText("");
        studentTable.clearSelection();
    }

    private void saveStudents() {
        try (ObjectOutputStream oos = new ObjectOutputStream(
                new FileOutputStream("students.dat"))) {
            oos.writeObject(students);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @SuppressWarnings("unchecked")
    private void loadStudents() {
        try (ObjectInputStream ois = new ObjectInputStream(
                new FileInputStream("students.dat"))) {
            students = (ArrayList<Student>) ois.readObject();
        } catch (FileNotFoundException e) {
            students = new ArrayList<>();
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            e.printStackTrace();
        }

        SwingUtilities.invokeLater(() -> {
            StudentManagementSystem sms = new StudentManagementSystem();
            sms.setVisible(true);
        });
    }
}

// Student Class
class Student implements Serializable {
    private static final long serialVersionUID = 1L;
    private String id;
    private String name;
    private int age;
    private String email;
    private String phone;
    private String course;

    public Student(String id, String name, int age, String email, String phone, String course) {
        this.id = id;
        this.name = name;
        this.age = age;
        this.email = email;
        this.phone = phone;
        this.course = course;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public int getAge() { return age; }
    public String getEmail() { return email; }
    public String getPhone() { return phone; }
    public String getCourse() { return course; }
}


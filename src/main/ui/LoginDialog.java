package com.capstone.ui;

import com.capstone.db.AuthManager;
import javax.swing.*;
import java.awt.*;

public class LoginDialog extends JDialog {

    private final AuthManager authManager = new AuthManager();
    private int    loggedInUserId   = -1;
    private String loggedInUsername = null;

    public LoginDialog(Frame owner) {
        super(owner, "Stock Trading App — Sign In", true);
        setSize(380, 280); setLocationRelativeTo(owner);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE); setResizable(false);
        buildUI();
    }

    private void buildUI() {
        JPanel root = new JPanel(new BorderLayout());
        JLabel title = new JLabel("Stock Trading App", SwingConstants.CENTER);
        title.setFont(new Font("SansSerif", Font.BOLD, 18));
        title.setBorder(BorderFactory.createEmptyBorder(20, 0, 4, 0));
        root.add(title, BorderLayout.NORTH);

        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(16, 32, 8, 32));
        GridBagConstraints gc = new GridBagConstraints();
        gc.fill = GridBagConstraints.HORIZONTAL; gc.insets = new Insets(5, 4, 5, 4);

        JTextField     usernameField = new JTextField(18);
        JPasswordField passField     = new JPasswordField(18);
        JLabel         statusLabel   = new JLabel(" ", SwingConstants.CENTER);
        statusLabel.setForeground(Color.RED);

        gc.gridx = 0; gc.gridy = 0; gc.weightx = 0; form.add(new JLabel("Username:"), gc);
        gc.gridx = 1; gc.weightx = 1; form.add(usernameField, gc);
        gc.gridx = 0; gc.gridy = 1; gc.weightx = 0; form.add(new JLabel("Password:"), gc);
        gc.gridx = 1; gc.weightx = 1; form.add(passField, gc);
        root.add(form, BorderLayout.CENTER);

        JPanel south = new JPanel(new BorderLayout());
        south.setBorder(BorderFactory.createEmptyBorder(0, 32, 16, 32));
        JPanel btnRow = new JPanel(new GridLayout(1, 2, 10, 0));
        JButton loginBtn = new JButton("Login"); JButton registerBtn = new JButton("Register");
        btnRow.add(loginBtn); btnRow.add(registerBtn);
        south.add(btnRow, BorderLayout.NORTH); south.add(statusLabel, BorderLayout.SOUTH);
        root.add(south, BorderLayout.SOUTH);

        loginBtn.addActionListener(e -> {
            String user = usernameField.getText().trim();
            String pass = new String(passField.getPassword());
            if (user.isEmpty() || pass.isEmpty()) { statusLabel.setText("Please enter username and password."); return; }
            int uid = authManager.login(user, pass);
            if (uid > 0) { loggedInUserId = uid; loggedInUsername = user; dispose(); }
            else { statusLabel.setText("Invalid username or password."); passField.setText(""); }
        });

        registerBtn.addActionListener(e -> {
            String user = usernameField.getText().trim();
            String pass = new String(passField.getPassword());
            if (user.isEmpty() || pass.isEmpty()) { statusLabel.setText("Please enter username and password."); return; }
            if (pass.length() < 6) { statusLabel.setText("Password must be at least 6 characters."); return; }
            if (authManager.register(user, pass)) { statusLabel.setForeground(new Color(0, 128, 0)); statusLabel.setText("Account created! You can now log in."); }
            else { statusLabel.setForeground(Color.RED); statusLabel.setText("Username already taken."); }
        });

        setContentPane(root);
    }

    public int    getLoggedInUserId()   { return loggedInUserId; }
    public String getLoggedInUsername() { return loggedInUsername; }
}

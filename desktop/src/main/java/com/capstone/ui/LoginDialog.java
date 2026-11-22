package com.capstone.ui;

import com.capstone.db.AuthManager;
import com.capstone.db.PasswordHasher;
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
            if (user.isEmpty() || pass.isEmpty()) { showStatus(statusLabel, "Please enter username and password.", false); return; }
            setBusy(true, loginBtn, registerBtn);
            showStatus(statusLabel, "Signing in…", true);
            UiTasks.run(() -> authManager.login(user, pass),
                uid -> {
                    setBusy(false, loginBtn, registerBtn);
                    if (uid > 0) { loggedInUserId = uid; loggedInUsername = user; dispose(); }
                    else { showStatus(statusLabel, "Invalid username or password.", false); passField.setText(""); }
                },
                err -> {
                    setBusy(false, loginBtn, registerBtn);
                    showStatus(statusLabel, "Can't reach the database. Is MySQL running?", false);
                });
        });

        registerBtn.addActionListener(e -> {
            String user = usernameField.getText().trim();
            String pass = new String(passField.getPassword());
            if (user.isEmpty() || pass.isEmpty()) { showStatus(statusLabel, "Please enter username and password.", false); return; }
            if (pass.length() < 6) { showStatus(statusLabel, "Password must be at least 6 characters.", false); return; }
            if (!PasswordHasher.isAcceptableLength(pass)) { showStatus(statusLabel, "Password is too long (max 72 bytes).", false); return; }
            setBusy(true, loginBtn, registerBtn);
            showStatus(statusLabel, "Creating account…", true);
            UiTasks.run(() -> authManager.register(user, pass),
                result -> {
                    setBusy(false, loginBtn, registerBtn);
                    switch (result) {
                        case OK             -> showStatus(statusLabel, "Account created! You can now log in.", true);
                        case USERNAME_TAKEN -> showStatus(statusLabel, "Username already taken.", false);
                        case INVALID        -> showStatus(statusLabel, "Invalid username or password.", false);
                        case ERROR          -> showStatus(statusLabel, "Can't reach the database. Is MySQL running?", false);
                    }
                },
                err -> {
                    setBusy(false, loginBtn, registerBtn);
                    showStatus(statusLabel, "Registration failed: " + err.getMessage(), false);
                });
        });

        setContentPane(root);
    }

    private static void showStatus(JLabel label, String text, boolean ok) {
        label.setForeground(ok ? new Color(0, 128, 0) : Color.RED);
        label.setText(text);
    }

    private static void setBusy(boolean busy, JButton... buttons) {
        for (JButton b : buttons) b.setEnabled(!busy);
    }

    public int    getLoggedInUserId()   { return loggedInUserId; }
    public String getLoggedInUsername() { return loggedInUsername; }
}

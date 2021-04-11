package vcpkg.installer;

import javax.swing.*;
import java.awt.*;
import java.util.concurrent.atomic.AtomicReference;

public class PackageInfo {
    public JCheckBox getUi() {
        return ui;
    }

    public enum Status {
        INSTALLED,
        NOT_INSTALLED,
        INSTALLING,
        REMOVING,
    }

    private final String name;
    private final String version;
    private final String description;
    private final AtomicReference<Status> status = new AtomicReference<>(Status.NOT_INSTALLED);
    private final JCheckBox ui = new JCheckBox();

    {
        ui.setFont(ui.getFont().deriveFont(Font.BOLD));

        ui.addActionListener(event -> update());

    }

    private void update() {
        switch (status.get()) {
            case INSTALLED:{
                ui.setForeground(Color.RED);
                ui.setText(ui.isSelected() ? "" : "to remove");
                ui.setEnabled(true);
            }
            break;

            case NOT_INSTALLED: {
                ui.setForeground(Color.GREEN);
                ui.setText(ui.isSelected() ? "to install" : "");
                ui.setEnabled(true);
            }
            break;

            case REMOVING: {
                ui.setText("removing");
                ui.setEnabled(false);
            }
            break;

            case INSTALLING: {
                ui.setText("installing");
                ui.setEnabled(false);
            }
            break;
        }
    }

    public PackageInfo(String name, String version, String description, Status status) {
        this.name = name;
        this.version = version;
        this.description = description;
        this.status.set(status);
        ui.setSelected(status == Status.INSTALLED);
        update();
    }

    public String getVersion() {
        return version;
    }

    public String getDescription() {
        return description;
    }

    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        if (version != null) {
            return String.format("%s  (%s)", name, version);
        }
        return name;
    }

    public Status getStatus() {
        return status.get();
    }

    public void setStatus(Status status) {
        this.status.set(status);
        update();
        ui.updateUI();
    }

    public boolean toInstall() {
        return ui.isSelected() && status.get() == Status.NOT_INSTALLED;
    }

    public boolean toRemove() {
        return !ui.isSelected() && status.get() == Status.INSTALLED;
    }

    public boolean installed() {
        return ui.isSelected() && status.get() == Status.INSTALLED;
    }

    public boolean notInstalled() {
        return !ui.isSelected() && status.get() == Status.NOT_INSTALLED;
    }

    public void ensureInstalled() {
        if (!toRemove() && status.get() != Status.REMOVING) {
            setStatus(Status.INSTALLED);
            ui.setSelected(true);
        }
    }

    public void ensureRemoved() {
        if (!toInstall()&& status.get() != Status.INSTALLING) {
            setStatus(Status.NOT_INSTALLED);
            ui.setSelected(false);
        }
    }

}

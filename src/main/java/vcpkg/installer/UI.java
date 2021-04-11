package vcpkg.installer;

import javax.swing.*;
import javax.swing.event.CellEditorListener;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.util.EventObject;
import java.util.List;
import java.util.function.Consumer;

public class UI {
    private final JPanel mainPanel = new JPanel(new GridBagLayout());
    private final JPanel searchTab = new JPanel(new GridBagLayout());
    private final JTextField search = new JTextField();
    private final JTable foundList = new JTable();
    private final JButton installButton = new JButton();
    private final JScrollPane scrollPane1 = new JScrollPane();

    private Storage storage;
    private List<String> names = List.of();

    {
        setupUI();
        storage = new Storage(() ->
                SwingUtilities.invokeLater(() -> {
                    names = storage.getNames();
                    foundList.revalidate();
                    foundList.updateUI();
                }));
        updateTable(foundList);
    }

    @SuppressWarnings("SameParameterValue")
    private GridBagConstraints contraint(int x, int y, double wx, double wy) {
        var gbc = new GridBagConstraints();
        gbc.gridx = x;
        gbc.gridy = y;
        gbc.weightx = wx;
        gbc.weighty = wy;
        gbc.fill = GridBagConstraints.BOTH;
        return gbc;
    }

    @SuppressWarnings("SameParameterValue")
    private GridBagConstraints contraint(int x, int y, double wx, double wy, int width) {
        var gbc = new GridBagConstraints();
        gbc.gridx = x;
        gbc.gridy = y;
        gbc.gridwidth = width;
        gbc.gridheight = 1;
        gbc.weightx = wx;
        gbc.weighty = wy;
        gbc.fill = GridBagConstraints.BOTH;
        return gbc;
    }

    private void setupUI() {
        mainPanel.add(searchTab, contraint(0, 0, 1, 1));

        installButton.setText("Update");

        searchTab.add(search, contraint(0, 0, 1, 0.01, 1));

        searchTab.add(scrollPane1);
        searchTab.add(scrollPane1, contraint(0, 1, 1, 0.8, 2));


        searchTab.add(installButton, contraint(1, 0, 0.1, 0.01));

        search.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                foundList.clearSelection();
                storage.searchAction(search.getText());
            }
        });

        installButton.addActionListener(e -> installSelected());
    }

    private void info(String text) {
        JOptionPane.showMessageDialog(this.getRootComponent(), text);
    }

    private void error(String text) {
        JOptionPane.showMessageDialog(this.getRootComponent(), text, "Error", JOptionPane.ERROR_MESSAGE);
    }

    private void installSelected() {
        storage.installAll(
            (name, ok) -> {
                if (ok) {
                    info("package '" + name + "' installed successfully");
                } else {
                    error("An error occurred while installing package '" + name + "'");
                }
            },
            (name, status, str, removeRecursiveOrCancel) -> {
                switch (status) {
                    case OK:
                        info("package '" + name + "' removed successfully");
                        break;
                    case FAIL:
                        error("An error occurred while uninstalling package '" + name + "'");
                        break;
                    case ASK_RECURSIVE:
                        askForRecurseRemoving(str, removeRecursiveOrCancel);
                        break;
                }
            },
            (name, ok) -> {
                if (ok) {
                    info("package '" + name + "' removed successfully");
                } else {
                    error("An error occurred while installing package '" + name + "'");
                }
            }
        );
    }

    private void askForRecurseRemoving(String message, Consumer<Boolean> removeRecursiveOrCancel) {
        final JOptionPane optionPane = new JOptionPane(message,
                JOptionPane.QUESTION_MESSAGE,
                JOptionPane.OK_CANCEL_OPTION);

        var frame = new JFrame("Recurse remove");
        final JDialog dialog = new JDialog(frame, "Recurse remove?", true);

        dialog.setContentPane(optionPane);
        dialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        optionPane.addPropertyChangeListener(e -> {
                    String prop = e.getPropertyName();
                    if (dialog.isVisible() && (e.getSource() == optionPane)
                            && (prop.equals(JOptionPane.VALUE_PROPERTY))) {
                        dialog.setVisible(false);
                    }
                });
        dialog.pack();
        dialog.setVisible(true);

        int value = (Integer) optionPane.getValue();
        if (value == JOptionPane.YES_OPTION) {
            removeRecursiveOrCancel.accept(false);
        } else if (value == JOptionPane.NO_OPTION) {
            dialog.setVisible(false);
            removeRecursiveOrCancel.accept(true);
        }
    }

    private void updateTable(JTable table) {
        table.setModel(new AbstractTableModel() {
            @Override
            public int getRowCount() {
                return names.size();
            }

            @Override
            public int getColumnCount() {
                return 4;
            }

            @Override
            public Object getValueAt(int rowIndex, int columnIndex) {
                var x = storage.get(names.get(rowIndex));
                switch (columnIndex) {
                    case 0: return x.getName();
                    case 1: return x.getVersion();
                    case 2: return x.getDescription();
                    case 3: return x.getUi().isSelected();
                }
                return null;
            }

            @Override
            public Class<?> getColumnClass(int columnIndex) {
                if (columnIndex == 3) return Boolean.class;
                return Object.class;
            }

            @Override
            public boolean isCellEditable(int rowIndex, int columnIndex) {
                return columnIndex == 3;
            }

            @Override
            public String getColumnName(int column) {
                switch (column) {
                    case 0: return "Name";
                    case 1: return "Version";
                    case 2: return "Description";
                    case 3: return "Action";
                }
                return "";
            }
        });

        table.getColumn("Name").setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(
                    JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                var c = (JLabel) super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                c.setFont(c.getFont().deriveFont(Font.BOLD));
                return c;
            }
        });

        table.getColumn("Description").setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(
                    JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                var c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                ((JLabel)c).setToolTipText(value.toString());
                return c;
            }
        });

        table.getColumn("Action").setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(
                    JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                var info = storage.get(names.get(row));
                return info.getUi();
            }
        });

        table.getColumn("Action").setCellEditor(new TableCellEditor() {
            @Override
            public Component getTableCellEditorComponent(
                    JTable table, Object value, boolean isSelected, int row, int column) {
                return storage.get(names.get(row)).getUi();
            }

            public Object getCellEditorValue() { return true; }
            public boolean isCellEditable(EventObject anEvent) { return true; }
            public boolean shouldSelectCell(EventObject anEvent) { return true; }
            public boolean stopCellEditing() { return true; }
            public void cancelCellEditing() { }
            public void addCellEditorListener(CellEditorListener l) { }
            public void removeCellEditorListener(CellEditorListener l) { }
        });

        table.getColumn("Action").setMaxWidth(100);
        table.getColumn("Action").setMinWidth(100);
    }


    {
        scrollPane1.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane1.setViewportView(foundList);
    }

    {
        foundList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        foundList.setShowVerticalLines(true);
        foundList.setShowHorizontalLines(true);
    }


    public JComponent getRootComponent() {
        return mainPanel;
    }
}

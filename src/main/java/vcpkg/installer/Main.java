package vcpkg.installer;

import com.formdev.flatlaf.intellijthemes.materialthemeuilite.FlatMonokaiProContrastIJTheme;

import javax.swing.*;

public class Main {

    public static void main(String[] args) {
        FlatMonokaiProContrastIJTheme.install();
        JFrame jf = new JFrame("VCPKG Installer");
        jf.setSize(800, 600);
        jf.setContentPane(new UI().getRootComponent());
        jf.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        jf.setVisible(true);
    }
}

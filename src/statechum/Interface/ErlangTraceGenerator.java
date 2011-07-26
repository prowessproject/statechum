/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

/*
 * ErlangTraceGenerator.java
 *
 * Created on Apr 18, 2011, 10:17:58 AM
 */
package statechum.Interface;

import java.awt.BorderLayout;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;

import statechum.Configuration;
import statechum.Helper;
import statechum.analysis.Erlang.ErlangLabel;
import statechum.analysis.Erlang.ErlangModule;
import statechum.analysis.learning.ErlangOracleLearner;
import statechum.analysis.learning.ErlangOracleLearner.TraceOutcome;
import statechum.analysis.learning.observers.ProgressDecorator.LearnerEvaluationConfiguration;

import com.ericsson.otp.erlang.OtpErlangTuple;

/**
 *
 * @author ramsay
 */
public class ErlangTraceGenerator extends javax.swing.JFrame {

    /**
	 * Serial ID.
	 */
	private static final long serialVersionUID = -5038890683208421642L;
	protected Set<ErlangLabel> alphabet;
    protected ErlangModule module;
    protected String wrapper;

    public void setAlphabet(Set<ErlangLabel> al) {
        alphabet = al;
        JLabel ta = new JLabel("<html>");
        for (OtpErlangTuple a : alphabet) {
            if (!ta.getText().equals("<html>")) {
                ta.setText(ta.getText() + "<br />");
            }
            ta.setText(ta.getText() + a.toString());
        }
        ta.setText(ta.getText() + "</html>");
        alphabetPane.getViewport().removeAll();
        alphabetPane.getViewport().add(ta, BorderLayout.CENTER);

    }

    public void setModule(ErlangModule mod) {
    	module = mod;
    	setAlphabet(module.behaviour.getAlphabet());
    	this.setTitle(module.name);
    }
    
    /** Creates new form ErlangTraceGenerator */
    public ErlangTraceGenerator() {
        initComponents();
    }

    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jLabel1 = new javax.swing.JLabel();
        alphabetPane = new javax.swing.JScrollPane();
        jSeparator1 = new javax.swing.JSeparator();
        genStyle = new javax.swing.JComboBox();
        jLabel2 = new javax.swing.JLabel();
        jButton1 = new javax.swing.JButton();
        jLabel3 = new javax.swing.JLabel();
        fileNameLabel = new javax.swing.JLabel();
        jButton2 = new javax.swing.JButton();
        useOutputMatchingCheckBox = new javax.swing.JCheckBox();
        useOutputMatchingCheckBox.setSelected(true);
        useOutputMatchingCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
            	useOutputMatchingCheckBoxActionPerformed(evt);
            }
        });
        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);

        jLabel1.setText("Alphabet:");

        genStyle.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "Random (length 3)", "Random (length 25)" }));

        jLabel2.setText("Generation style:");

        jButton1.setText("Generate");
        jButton1.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton1ActionPerformed(evt);
            }
        });

        jLabel3.setText("Output file:");

        jButton2.setText("...");
        jButton2.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton2ActionPerformed(evt);
            }
        });

        useOutputMatchingCheckBox.setText("Use output matching");

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jSeparator1, javax.swing.GroupLayout.DEFAULT_SIZE, 827, Short.MAX_VALUE)
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(alphabetPane, javax.swing.GroupLayout.DEFAULT_SIZE, 807, Short.MAX_VALUE)
                            .addComponent(jLabel1))
                        .addContainerGap())
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(layout.createSequentialGroup()
                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addGroup(layout.createSequentialGroup()
                                        .addComponent(jLabel2)
                                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                        .addComponent(genStyle, 0, 713, Short.MAX_VALUE))
                                    .addGroup(layout.createSequentialGroup()
                                        .addComponent(jLabel3)
                                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                        .addComponent(fileNameLabel, javax.swing.GroupLayout.DEFAULT_SIZE, 654, Short.MAX_VALUE)
                                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                        .addComponent(jButton2)))
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 3, javax.swing.GroupLayout.PREFERRED_SIZE))
                            .addComponent(jButton1, javax.swing.GroupLayout.Alignment.TRAILING))
                        .addContainerGap())
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(useOutputMatchingCheckBox)
                        .addContainerGap(663, Short.MAX_VALUE))))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jLabel1)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(alphabetPane, javax.swing.GroupLayout.PREFERRED_SIZE, 236, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(jSeparator1, javax.swing.GroupLayout.PREFERRED_SIZE, 12, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(useOutputMatchingCheckBox)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 9, Short.MAX_VALUE)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel3)
                    .addComponent(fileNameLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 25, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jButton2))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(genStyle, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel2))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jButton1)
                .addContainerGap())
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents
    protected File outfile;

    private void jButton2ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton2ActionPerformed
        JFileChooser chooser = new JFileChooser();
        chooser.setAcceptAllFileFilterUsed(false);
        int returnValue = chooser.showOpenDialog(null);
        if (returnValue == JFileChooser.APPROVE_OPTION) {
            try {
                fileNameLabel.setText(chooser.getSelectedFile().getCanonicalPath());
                outfile = chooser.getSelectedFile();
            } catch (IOException ex) {
                Logger.getLogger(Start.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }//GEN-LAST:event_jButton2ActionPerformed

    private void jButton1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton1ActionPerformed
        String style = genStyle.getSelectedItem().toString();
        if (outfile == null) {
            JOptionPane.showMessageDialog(this, "No output file specified...");
        } else {
            if (style.equals("Random (length 3)")) {
                genRandom(outfile, 3, 25);
            } else if (style.equals("Random (length 25)")) {
                genRandom(outfile, 25, 25);
            } else {
                Helper.throwUnchecked("Unknown random generation style selected (somehow...)", new RuntimeException("Unknown random generation style selected (somehow...)"));
            }
        }
    }//GEN-LAST:event_jButton1ActionPerformed
    public Process erlangProcess = null;

    /** Generate a trace file with random traces up to the length supplied. */
    private void genRandom(File file, int length, int count) {
        try {
            BufferedWriter out = new BufferedWriter(new FileWriter(file));
            out.write("config erlangSourceFile " + module.sourceFolder + File.separator + module.name + ".erl\n");
            out.write("config labelKind LABEL_ERLANG\n");
            out.write("config erlangModuleName " + module.name + "\n");
        	Configuration config = Configuration.getDefaultConfiguration().copy();
        	if(!useOutputMatchingCheckBox.isSelected()) {
        		config.setUseErlangOutputs(false);
        		out.write("config useErlangOutputs false\n");
        	}
        	config.setErlangModuleName(module.name);
        	config.setErlangSourceFile(module.sourceFolder + File.separator + module.name + ".erl");
        	ErlangOracleLearner learner = new ErlangOracleLearner(this, new LearnerEvaluationConfiguration(config));
            for (int i = 0; i < count; i++) {
                List<ErlangLabel> line = randLine(alphabet, length);
                System.out.println("trying " + line + "...");
                TraceOutcome response = learner.askErlang(line);
                System.out.println("Got: " + response);
                out.write(response.toString() + "\n");
            }
            out.close();
        } catch (IOException e) {
            Helper.throwUnchecked("Error writing traces file", e);
        }
        Traces.main(new String[] {file.getAbsolutePath()});
    }

    /** This needs to use RandomPathGenerator */ 
    private List<ErlangLabel> randLine(Set<ErlangLabel> alphabet, int lenght) {
    	List<ErlangLabel> list = new ArrayList<ErlangLabel>(alphabet.size());list.addAll(alphabet);
        LinkedList<ErlangLabel> result = new LinkedList<ErlangLabel>();
        for(int i = 0; i < lenght; i++) {
            int rand = (int) Math.floor(Math.random() * alphabet.size());
            result.add(list.get(rand));
        }
        return result;
    }

    /**
     * @param args the command line arguments
     */
    public static void main(String args[]) {

        java.awt.EventQueue.invokeLater(new Runnable() {

            @Override
			public void run() {
                new ErlangTraceGenerator().setVisible(true);
            }
        });
    }
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JScrollPane alphabetPane;
    private javax.swing.JLabel fileNameLabel;
    private javax.swing.JComboBox genStyle;
    private javax.swing.JButton jButton1;
    private javax.swing.JButton jButton2;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JSeparator jSeparator1;
    private javax.swing.JCheckBox useOutputMatchingCheckBox;
    // End of variables declaration//GEN-END:variables
    
    protected void useOutputMatchingCheckBoxActionPerformed(java.awt.event.ActionEvent ev) {
    	Configuration config = Configuration.getDefaultConfiguration();
    	if(!useOutputMatchingCheckBox.isSelected()) {
    		config.setUseErlangOutputs(false);
    	} else {
    		config.setUseErlangOutputs(true);
    	}
    	try {
			module = ErlangModule.loadModule(module.sourceFolder + File.separator + module.name + ".erl", config, true);
		} catch (IOException e) {
			e.printStackTrace();
		}
		this.setVisible(false);
    	setAlphabet(module.behaviour.getAlphabet());
		this.setVisible(true);
    }
}

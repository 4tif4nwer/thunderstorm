package cz.cuni.lf1.lge.ThunderSTORM.results;

import cz.cuni.lf1.lge.ThunderSTORM.FormulaParser.FormulaParser;
import cz.cuni.lf1.lge.ThunderSTORM.FormulaParser.FormulaParserException;
import cz.cuni.lf1.lge.ThunderSTORM.FormulaParser.SyntaxTree.Node;
import cz.cuni.lf1.lge.ThunderSTORM.FormulaParser.SyntaxTree.RetVal;
import cz.cuni.lf1.lge.ThunderSTORM.UI.GUI;
import cz.cuni.lf1.lge.ThunderSTORM.UI.Help;
import cz.cuni.lf1.lge.ThunderSTORM.estimators.PSF.MoleculeDescriptor.Units;
import static cz.cuni.lf1.lge.ThunderSTORM.estimators.PSF.PSFModel.Params.LABEL_X;
import static cz.cuni.lf1.lge.ThunderSTORM.estimators.PSF.PSFModel.Params.LABEL_Y;
import cz.cuni.lf1.lge.ThunderSTORM.rendering.ui.EmptyRendererUI;
import cz.cuni.lf1.lge.ThunderSTORM.util.GridBagHelper;
import cz.cuni.lf1.lge.thunderstorm.util.macroui.ParameterKey;
import ij.IJ;
import java.awt.Color;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.SwingWorker;

class ResultsFilter extends PostProcessingModule {

    private JTextField filterTextField;
    private JButton applyButton;
    private JButton restrictToROIButton;

    private ParameterKey.String formulaParameter;

    @Override
    public String getMacroName() {
        return "filter";
    }

    @Override
    public String getTabName() {
        return "Filter";
    }

    public ResultsFilter(ResultsTableWindow table, TripleStateTableModel model) {
        this.table = table;
        this.model = model;
        formulaParameter = params.createStringField("formula", null, "");
    }

    public void setFilterFormula(String formula) {
        filterTextField.setText(formula);
    }

    public String getFilterFormula() {
        return filterTextField.getText();
    }

    @Override
    protected JPanel createUIPanel() {
        JPanel filterPanel = new JPanel(new GridBagLayout());
        filterTextField = new JTextField();
        InputListener listener = new InputListener();
        filterTextField.addKeyListener(listener);
        formulaParameter.registerComponent(filterTextField);
        applyButton = new JButton("Apply");
        applyButton.addActionListener(listener);
        restrictToROIButton = new JButton("Restrict to ROI");
        restrictToROIButton.addActionListener(listener);
        filterPanel.add(new JLabel("Filter: "), new GridBagHelper.Builder().gridxy(0, 0).anchor(GridBagConstraints.WEST).build());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1;
        filterPanel.add(filterTextField, new GridBagHelper.Builder().gridxy(0, 1).fill(GridBagConstraints.HORIZONTAL).weightx(1).build());
        filterPanel.add(Help.createHelpButton(getClass()), new GridBagHelper.Builder().gridxy(2, 0).anchor(GridBagConstraints.EAST).build());
        filterPanel.add(applyButton, new GridBagHelper.Builder().gridxy(1, 1).build());
        filterPanel.add(restrictToROIButton, new GridBagHelper.Builder().gridxy(2, 1).build());
        return filterPanel;
    }

    @Override
    protected void runImpl() {
        final String filterText = formulaParameter.getValue();
        if((!applyButton.isEnabled()) || (filterText == null) || ("".equals(filterText))) {
            return;
        }
        filterTextField.setBackground(Color.WHITE);
        GUI.closeBalloonTip();
        try {
            applyButton.setEnabled(false);
            saveStateForUndo(FilteringOperation.class);
            final int all = model.getRowCount();
            new SwingWorker() {

                @Override
                protected Object doInBackground() throws Exception {
                    applyToModel(model, filterText);
                    return null;
                }

                @Override
                protected void done() {
                    try {
                        get();  // throws an exception if doInBackground hasn't finished succesfully
                        int filtered = all - model.getRowCount();
                        ResultsFilter.this.addOperationToHistory(new FilteringOperation(filterText));
                        String be = ((filtered > 1) ? "were" : "was");
                        String item = ((all > 1) ? "items" : "item");
                        table.setStatus(filtered + " out of " + all + " " + item + " " + be + " filtered out");
                        // since filtering can significantly change dimensions of the image,
                        // force a new renderer independent of the analyzed image
                        table.showPreview(null, true);
                    } catch(ExecutionException ex) {
                        filterTextField.setBackground(new Color(255, 200, 200));
                        GUI.showBalloonTip(filterTextField, ex.getCause().getMessage());
                    } catch(Exception ex) {
                        IJ.handleException(ex);
                    } finally {
                        applyButton.setEnabled(true);
                    }
                }
            }.execute();
        } catch(Exception ex) {
            IJ.handleException(ex);
            filterTextField.setBackground(new Color(255, 200, 200));
            GUI.showBalloonTip(filterTextField, ex.toString());
        }
    }
    
    public void restrictToROIFilter() {
        Rectangle roi = IJ.getImage().getProcessor().getRoi();
        double mag = new EmptyRendererUI().magnification.getValue();
        IJResultsTable rt = IJResultsTable.getResultsTable();
        Units ux = rt.getColumnUnits(LABEL_X);
        Units uy = rt.getColumnUnits(LABEL_Y);
        //
        double leftVal   = Units.PIXEL.convertTo(ux, Math.ceil(roi.getMinX() / mag));
        double rightVal  = Units.PIXEL.convertTo(ux, Math.ceil(roi.getMaxX() / mag));
        double topVal    = Units.PIXEL.convertTo(uy, Math.ceil(roi.getMinY() / mag));
        double bottomVal = Units.PIXEL.convertTo(uy, Math.ceil(roi.getMaxY() / mag));
        //
        rt.addNewFilter("x", leftVal, rightVal);
        rt.addNewFilter("y", topVal, bottomVal);
    }

    private void applyToModel(GenericTableModel model, String text) {
        boolean[] results = new boolean[model.getRowCount()];
        if(text.isEmpty()) {
            Arrays.fill(results, true);
        } else {
            Node tree = new FormulaParser(text, FormulaParser.FORMULA_RESULTS_FILTER).parse();
            tree.semanticScan();
            RetVal retval = tree.eval(null);
            if(!retval.isVector()) {
                throw new FormulaParserException("Semantic error: result of filtering formula must be a vector of boolean values!");
            }
            Double[] res = (Double[]) retval.get();
            for(int i = 0; i < res.length; i++) {
                results[i] = (res[i].doubleValue() != 0.0);
            }
            model.filterRows(results);
        }
    }

    class FilteringOperation extends OperationsHistoryPanel.Operation {

        final String name = "Filtering";
        String filterText;

        public FilteringOperation(String filterText) {
            this.filterText = filterText;
        }

        @Override
        protected String getName() {
            return name;
        }

        @Override
        protected boolean isUndoAble() {
            return true;
        }

        @Override
        protected void clicked() {
            if(uiPanel.getParent() instanceof JTabbedPane) {
                JTabbedPane tabbedPane = (JTabbedPane) uiPanel.getParent();
                tabbedPane.setSelectedComponent(uiPanel);
            }
            filterTextField.setText(filterText);
        }

        @Override
        protected void undo() {
            model.swapUndoAndActual();
            table.setStatus("Filtering: Undo.");
            table.showPreview();
        }

        @Override
        protected void redo() {
            model.swapUndoAndActual();
            table.setStatus("Filtering: Redo.");
            table.showPreview();
        }
    }

    private class InputListener extends KeyAdapter implements ActionListener {

        @Override
        public void actionPerformed(ActionEvent e) {
            if(e.getSource() == applyButton) {
                run();
            } else if(e.getSource() == restrictToROIButton) {
                restrictToROIFilter();
                run();
            }
        }

        @Override
        public void keyPressed(KeyEvent e) {
            if(e.getKeyCode() == KeyEvent.VK_ENTER) {
                run();
            }
        }
    }
}

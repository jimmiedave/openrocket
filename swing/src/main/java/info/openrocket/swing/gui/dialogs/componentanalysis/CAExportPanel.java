package info.openrocket.swing.gui.dialogs.componentanalysis;

import info.openrocket.core.componentanalysis.CADataBranch;
import info.openrocket.core.componentanalysis.CADataType;
import info.openrocket.core.l10n.Translator;
import info.openrocket.core.rocketcomponent.RocketComponent;
import info.openrocket.core.startup.Application;
import info.openrocket.core.unit.Unit;
import info.openrocket.core.util.StringUtils;
import info.openrocket.swing.gui.components.CsvOptionPanel;
import info.openrocket.swing.gui.util.FileHelper;
import info.openrocket.swing.gui.util.SaveCSVWorker;
import info.openrocket.swing.gui.util.SwingPreferences;
import info.openrocket.swing.gui.widgets.CSVExportPanel;
import info.openrocket.swing.gui.widgets.SaveFileChooser;

import javax.swing.AbstractCellEditor;
import javax.swing.BorderFactory;
import javax.swing.DefaultCellEditor;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class CAExportPanel extends CSVExportPanel<CADataType> {
	private static final long serialVersionUID = 4423905472892675964L;
	private static final Translator trans = Application.getTranslator();
	private static final int FIXED_COMPONENT_COLUMN_WIDTH = 500;

	private static final int OPTION_COMPONENT_ANALYSIS_COMMENTS = 0;
	private static final int OPTION_FIELD_DESCRIPTIONS = 1;

	private final List<Map<RocketComponent, Boolean>> selectedComponents;
	private final ComponentAnalysisPlotExportPanel parent;

	private CAExportPanel(ComponentAnalysisPlotExportPanel parent, CADataType[] types,
						  boolean[] selected, CsvOptionPanel csvOptions, Component... extraComponents) {
		super(types, selected, csvOptions, true, extraComponents);

		this.parent = parent;

		selectedComponents = new ArrayList<>(types.length);
		Map<RocketComponent, Boolean> componentSelectedMap;
		List<RocketComponent> components;
		for (CADataType type : types) {
			components = parent.getComponentsForType(type);
			componentSelectedMap = new HashMap<>(components.size());
			for (int i = 0; i < components.size(); i++) {
				// Select the first component by default
				componentSelectedMap.put(components.get(i), i == 0);
			}
			selectedComponents.add(componentSelectedMap);
		}

		// Set row heights dynamically
		for (int row = 0; row < table.getRowCount(); row++) {
			int numComponents = ((Map<?, ?>) table.getValueAt(row, 3)).size();
			double correctNumComponents = Math.ceil(numComponents / 3.0);  // 3 components per row
			double height = Math.round(correctNumComponents * 25) + 10;  // 25 pixels per component + 10 pixel margin
			int rowHeight = Math.max(table.getRowHeight(), (int) height);
			table.setRowHeight(row, rowHeight);
		}
	}

	public static CAExportPanel create(ComponentAnalysisPlotExportPanel parent, CADataType[] types) {
		boolean[] selected = new boolean[types.length];
		for (int i = 0; i < types.length; i++) {
			selected[i] = ((SwingPreferences) Application.getPreferences()).isComponentAnalysisDataTypeExportSelected(types[i]);
		}

		CsvOptionPanel csvOptions = new CsvOptionPanel(CAExportPanel.class, false,
				trans.get("CAExportPanel.checkbox.Includecadesc"),
				trans.get("CAExportPanel.checkbox.ttip.Includecadesc"),
				trans.get("SimExpPan.checkbox.Includefielddesc"),
				trans.get("SimExpPan.checkbox.ttip.Includefielddesc"));

		return new CAExportPanel(parent, types, selected, csvOptions);
	}

	protected void initializeTable(CADataType[] types) {
		super.initializeTable(types);

		// Set custom renderers for each column
		TableColumn firstColumn = table.getColumnModel().getColumn(0);
		firstColumn.setCellRenderer(new CheckBoxRenderer());
		firstColumn.setCellEditor(new CheckBoxEditor());
		table.getColumnModel().getColumn(1).setCellRenderer(new LeftAlignedRenderer());
		table.getColumnModel().getColumn(2).setCellRenderer(new LeftAlignedRenderer());

		ComponentCheckBoxPanel.ComponentSelectionListener listener = (newStates) -> {
			int row = table.getSelectedRow();
			if (row != -1) {
				selectedComponents.set(row, newStates);
			}
		};

		TableColumn componentColumn = table.getColumnModel().getColumn(3);
		componentColumn.setCellRenderer(new ComponentCheckBoxRenderer(listener));
		componentColumn.setCellEditor(new ComponentCheckBoxEditor(listener));
		componentColumn.setPreferredWidth(FIXED_COMPONENT_COLUMN_WIDTH);

		// Set specific client properties for FlatLaf
		table.setShowHorizontalLines(true);
	}

	@Override
	public boolean doExport() {
		CADataBranch branch = this.parent.runParameterSweep();

		// Check for data types with no selected components
		List<CADataType> typesWithNoComponents = new ArrayList<>();
		for (int i = 0; i < selected.length; i++) {
			if (selected[i]) {
				boolean hasSelectedComponent = selectedComponents.get(i).values().stream().anyMatch(v -> v);
				if (!hasSelectedComponent) {
					typesWithNoComponents.add(types[i]);
				}
			}
		}

		// Show warning dialog if there are data types with no selected components
		if (!typesWithNoComponents.isEmpty()) {
			StringBuilder message = new StringBuilder(trans.get("CAExportPanel.dlg.MissingComponents.txt1"));
			message.append("\n\n");
			for (CADataType type : typesWithNoComponents) {
				message.append("- ").append(StringUtils.removeHTMLTags(type.getName())).append("\n");
			}
			message.append("\n").append(trans.get("CAExportPanel.dlg.MissingComponents.txt2"));

			int result = JOptionPane.showConfirmDialog(
					this,
					message.toString(),
					trans.get("CAExportPanel.dlg.MissingComponents.title"),
					JOptionPane.YES_NO_OPTION,
					JOptionPane.WARNING_MESSAGE
			);

			if (result != JOptionPane.YES_OPTION) {
				return false;
			}
		}

		JFileChooser chooser = new SaveFileChooser();
		chooser.setFileFilter(FileHelper.CSV_FILTER);
		chooser.setCurrentDirectory(((SwingPreferences) Application.getPreferences()).getDefaultDirectory());

		if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION)
			return false;

		File file = chooser.getSelectedFile();
		if (file == null)
			return false;

		file = FileHelper.forceExtension(file, "csv");
		if (!FileHelper.confirmWrite(file, this)) {
			return false;
		}


		String commentChar = csvOptions.getCommentCharacter();
		String fieldSep = csvOptions.getFieldSeparator();
		int decimalPlaces = csvOptions.getDecimalPlaces();
		boolean isExponentialNotation = csvOptions.isExponentialNotation();
		boolean analysisComments = csvOptions.getSelectionOption(OPTION_COMPONENT_ANALYSIS_COMMENTS);
		boolean fieldDescriptions = csvOptions.getSelectionOption(OPTION_FIELD_DESCRIPTIONS);
		csvOptions.storePreferences();

		// Store preferences and export
		int n = 0;
		((SwingPreferences) Application.getPreferences()).setDefaultDirectory(chooser.getCurrentDirectory());
		for (int i = 0; i < selected.length; i++) {
			((SwingPreferences) Application.getPreferences()).setComponentAnalysisExportSelected(types[i], selected[i]);
			if (selected[i])
				n++;
		}

		List<CADataType> fieldTypes = new ArrayList<>();
		List<Unit> fieldUnits = new ArrayList<>();
		Map<CADataType, List<RocketComponent>> components = new HashMap<>();

		// Iterate through the table to get selected items
		for (int i = 0; i < selected.length; i++) {
			if (selected[i]) {
				List<RocketComponent> selectedComponentsList = new ArrayList<>();
				for (Map.Entry<RocketComponent, Boolean> entry : selectedComponents.get(i).entrySet()) {
					if (entry.getValue()) {
						selectedComponentsList.add(entry.getKey());
					}
				}
				if (!selectedComponentsList.isEmpty()) {
					fieldTypes.add(types[i]);
					fieldUnits.add(units[i]);
					components.put(types[i], selectedComponentsList);
				}
			}
		}


		if (fieldSep.equals(SPACE)) {
			fieldSep = " ";
		} else if (fieldSep.equals(TAB)) {
			fieldSep = "\t";
		}


		SaveCSVWorker.exportCAData(file, parent.getParameters(), branch, parent.getSelectedParameter(),
				fieldTypes.toArray(new CADataType[0]), components, fieldUnits.toArray(new Unit[0]), fieldSep,
				decimalPlaces, isExponentialNotation, commentChar, analysisComments,
				fieldDescriptions, SwingUtilities.getWindowAncestor(this));

		return true;
	}

	@Override
	protected CSVExportPanel<CADataType>.SelectionTableModel createTableModel() {
		return new CASelectionTableModel();
	}

	protected class CASelectionTableModel extends SelectionTableModel {
		private static final int COMPONENTS = 3;

		@Override
		public int getColumnCount() {
			return 4;
		}

		@Override
		public String getColumnName(int column) {
			//// Components
			if (column == COMPONENTS) {
				return trans.get("CAExportPanel.Col.Components");
			}
			return super.getColumnName(column);
		}

		@Override
		public Class<?> getColumnClass(int column) {
			//// Components
			if (column == COMPONENTS) {
				return Map.class;
			}
			return super.getColumnClass(column);
		}

		@Override
		public Object getValueAt(int row, int column) {
			if (column == COMPONENTS) {
				return selectedComponents.get(row);
			}
			return super.getValueAt(row, column);
		}

		@Override
		public void setValueAt(Object value, int row, int column) {
			if (column == COMPONENTS) {
				selectedComponents.set(row, (Map<RocketComponent, Boolean>) value);
				fireTableCellUpdated(row, column);
			} else {
				super.setValueAt(value, row, column);
			}
		}

		@Override
		public boolean isCellEditable(int row, int column) {
			if (column == COMPONENTS) {
				return true;
			}
			return super.isCellEditable(row, column);
		}
	}

	private static class CheckBoxRenderer extends JCheckBox implements TableCellRenderer {
		public CheckBoxRenderer() {
			setHorizontalAlignment(JLabel.CENTER);
		}

		@Override
		public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
			setSelected((Boolean) value);
			setBackground(isSelected ? table.getSelectionBackground() : table.getBackground());
			setForeground(isSelected ? table.getSelectionForeground() : table.getForeground());
			setBorder(BorderFactory.createEmptyBorder(5, 0, 0, 0)); // Add top padding
			setVerticalAlignment(JLabel.TOP);
			return this;
		}
	}

	private static class CheckBoxEditor extends DefaultCellEditor {
		public CheckBoxEditor() {
			super(new JCheckBox());
			((JCheckBox)getComponent()).setHorizontalAlignment(JLabel.CENTER);
		}

		@Override
		public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
			Component c = super.getTableCellEditorComponent(table, value, isSelected, row, column);
			if (c instanceof JCheckBox) {
				JCheckBox cb = (JCheckBox) c;
				cb.setVerticalAlignment(JLabel.TOP);
				cb.setBorder(BorderFactory.createEmptyBorder(5, 0, 0, 0));
			}
			return c;
		}
	}

	private static class LeftAlignedRenderer extends DefaultTableCellRenderer {
		public LeftAlignedRenderer() {
			setHorizontalAlignment(JLabel.LEFT);
			setVerticalAlignment(JLabel.TOP);
		}

		@Override
		public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
			Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
			c.setBackground(isSelected ? table.getSelectionBackground() : table.getBackground());
			c.setForeground(isSelected ? table.getSelectionForeground() : table.getForeground());
			((JComponent) c).setBorder(BorderFactory.createEmptyBorder(5, 5, 0, 0)); // Add top and left padding
			return c;
		}
	}

	private static class ComponentCheckBoxRenderer implements TableCellRenderer {
		private ComponentCheckBoxPanel panel;
		private final ComponentCheckBoxPanel.ComponentSelectionListener listener;

		public ComponentCheckBoxRenderer(ComponentCheckBoxPanel.ComponentSelectionListener listener) {
			this.listener = listener;
		}

		@Override
		public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
			if (!(value instanceof Map)) {
				JLabel errorLabel = new JLabel("Invalid data");
				errorLabel.setForeground(Color.RED);
				return errorLabel;
			}

			@SuppressWarnings("unchecked")
			Map<RocketComponent, Boolean> componentMap = (Map<RocketComponent, Boolean>) value;

			if (panel == null) {
				panel = new ComponentCheckBoxPanel(componentMap);
				panel.setComponentSelectionListener(listener);
			} else {
				panel.updateComponentStates(componentMap);
			}

			panel.setBackground(isSelected ? table.getSelectionBackground() : table.getBackground());
			panel.setGridColor(table.getGridColor());
			panel.setSelected(isSelected);

			return panel;
		}
	}

	private static class ComponentCheckBoxPanel extends JPanel {
		private final Map<RocketComponent, JCheckBox> checkBoxMap = new HashMap<>();
		private final AtomicBoolean updatingState = new AtomicBoolean(false);
		private Color gridColor = Color.GRAY;
		private boolean isSelected = false;

		public ComponentCheckBoxPanel(Map<RocketComponent, Boolean> componentMap) {
			setLayout(new GridBagLayout());
			GridBagConstraints gbc = new GridBagConstraints();
			gbc.anchor = GridBagConstraints.NORTHWEST;
			gbc.fill = GridBagConstraints.HORIZONTAL;
			gbc.weightx = 1.0;
			gbc.insets = new Insets(2, 2, 2, 2);

			createCheckBoxes(componentMap, gbc);

			// Add an empty component to push everything to the top-left
			gbc.gridx = 0;
			gbc.gridy = (componentMap.size() + 2) / 3 + 1;
			gbc.weighty = 1.0;
			gbc.fill = GridBagConstraints.BOTH;
			add(new JPanel(), gbc);

			// Ensure at least one checkbox is selected
			if (checkBoxMap.values().stream().noneMatch(JCheckBox::isSelected) && !checkBoxMap.isEmpty()) {
				checkBoxMap.values().iterator().next().setSelected(true);
			}
		}

		private void createCheckBoxes(Map<RocketComponent, Boolean> componentMap, GridBagConstraints gbc) {
			int row = 0;
			int col = 0;
			for (Map.Entry<RocketComponent, Boolean> entry : componentMap.entrySet()) {
				RocketComponent component = entry.getKey();
				Boolean isSelected = entry.getValue();

				// Skip null components
				if (component == null) {
					continue;
				}

				String componentName = component.getName();
				// Use a default name if getName() returns null
				if (componentName == null) {
					componentName = "Unnamed Component";
				}

				JCheckBox checkBox = new JCheckBox(componentName, isSelected != null && isSelected);
				checkBox.setOpaque(false);
				checkBox.setMargin(new Insets(0, 0, 0, 0));
				checkBox.addItemListener(checkBoxListener);
				checkBoxMap.put(component, checkBox);

				gbc.gridx = col;
				gbc.gridy = row;
				add(checkBox, gbc);

				col++;
				if (col > 2) {
					col = 0;
					row++;
				}
			}
		}

		public void updateComponentStates(Map<RocketComponent, Boolean> newStates) {
			updatingState.set(true);
			try {
				for (Map.Entry<RocketComponent, Boolean> entry : newStates.entrySet()) {
					JCheckBox checkBox = checkBoxMap.get(entry.getKey());
					if (checkBox != null) {
						checkBox.setSelected(entry.getValue());
					}
				}
			} finally {
				updatingState.set(false);
			}
		}

		public Map<RocketComponent, Boolean> getComponentStates() {
			return checkBoxMap.entrySet().stream()
					.collect(HashMap::new,
							(m, e) -> m.put(e.getKey(), e.getValue().isSelected()),
							HashMap::putAll);
		}

		public void setGridColor(Color color) {
			this.gridColor = color;
		}

		public void setSelected(boolean selected) {
			this.isSelected = selected;
		}

		@Override
		protected void paintComponent(Graphics g) {
			super.paintComponent(g);

			// Draw the bottom border
			g.setColor(gridColor);
			g.drawLine(0, getHeight() - 1, getWidth(), getHeight() - 1);

			// If selected, draw a slight highlight
			if (isSelected) {
				g.setColor(new Color(0, 0, 255, 30)); // Semi-transparent blue
				g.fillRect(0, 0, getWidth(), getHeight() - 1);
			}
		}

		public interface ComponentSelectionListener {
			void onComponentSelectionChanged(Map<RocketComponent, Boolean> newStates);
		}

		private ComponentSelectionListener listener;

		public void setComponentSelectionListener(ComponentSelectionListener listener) {
			this.listener = listener;
		}

		private final ItemListener checkBoxListener = e -> {
			if (updatingState.get()) return;

			// Remove the check for deselection and forced selection
			if (listener != null) {
				listener.onComponentSelectionChanged(getComponentStates());
			}

			// Notify the table that the value has changed
			firePropertyChange("value", null, getComponentStates());
		};
	}

	private static class ComponentCheckBoxEditor extends AbstractCellEditor implements TableCellEditor {
		private ComponentCheckBoxPanel panel;
		private final ComponentCheckBoxPanel.ComponentSelectionListener listener;

		public ComponentCheckBoxEditor(ComponentCheckBoxPanel.ComponentSelectionListener listener) {
			this.listener = listener;
		}

		@Override
		public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
			if (!(value instanceof Map)) {
				// Return a default component if value is not a Map
				JLabel errorLabel = new JLabel("Invalid data");
				errorLabel.setForeground(Color.RED);
				return errorLabel;
			}

			@SuppressWarnings("unchecked")
			Map<RocketComponent, Boolean> componentMap = (Map<RocketComponent, Boolean>) value;

			if (panel == null) {
				panel = new ComponentCheckBoxPanel(componentMap);
				panel.setComponentSelectionListener(listener);
			} else {
				panel.updateComponentStates(componentMap);
			}

			panel.setBackground(isSelected ? table.getSelectionBackground() : table.getBackground());
			return panel;
		}

		@Override
		public Object getCellEditorValue() {
			return panel.getComponentStates();
		}
	}
}
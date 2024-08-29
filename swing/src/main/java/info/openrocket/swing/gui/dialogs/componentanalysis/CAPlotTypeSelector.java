package info.openrocket.swing.gui.dialogs.componentanalysis;

import info.openrocket.core.componentanalysis.CADataType;
import info.openrocket.core.componentanalysis.CADataTypeGroup;
import info.openrocket.core.rocketcomponent.RocketComponent;
import info.openrocket.core.unit.Unit;
import info.openrocket.core.util.StringUtils;
import info.openrocket.swing.gui.plot.PlotTypeSelector;

import javax.swing.JComboBox;
import javax.swing.JLabel;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemListener;
import java.util.List;

public class CAPlotTypeSelector extends PlotTypeSelector<CADataType, CADataTypeGroup> {
	private final JComboBox<RocketComponent> componentSelector;

	public CAPlotTypeSelector(final ComponentAnalysisPlotExportPanel parent, int plotIndex,
							  CADataType type, Unit unit, int position, List<CADataType> availableTypes,
							  List<RocketComponent> componentsForType, CAPlotConfiguration configuration,
							  RocketComponent selectedComponent) {
		super(plotIndex, type, unit, position, availableTypes, false);

		if (componentsForType.isEmpty()) {
			throw new IllegalArgumentException("No components for type " + type);
		}

		// Component selector
		selectedComponent = selectedComponent != null ? selectedComponent : componentsForType.get(0);
		this.add(new JLabel(trans.get("CAPlotTypeSelector.lbl.component")));
		componentSelector = new JComboBox<>(componentsForType.toArray(new RocketComponent[0]));
		componentSelector.setSelectedItem(selectedComponent);
		configuration.setPlotDataComponent(plotIndex, selectedComponent);
		this.add(componentSelector, "gapright para");

		addRemoveButton();

		typeSelector.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				CADataType type = (CADataType) typeSelector.getSelectedItem();
				List<RocketComponent> componentsForType = parent.getComponentsForType(type);
				componentSelector.removeAllItems();
				for (RocketComponent component : componentsForType) {
					componentSelector.addItem(component);
				}
				componentSelector.setSelectedIndex(0);
				configuration.setPlotDataComponent(plotIndex, (RocketComponent) componentSelector.getSelectedItem());
			}
		});
	}

	public CAPlotTypeSelector(final ComponentAnalysisPlotExportPanel parent, int plotIndex,
							  CADataType type, Unit unit, int position, List<CADataType> availableTypes,
							  List<RocketComponent> componentsForType, CAPlotConfiguration configuration) {
		this(parent, plotIndex, type, unit, position, availableTypes, componentsForType, configuration, null);
	}

	public void addComponentSelectionListener(ItemListener listener) {
		componentSelector.addItemListener(listener);
	}

	public RocketComponent getSelectedComponent() {
		return (RocketComponent) componentSelector.getSelectedItem();
	}

	@Override
	protected String getDisplayString(CADataType item) {
		return StringUtils.removeHTMLTags(item.getName());
	}
}
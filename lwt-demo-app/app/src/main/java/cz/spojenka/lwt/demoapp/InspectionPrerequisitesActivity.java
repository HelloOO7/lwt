package cz.spojenka.lwt.demoapp;

import cz.spojenka.lwt.FeaturePrerequisite;

public class InspectionPrerequisitesActivity extends PrerequisitesActivityBase {

    @Override
    protected void onCreatePrerequisites() {
        bindPrerequisite(FeaturePrerequisite.BLE_HARDWARE, R.string.common_prerequisites_hardware_title, R.string.inspection_prerequisites_hardware_desc, R.string.inspection_prerequisites_hardware_desc);
        bindPrerequisite(FeaturePrerequisite.BLUETOOTH_CLIENT);
        bindPrerequisite(FeaturePrerequisite.FINE_LOCATION);
    }
}

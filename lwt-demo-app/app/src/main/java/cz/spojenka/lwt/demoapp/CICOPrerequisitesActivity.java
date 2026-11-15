package cz.spojenka.lwt.demoapp;

import cz.spojenka.lwt.FeaturePrerequisite;

public class CICOPrerequisitesActivity extends PrerequisitesActivityBase {

    @Override
    protected void onCreatePrerequisites() {
        bindPrerequisite(FeaturePrerequisite.CICO_HARDWARE, R.string.common_prerequisites_hardware_title, R.string.cico_prerequisites_hardware_desc, R.string.cico_prerequisites_hardware_desc);
        bindPrerequisite(FeaturePrerequisite.BLUETOOTH_CLIENT_SERVER);
        bindPrerequisite(FeaturePrerequisite.BLUETOOTH_ON);
        bindPrerequisite(FeaturePrerequisite.LOCATION_FOR_LE_SCAN);
        bindPrerequisite(FeaturePrerequisite.BACKGROUND_LOCATION_FOR_LE_SCAN);
        bindPrerequisite(FeaturePrerequisite.NOTIFICATION_PERMISSION);
        bindPrerequisite(FeaturePrerequisite.BATTERY_EXEMPTION);
    }
}

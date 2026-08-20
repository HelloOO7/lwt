package cz.spojenka.lwt.demoapp;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import com.google.android.material.color.MaterialColors;
import com.google.android.material.divider.MaterialDivider;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

import androidx.activity.result.contract.ActivityResultContract;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import cz.dpp.praguepublictransport.LitackaUtils;
import cz.spojenka.android.ui.activity.SubActivity;
import cz.spojenka.android.ui.dialog.CommonDialogs;
import cz.spojenka.android.ui.resources.ListFormat;
import cz.spojenka.android.util.ViewUtils;
import cz.spojenka.lwt.demoapp.databinding.ActivityLitackaZonePickerBinding;
import cz.spojenka.lwt.demoapp.databinding.LitackaZoneToggleBinding;

public class LitackaZonePickerActivity extends SubActivity {

    public static final String EXTRA_AVAILABLE_ZONES = "available_zones";
    public static final String EXTRA_INITIAL_SELECTION = "initial_selection";
    public static final String EXTRA_MAX_ZONES = "max_zones";

    public static final String RESULT_EXTRA_ZONES = "selected_zones";

    private static final String STATE_SELECTED_ZONES = "selected_zones";

    public static ActivityResultContract<Input, List<String>> PICK_ZONES = new ActivityResultContract<>() {

        @Override
        public List<String> parseResult(int resultCode, @Nullable Intent intent) {
            if (resultCode == RESULT_OK && intent != null) {
                return intent.getStringArrayListExtra(RESULT_EXTRA_ZONES);
            }
            return null;
        }

        @NonNull
        @Override
        public Intent createIntent(@NonNull Context context, Input input) {
            return new Intent(context, LitackaZonePickerActivity.class)
                    .putStringArrayListExtra(EXTRA_AVAILABLE_ZONES, new ArrayList<>(input.availableZones()))
                    .putStringArrayListExtra(EXTRA_INITIAL_SELECTION, new ArrayList<>(input.initialSelection()))
                    .putExtra(EXTRA_MAX_ZONES, input.maxZones());
        }
    };

    private ActivityLitackaZonePickerBinding binding;

    private List<String> availableZones;

    private int maxZones;
    private boolean isPragueTwoZones;

    private List<LitackaZoneToggleBinding> toggles = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        availableZones = LitackaUtils.sortZonesForPrint(Objects.requireNonNullElseGet(intent.getStringArrayListExtra(EXTRA_AVAILABLE_ZONES), List::of));
        maxZones = intent.getIntExtra(EXTRA_MAX_ZONES, availableZones.size());
        isPragueTwoZones = false; // 2026 - one zone

        binding = ActivityLitackaZonePickerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.fabConfirm.setOnClickListener(v -> showWarningOrFinish());

        initZoneUI(getLayoutInflater());

        List<String> selection = null;

        if (savedInstanceState != null) {
            selection = savedInstanceState.getStringArrayList(STATE_SELECTED_ZONES);
        }

        if (selection == null) {
            selection = Objects.requireNonNullElseGet(intent.getStringArrayListExtra(EXTRA_INITIAL_SELECTION), List::of);
        }

        Set<String> selectionSet = Set.copyOf(selection);

        for (LitackaZoneToggleBinding toggle : toggles) {
            setToggleSelectedSilent(toggle, selectionSet.contains(getToggleZoneName(toggle)));
        }

        ensureSelectionContiguous();

        toggles.forEach(toggle -> toggle.cbCheckBox.jumpDrawablesToCurrentState());

        updateSelectableToggles();
        updateConfirmButton();
    }

    private void showWarningOrFinish() {
        ArrayList<String> zones = collectSelectedZones();
        if (zones.size() == maxZones) {
            finishWithResult(zones);
        } else {
            CommonDialogs.newYesNoDialog(
                    this,
                    getString(R.string.warning),
                    getString(
                            R.string.zone_selection_warning_format,
                            maxZones,
                            getResources().getQuantityString(R.plurals.zones_pid_locative, zones.size()),
                            getResources().getQuantityString(R.plurals.zones_pid_accusative, zones.size()), ListFormat.formatList(this, zones)
                    ),
                    (dialog, which) -> finishWithResult(zones),
                    null
            ).show();
        }
    }

    private void finishWithResult(ArrayList<String> result) {
        setResult(RESULT_OK, new Intent().putStringArrayListExtra(RESULT_EXTRA_ZONES, result));
        finish();
    }

    private ArrayList<String> collectSelectedZones() {
        ArrayList<String> selected = new ArrayList<>();
        for (LitackaZoneToggleBinding toggle : toggles) {
            if (toggle.cbCheckBox.isChecked()) {
                selected.add(getToggleZoneName(toggle));
            }
        }
        return selected;
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putStringArrayList(STATE_SELECTED_ZONES, collectSelectedZones());
    }

    private int getToggleZoneIndex(LitackaZoneToggleBinding toggle) {
        return (int) toggle.getRoot().getTag();
    }

    private String getToggleZoneName(LitackaZoneToggleBinding toggle) {
        return availableZones.get(getToggleZoneIndex(toggle));
    }

    private int getZoneSpan(String zoneName) {
        if (isPragueTwoZones) {
            if (LitackaUtils.isInnerPragueZone(zoneName)) {
                return 2;
            }
        }
        return 1;
    }

    private void initZoneUI(LayoutInflater inflater) {
        boolean hasP = false;
        boolean hasForeign = false;
        boolean hasPrague = false;
        boolean hasRegion = false;

        for (String zoneOption : availableZones) {
            hasP |= LitackaUtils.isInnerPragueZone(zoneOption);
            hasForeign |= LitackaUtils.isForeignRegionZone(zoneOption);
            hasPrague |= LitackaUtils.isPragueZone(zoneOption);
            hasRegion |= LitackaUtils.isOuterZone(zoneOption);
            addZoneToggle(inflater, zoneOption);
        }

        binding.cvZonesPrague.setVisibility(hasPrague ? View.VISIBLE : View.GONE);
        binding.cvZonesOuter.setVisibility(hasRegion ? View.VISIBLE : View.GONE);

        binding.tvPragueDoubleZoneNote.setVisibility(hasP && isPragueTwoZones ? View.VISIBLE : View.GONE);
        binding.tvRegionForeignZonesNote.setVisibility(hasForeign ? View.VISIBLE : View.GONE);
    }

    private void addZoneToggle(LayoutInflater inflater, String zoneName) {
        LitackaZoneToggleBinding toggle = LitackaZoneToggleBinding.inflate(inflater);
        toggle.tvZoneLabel.setText(zoneName);
        toggle.getRoot().setTag(toggles.size());
        toggle.getRoot().setOnClickListener(v -> toggle.cbCheckBox.performClick());

        toggle.cbCheckBox.setOnClickListener(v -> { //better than onCheckedChange, as it is not invoked through programmatic changes
            performToggleSelected(toggle, toggle.cbCheckBox.isChecked());
            updateSelectableToggles();
            updateConfirmButton();
        });

        toggles.add(toggle);

        if (LitackaUtils.isPragueZone(zoneName)) {
            addZoneToggleBeforeNote(binding.llPragueItems, toggle);
        } else {
            addZoneToggleBeforeNote(binding.llRegionItems, toggle);
        }
    }

    private int getFirstSelectedIndex() {
        for (int i = 0; i < toggles.size(); i++) {
            if (toggles.get(i).cbCheckBox.isChecked()) {
                return i;
            }
        }
        return -1;
    }

    private int getLastSelectedIndex() {
        for (int i = toggles.size() - 1; i >= 0; i--) {
            if (toggles.get(i).cbCheckBox.isChecked()) {
                return i;
            }
        }
        return -1;
    }

    private boolean isSelectionContiguous() {
        int firstSelected = getFirstSelectedIndex();
        int lastSelected = getLastSelectedIndex();
        if (firstSelected == -1 || lastSelected == -1) {
            return true;
        }

        for (int i = firstSelected; i <= lastSelected; i++) {
            if (!toggles.get(i).cbCheckBox.isChecked()) {
                return false;
            }
        }
        return true;
    }

    private void updateToggleStyleOnSelected(LitackaZoneToggleBinding toggle, boolean selected) {
        ViewUtils.setBold(toggle.tvZoneLabel, selected);
        toggle.tvZoneLabel.setTextColor(MaterialColors.getColor(toggle.getRoot(), selected ? android.R.attr.colorPrimary : android.R.attr.colorControlNormal));
    }

    private void setToggleSelectedSilent(LitackaZoneToggleBinding toggle, boolean selected) {
        toggle.cbCheckBox.setChecked(selected);
        updateToggleStyleOnSelected(toggle, selected);
    }

    private boolean isAnyZoneSelected(Predicate<String> predicate) {
        for (LitackaZoneToggleBinding t : toggles) {
            if (t.cbCheckBox.isChecked() && predicate.test(getToggleZoneName(t))) {
                return true;
            }
        }
        return false;
    }

    private void selectTogglesSilentByPredicate(Predicate<String> zonePredicate, boolean selected) {
        for (LitackaZoneToggleBinding t : toggles) {
            if (zonePredicate.test(getToggleZoneName(t))) {
                setToggleSelectedSilent(t, selected);
            }
        }
    }

    private void performToggleSelected(LitackaZoneToggleBinding toggle, boolean selected) {
        setToggleSelectedSilent(toggle, selected);

        String zoneName = getToggleZoneName(toggle);

        if (selected && LitackaUtils.isInnerPragueZone(zoneName)) {
            selectTogglesSilentByPredicate(LitackaUtils::isPragueZone, true);
        } else if (!selected && LitackaUtils.isPragueZone(zoneName)) {
            //P can not be purchased without 0 and B
            selectTogglesSilentByPredicate(LitackaUtils::isInnerPragueZone, false);
        }

        if (!isSelectionContiguous()) {
            if (!selected) {
                for (int i = getToggleZoneIndex(toggle); i < toggles.size(); ++i) {
                    if (toggles.get(i).cbCheckBox.isChecked()) {
                        setToggleSelectedSilent(toggles.get(i), false);
                    }
                }
            } else {
                ensureSelectionContiguous();
            }
        }
    }

    private void ensureSelectionContiguous() {
        int start = getFirstSelectedIndex();
        int end = getLastSelectedIndex();
        if (start == -1 || end == -1) {
            return;
        }
        for (int i = start; i <= end; ++i) {
            setToggleSelectedSilent(toggles.get(i), true);
        }
    }

    private void updateSelectableToggles() {
        int minSelected = getFirstSelectedIndex();
        int maxSelected = getLastSelectedIndex();
        if (minSelected == -1 || maxSelected == -1) {
            for (LitackaZoneToggleBinding toggle : toggles) {
                setToggleEnabled(toggle, true);
            }
        } else {
            int selectedCount = maxSelected - minSelected + 1;
            int remaining = maxZones - selectedCount;

            if (isPragueTwoZones && isAnyZoneSelected(LitackaUtils::isInnerPragueZone)) {
                --remaining;
            }

            for (int i = 0; i < toggles.size(); ++i) {
                LitackaZoneToggleBinding toggle = toggles.get(i);
                int toggleSpan = getZoneSpan(getToggleZoneName(toggle)) / 2; //in both directions
                int outReach = Math.max(0, remaining - toggleSpan);
                setToggleEnabled(toggle, i >= minSelected - outReach && i <= maxSelected + outReach);
            }
        }
    }

    private void setToggleEnabled(LitackaZoneToggleBinding toggle, boolean enabled) {
        toggle.getRoot().setEnabled(enabled);
        toggle.cbCheckBox.setVisibility(enabled ? View.VISIBLE : View.INVISIBLE);
    }

    private void addZoneToggleBeforeNote(LinearLayout container, LitackaZoneToggleBinding toggle) {
        if (container.getChildCount() > 2) { //title and note
            MaterialDivider divider = new MaterialDivider(this);
            container.addView(divider, container.getChildCount() - 1, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        container.addView(toggle.getRoot(), container.getChildCount() - 1);
    }

    private void updateConfirmButton() {
        boolean anything = isAnyZoneSelected(s -> true);
        if (!anything) {
            binding.fabConfirm.setEnabled(false);
            binding.fabConfirm.setText(R.string.zone_selection_pid_prompt);
        } else {
            binding.fabConfirm.setEnabled(true);

            boolean applicable = isAnyZoneSelected(Predicate.not(LitackaUtils::isForeignRegionZone));
            binding.fabConfirm.setEnabled(applicable);
            binding.fabConfirm.setText(applicable ? R.string.zone_selection_confirm : R.string.zone_selection_not_applicable);
        }
    }

    public static record Input(List<String> availableZones, List<String> initialSelection, int maxZones) {

        public Input(List<String> availableZones, int maxZones) {
            this(availableZones, List.of(), maxZones);
        }
    }
}

package cz.spojenka.lwt.demoapp;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.material.divider.MaterialDivider;
import com.google.android.material.textview.MaterialTextView;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.PluralsRes;
import androidx.annotation.StringRes;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.core.content.res.ResourcesCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import cz.dpp.praguepublictransport.LitackaUtils;
import cz.spojenka.android.ui.activity.BaseActivity;
import cz.spojenka.android.ui.helpers.BasicListAdapter;
import cz.spojenka.android.ui.helpers.FlexBoxGapDrawable;
import cz.spojenka.android.ui.helpers.HorizontalSpaceItemDecoration;
import cz.spojenka.android.ui.helpers.VerticalSpaceItemDecoration;
import cz.spojenka.android.ui.view.LoadingPlaceholderContainer;
import cz.spojenka.android.util.ViewUtils;
import cz.spojenka.lwt.demoapp.databinding.ActivityTicketOffersBinding;
import cz.spojenka.lwt.demoapp.databinding.ProductBundleViewBinding;
import cz.spojenka.lwt.ticketing.api.ProductCategory;
import cz.spojenka.lwt.ticketing.api.ProductResponse;
import cz.spojenka.lwt.ticketing.api.ProductValidityType;

public class TicketOffersActivity extends BaseActivity {

    private static final String TAG = TicketOffersActivity.class.getName();

    private ErrorAndRetryScreen errorAndRetry;
    private LoadingPlaceholderContainer loading;

    private ActivityTicketOffersBinding binding;

    private final List<Object> uiElements = new ArrayList<>();
    private ProductAdapter adapter;

    private Set<String> knownZones = new HashSet<>();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityTicketOffersBinding.inflate(getLayoutInflater());
        initProductListRecyclerView();
        errorAndRetry = new ErrorAndRetryScreen(this, binding.getRoot());
        loading = LoadingPlaceholderContainer.wrapScreens(this, errorAndRetry.getRoot());
        errorAndRetry.setOnRetryListener(this::reload);
        setContentView(loading.getRoot());
        reload();
    }

    private void reload() {
        loading.showLoading();
        var future = TicketingRepository.getInstance(getApplication()).getProducts();
        if (future.isDone() && !future.isCompletedExceptionally()) {
            // prevent flicker
            onProductsReceived(future.join(), null);
        } else {
            future.whenCompleteAsync(this::onProductsReceived, getLifecycleExecutor());
        }
    }

    private void onProductsReceived(List<ProductResponse> products, Throwable error) {
        if (error != null) {
            errorAndRetry.showError(error);
        } else {
            initProductViews(products);
            errorAndRetry.showContent();
        }
        loading.showContent();
    }

    private void initProductViews(List<ProductResponse> products) {
        initKnownZones(products);
        products = new ArrayList<>(products);
        uiElements.clear();
        addProductPanel(R.string.ticket_offer_panel_prague, products, this::isPragueProduct);
        addProductPanel(R.string.ticket_offer_panel_pid_single, products, this::isStandardSinglePIDProduct);
        addProductPanel(R.string.ticket_offer_panel_network, products, this::isNetworkTicket);
        addProductPanel(R.string.ticket_offer_panel_other, products, Predicate.not(this::isExtraProduct));
        addProductPanel(R.string.ticket_offer_panel_extra, products, this::isExtraProduct);
        adapter.notifyDataSetChanged();
    }

    private void initKnownZones(List<ProductResponse> products) {
        knownZones.clear();
        for (ProductResponse p : products) {
            knownZones.addAll(LitackaUtils.parseCommaSeparatedList(p.eligibleZones()));
        }
    }

    private boolean allZonesMatch(ProductResponse p, Predicate<String> pred) {
        if (p.eligibleZones() == null || p.eligibleZones().isEmpty()) {
            return false;
        }
        for (String zone : LitackaUtils.parseCommaSeparatedList(p.eligibleZones())) {
            if (!pred.test(zone)) {
                return false;
            }
        }
        return true;
    }

    private boolean isPragueProduct(ProductResponse p) {
        return allZonesMatch(p, LitackaUtils::isPragueZone) && !isExtraProduct(p);
    }

    private boolean isStandardSinglePIDProduct(ProductResponse p) {
        return p.zoneCount() != null && !isExtraProduct(p);
    }

    private boolean isNetworkTicket(ProductResponse p) {
        return p.zoneCount() == null && !isPragueProduct(p) && !isExtraProduct(p);
    }

    private boolean isExtraProduct(ProductResponse p) {
        return getProductCategoryIfSpecial(p) != null;
    }

    private void addProductPanel(@StringRes int title, List<ProductResponse> products, Predicate<ProductResponse> filter) {
        List<ProductBundle> bundles = createProductBundlesForView(products, filter);
        if (!bundles.isEmpty()) {
            uiElements.add(title);
            uiElements.addAll(bundles);
        }
    }

    private void initProductListRecyclerView() {
        ResponsiveGridRecyclerView productList = binding.rvProductListContent;
        int spacing = ViewUtils.dpToPx(this, 4);
        productList.addItemDecoration(new VerticalSpaceItemDecoration(spacing));
        productList.addItemDecoration(new HorizontalSpaceItemDecoration(spacing));
        GridLayoutManager layoutManager = new GridLayoutManager(this, 2);
        productList.setLayoutManager(layoutManager);
        // resize columns to fit screen
        productList.setMinColumnWidth(getResources().getDimensionPixelSize(R.dimen.ticket_offers_product_cell_width));
        adapter = new ProductAdapter();
        productList.setAdapter(adapter);
        layoutManager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                return adapter.getItemViewType(position) == ProductAdapter.VT_TITLE ? layoutManager.getSpanCount() : 1;
            }
        });
    }

    private List<ProductBundle> createProductBundlesForView(List<ProductResponse> products, Predicate<ProductResponse> filter) {
        Map<ProductBundleKey, ProductBundle> bundles = new HashMap<>();
        List<ProductResponse> productsHandled = new ArrayList<>();
        for (ProductResponse p : products) {
            if (filter.test(p)) {
                ProductBundleKey key = makeProductKey(p);
                bundles.computeIfAbsent(key, k -> new ProductBundle(k, new ArrayList<>())).products().add(p);
                productsHandled.add(p);
            }
        }
        products.removeAll(productsHandled);

        List<ProductBundle> productBundles = new ArrayList<>(bundles.values());
        Collections.sort(productBundles);
        productBundles.forEach(b -> b.products().sort(Comparator.comparing(ProductResponse::price)));
        return productBundles;
    }

    private String adjustEligibleZonesForCPMatch(ProductResponse p) {
        if (p.zoneCount() == null) {
            return p.eligibleZones();
        }
        // can not add prague with less than 3 zones
        if (p.zoneCount() < 3) {
            return p.eligibleZones();
        }
        // not a single ticket
        if (p.validityDuration().toDays() >= 1) {
            return p.eligibleZones();
        }
        // reduced tickets can not be valid in prague, but we want them to be grouped together with adult ones
        String pragueEndZones = "0";
        if (p.category() == ProductCategory.DISABLED) {
            // ZTP has Prague free, so the zones are not included in the tickets
            pragueEndZones = "1";
        }
        Set<String> zones = new HashSet<>(LitackaUtils.parseCommaSeparatedList(p.eligibleZones()));
        if (zones.contains(pragueEndZones) && zones.contains("13")) {
            // add Prague zones so that reduced tickets get grouped with adult tickets
            zones.addAll(List.of("P", "0", "B"));
        }
        return LitackaUtils.createCommaSeparatedList(LitackaUtils.sortZonesForPrint(List.copyOf(zones)));
    }

    private ProductBundleKey makeProductKey(ProductResponse p) {
        return new ProductBundleKey(adjustEligibleZonesForCPMatch(p), p.zoneCount(), p.validityDuration(), p.validityType(), getProductCategoryIfSpecial(p));
    }

    private ProductCategory getProductCategoryIfSpecial(ProductResponse p) {
        return switch (p.category()) {
            case BIKE, DOG, GROUP_3, GROUP_6, CABLE_CAR, LUGGAGE -> p.category();
            default -> null;
        };
    }

    private record ProductBundleKey(
            String applicableZones,
            Integer numZones,
            Duration validity,
            ProductValidityType validityType,
            ProductCategory specialCategory
    ) implements Comparable<ProductBundleKey> {

        public String applicableZonesNullIfEmpty() {
            if (applicableZones == null || applicableZones.isEmpty()) {
                return null;
            }
            return applicableZones;
        }

        private static final Comparator<ProductBundleKey> DEFAULT_COMPARATOR =
                Comparator.comparing(ProductBundleKey::numZones, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(ProductBundleKey::validity)
                        .thenComparing(ProductBundleKey::validityType)
                        .thenComparing(ProductBundleKey::applicableZonesNullIfEmpty, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(ProductBundleKey::specialCategory, Comparator.nullsLast(Comparator.naturalOrder()));

        @Override
        public int compareTo(ProductBundleKey o) {
            return DEFAULT_COMPARATOR.compare(this, o);
        }
    }

    private record ProductBundle(ProductBundleKey key,
                                 List<ProductResponse> products) implements Comparable<ProductBundle> {

        @Override
        public int compareTo(ProductBundle o) {
            return key.compareTo(o.key);
        }

        public List<ProductCategory> getProductCategories() {
            return products.stream().map(ProductResponse::category).distinct().sorted().collect(Collectors.toList());
        }
    }

    private class ProductAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

        public static final int VT_TITLE = 1;
        public static final int VT_PRODUCT = 2;


        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return switch (viewType) {
                case VT_TITLE -> TitleViewHolder.create(parent.getContext());
                case VT_PRODUCT -> new ProductViewHolder(ProductBundleViewBinding.inflate(getLayoutInflater(), parent, false));
                default -> throw new IllegalArgumentException();
            };
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            var el = uiElements.get(position);
            if (el instanceof Integer i) {
                ((TitleViewHolder) holder).bind(i);
            } else if (el instanceof ProductBundle pb) {
                ((ProductViewHolder) holder).bind(pb);
            }
        }

        @Override
        public int getItemViewType(int position) {
            var el = uiElements.get(position);
            if (el instanceof Integer) {
                return VT_TITLE;
            } else {
                return VT_PRODUCT;
            }
        }

        @Override
        public int getItemCount() {
            return uiElements.size();
        }
    }

    private static class TitleViewHolder extends BasicListAdapter.BasicViewHolder<Integer> {

        private final TextView tv;

        public TitleViewHolder(TextView tv) {
            super(tv);
            this.tv = tv;
        }

        public static TitleViewHolder create(Context context) {
            return new TitleViewHolder(createProductPanelTitle(context));
        }

        private static TextView createProductPanelTitle(Context context) {
            TextView title = new MaterialTextView(context);
            title.setTextSize(TypedValue.COMPLEX_UNIT_PX, context.getResources().getDimensionPixelSize(R.dimen.text_title_s));
            ViewUtils.setPaddingDp(title, 0, 8);
            return title;
        }

        @Override
        public void bind(Integer item) {
            tv.setText(item);
        }
    }

    private class ProductViewHolder extends BasicListAdapter.BasicViewHolder<ProductBundle> {

        private final ProductBundleViewBinding binding;

        public ProductViewHolder(ProductBundleViewBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
            int spacing = ViewUtils.dpToPx(binding.getRoot(), 4);
            binding.fblCustomerProfiles.setDividerDrawableHorizontal(FlexBoxGapDrawable.vertical(spacing));
            binding.fblCustomerProfiles.setDividerDrawableVertical(FlexBoxGapDrawable.horizontal(spacing));
        }

        @Override
        public void bind(ProductBundle item) {
            ProductBundleKey key = item.key();
            if (key.numZones() != null) {
                setTextForNumZones(key.numZones());
            } else {
                if (key.applicableZones() == null || key.applicableZones().isEmpty() || Set.copyOf(LitackaUtils.parseCommaSeparatedList(key.applicableZones())).containsAll(knownZones)) {
                    binding.tvZonesTitle.setText(R.string.ticket_offer_network_symbol);
                    binding.tvZonesSubtitle.setText(R.string.ticket_offer_network_all);
                } else {
                    List<String> zonesList = LitackaUtils.parseCommaSeparatedList(key.applicableZones());
                    binding.tvZonesTitle.setText(formatConciseZoneDescription(zonesList));
                    binding.tvZonesSubtitle.setText(itemView.getResources().getQuantityString(R.plurals.zones_pid, 999));
                }
            }
            setTextForValidity(key.validity(), key.validityType());

            binding.fblCustomerProfiles.removeAllViews();
            for (ProductCategory cat : item.getProductCategories()) {
                addProductCatIcon(cat);
            }
        }

        private String formatConciseZoneDescription(List<String> zones) {
            zones = LitackaUtils.sortZonesForPrint(zones);
            if (zones.size() <= 3) {
                return String.join("/", zones);
            } else {
                return itemView.getContext().getString(R.string.ticket_offer_zone_range, zones.get(0), zones.get(zones.size() - 1));
            }
        }

        private void setTextForNumZones(int numZones) {
            binding.tvZonesTitle.setText(String.format(Locale.getDefault(), "%d", numZones));
            binding.tvZonesSubtitle.setText(itemView.getResources().getQuantityString(R.plurals.zones_pid_genitive, numZones));
        }

        private void setTextForValidity(Duration validity, ProductValidityType validityType) {
            float count;
            int formatCount;
            @PluralsRes int unit;
            if (validityType == ProductValidityType.TO_MIDNIGHT) {
                count = 1;
                unit = R.plurals.product_validity_days;
                formatCount = (int) count;
            } else {
                if (validity.toHours() < 2) {
                    count = validity.toMinutes();
                    unit = R.plurals.product_validity_minutes;
                    formatCount = (int) count;
                } else {
                    count = validity.toMinutes() / 60f;
                    formatCount = (int) count;
                    if (count == formatCount) {
                        unit = R.plurals.product_validity_hours;
                    } else {
                        unit = R.plurals.product_validity_hours_fractional;
                    }
                }
            }
            binding.tvTimeTitle.setText(String.format(Locale.getDefault(), "%.0f", count));
            binding.tvTimeSubtitle.setText(itemView.getContext().getResources().getQuantityString(unit, formatCount));
        }

        private void addProductCatIcon(ProductCategory cat) {
            int id = getProductCatIcon(cat);
            if (id == ResourcesCompat.ID_NULL) {
                return;
            }
            ImageView iv = new AppCompatImageView(binding.getRoot().getContext());
            iv.setImageResource(id);
            binding.fblCustomerProfiles.addView(iv);
        }

        private @DrawableRes int getProductCatIcon(ProductCategory cat) {
            return switch (cat) {
                case ADULT -> R.drawable.customer_profile_adult;
                case REDUCED -> R.drawable.customer_profile_reduced;
                case SENIOR -> R.drawable.customer_profile_senior;
                case DISABLED -> R.drawable.customer_profile_disabled;
                default -> ResourcesCompat.ID_NULL;
            };
        }
    }
}

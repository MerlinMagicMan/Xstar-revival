package io.xstarrevival.sdkprobe;

import android.os.Bundle;
import android.widget.ScrollView;
import android.widget.TextView;

import com.autel.common.CallbackWithOneParam;
import com.autel.common.battery.BatteryState;
import com.autel.common.error.AutelError;
import com.autel.common.product.AutelProductType;
import com.autel.sdk.Autel;
import com.autel.sdk.ProductConnectListener;
import com.autel.sdk.battery.XStarBattery;
import com.autel.sdk.product.BaseProduct;
import com.autel.sdk.product.XStarPremiumAircraft;

import android.support.v7.app.AppCompatActivity;

import java.util.Arrays;

/**
 * Deliberately read-only engineering probe.
 *
 * This class exposes no arm, motor, takeoff, landing, mission, calibration,
 * firmware, parameter-write, or battery-write APIs.
 */
public final class MainActivity extends AppCompatActivity {
    private TextView output;
    private XStarBattery battery;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        output = new TextView(this);
        output.setTextSize(16f);
        output.setPadding(32, 32, 32, 32);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(output);
        setContentView(scroll);

        line("X-STAR OFFICIAL SDK PROBE");
        line("READ-ONLY / PROPS-OFF BENCH MODE");
        line("");
        line("SDK auth: " + app().getAuthStatus());
        line("Waiting for product connection…");

        Autel.setProductConnectListener(new ProductConnectListener() {
            @Override
            public void productConnected(BaseProduct product) {
                runOnUiThread(() -> inspectProduct(product));
            }

            @Override
            public void productDisconnected() {
                clearBatteryListener();
                runOnUiThread(() -> line("Product disconnected"));
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        line("SDK auth: " + app().getAuthStatus());
    }

    @Override
    protected void onDestroy() {
        clearBatteryListener();
        super.onDestroy();
    }

    private void inspectProduct(BaseProduct product) {
        if (product == null) {
            line("Connected callback returned null product");
            return;
        }

        AutelProductType type = product.getType();
        line("");
        line("PRODUCT CONNECTED");
        line("Type: " + type);
        line("Class: " + product.getClass().getName());

        if (type != AutelProductType.PREMIUM || !(product instanceof XStarPremiumAircraft)) {
            line("Read-only battery probe currently enables only PREMIUM.");
            line("No control methods will be attempted.");
            return;
        }

        XStarPremiumAircraft aircraft = (XStarPremiumAircraft) product;
        battery = (XStarBattery) aircraft.getBattery();

        if (battery == null) {
            line("Battery interface: unavailable");
            return;
        }

        line("Battery interface: available");
        subscribeBatteryState();
        readBatterySnapshot();
    }

    private void subscribeBatteryState() {
        battery.setBatteryStateListener(new CallbackWithOneParam<BatteryState>() {
            @Override
            public void onSuccess(BatteryState state) {
                runOnUiThread(() -> line("Battery state: " + String.valueOf(state)));
            }

            @Override
            public void onFailure(AutelError error) {
                runOnUiThread(() -> line("Battery listener error: " + XStarProbeApplication.safeError(error)));
            }
        });
    }

    private void readBatterySnapshot() {
        battery.getVoltageCells(new CallbackWithOneParam<int[]>() {
            @Override
            public void onSuccess(int[] cells) {
                runOnUiThread(() -> line("Cell voltages: " + Arrays.toString(cells)));
            }

            @Override
            public void onFailure(AutelError error) {
                runOnUiThread(() -> line("Cell voltage error: " + XStarProbeApplication.safeError(error)));
            }
        });

        battery.getVoltage(floatCallback("Pack voltage"));
        battery.getCurrent(floatCallback("Current"));
        battery.getTemperature(floatCallback("Temperature"));
        battery.getDesignCapacity(floatCallback("Design capacity"));
        battery.getCapacity(floatCallback("Capacity"));

        battery.getRemainingPercent(new CallbackWithOneParam<Integer>() {
            @Override
            public void onSuccess(Integer value) {
                runOnUiThread(() -> line("Remaining percent: " + value));
            }

            @Override
            public void onFailure(AutelError error) {
                runOnUiThread(() -> line("Remaining percent error: " + XStarProbeApplication.safeError(error)));
            }
        });
    }

    private CallbackWithOneParam<Float> floatCallback(final String label) {
        return new CallbackWithOneParam<Float>() {
            @Override
            public void onSuccess(Float value) {
                runOnUiThread(() -> line(label + ": " + value));
            }

            @Override
            public void onFailure(AutelError error) {
                runOnUiThread(() -> line(label + " error: " + XStarProbeApplication.safeError(error)));
            }
        };
    }

    private void clearBatteryListener() {
        if (battery != null) {
            try {
                battery.setBatteryStateListener(null);
            } catch (Throwable ignored) {
                // Diagnostic app must not crash during teardown.
            }
            battery = null;
        }
    }

    private XStarProbeApplication app() {
        return (XStarProbeApplication) getApplication();
    }

    private void line(String value) {
        if (output == null) return;
        output.append(value + "\n");
    }
}

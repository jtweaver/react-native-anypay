package com.reactnativeanypay;

import android.bluetooth.BluetoothDevice;
import android.util.Log;

import androidx.annotation.MainThread;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;

import com.anywherecommerce.android.sdk.AnyPay;
import com.anywherecommerce.android.sdk.AppBackgroundingManager;
import com.anywherecommerce.android.sdk.AuthenticationListener;
import com.anywherecommerce.android.sdk.GenericEventListener;
import com.anywherecommerce.android.sdk.GenericEventListenerWithParam;
import com.anywherecommerce.android.sdk.LogStream;
import com.anywherecommerce.android.sdk.MeaningfulError;
import com.anywherecommerce.android.sdk.MeaningfulErrorListener;
import com.anywherecommerce.android.sdk.MeaningfulMessage;
import com.anywherecommerce.android.sdk.RequestListener;
import com.anywherecommerce.android.sdk.Terminal;
import com.anywherecommerce.android.sdk.devices.CardReader;
import com.anywherecommerce.android.sdk.devices.MultipleBluetoothDevicesFoundListener;
import com.anywherecommerce.android.sdk.devices.bbpos.BBPOSDevice;
import com.anywherecommerce.android.sdk.devices.CardReaderController;
import com.anywherecommerce.android.sdk.endpoints.AnyPayTransaction;
import com.anywherecommerce.android.sdk.endpoints.propay.PropayEndpoint;
import com.anywherecommerce.android.sdk.logging.LogConfigurationProperties;
import com.anywherecommerce.android.sdk.models.TransactionStatus;
import com.anywherecommerce.android.sdk.models.TransactionType;
import com.anywherecommerce.android.sdk.transactions.Transaction;
import com.anywherecommerce.android.sdk.transactions.listener.CardTransactionListener;
import com.anywherecommerce.android.sdk.util.Amount;
import com.bbpos.bbdevice.BBDeviceController;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.WritableNativeMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;

import java.util.List;

import javax.annotation.Nullable;

@MainThread
public class AnypayModule extends ReactContextBaseJavaModule {
  protected PropayEndpoint endpoint;
  protected Transaction refTransaction;
  protected CardReaderController cardReaderController;

  @MainThread
  AnypayModule(ReactApplicationContext context) {
    super(context);
  }

  @Override
  public String getName() {
    return "RNAnypay";
  }

  @ReactMethod
  public void startup() {
    Log.i("RNAnypay", "Starting up Anypay");
    AnyPay.initialize(getReactApplicationContext().getCurrentActivity().getApplication());

    if (!PermissionsController.verifyAppPermissions(getReactApplicationContext().getCurrentActivity())) {
      PermissionsController.requestAppPermissions(getReactApplicationContext().getCurrentActivity(), PermissionsController.permissions, 1001);
    }
  }

  @ReactMethod
  @MainThread
  public void initialize(ReadableMap config, Promise promise) {
    Log.i("RNAnypay", "Initializing Anypay");

    getReactApplicationContext().getCurrentActivity().runOnUiThread(() -> {
      try {
        Terminal.restoreState();
        endpoint = (PropayEndpoint) Terminal.getInstance().getEndpoint();
      }
      catch (Exception ex) {
        endpoint = new PropayEndpoint();
        Terminal.initialize(endpoint);
      }

      AppBackgroundingManager.get().registerListener(new AppBackgroundingManager.AppBackroundedListener() {
        @Override
        public void onBecameForeground() {
          Log.i("RNAnypay", "Caught app in foreground.");
        }

        @Override
        public void onBecameBackground() {
          Log.i("RNAnypay", "Caught app in background.");
        }
      });


      AnyPay.getSupportKey("pergopass", new RequestListener<String>() {
        @Override
        public void onRequestComplete(String s) {
          Log.i("RNAnypay", "Support Key - " + s);
        }

        @Override
        public void onRequestFailed(MeaningfulError meaningfulError) {
          Log.w("RNAnypay", "Support Key request failed");
        }
      });

      // Change logging configuration
      LogConfigurationProperties lp = LogStream.getLogger("device").getConfiguration();
      lp.remoteLoggingEnabled = true;
      lp.logLevel = "DEBUG";
      lp.logToConsole = true;
      LogStream.getLogger("device").applyConfiguration(lp);

      cardReaderController = CardReaderController.getControllerFor(BBPOSDevice.class);

      cardReaderController.subscribeOnCardReaderConnected(new GenericEventListenerWithParam<CardReader>() {
        @Override
        public void onEvent(CardReader deviceInfo) {
          if (deviceInfo == null)
            Log.i("RNAnypay", "Unknown device connected");
          else {
            Log.i("RNAnypay", "Card reader connected");

            WritableMap map = new WritableNativeMap();
            map.putString("message", deviceInfo.getModelDisplayName());
            sendEvent(getReactApplicationContext(), "CardReaderConnected", map);
          }
        }
      });

      cardReaderController.subscribeOnCardReaderDisconnected(new GenericEventListener() {
        @Override
        public void onEvent() {
          Log.w("RNAnypay", "Card reader disconnected");

          WritableMap map = new WritableNativeMap();
          map.putString("message", "Card reader disconnected");
          sendEvent(getReactApplicationContext(), "CardReaderDisconnected", map);
        }
      });

      cardReaderController.subscribeOnCardReaderConnectFailed(new MeaningfulErrorListener() {
        @Override
        public void onError(MeaningfulError error) {
          Log.i("RNAnypay", "Device connect failed: " + error.toString());

          WritableMap map = new WritableNativeMap();
          map.putString("error", error.toString());
          sendEvent(getReactApplicationContext(), "CardReaderConnectionFailed", map);
        }
      });

      cardReaderController.subscribeOnCardReaderError(new MeaningfulErrorListener() {
        @Override
        public void onError(MeaningfulError error) {
          Log.i("RNAnypay", "Device error: " + error.toString());

          WritableMap map = new WritableNativeMap();
          map.putString("error", error.toString());
          sendEvent(getReactApplicationContext(), "CardReaderError", map);
        }
      });

      endpoint.setCertStr(config.getString("certStr"));
      endpoint.setX509Cert(config.getString("x509Certificate"));
      endpoint.setXmlApiBaseUrl(config.getString("integrationServerUri"));
      endpoint.setJsonApiBaseUrl(config.getString("gatewayUrl"));
      endpoint.setAccountNum(config.getString("accountNumber"));
      endpoint.setTerminalId(config.getString("terminalId"));

      endpoint.validateConfiguration(new AuthenticationListener() {
        @Override
        public void onAuthenticationComplete() {
          Log.i("RNAnypay", "Endpoint Validation Success");

          Terminal.getInstance().getConfiguration().setProperty("endpoint", endpoint);
          Terminal.getInstance().saveState();

          WritableMap map = new WritableNativeMap();
          map.putBoolean("success", true);
          promise.resolve(map);
        }

        @Override
        public void onAuthenticationFailed(MeaningfulError meaningfulError) {
          Log.i("RNAnypay", "Propay Validation Error: " + meaningfulError.message);

          promise.reject("FAILED", meaningfulError.toString());
        }
      });
    });
  }

  @ReactMethod
  @MainThread
  public void connectBluetooth(Promise promise) {
    Log.i("RNAnypay", "Connecting to bluetooth");

    getReactApplicationContext().getCurrentActivity().runOnUiThread(() -> {
      cardReaderController.connectBluetooth(new MultipleBluetoothDevicesFoundListener() {
        @Override
        public void onMultipleBluetoothDevicesFound(List<BluetoothDevice> matchingDevices) {
          for (BluetoothDevice b : matchingDevices) {
            Log.i("RNAnypay", "Found reader " + b.getName());
          }

          Log.i("RNAnypay", "Connecting to " + matchingDevices.get(0).getName());
          cardReaderController.connectSpecificBluetoothDevice(matchingDevices.get(0));
        }
      });
    });

    promise.resolve("Connecting");
  }

  @ReactMethod
  public void emvSale(ReadableMap params, Promise promise) {
    Log.i("RNAnypay", "EMV sale");

    getReactApplicationContext().getCurrentActivity().runOnUiThread(() -> {

      BBDeviceController.setDebugLogEnabled(true);
      if (!CardReaderController.isCardReaderConnected()) {
        promise.reject("NOT_CONNECTED", "Reader not connected");
        return;
      }

      final AnyPayTransaction transaction = new AnyPayTransaction();
      transaction.setEndpoint(endpoint);
      transaction.useCardReader(CardReaderController.getConnectedReader());
      transaction.setTransactionType(TransactionType.SALE);
      transaction.setAddress("123 Main Street");
      transaction.setPostalCode("30004");
      transaction.setCurrency("USD");

      transaction.setTotalAmount(new Amount(params.getString("amount")));
      //        transaction.addCustomField("sessionToken", sessionToken);

      refTransaction = transaction;

      transaction.execute(new CardTransactionListener() {
        @Override
        public void onCardReaderEvent(MeaningfulMessage event) {
          Log.i("RNAnypay", "Card reader event");
          WritableMap map = new WritableNativeMap();
          map.putString("message", event.message);
          sendEvent(getReactApplicationContext(), "CardReaderEvent", map);
        }

        @Override
        public void onTransactionCompleted() {
          Log.i("RNAnypay", "Transactrion completed");

          if (transaction.isApproved()) {
            WritableMap map = new WritableNativeMap();
            map.putString("status", "APPROVED");
            map.putString("transactionId", transaction.getExternalId());
            map.putString("responseText", transaction.getResponseText());

            promise.resolve(map);
          } else if (transaction.getStatus() == TransactionStatus.DECLINED) {
            promise.reject("DECLINED", "EMV Sale Transaction DECLINED");
          } else {
            promise.reject("NOT_APPROVED", transaction.getStatusText());
          }
        }

        @Override
        public void onTransactionFailed(MeaningfulError reason) {
          promise.reject("FAILED", reason.toString());
        }
      });
    });
  }

  @ReactMethod
  public void cancelEMVSale(Promise promise) {
    Log.i("RNAnypay", "Cancelling EMV sale");

    getReactApplicationContext().getCurrentActivity().runOnUiThread(() -> {
      if (refTransaction != null) {
        refTransaction.cancel();
        refTransaction = null;
      }
    });
    promise.resolve("Cancelled");
  }

  @ReactMethod
  public void disconnect(Promise promise) {
    Log.i("RNAnypay", "Disconnecting reader");

    getReactApplicationContext().getCurrentActivity().runOnUiThread(() -> {
      if (refTransaction != null) {
        refTransaction.cancel();
        refTransaction = null;
      }
      cardReaderController.disconnectReader();
    });
  }

  @ReactMethod
  public void isReaderConnected(Promise promise) {
    Log.i("RNAnypay", "Checking reader connection");

    getReactApplicationContext().getCurrentActivity().runOnUiThread(() -> {
      promise.resolve(CardReaderController.isCardReaderConnected());
    });
  }

  private void sendEvent(ReactContext reactContext,
                         String eventName,
                         @Nullable WritableMap params) {
    reactContext
      .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
      .emit(eventName, params);
  }
}

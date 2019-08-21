
package com.reactlibrary;

import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;

import org.whispersystems.libsignal.DuplicateMessageException;
import org.whispersystems.libsignal.IdentityKeyPair;
import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.libsignal.InvalidKeyIdException;
import org.whispersystems.libsignal.InvalidMessageException;
import org.whispersystems.libsignal.InvalidVersionException;
import org.whispersystems.libsignal.LegacyMessageException;
import org.whispersystems.libsignal.UntrustedIdentityException;
import org.whispersystems.libsignal.util.KeyHelper;
import org.whispersystems.libsignal.state.PreKeyRecord;
import org.whispersystems.libsignal.state.SignedPreKeyRecord;

import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Promise;

import com.reactlibrary.axolotl.CryptoFailedException;
import com.reactlibrary.axolotl.XmppAxolotlSession;
import com.reactlibrary.storage.ProtocolStorage;
import com.reactlibrary.axolotl.XmppAxolotlService;

import android.util.Log;
import android.util.Base64;

import java.io.UnsupportedEncodingException;
import java.lang.Exception;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;


public class RNLibsignalProtocolModule extends ReactContextBaseJavaModule {

  private final ReactApplicationContext reactContext;
  public static final String RN_LIBSIGNAL_ERROR = "RN_LIBSIGNAL_ERROR";

  private ProtocolStorage protocolStorage;
  private XmppAxolotlService xmppAxolotlService;

  public RNLibsignalProtocolModule(ReactApplicationContext reactContext) {
    super(reactContext);
    this.reactContext = reactContext;

    protocolStorage = new ProtocolStorage(reactContext);
    xmppAxolotlService =  new XmppAxolotlService(reactContext, protocolStorage);
  }

  @Override
  public String getName() {
    return "RNLibsignalProtocol";
  }

  @ReactMethod
  public void generateIdentityKeyPair(Promise promise) {
    try {
      IdentityKeyPair identityKeyPair = KeyHelper.generateIdentityKeyPair();
      String publicKey = Base64.encodeToString(identityKeyPair.getPublicKey().serialize(), Base64.NO_WRAP);
      String privateKey = Base64.encodeToString(identityKeyPair.getPrivateKey().serialize(), Base64.NO_WRAP);
      String serializedKP = Base64.encodeToString(identityKeyPair.serialize(), Base64.NO_WRAP);
      WritableMap keyPairMap = Arguments.createMap();
      keyPairMap.putString("publicKey", publicKey);
      keyPairMap.putString("privateKey", privateKey);
      keyPairMap.putString("serializedKP", serializedKP);

      protocolStorage.setIdentityKeyPair(identityKeyPair);
      promise.resolve(keyPairMap);

    } catch (Exception e) {
      promise.reject(RN_LIBSIGNAL_ERROR, e.getMessage());
    }
  }

  @ReactMethod
  public void generateRegistrationId(Promise promise) {
    try {
      int registrationId = KeyHelper.generateRegistrationId(false);
      protocolStorage.setLocalRegistrationId(registrationId);
      promise.resolve(registrationId);
    } catch (Exception e) {
      e.printStackTrace();
      promise.reject(RN_LIBSIGNAL_ERROR, e.getMessage());
    }
  }

  @ReactMethod
  public void generatePreKeys(int startId, int count, Promise promise) {
    try {
      List<PreKeyRecord> preKeys = KeyHelper.generatePreKeys(startId, count);

      WritableArray preKeyMapsArray = Arguments.createArray();
      for (PreKeyRecord key : preKeys) {
        String preKeyPublic = Base64.encodeToString(key.getKeyPair().getPublicKey().serialize(), Base64.NO_WRAP);
        String preKeyPrivate = Base64.encodeToString(key.getKeyPair().getPrivateKey().serialize(), Base64.NO_WRAP);
        int preKeyId = key.getId();
        String seriaizedPreKey = Base64.encodeToString(key.serialize(), Base64.NO_WRAP);
        WritableMap preKeyMap = Arguments.createMap();
        preKeyMap.putString("preKeyPublic", preKeyPublic);
        preKeyMap.putString("preKeyPrivate", preKeyPrivate);
        preKeyMap.putInt("preKeyId", preKeyId);
        preKeyMap.putString("seriaizedPreKey", seriaizedPreKey);
        preKeyMapsArray.pushMap(preKeyMap);

        protocolStorage.storePreKey(preKeyId, key);
      }

      promise.resolve(preKeyMapsArray);
    } catch (Exception e) {
      e.printStackTrace();
      promise.reject(RN_LIBSIGNAL_ERROR, e.getMessage());
    }
  }

  @ReactMethod
  public void generateSignedPreKey(ReadableMap identityKeyPair, int signedKeyId, Promise promise) {
    try {
      byte[] serialized = Base64.decode(identityKeyPair.getString("serializedKP"), Base64.NO_WRAP);

      IdentityKeyPair IKP = new IdentityKeyPair(serialized);
      SignedPreKeyRecord signedPreKey = KeyHelper.generateSignedPreKey(IKP, signedKeyId);
      String signedPreKeyPublic = Base64.encodeToString(signedPreKey.getKeyPair().getPublicKey().serialize(), Base64.NO_WRAP);
      String signedPreKeyPrivate = Base64.encodeToString(signedPreKey.getKeyPair().getPrivateKey().serialize(), Base64.NO_WRAP);
      String signedPreKeySignature = Base64.encodeToString(signedPreKey.getSignature(), Base64.NO_WRAP);
      int signedPreKeyId = signedPreKey.getId();
      String seriaizedSignedPreKey = Base64.encodeToString(signedPreKey.serialize(), Base64.NO_WRAP);
      
      WritableMap signedPreKeyMap = Arguments.createMap();
      signedPreKeyMap.putString("signedPreKeyPublic", signedPreKeyPublic);
      signedPreKeyMap.putString("signedPreKeyPrivate", signedPreKeyPrivate);
      signedPreKeyMap.putString("signedPreKeySignature", signedPreKeySignature);
      signedPreKeyMap.putInt("signedPreKeyId", signedPreKeyId);
      signedPreKeyMap.putString("seriaizedSignedPreKey", seriaizedSignedPreKey);
      
      protocolStorage.storeSignedPreKey(signedPreKeyId, signedPreKey);

      promise.resolve(signedPreKeyMap);
    } catch (Exception e) {
      e.printStackTrace();
      promise.reject(RN_LIBSIGNAL_ERROR, e.getMessage());
    }
  }

  @ReactMethod
  public void buildSession(String recipientId, ReadableArray deviceListAndBundle, Promise promise) {
    try {
      ArrayList<ReadableMap> deviceListWithBundle = new ArrayList<ReadableMap>();
      for (int i = 0; i < deviceListAndBundle.size(); i++) {
        ReadableMap rm = deviceListAndBundle.getMap(i);
        WritableMap infoMapNew = Arguments.createMap();
        WritableMap empty = Arguments.createMap();
        empty.merge(rm.getMap("bundle"));
        infoMapNew.putInt("deviceId", rm.getInt("deviceId"));
        infoMapNew.putMap("bundle", empty);
        deviceListWithBundle.add(infoMapNew);
      }
      xmppAxolotlService.buildSession(recipientId, deviceListWithBundle);
      promise.resolve(true);
    } catch (InvalidKeyException e) {
      e.printStackTrace();
      promise.reject(RN_LIBSIGNAL_ERROR, e.getMessage());
    } catch (UntrustedIdentityException e) {
      e.printStackTrace();
      promise.reject(RN_LIBSIGNAL_ERROR, e.getMessage());
    }
  }

  @ReactMethod
  public void encrypt (String message, String recipientId, int deviceId, Promise promise) {
    try {
      promise.resolve(xmppAxolotlService.encrypt(message, recipientId, deviceId));
    } catch (UntrustedIdentityException e) {
      e.printStackTrace();
      promise.reject(RN_LIBSIGNAL_ERROR, e.getMessage());
    } catch (UnsupportedEncodingException e) {
      e.printStackTrace();
      promise.reject(RN_LIBSIGNAL_ERROR, e.getMessage());
    }
  }

  @ReactMethod
  public void encryptTwo (String ownId, int ownDeviceId, String recipientId, ReadableArray deviceList, String message, Promise promise) {
    try {
      ArrayList<Integer> deviceIds = new ArrayList<Integer>();
      for (int i = 0; i < deviceList.size(); i++) {
        deviceIds.add(deviceList.getInt(i));
      }
      promise.resolve(xmppAxolotlService.encryptTwo(ownId, ownDeviceId, recipientId, deviceIds, message));
    } catch (CryptoFailedException e) {
      e.printStackTrace();
      promise.reject(RN_LIBSIGNAL_ERROR, e.getMessage());
    }
  }

  @ReactMethod
  public void decryptTwo (String recipientId, int deviceId, String iV, ReadableArray keysList, String cipherText, Promise promise) {
    try {
      ArrayList keys = new ArrayList<>();
      for (int i = 0; i < keysList.size(); i++) {
        ReadableMap axKeys = keysList.getMap(i);
        keys.add(new XmppAxolotlSession.AxolotlKey(axKeys.getInt("deviceId"), Base64.decode(axKeys.getString("key"), Base64.NO_WRAP), axKeys.getBoolean("prekey")));
      }
      promise.resolve(xmppAxolotlService.decryptTwo(recipientId, deviceId, Base64.decode(iV, Base64.NO_WRAP), keys, Base64.decode(cipherText, Base64.NO_WRAP)));
    } catch (CryptoFailedException e) {
      e.printStackTrace();
      promise.reject(RN_LIBSIGNAL_ERROR, e.getMessage());
    }
  }

  @ReactMethod
  public void decrypt (String message, String recipientId, int deviceId, Promise promise) {
    try {
      promise.resolve(xmppAxolotlService.decrypt(message, recipientId, deviceId));
    } catch (UntrustedIdentityException e) {
      e.printStackTrace();
      promise.reject(RN_LIBSIGNAL_ERROR, e.getMessage());
    } catch (LegacyMessageException e) {
      e.printStackTrace();
      promise.reject(RN_LIBSIGNAL_ERROR, e.getMessage());
    } catch (InvalidMessageException e) {
      e.printStackTrace();
      promise.reject(RN_LIBSIGNAL_ERROR, e.getMessage());
    } catch (DuplicateMessageException e) {
      e.printStackTrace();
      promise.reject(RN_LIBSIGNAL_ERROR, e.getMessage());
    } catch (InvalidVersionException e) {
      e.printStackTrace();
      promise.reject(RN_LIBSIGNAL_ERROR, e.getMessage());
    } catch (InvalidKeyIdException e) {
      e.printStackTrace();
      promise.reject(RN_LIBSIGNAL_ERROR, e.getMessage());
    } catch (InvalidKeyException e) {
      e.printStackTrace();
      promise.reject(RN_LIBSIGNAL_ERROR, e.getMessage());
    }
  }


  /**
   *
   * This can be used to get the latest prekeys set
   * may be after decrypt, and update the user's bundle info.
   * (becuse after decryption the used prekey is removed)
   */
  @ReactMethod
  public void loadPreKeys(Promise promise) {
    List<PreKeyRecord> preKeys = protocolStorage.loadPreKeys();

    WritableArray preKeyMapsArray = Arguments.createArray();
    for (PreKeyRecord key : preKeys) {
      String preKeyPublic = Base64.encodeToString(key.getKeyPair().getPublicKey().serialize(), Base64.NO_WRAP);
      String preKeyPrivate = Base64.encodeToString(key.getKeyPair().getPrivateKey().serialize(), Base64.NO_WRAP);
      int preKeyId = key.getId();
      String seriaizedPreKey = Base64.encodeToString(key.serialize(), Base64.NO_WRAP);
      WritableMap preKeyMap = Arguments.createMap();
      preKeyMap.putString("preKeyPublic", preKeyPublic);
      preKeyMap.putString("preKeyPrivate", preKeyPrivate);
      preKeyMap.putInt("preKeyId", preKeyId);
      preKeyMap.putString("seriaizedPreKey", seriaizedPreKey);
      preKeyMapsArray.pushMap(preKeyMap);
    }
    promise.resolve(preKeyMapsArray);
  }
}
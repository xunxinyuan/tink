// Copyright 2017 Google Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//
////////////////////////////////////////////////////////////////////////////////

package com.google.cloud.crypto.tink.signature;

import com.google.cloud.crypto.tink.EcdsaProto.EcdsaKeyFormat;
import com.google.cloud.crypto.tink.EcdsaProto.EcdsaParams;
import com.google.cloud.crypto.tink.EcdsaProto.EcdsaPrivateKey;
import com.google.cloud.crypto.tink.EcdsaProto.EcdsaPublicKey;
import com.google.cloud.crypto.tink.KeyManager;
import com.google.cloud.crypto.tink.PublicKeySign;
import com.google.cloud.crypto.tink.TinkProto.KeyFormat;
import com.google.cloud.crypto.tink.Util;
import com.google.cloud.crypto.tink.subtle.EcdsaSignJce;
import com.google.cloud.crypto.tink.subtle.SubtleUtil;
import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;

final class EcdsaSignKeyManager implements KeyManager<PublicKeySign> {
  /**
   * Type url that this manager supports
   */
  static final String ECDSA_PRIVATE_KEY_TYPE =
      "type.googleapis.com/google.cloud.crypto.tink.EcdsaPrivateKey";

  /**
   * Current version of this key manager.
   * Keys with greater version are not supported.
   */
  private static final int VERSION = 0;

  @Override
  public PublicKeySign getPrimitive(Any proto) throws GeneralSecurityException {
    EcdsaPrivateKey privKeyProto;
    try {
      privKeyProto = EcdsaPrivateKey.parseFrom(proto.getValue());
    } catch (InvalidProtocolBufferException e) {
      throw new GeneralSecurityException("Invalid Ecdsa private key");
    }
    validateKey(privKeyProto);
    ECPrivateKey privateKey = Util.getEcPrivateKey(
        privKeyProto.getPublicKey().getParams().getCurve(),
        privKeyProto.getKeyValue().toByteArray());
    return new EcdsaSignJce(privateKey,
        SigUtil.hashToEcdsaAlgorithmName(privKeyProto.getPublicKey().getParams().getHashType()));
  }

  @Override
  public Any newKey(KeyFormat format) throws GeneralSecurityException {
    EcdsaKeyFormat ecdsaKeyFormat;
    try {
      ecdsaKeyFormat = EcdsaKeyFormat.parseFrom(format.getFormat().getValue());
    } catch (InvalidProtocolBufferException e) {
      throw new GeneralSecurityException("Invalid Ecdsa key format");
    }
    EcdsaParams ecdsaParams = ecdsaKeyFormat.getParams();
    SigUtil.validateEcdsaParams(ecdsaParams);
    ECParameterSpec ecParams = Util.getCurveSpec(ecdsaParams.getCurve());
    KeyPairGenerator keyGen = KeyPairGenerator.getInstance("EC");
    keyGen.initialize(ecParams);
    KeyPair keyPair = keyGen.generateKeyPair();
    ECPublicKey pubKey = (ECPublicKey) keyPair.getPublic();
    ECPrivateKey privKey = (ECPrivateKey) keyPair.getPrivate();
    ECPoint w = pubKey.getW();

    // Creates EcdsaPublicKey.
    EcdsaPublicKey ecdsaPubKey = EcdsaPublicKey.newBuilder()
        .setVersion(VERSION)
        .setParams(ecdsaParams)
        .setX(ByteString.copyFrom(w.getAffineX().toByteArray()))
        .setY(ByteString.copyFrom(w.getAffineY().toByteArray()))
        .build();

    //Creates EcdsaPrivateKey.
    EcdsaPrivateKey ecdsaPrivKey = EcdsaPrivateKey.newBuilder()
        .setVersion(VERSION)
        .setPublicKey(ecdsaPubKey)
        .setKeyValue(ByteString.copyFrom(privKey.getS().toByteArray()))
        .build();

    return Util.pack(ECDSA_PRIVATE_KEY_TYPE, ecdsaPrivKey);
  }

  @Override
  public boolean doesSupport(String typeUrl) {
    return ECDSA_PRIVATE_KEY_TYPE.equals(typeUrl);
  }

  private void validateKey(EcdsaPrivateKey privKey) throws GeneralSecurityException {
    SubtleUtil.validateVersion(privKey.getVersion(), VERSION);
    SigUtil.validateEcdsaParams(privKey.getPublicKey().getParams());
  }
}
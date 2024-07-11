/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */

package com.veda.central.service.auth;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.veda.central.core.user.profile.api.UserProfile;
import com.veda.central.core.user.profile.api.UserProfileRequest;
import com.veda.central.service.profile.UserProfileService;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.security.KeyPair;
import java.util.List;

@Service
public class TokenService {

    private static final Logger LOGGER = LoggerFactory.getLogger(TokenService.class);

    private final KeyLoader keyLoader;
    private final UserProfileService userProfileService;

    @Autowired
    public TokenService(KeyLoader keyLoader, UserProfileService userProfileService) {
        this.keyLoader = keyLoader;
        this.userProfileService = userProfileService;
    }


    public String generateWithCustomClaims(String token, long tenantId) throws Exception {
        KeyPair keyPair = keyLoader.getKeyPair();
        String keyID = keyLoader.getKeyID();

        SignedJWT signedJWT = SignedJWT.parse(token);

        JWTClaimsSet oldClaims = signedJWT.getJWTClaimsSet();
        String email = String.valueOf(oldClaims.getClaim("email"));

        JWTClaimsSet newClaims = null;
        try {
            if (StringUtils.isNotBlank(email)) {
                UserProfileRequest request = UserProfileRequest.newBuilder()
                        .setTenantId(tenantId)
                        .setProfile(UserProfile.newBuilder().setUsername(email).build())
                        .build();

                List<String> allGroupIDsOfUser = userProfileService.getAllGroupIDsOfUser(request);
                newClaims = new JWTClaimsSet.Builder(oldClaims)
                        .claim("groups", allGroupIDsOfUser)
                        .build();
            }

        } catch (Exception ex) {
            LOGGER.error("Error while adding custom claims to the token belongs to: {}", email);
        }

        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256)
                .keyID(keyID)
                .type(JOSEObjectType.JWT)
                .build();

        SignedJWT newSignedJWT = new SignedJWT(header, (newClaims != null ? newClaims : oldClaims));

        JWSSigner signer = new RSASSASigner(keyPair.getPrivate());
        newSignedJWT.sign(signer);

        return newSignedJWT.serialize();
    }

}
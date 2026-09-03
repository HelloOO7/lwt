#include "lwt_SecureTokenFormat.h"
#include "BitConverter.h"

using BC = BitConverter<std::endian::big>;

ByteVector SecureToken::CreateSignedToken(DigitalSignature& privateKey, const ByteSpan& message, KeyInfo keyInfo) {
    ByteVector messageCopy(message.begin(), message.end());
    return CreateSignedToken(privateKey, std::move(messageCopy), keyInfo);
}

ByteVector SecureToken::CreateSignedToken(DigitalSignature& privateKey, ByteVector&& message, KeyInfo keyInfo) {
    message.resize(message.size() + sizeof(keyInfo));
    // add key info to signed part
    BC::FromUInt32(keyInfo, message.data() + message.size() - sizeof(keyInfo));
    ByteVector signature;
    assert(privateKey.Sign(message, &signature) == PSA_SUCCESS);
    // insert signature
    message.insert(message.end(), signature.begin(), signature.end());
    // insert signature size footer
    message.resize(message.size() + sizeof(uint16_t));
    BC::FromUInt16(static_cast<uint16_t>(signature.size()), message.data() + message.size() - sizeof(uint16_t));
    return message;
}

bool SplitSignedToken(const ByteSpan& token, ByteSpan* pData, ByteSpan* pSignature) {
    uint16_t signatureSize;

    if (token.size() < sizeof(signatureSize)) {
        return false;
    }

    auto dataStart = token.data();
    auto dataEnd = dataStart + token.size();

    signatureSize = BC::ToUInt16(dataEnd - sizeof(signatureSize));
    if (signatureSize + sizeof(signatureSize) > token.size()) {
        return false;
    }

    auto signatureStart = dataEnd - sizeof(signatureSize) - signatureSize;

    *pData = ByteSpan(dataStart, signatureStart);
    *pSignature = ByteSpan(signatureStart, signatureSize);

    return true;
}

bool SecureToken::ParseSignedToken(const ByteSpan& token, ByteSpan* pData, ByteSpan* pSignature, KeyInfo* pKeyInfo) {
    if (!SplitSignedToken(token, pData, pSignature)) {
        return false;
    }

    if (pKeyInfo) {
        if (pData->size() < sizeof(KeyInfo)) {
            return false;
        }

        *pKeyInfo = BC::ToUInt32(pData->data() + pData->size() - sizeof(KeyInfo));
    }

    return true;
}

bool SecureToken::VerifySignedToken(DigitalSignature& publicKey, const ByteSpan& token) {
    ByteSpan data;
    ByteSpan signature;
    if (!SplitSignedToken(token, &data, &signature)) {
        return false;
    }

    return publicKey.Verify(data, signature);
}

bool SecureToken::VerifySignedToken(Certificate& publicKeyCert, const ByteSpan& token) {
    ByteSpan data;
    ByteSpan signature;
    if (!SplitSignedToken(token, &data, &signature)) {
        return false;
    }

    return publicKeyCert.VerifyMessage(data, signature);
}
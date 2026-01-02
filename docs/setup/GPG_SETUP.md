# GPG Signing Guide

Maven Central **requires** all artifacts to be signed with GPG. You cannot publish without it.

## 1. Install GPG
*   **Mac**: `brew install gpg`
*   **Linux**: `sudo apt-get install gnupg`
*   **Windows**: Download Gpg4win

## 2. Generate a Key
Run:
```bash
gpg --full-generate-key
```
*   Select `(1) RSA and RSA`
*   Key size: `4096`
*   Expiration: `0` (does not expire)
*   Enter your name and email (must match your Sonatype account if possible)
*   Set a strong passphrase.

## 3. List Keys
```bash
gpg --list-secret-keys --keyid-format LONG
```
Output example:
```
sec   rsa4096/8A10E5D812345678 2024-01-01 [SC]
      ...
```
Your **Key ID** is the last 8 characters: `12345678`.

## 4. Export Secret Key
Export the key in ASCII armor format (this goes into GitHub Secrets):
```bash
gpg --export-secret-keys --armor 12345678
```
Copy the *entire* block including `-----BEGIN PGP PRIVATE KEY BLOCK-----`.

## 5. Distribute Public Key
You must publish your public key to a keyserver so Sonatype can verify it:
```bash
gpg --keyserver keyserver.ubuntu.com --send-keys 12345678
```

## 6. Add to GitHub Secrets
Add these to your repository secrets:
*   `SIGNING_KEY_ID`: `12345678`
*   `SIGNING_PASSWORD`: Your passphrase
*   `SIGNING_KEY`: The ASCII armored private key block

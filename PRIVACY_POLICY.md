# Privacy Policy

**MyPassMan** (`my.passman`)

Effective date: October 27, 2026

MyPassMan is an open-source, local-first password manager for Android. This policy explains what
data the app handles, where that data is stored, and when it leaves your device.

In short: **the developer does not collect, receive, or have access to any of your data.** The app
has no developer-run servers, accounts, analytics, advertising, or crash reporting.

---

## 1. Data stored on your device

The app stores the following data only on your device:

- **Vault records**: titles, logins, passwords, notes, and tags you enter.
- **Settings**: theme, sort order, sync preferences, and similar options.
- **Unlock PIN**: stored only as a salted Argon2id hash. The PIN itself is never saved.
- **Cloud sync credentials**: if you turn on sync, the sync passphrase and the Yandex Disk access
  token. Both are encrypted with a key held in the Android Keystore.

The vault database is encrypted with SQLCipher. Its key is generated on the device and protected by
a non-exportable Android Keystore key, so it never leaves the device.

## 2. Biometric authentication

Fingerprint or face unlock is handled entirely by the Android system through `BiometricPrompt`.
MyPassMan never receives, stores, or transmits biometric data. It only learns whether
authentication succeeded.

## 3. Optional cloud sync

Cloud sync is **off by default**. It starts only if you choose a provider and sign in.

| Provider     | Where the backup is stored                                    | Access requested                    |
|--------------|---------------------------------------------------------------|-------------------------------------|
| Google Drive | The app's hidden application data folder (`appDataFolder`)    | `drive.appdata`, this folder only   |
| Yandex Disk  | The app's own folder on your Disk (`app:/backup.pman`)        | `cloud_api:disk.app_folder` only    |

With sync turned on:

- The app uploads a single backup file to **your own** cloud storage account. It cannot see your
  other files.
- **Before upload**, the backup is encrypted on your device with AES-256-GCM. The key is derived
  from your sync passphrase. The cloud provider and the developer cannot read the contents.
- To decide which copy is newer, the app also stores a modification timestamp with the file.
- Sync runs in the background, about every 15 minutes, while it is enabled.

Signing in and storing files are handled by Google and Yandex under their own privacy policies:

- Google: <https://policies.google.com/privacy>
- Yandex: <https://yandex.com/legal/confidential/>

To stop sync, sign out of the provider in Settings. Signing out of Google Drive also revokes the
app's access. You can delete the backup file at any time through your Google or Yandex account
settings.

## 4. Manual backup export and import

You can export your vault to a `.pman` file and import it again. The file is encrypted with
AES-256-GCM using a password you choose. It is saved only where you pick through the Android
system file picker. The app does not send it anywhere.

## 5. Android system backup

The encrypted vault database is excluded from Android cloud backup and device-to-device transfer.
Your settings may be included in Android's own system backup if you have it enabled. Any secrets
in them stay encrypted with a Keystore key that is not backed up, so they cannot be decrypted on
another device.

## 6. Other protections

- **Screenshots and screen recording** of the app are blocked, and the app content is hidden in
  the recent apps screen.
- **Copied passwords** are marked as sensitive, so Android 13 and later hides them in clipboard
  previews.

## 7. Permissions

| Permission             | Why it is needed                              |
|------------------------|-----------------------------------------------|
| `INTERNET`             | Only for optional Google Drive / Yandex Disk sync |
| `ACCESS_NETWORK_STATE` | To run background sync only when online       |

The app does not request access to contacts, location, camera, microphone, or device identifiers.

## 8. Data sharing

The developer does not sell, share, or transfer your data to anyone. Your data is sent only to
the cloud provider you choose, and only as an encrypted backup.

## 9. Data retention and deletion

All data stays on your device until you delete it. Uninstalling the app or clearing its data in
Android settings permanently deletes the local vault and its encryption keys. Cloud backups stay in
your Google Drive or Yandex Disk until you delete them there.

## 10. Children

MyPassMan is not directed at children and does not knowingly collect information from anyone.

## 11. Changes to this policy

If this policy changes, the updated version will be published in this repository with a new
effective date.

## 12. Contact

For questions about this policy, email <mdeveloperspost@gmail.com> or open an issue at
<https://github.com/kitfist0/MyPassMan/issues>.

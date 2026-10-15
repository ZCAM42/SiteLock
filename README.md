# SiteLock CI

Android starter app with a small IPv4 DNS filter and GitHub Actions APK build.

## Build the APK
1. Upload this project to the root of your GitHub repository.
2. Open **Actions** and run **Build SiteLock CI APK** (or push a commit).
3. Open the completed workflow run, download artifact `SiteLock-CI-debug-apk`, unzip it and install `app-debug.apk`.

## Important limitations
- This starter filters a short exact-domain list over IPv4 DNS. It is not a comprehensive adult-content or betting filter.
- DNS over HTTPS, other VPN apps, private DNS settings, IPv6 DNS, and apps using their own resolvers may bypass this starter. The implementation is not independently security-audited.
- The duration lock is an in-app commitment, not a secure anti-tamper guarantee. A normal app cannot prevent its own uninstall or force itself to stay enabled. Stronger enforcement requires managed-device/device-owner provisioning and testing.
- This is a debug APK, not a Play Store release or signed production build.

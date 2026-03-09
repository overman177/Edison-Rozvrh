# Privacy Policy

Effective date: 2026-03-09

This Privacy Policy explains how Edison Rozvrh ("the App") handles your information.

## 1. Who is responsible

Developer: Edison Rozvrh project maintainer
Repository: https://github.com/overman177/Edison-Rozvrh

## 2. What data the App processes

The App may process the following data after you log in to Edison:
- Study schedule information
- Study results and grades
- Study profile information available in Edison
- Session cookies from Edison SSO required to download the above data

The App may also send room query strings to VSB map services when you use map-related features.

## 3. How data is obtained

- Login is done through an embedded WebView to Edison SSO.
- The App reads Edison session cookies after successful login.
- The App uses those cookies to request your study data from Edison endpoints.

## 4. How data is used

Data is used only to provide App functionality:
- display your schedule,
- display your results/study information,
- open or resolve map locations for rooms.

## 5. Data sharing

The App does not operate its own backend for storing your personal data.

Your data is transmitted only to third-party systems needed for functionality:
- Edison SSO and Edison portals (for authentication and study data)
- VSB map services (when using room/map features)

The App does not sell your data.

## 6. Local storage and retention

The App stores downloaded schedule/results/study data locally on your device (SharedPreferences cache) to speed up loading.

You can remove this data by:
- clearing App data in Android settings, or
- uninstalling the App.

## 7. Permissions

The App uses the `INTERNET` permission to communicate with Edison and map services.

## 8. Analytics, ads, and tracking

The App does not include advertising SDKs.
The App does not include analytics or crash tracking services.
The App does not intentionally track users across apps or websites.

## 9. Children

The App is not directed to children under 13.

## 10. Security

Reasonable technical measures are used, but no method of electronic transmission or storage is 100% secure.

## 11. Changes to this policy

This Privacy Policy may be updated. The latest version will be published in this repository.

## 12. Contact

For privacy questions, open an issue at:
https://github.com/overman177/Edison-Rozvrh/issues

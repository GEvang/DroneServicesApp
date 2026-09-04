# Firebase App Distribution

The Android app is registered with Firebase and the Gradle upload plugin is enabled.

## Local publisher configuration

Create or edit your user Gradle properties file:

`C:\\Users\\DroneServices\\.gradle\\gradle.properties`

Add one recipient mechanism. A Firebase tester group is preferred:

```properties
firebaseGroups=management
firebaseReleaseNotes=Describe this update here
```

Alternatively, target individual Firebase testers:

```properties
firebaseTesters=boss@example.com
firebaseReleaseNotes=Describe this update here
```

Do not set both `firebaseGroups` and `firebaseTesters` for the same release.

## Sign the release build

Firebase distributions must be signed. Create and safeguard an Android release keystore, then create the ignored `keystore.properties` file in the project root:

```properties
storeFile=C:\\Users\\DroneServices\\.android\\drone-services-release.jks
storePassword=your-keystore-password
keyAlias=drone-services
keyPassword=your-key-password
```

Never commit the keystore or this properties file.

## Publish

From the project root, run:

```powershell
.\\gradlew.bat appDistributionUploadRelease
```

This builds the release APK, uploads it to Firebase, and notifies the selected Firebase testers. Increase `versionCode` in `app/build.gradle` for every new release.

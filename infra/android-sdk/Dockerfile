# Android build toolchain for car-launcher — everything runs here, nothing is installed on the host.
# Versions verified 2026-11-06: AGP 9.5.0 needs Gradle ≥ 9.6.0, JDK 17, Build Tools 36.0.0 (developer.android.com/build/releases/gradle-plugin).
FROM eclipse-temurin:17-jdk-jammy

ENV ANDROID_HOME=/opt/android-sdk GRADLE_HOME=/opt/gradle
ENV PATH=$PATH:/opt/android-sdk/cmdline-tools/latest/bin:/opt/android-sdk/platform-tools:/opt/gradle/bin

RUN apt-get update && apt-get install -y --no-install-recommends unzip curl && rm -rf /var/lib/apt/lists/*

# Command-line tools 15859902 — SHA-256 from developer.android.com/studio («Command line tools only»).
RUN curl -fsSL -o /tmp/clt.zip https://dl.google.com/android/repository/commandlinetools-linux-15859902_latest.zip \
 && echo "4e4c464f145a7512b57d088ac6c278c03c9eea610886b35a5e0804e74eedf583  /tmp/clt.zip" | sha256sum -c - \
 && mkdir -p $ANDROID_HOME/cmdline-tools && unzip -q /tmp/clt.zip -d /tmp/clt \
 && mv /tmp/clt/cmdline-tools $ANDROID_HOME/cmdline-tools/latest && rm -rf /tmp/clt /tmp/clt.zip

RUN yes | sdkmanager --licenses >/dev/null \
 && sdkmanager "platform-tools" "platforms;android-36" "build-tools;36.0.0"

# Gradle 9.6.0 (AGP 9.5 minimum).
RUN curl -fsSL -o /tmp/gradle.zip https://services.gradle.org/distributions/gradle-9.6.0-bin.zip \
 && unzip -q /tmp/gradle.zip -d /opt && mv /opt/gradle-9.6.0 $GRADLE_HOME && rm /tmp/gradle.zip

WORKDIR /workspace

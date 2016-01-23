#!/bin/bash

#This script can be run on any ubuntu machine, in order to make it a development machine for Andoid Studio.
#Should you not require NDK (native), comment out the relevant section, as it's taking ~3 Gb 
#I am using this script on top of a vagrant machine taken from: https://vagrantcloud.com/box-cutter/boxes/ubuntu1404-desktop

#This script is adapted from: https://github.com/samtstern/android-vagrant . The idea is to make faster, and independent of Docker, and direclty on Ubuntu 14 (as opposite to Xubuntu).

# Create studio user
STUDIO_USER=`whoami`
#STUDIO_USER=studio
#useradd -u 12345 -d /home/${STUDIO_USER} -s /bin/bash -m ${STUDIO_USER}
#sudo bash -c 'echo "${STUDIO_USER}        ALL=(ALL)       NOPASSWD: ALL" | (EDITOR="tee -a" visudo)'

# Dependencies
sudo apt-add-repository ppa:webupd8team/java
sudo apt-get update
sudo apt-get install -y oracle-java8-installer
#openjdk-7-jdk
sudo dpkg --add-architecture i386 && apt-get update && apt-get install -yq libstdc++6:i386 zlib1g:i386 libncurses5:i386 p7zip-full git ant maven --no-install-recommends
export GRADLE_VERSION=2.10
GRADLE_URL="http://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-all.zip"
curl -L ${GRADLE_URL} -o /tmp/gradle-${GRADLE_VERSION}-all.zip && sudo unzip /tmp/gradle-${GRADLE_VERSION}-all.zip -d /usr/local && sudo ln -sf /usr/local/gradle-${GRADLE_VERSION} /usr/local/gradle  && rm -f /tmp/gradle-${GRADLE_VERSION}-all.zip
echo "export GRADLE_HOME=/usr/local/gradle" >> /tmp/profile_to_add
echo "export PATH=\$PATH:\${GRADLE_HOME}/bin" >> /tmp/profile_to_add

# Download and untar SDK
ANDROID_SDK_VER='r24.4.1'
ANDROID_SDK_URL="http://dl.google.com/android/android-sdk_${ANDROID_SDK_VER}-linux.tgz"
curl -L ${ANDROID_SDK_URL} | sudo tar xz -C /usr/local
ANDROID_HOME=/usr/local/android-sdk-linux
sudo chown -R ${STUDIO_USER}   ${ANDROID_HOME} 
echo "export ANDROID_HOME=${ANDROID_HOME}"  >> /tmp/profile_to_add
echo "export PATH=\$PATH:\${ANDROID_HOME}/tools:\$ANDROID_HOME/platform-tools"  >> /tmp/profile_to_add


# Install Android SDK components
#ANDROID_SDK_COMPONENTS_VER="23"
#ANDROID_SDK_COMPONENTS_BUILD_TOOLS_VER="23.0.2"
#ANDROID_SDK_COMPONENTS="platform-tools,build-tools-${ANDROID_SDK_COMPONENTS_BUILD_TOOLS_VER},android-${ANDROID_SDK_COMPONENTS_VER},extra-android-support"
ANDROID_SDK_COMPONENTS="platform-tools"  #REST are installed by studio anyway, directly latest version.
echo y | ${ANDROID_HOME}/tools/android update sdk --no-ui --all --filter "${ANDROID_SDK_COMPONENTS}"

# Install Android NDK
NDK_HOME=/usr/local/android-ndk-linux/
NDK_VER='android-ndk-r10c'
NDK_FILE="/tmp/${NDK_VER}-darwin-x86_64.bin"
curl -L http://dl.google.com/android/ndk/${NDK_FILE} -o ${NDK_FILE}
sudo 7za x -o/usr/local/  ${NDK_FILE}
ln -sf /usr/local/${NDK_VER}  ${NDK_HOME}
rm -f ${NDK_FILE}

echo "export NDK_HOME=${NDK_HOME}"  >> /tmp/profile_to_add
echo "export PATH=\$PATH:\${NDK_HOME}"  >> /tmp/profile_to_add

# Path
echo "export PATH=\$PATH:${ANDROID_HOME}/tools:$ANDROID_HOME/platform-tools:${GRADLE_HOME}/bin"  >> ~/.profile

#####################

# Download and Unzip Android Studio
ANDROID_STUDIO_URL="https://dl.google.com/dl/android/studio/ide-zips/1.5.1.0/android-studio-ide-141.2456560-linux.zip"
curl -L ${ANDROID_STUDIO_URL} -o /tmp/android-studio-ide.zip && unzip /tmp/android-studio-ide.zip -d /usr/local && rm -f /tmp/android-studio-ide.zip
echo "export ANDROID_STUDIO_HOME=/usr/local/android-studio" >> /tmp/profile_to_add
echo "export PATH=\$PATH:\${ANDROID_STUDIO_HOME}/bin"  >> /tmp/profile_to_add
echo "export IBUS_ENABLE_SYNC_MODE=1"  >> /tmp/profile_to_add

# Install extra Android SDK
#ANDROID_SDK_EXTRA_COMPONENTS="extra-google-google_play_services,extra-google-m2repository,extra-android-m2repository,source-21,addon-google_apis-google-21,sys-img-x86-addon-google_apis-google-21"
#echo y | ${ANDROID_HOME}/tools/android update sdk --no-ui --all --filter "${ANDROID_SDK_EXTRA_COMPONENTS}"

# Path
sudo -S -i -u ${STUDIO_USER} bash -l -c 'cat  /tmp/profile_to_add >> ~/.profile'

# Set Android Studio entrypoint
#USER studio
#ENTRYPOINT studio.sh

From <https://developer.android.com/ndk/downloads/index.html> 
